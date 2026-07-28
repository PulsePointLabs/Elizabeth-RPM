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
enum class FuelDataSource { ECU_REPORTED, MAF_ESTIMATED, UNAVAILABLE }
enum class DriveAutomationPhase {
    IDLE,
    WAITING_FOR_ADAPTER,
    CONNECTING_TO_ADAPTER,
    WAITING_FOR_IGNITION,
    CONNECTING_TO_ECU,
    CONNECTED,
    RECORDING,
    RECONNECTING,
    HOLDING_TRIP,
    CONNECTION_UNAVAILABLE,
}

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
    val actualEquivalenceRatio: Double? = null,
    val fuelRateEstimated: Boolean = false,
    val manifoldPressureKpa: Double? = null,
    val barometricPressureKpa: Double? = null,
    val chargeAirC: Double? = null,
    val fuelRailPressureKpa: Double? = null,
    val acceleratorPedalPercent: Double? = null,
    val commandedThrottlePercent: Double? = null,
    val absoluteEngineLoad: Double? = null,
    val ambientC: Double? = null,
    val oilC: Double? = null,
    val driverDemandTorquePercent: Double? = null,
    val actualTorquePercent: Double? = null,
    val referenceTorqueNm: Double? = null,
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

data class SavedTripSummary(
    val id: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val summary: TripSummary,
)

data class SavedTrip(
    val id: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val summary: TripSummary,
    val samples: List<TelemetrySample>,
    val events: List<TripEvent>,
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
    val automaticConnection: Boolean = true,
    val automaticTrips: Boolean = true,
    val automaticTripEndDelayMinutes: Int = 3,
    val overlayDuringAutomaticTrips: Boolean = false,
    val overlayEnabled: Boolean = false,
    val fuelPricePerGallon: Double = 4.00,
    val fuelPriceSource: String = "Example local price · edit before use",
)

data class PairedObdDevice(val name: String, val address: String)

data class PidDiagnostic(
    val command: String,
    val name: String,
    val status: String,
    val response: String,
    val value: Double? = null,
)

data class DriveAutomationStatus(
    val phase: DriveAutomationPhase = DriveAutomationPhase.IDLE,
    val statusText: String = "Ready",
    val companionAssociated: Boolean = false,
    val backgroundServiceRunning: Boolean = false,
    val activeTripId: Long? = null,
    val activeTripAutomatic: Boolean = false,
    val lastSampleMillis: Long? = null,
    val pendingSamples: Int = 0,
    val lastFlushMillis: Long? = null,
    val reconnectCount: Int = 0,
    val graceStartedAtMillis: Long? = null,
    val graceEndsAtMillis: Long? = null,
    val recoveredAfterProcessDeath: Boolean = false,
    val lastSavedTripId: Long? = null,
    val lastSavedAtMillis: Long? = null,
)

data class ReadinessMonitor(
    val name: String,
    val complete: Boolean,
)

data class VehicleDiagnostics(
    val isLoading: Boolean = false,
    val vin: String? = null,
    val storedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val permanentDtcs: List<String> = emptyList(),
    val readinessMonitors: List<ReadinessMonitor> = emptyList(),
    val milOn: Boolean? = null,
    val freezeFrameAvailable: Boolean? = null,
    val lastCheckedMillis: Long? = null,
    val error: String? = null,
)

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
    val pidDiagnostics: Map<Int, PidDiagnostic> = emptyMap(),
    val diagnostics: VehicleDiagnostics = VehicleDiagnostics(),
    val protocolName: String? = null,
    val lastConnectionError: String? = null,
    val trip: TripSummary = TripSummary(),
    val savedTrips: List<SavedTripSummary> = emptyList(),
    val selectedTrip: SavedTrip? = null,
    val tripHistoryLoading: Boolean = false,
    val driveAutomation: DriveAutomationStatus = DriveAutomationStatus(),
    val settings: AppSettings = AppSettings(),
)
