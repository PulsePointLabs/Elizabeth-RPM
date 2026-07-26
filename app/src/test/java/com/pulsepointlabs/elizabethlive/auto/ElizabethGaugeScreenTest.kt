package com.pulsepointlabs.elizabethlive.auto

import android.content.Context
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.pulsepointlabs.elizabethlive.ConnectionState
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.ObdSessionController
import com.pulsepointlabs.elizabethlive.TelemetrySample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ElizabethGaugeScreenTest {
    @Test
    fun `car template contains four stable live metrics`() {
        val source = FakeObdSource(
            ElizabethUiState(
                connectionState = ConnectionState.CONNECTED,
                samples = listOf(
                    TelemetrySample(
                        timestampMillis = 1L,
                        rpm = 1_842.0,
                        speedKph = 88.0,
                        boostPsi = -2.9,
                        throttlePercent = 18.0,
                        coolantC = 92.0,
                        intakeC = 34.0,
                        shortFuelTrim = 1.0,
                        longFuelTrim = 2.0,
                        voltage = 14.1,
                        engineLoad = 28.0,
                        timingAdvance = 18.0,
                        fuelRateLitersPerHour = 4.4,
                    )
                ),
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = ElizabethGaugeScreen(TestCarContext.createCarContext(context), source)

        val template = screen.onGetTemplate() as PaneTemplate
        val rows = template.pane.rows

        assertEquals(4, rows.size)
        assertEquals("ENGINE RPM", rows[0].title?.toString())
        assertEquals("CALCULATED BOOST / VACUUM", rows[1].title?.toString())
        assertEquals("COOLANT TEMPERATURE", rows[2].title?.toString())
        assertEquals("CONTROL-MODULE VOLTAGE", rows[3].title?.toString())
        assertTrue(rows[0].texts.first().toString().contains("1,842 RPM"))
        assertTrue(rows[1].texts.first().toString().contains("-2.9 PSI"))
        assertTrue(rows[2].texts.first().toString().contains("197 °F"))
        assertTrue(rows[3].texts.first().toString().contains("14.1 V"))
        assertEquals(2, template.pane.actions.size)
    }

    @Test
    fun `disconnected template provides a working connect action`() {
        val source = FakeObdSource(ElizabethUiState())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = ElizabethGaugeScreen(TestCarContext.createCarContext(context), source)
        val template = screen.onGetTemplate() as PaneTemplate

        assertEquals(1, template.pane.actions.size)
        assertEquals("CONNECT", template.pane.actions.single().title?.toString())
        template.pane.actions.single().onClickDelegate?.sendClick(object : OnDoneCallback {})
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, source.connectCalls)
    }
}

private class FakeObdSource(initial: ElizabethUiState) : ObdSessionController {
    private val mutableState = MutableStateFlow(initial)
    var connectCalls = 0
    override val state: StateFlow<ElizabethUiState> = mutableState
    override fun connectSavedDevice(): Boolean {
        connectCalls++
        return true
    }
    override fun disconnect() = Unit
    override fun toggleTrip() = Unit
}
