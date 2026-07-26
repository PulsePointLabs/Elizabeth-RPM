package com.pulsepointlabs.elizabethlive.auto

import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pulsepointlabs.elizabethlive.ConnectionState
import com.pulsepointlabs.elizabethlive.ElizabethApplication
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.ObdSessionController
import com.pulsepointlabs.elizabethlive.R
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.UnitSystem
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Android Auto surface backed by the same read-only OBD session as the phone UI.
 *
 * Android Auto owns typography and placement, so the four stable rows intentionally keep
 * their titles fixed. Only the secondary value text changes, which the host treats as a
 * refresh instead of consuming the template-navigation quota on every telemetry sample.
 */
class ElizabethCarAppService : CarAppService() {
    // This first Android Auto build is distributed as a private debug sideload. Allowing the
    // developer host keeps it usable with Android Auto/DHU before a production host allowlist
    // and Play car-app review are introduced.
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session =
        ElizabethCarSession((application as ElizabethApplication).obdSession)
}

private class ElizabethCarSession(
    private val obdSession: ObdSessionController,
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        ElizabethGaugeScreen(carContext, obdSession)
}

@OptIn(FlowPreview::class)
internal class ElizabethGaugeScreen(
    carContext: CarContext,
    private val obdSession: ObdSessionController,
) : Screen(carContext), DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var state: ElizabethUiState = obdSession.state.value
    private var telemetryJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        if (state.connectionState == ConnectionState.DISCONNECTED) {
            obdSession.connectSavedDevice()
        }
        telemetryJob = scope.launch {
            obdSession.state
                .sample(750)
                .collect { update ->
                    state = update
                    invalidate()
                }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        telemetryJob?.cancel()
        scope.cancel()
    }

    override fun onGetTemplate(): Template {
        val sample = state.samples.lastOrNull()
        val pane = Pane.Builder()
            .addRow(metricRow(
                title = "ENGINE RPM",
                value = sample?.rpm?.toInt()?.let { "%,d RPM".format(it) } ?: unavailableValue(),
                detail = connectionDetail(sample),
                icon = R.drawable.ic_car_rpm,
                color = rpmColor(sample?.rpm),
            ))
            .addRow(metricRow(
                title = "CALCULATED BOOST / VACUUM",
                value = boostValue(sample, state.settings.units),
                detail = "MAP minus barometric pressure",
                icon = R.drawable.ic_car_boost,
                color = boostColor(sample?.boostPsi),
            ))
            .addRow(metricRow(
                title = "COOLANT TEMPERATURE",
                value = temperatureValue(sample?.coolantC, state.settings.units),
                detail = coolantStatus(sample?.coolantC),
                icon = R.drawable.ic_car_temp,
                color = coolantColor(sample?.coolantC),
            ))
            .addRow(metricRow(
                title = "CONTROL-MODULE VOLTAGE",
                value = sample?.voltage?.let { "${it.oneDecimal()} V" } ?: unavailableValue(),
                detail = voltageStatus(sample?.voltage),
                icon = R.drawable.ic_car_voltage,
                color = voltageColor(sample?.voltage),
            ))

        when (state.connectionState) {
            ConnectionState.DISCONNECTED -> pane.addAction(
                Action.Builder()
                    .setTitle("CONNECT")
                    .setOnClickListener {
                        obdSession.connectSavedDevice()
                        state = obdSession.state.value
                        invalidate()
                    }
                    .build()
            )
            ConnectionState.CONNECTED -> {
                pane.addAction(
                    Action.Builder()
                        .setTitle(if (state.trip.isRecording) "STOP TRIP" else "START TRIP")
                        .setOnClickListener {
                            obdSession.toggleTrip()
                            state = obdSession.state.value
                            invalidate()
                        }
                        .build()
                )
                pane.addAction(
                    Action.Builder()
                        .setTitle("DISCONNECT")
                        .setOnClickListener {
                            obdSession.disconnect()
                            state = obdSession.state.value
                            invalidate()
                        }
                        .build()
                )
            }
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> pane.addAction(
                Action.Builder()
                    .setTitle("CANCEL")
                    .setOnClickListener {
                        obdSession.disconnect()
                        state = obdSession.state.value
                        invalidate()
                    }
                    .build()
            )
        }

        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle("Elizabeth Live")
            .build()
        return PaneTemplate.Builder(pane.build())
            .setHeader(header)
            .build()
    }

    private fun metricRow(
        title: String,
        value: String,
        detail: String,
        icon: Int,
        color: CarColor,
    ): Row = Row.Builder()
        .setTitle(title)
        .addText(colored(value, color))
        .addText(detail)
        .setImage(
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, icon)
            ).build()
        )
        .build()

    private fun colored(text: String, color: CarColor): CharSequence =
        SpannableString(text).apply {
            setSpan(
                ForegroundCarColorSpan.create(color),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

    private fun unavailableValue(): String = when (state.connectionState) {
        ConnectionState.CONNECTING -> "CONNECTING…"
        ConnectionState.RECONNECTING -> "RECONNECTING…"
        else -> "—"
    }

    private fun connectionDetail(sample: TelemetrySample?): String = when {
        sample != null -> "Live from ${state.adapterName}"
        state.lastConnectionError != null -> state.lastConnectionError ?: "Connection problem"
        else -> state.connectionDetail
    }

    private fun boostValue(sample: TelemetrySample?, units: UnitSystem): String {
        val boostPsi = sample?.boostPsi ?: return unavailableValue()
        return if (units == UnitSystem.US) {
            "${boostPsi.signedOneDecimal()} PSI"
        } else {
            "${(boostPsi * 6.89476).signedOneDecimal()} KPA"
        }
    }

    private fun temperatureValue(valueC: Double?, units: UnitSystem): String {
        valueC ?: return unavailableValue()
        return if (units == UnitSystem.US) {
            "${(valueC * 9 / 5 + 32).toInt()} °F"
        } else {
            "${valueC.toInt()} °C"
        }
    }

    private fun coolantStatus(valueC: Double?): String = when {
        valueC == null -> "Not reported by ECU"
        valueC < 70 -> "Warming up"
        valueC <= 100 -> "Normal"
        valueC <= 110 -> "Warm"
        else -> "High"
    }

    private fun voltageStatus(voltage: Double?): String = when {
        voltage == null -> "Not reported by ECU"
        voltage < 11.8 -> "Low"
        voltage > 15.0 -> "High"
        else -> "Normal"
    }

    private fun rpmColor(rpm: Double?): CarColor = when {
        rpm == null -> CarColor.DEFAULT
        rpm >= 5_500 -> CarColor.RED
        rpm >= 4_000 -> CarColor.YELLOW
        else -> CarColor.BLUE
    }

    private fun boostColor(boostPsi: Double?): CarColor = when {
        boostPsi == null -> CarColor.DEFAULT
        boostPsi >= 16 -> CarColor.RED
        boostPsi >= 11 -> CarColor.YELLOW
        else -> CarColor.BLUE
    }

    private fun coolantColor(valueC: Double?): CarColor = when {
        valueC == null -> CarColor.DEFAULT
        valueC > 110 -> CarColor.RED
        valueC > 100 -> CarColor.YELLOW
        else -> CarColor.GREEN
    }

    private fun voltageColor(voltage: Double?): CarColor = when {
        voltage == null -> CarColor.DEFAULT
        voltage < 11.8 || voltage > 15.0 -> CarColor.RED
        voltage < 12.4 || voltage > 14.8 -> CarColor.YELLOW
        else -> CarColor.GREEN
    }

    private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
    private fun Double.signedOneDecimal(): String = String.format(Locale.US, "%+.1f", this)
}
