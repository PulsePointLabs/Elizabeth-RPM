package com.pulsepointlabs.elizabethlive

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }
enum class DriveScenario(val label: String) {
    IDLE("Idle"),
    CRUISE("Normal cruise"),
    MODERATE("Moderate acceleration"),
    HARD("Hard acceleration"),
    WARM_UP("Warm-up"),
    HEAT_SOAK("Heat-soak"),
    LOW_VOLTAGE_START("Low-voltage start"),
}
enum class TimeWindow(val label: String, val seconds: Int?) {
    THIRTY_SECONDS("30 sec", 30),
    TWO_MINUTES("2 min", 120),
    TEN_MINUTES("10 min", 600),
    TRIP("Trip", null),
}
enum class ThemeSetting { SYSTEM, LIGHT, DARK }
enum class UnitSystem { US, METRIC }

data class TelemetrySample(
    val timestampMillis: Long,
    val rpm: Double,
    val speedKph: Double,
    val boostPsi: Double,
    val throttlePercent: Double,
    val coolantC: Double,
    val intakeC: Double,
    val shortFuelTrim: Double,
    val longFuelTrim: Double,
    val voltage: Double,
    val engineLoad: Double,
    val timingAdvance: Double,
)

data class TripEvent(
    val timestampMillis: Long,
    val label: String,
    val detail: String,
)

data class TripSummary(
    val isRecording: Boolean = false,
    val startedAtMillis: Long? = null,
    val durationSeconds: Long = 1_487,
    val distanceKm: Double = 27.4,
    val averageSpeedKph: Double = 66.3,
    val maximumSpeedKph: Double = 111.0,
    val averageRpm: Double = 1_842.0,
    val maximumRpm: Double = 4_610.0,
    val maximumBoostPsi: Double = 15.4,
    val coolantRangeC: ClosedFloatingPointRange<Double> = 81.0..96.0,
    val intakeRangeC: ClosedFloatingPointRange<Double> = 24.0..46.0,
    val averageThrottle: Double = 21.0,
    val fuelTrimRange: ClosedFloatingPointRange<Double> = -4.7..7.8,
    val minimumVoltage: Double = 12.1,
    val events: List<TripEvent> = emptyList(),
)

data class HealthItem(
    val title: String,
    val summary: String,
    val status: HealthStatus,
)
enum class HealthStatus { GOOD, NOTICE, WARNING, UNSUPPORTED }

data class AppSettings(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val units: UnitSystem = UnitSystem.US,
    val smoothing: Boolean = true,
    val defaultWindow: TimeWindow = TimeWindow.THIRTY_SECONDS,
    val recordingIntervalMillis: Long = 500,
    val autoStartRecording: Boolean = false,
)

data class ElizabethUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionDetail: String = "Ready to connect",
    val adapterName: String = "vLinker MC+",
    val isSimulated: Boolean = true,
    val scenario: DriveScenario = DriveScenario.CRUISE,
    val samples: List<TelemetrySample> = emptyList(),
    val selectedChannels: Set<String> = setOf("RPM", "Boost", "Throttle"),
    val timeWindow: TimeWindow = TimeWindow.THIRTY_SECONDS,
    val graphPaused: Boolean = false,
    val inspectedSample: TelemetrySample? = null,
    val trip: TripSummary = TripSummary(),
    val settings: AppSettings = AppSettings(),
)

