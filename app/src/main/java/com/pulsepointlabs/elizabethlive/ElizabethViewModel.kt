package com.pulsepointlabs.elizabethlive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class ElizabethViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(ElizabethUiState())
    val state: StateFlow<ElizabethUiState> = mutableState.asStateFlow()
    private var demoJob: Job? = null
    private var tick = 0L

    init {
        startDemoStream()
    }

    fun connect() {
        if (state.value.connectionState != ConnectionState.DISCONNECTED) {
            mutableState.update {
                it.copy(connectionState = ConnectionState.DISCONNECTED, connectionDetail = "Disconnected")
            }
            return
        }
        viewModelScope.launch {
            mutableState.update {
                it.copy(connectionState = ConnectionState.CONNECTING, connectionDetail = "Selecting paired adapter…")
            }
            delay(650)
            mutableState.update { it.copy(connectionDetail = "Initializing ELM327…") }
            delay(650)
            mutableState.update { it.copy(connectionDetail = "Detecting protocol and supported PIDs…") }
            delay(750)
            mutableState.update {
                it.copy(
                    connectionState = ConnectionState.CONNECTED,
                    connectionDetail = "Demo source connected",
                    isSimulated = true,
                )
            }
        }
    }

    fun setScenario(scenario: DriveScenario) = mutableState.update { it.copy(scenario = scenario) }
    fun setWindow(window: TimeWindow) = mutableState.update { it.copy(timeWindow = window) }
    fun togglePaused() = mutableState.update {
        it.copy(graphPaused = !it.graphPaused, inspectedSample = if (it.graphPaused) null else it.samples.lastOrNull())
    }
    fun inspect(sample: TelemetrySample?) = mutableState.update { it.copy(inspectedSample = sample) }
    fun toggleChannel(channel: String) = mutableState.update {
        val changed = it.selectedChannels.toMutableSet().apply {
            if (!add(channel)) remove(channel)
        }
        it.copy(selectedChannels = changed)
    }

    fun toggleTrip() = mutableState.update {
        val nowRecording = !it.trip.isRecording
        it.copy(
            trip = if (nowRecording) {
                it.trip.copy(isRecording = true, startedAtMillis = System.currentTimeMillis(), events = emptyList())
            } else {
                it.trip.copy(isRecording = false)
            }
        )
    }

    fun deleteTrip() = mutableState.update { it.copy(trip = TripSummary()) }
    fun setTheme(theme: ThemeSetting) = mutableState.update {
        it.copy(settings = it.settings.copy(theme = theme))
    }
    fun setUnits(units: UnitSystem) = mutableState.update {
        it.copy(settings = it.settings.copy(units = units))
    }
    fun toggleSmoothing() = mutableState.update {
        it.copy(settings = it.settings.copy(smoothing = !it.settings.smoothing))
    }
    fun setDefaultWindow(window: TimeWindow) = mutableState.update {
        it.copy(settings = it.settings.copy(defaultWindow = window), timeWindow = window)
    }
    fun setRecordingInterval(intervalMillis: Long) = mutableState.update {
        it.copy(settings = it.settings.copy(recordingIntervalMillis = intervalMillis))
    }
    fun toggleAutoStart() = mutableState.update {
        it.copy(settings = it.settings.copy(autoStartRecording = !it.settings.autoStartRecording))
    }
    fun replayTrip() = mutableState.update {
        it.copy(
            connectionState = ConnectionState.CONNECTED,
            connectionDetail = "Replaying simulated trip",
            isSimulated = true,
            scenario = DriveScenario.CRUISE,
        )
    }

    private fun startDemoStream() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = state.value
                if (!snapshot.graphPaused) {
                    val sample = generateSample(snapshot.scenario)
                    val updated = (snapshot.samples + sample).takeLast(2_400)
                    val event = detectEvent(sample)
                    mutableState.update {
                        it.copy(
                            samples = updated,
                            trip = if (it.trip.isRecording && event != null) {
                                it.trip.copy(events = (it.trip.events + event).takeLast(20))
                            } else it.trip,
                        )
                    }
                }
                tick++
                delay(250)
            }
        }
    }

    private fun generateSample(scenario: DriveScenario): TelemetrySample {
        val phase = tick / 4.0
        val wave = sin(phase * PI / 6)
        val noise = { scale: Double -> Random.nextDouble(-scale, scale) }
        val values = when (scenario) {
            DriveScenario.IDLE -> ScenarioValues(760 + wave * 25, 0.0, -9.5, 5.2, 91.0, 31.0, 14.2)
            DriveScenario.CRUISE -> ScenarioValues(1_780 + wave * 190, 92 + wave * 4, -2.2 + wave, 18 + wave * 4, 92.0, 34.0, 14.15)
            DriveScenario.MODERATE -> ScenarioValues(2_850 + wave * 650, 72 + phase % 20, 8.2 + wave * 3, 44 + wave * 14, 93.0, 37.0, 14.1)
            DriveScenario.HARD -> ScenarioValues(4_200 + wave * 900, 85 + phase % 35, 15.0 + wave * 2, 78 + wave * 12, 95.0, 42.0, 13.95)
            DriveScenario.WARM_UP -> ScenarioValues(1_150 + wave * 120, 0.0, -8.2, 8 + wave * 2, max(28.0, 28 + tick * .12), 23.0, 14.3)
            DriveScenario.HEAT_SOAK -> ScenarioValues(790 + wave * 30, 0.0, -9.0, 5.5, 98.0, 59.0, 14.05)
            DriveScenario.LOW_VOLTAGE_START -> {
                val starting = tick % 40 < 8
                ScenarioValues(if (starting) 280.0 else 780.0, 0.0, -9.4, 6.0, 86.0, 30.0, if (starting) 10.6 else 14.25)
            }
        }
        return TelemetrySample(
            timestampMillis = System.currentTimeMillis(),
            rpm = max(0.0, values.rpm + noise(15.0)),
            speedKph = max(0.0, values.speed + noise(0.8)),
            boostPsi = values.boost + noise(.18),
            throttlePercent = values.throttle.coerceIn(0.0, 100.0) + noise(.35),
            coolantC = values.coolant + noise(.15),
            intakeC = values.intake + noise(.2),
            shortFuelTrim = wave * 3.2 + noise(.5),
            longFuelTrim = 2.3 + noise(.15),
            voltage = values.voltage + noise(.025),
            engineLoad = (values.throttle * 1.08).coerceIn(0.0, 100.0),
            timingAdvance = 16 + wave * 6,
        )
    }

    private fun detectEvent(sample: TelemetrySample): TripEvent? = when {
        sample.voltage < 11.5 -> TripEvent(sample.timestampMillis, "Low voltage", "${"%.1f".format(sample.voltage)} V")
        sample.rpm > 4_500 -> TripEvent(sample.timestampMillis, "High RPM", "${sample.rpm.toInt()} rpm")
        sample.boostPsi > 14.8 -> TripEvent(sample.timestampMillis, "Peak boost", "${"%.1f".format(sample.boostPsi)} psi")
        sample.intakeC > 55 -> TripEvent(sample.timestampMillis, "High intake temperature", "${sample.intakeC.toInt()} °C")
        else -> null
    }

    private data class ScenarioValues(
        val rpm: Double,
        val speed: Double,
        val boost: Double,
        val throttle: Double,
        val coolant: Double,
        val intake: Double,
        val voltage: Double,
    )
}
