package com.pulsepointlabs.elizabethlive

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }
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
    val rpm: Double?,
    val speedKph: Double?,
    val boostPsi: Double?,
    val throttlePercent: Double?,
    val coolantC: Double?,
    val intakeC: Double?,
    val shortFuelTrim: Double?,
    val longFuelTrim: Double?,
    val voltage: Double?,
    val engineLoad: Double?,
    val timingAdvance: Double?,
    val fuelRateLitersPerHour: Double?,
    val massAirFlowGramsPerSecond: Double? = null,
    val commandedEquivalenceRatio: Double? = null,
    val fuelRateEstimated: Boolean = false,
)

data class TripEvent(
    val timestampMillis: Long,
    val label: String,
    val detail: String,
)

data class TripSummary(
    val isRecording: Boolean = false,
    val startedAtMillis: Long? = null,
    val durationSeconds: Long = 0,
    val distanceKm: Double = 0.0,
    val averageSpeedKph: Double = 0.0,
    val maximumSpeedKph: Double = 0.0,
    val averageRpm: Double = 0.0,
    val maximumRpm: Double = 0.0,
    val maximumBoostPsi: Double = 0.0,
    val coolantRangeC: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val intakeRangeC: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val averageThrottle: Double = 0.0,
    val fuelTrimRange: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val minimumVoltage: Double = 0.0,
    val fuelUsedLiters: Double = 0.0,
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
    val fuelPricePerGallon: Double = 4.00,
    val fuelPriceSource: String = "Example local price · edit before use",
)

data class PairedObdDevice(val name: String, val address: String)

data class ElizabethUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionDetail: String = "Ready to connect",
    val adapterName: String = "vLinker MC+",
    val samples: List<TelemetrySample> = emptyList(),
    val selectedChannels: Set<String> = setOf("RPM", "Boost", "Throttle"),
    val timeWindow: TimeWindow = TimeWindow.THIRTY_SECONDS,
    val graphPaused: Boolean = false,
    val inspectedSample: TelemetrySample? = null,
    val liveDriveStartedAtMillis: Long = System.currentTimeMillis(),
    val liveFuelUsedLiters: Double = 0.0,
    val liveDistanceKm: Double = 0.0,
    val pairedDevices: List<PairedObdDevice> = emptyList(),
    val showDevicePicker: Boolean = false,
    val supportedPids: Set<Int> = emptySet(),
    val protocolName: String? = null,
    val lastConnectionError: String? = null,
    val trip: TripSummary = TripSummary(),
    val settings: AppSettings = AppSettings(),
)
