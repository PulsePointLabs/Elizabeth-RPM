package com.pulsepointlabs.elizabethlive

import android.annotation.SuppressLint
import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.pulsepointlabs.elizabethlive.automation.AutomationDecision
import com.pulsepointlabs.elizabethlive.automation.DriveAutomationEngine
import com.pulsepointlabs.elizabethlive.automation.DriveAutomationService
import com.pulsepointlabs.elizabethlive.connection.RememberedAdapterDecision
import com.pulsepointlabs.elizabethlive.connection.RememberedAdapterPolicy
import com.pulsepointlabs.elizabethlive.obd.elm327.Elm327Client
import com.pulsepointlabs.elizabethlive.obd.pid.PollPriority
import com.pulsepointlabs.elizabethlive.obd.pid.StandardPids
import com.pulsepointlabs.elizabethlive.obd.transport.BluetoothClassicObdTransport
import com.pulsepointlabs.elizabethlive.overlay.FloatingTripOverlayService
import com.pulsepointlabs.elizabethlive.data.ElizabethDatabase
import com.pulsepointlabs.elizabethlive.data.TripRepository
import com.pulsepointlabs.elizabethlive.trip.FuelEfficiencyCalculator
import com.pulsepointlabs.elizabethlive.trip.TripSummaryCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ElizabethObdSession(private val application: Application) : ObdSessionController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = application.getSharedPreferences("elizabeth_settings", 0)
    private val hasSavedFuelPrice = preferences.contains("fuel_price_per_gallon")
    private val savedFuelPrice = preferences.getFloat("fuel_price_per_gallon", 4.00f).toDouble()
    private val mutableState = MutableStateFlow(
        ElizabethUiState().let { initial ->
            initial.copy(
                adapterName = preferences.getString("obd_device_name", null) ?: initial.adapterName,
                settings = initial.settings.copy(
                    theme = preferences.getString("theme", null)
                        ?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() }
                        ?: initial.settings.theme,
                    units = preferences.getString("units", null)
                        ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                        ?: initial.settings.units,
                    smoothing = preferences.getBoolean("graph_smoothing", initial.settings.smoothing),
                    defaultWindow = preferences.getString("default_window", null)
                        ?.let { runCatching { TimeWindow.valueOf(it) }.getOrNull() }
                        ?: initial.settings.defaultWindow,
                    recordingIntervalMillis = preferences.getLong(
                        "recording_interval_millis",
                        initial.settings.recordingIntervalMillis,
                    ),
                    autoStartRecording = preferences.getBoolean(
                        "auto_start_recording",
                        initial.settings.autoStartRecording,
                    ),
                    automaticConnection = preferences.getBoolean(
                        "automatic_connection",
                        initial.settings.automaticConnection,
                    ),
                    automaticTrips = preferences.getBoolean(
                        "automatic_trips",
                        initial.settings.automaticTrips,
                    ),
                    automaticTripEndDelayMinutes = preferences.getInt(
                        "automatic_trip_end_delay_minutes",
                        initial.settings.automaticTripEndDelayMinutes,
                    ),
                    overlayDuringAutomaticTrips = preferences.getBoolean(
                        "overlay_during_automatic_trips",
                        initial.settings.overlayDuringAutomaticTrips,
                    ),
                    overlayEnabled = preferences.getBoolean(
                        "floating_trip_overlay",
                        initial.settings.overlayEnabled,
                    ),
                    fuelPricePerGallon = savedFuelPrice,
                    fuelPriceSource = if (hasSavedFuelPrice) {
                        "Saved local regular-gas price"
                    } else {
                        initial.settings.fuelPriceSource
                    },
                )
            )
        }
    )
    override val state: StateFlow<ElizabethUiState> = mutableState.asStateFlow()

    private val transport = BluetoothClassicObdTransport(application)
    private val elm = Elm327Client(transport)
    private val tripRepository = TripRepository(ElizabethDatabase.get(application))
    private val recordedTripSamples = mutableListOf<TelemetrySample>()
    private val recordedTripEvents = mutableListOf<TripEvent>()
    private val pendingTripSamples = mutableListOf<TelemetrySample>()
    private val pendingTripEvents = mutableListOf<TripEvent>()
    private val lastTripEventMillis = mutableMapOf<String, Long>()
    private val tripMutex = Mutex()
    private val automationEngine = DriveAutomationEngine()
    private var activeTripId: Long? = null
    private var activeTripAutomatic = false
    private var activeTripRecovered = false
    private var fuelDataSource = FuelDataSource.UNAVAILABLE
    private var selectedDevice: PairedObdDevice? =
        preferences.getString("obd_device_address", null)?.let { address ->
            PairedObdDevice(
                name = preferences.getString("obd_device_name", null) ?: "vLinker MC+",
                address = address,
            )
        }
    private var connectionJob: Job? = null
    private var pollingJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var reconnectJob: Job? = null
    private var flushJob: Job? = null
    private var graceJob: Job? = null
    private var reconnectBackoffAttempt = 0
    private var reconnectCount = 0
    private var serviceRunning = false
    private var userDisconnected = false
    private val recoveryComplete = CompletableDeferred<Unit>()

    init {
        scope.launch {
            tripRepository.trips.collect { trips ->
                mutableState.update { it.copy(savedTrips = trips) }
            }
        }
        scope.launch {
            try {
                recoverActiveTrip()
            } finally {
                recoveryComplete.complete(Unit)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun prepareConnection() {
        if (state.value.connectionState != ConnectionState.DISCONNECTED) {
            disconnect()
            return
        }
        userDisconnected = false
        if (connectSavedDevice()) return
        showAdapterPicker()
    }

    @SuppressLint("MissingPermission")
    fun prepareAutomaticConnectionOnOpen() {
        if (!state.value.settings.automaticConnection) return
        if (
            state.value.connectionState == ConnectionState.DISCONNECTED &&
            connectionJob?.isActive != true &&
            !connectSavedDevice()
        ) {
            showAdapterPicker()
        }
    }

    @SuppressLint("MissingPermission")
    fun changeAdapter() {
        userDisconnected = true
        disconnectInternal(manual = true)
        showAdapterPicker()
    }

    @SuppressLint("MissingPermission")
    private fun showAdapterPicker() {
        val adapter = application.getSystemService(BluetoothManager::class.java)
            .adapter
        if (adapter == null) {
            showConnectionError("Bluetooth is not available on this phone.")
            return
        }
        if (!adapter.isEnabled) {
            showConnectionError("Bluetooth is disabled. Turn it on, then tap Connect again.")
            return
        }
        val devices = adapter.bondedDevices
            .map { PairedObdDevice(it.name ?: "Paired OBD adapter", it.address) }
            .sortedWith(
                compareByDescending<PairedObdDevice> {
                    it.name.contains("vLinker", ignoreCase = true)
                }.thenBy { it.name.lowercase() }
            )
        mutableState.update {
            it.copy(
                pairedDevices = devices,
                showDevicePicker = true,
                driveAutomation = it.driveAutomation.copy(
                    phase = if (devices.isEmpty()) {
                        DriveAutomationPhase.CONNECTION_UNAVAILABLE
                    } else {
                        DriveAutomationPhase.WAITING_FOR_ADAPTER
                    },
                    statusText = if (devices.isEmpty()) {
                        "Connection unavailable"
                    } else {
                        "Select the paired vLinker"
                    },
                ),
                lastConnectionError = if (devices.isEmpty()) {
                    "No paired Bluetooth devices found. Pair the vLinker MC+ in Android Settings first."
                } else null,
            )
        }
    }

    fun dismissDevicePicker() = mutableState.update { it.copy(showDevicePicker = false) }

    fun selectDevice(device: PairedObdDevice) {
        userDisconnected = false
        rememberDevice(device)
        mutableState.update {
            it.copy(
                showDevicePicker = false,
                adapterName = device.name,
                samples = emptyList(),
                supportedPids = emptySet(),
                lastConnectionError = null,
            )
        }
        connectToSelectedDevice(reconnecting = false)
    }

    fun onBluetoothPermissionDenied() {
        showConnectionError("Bluetooth permission denied. Elizabeth Live cannot reach the paired vLinker.")
    }

    @SuppressLint("MissingPermission")
    override fun connectSavedDevice(): Boolean {
        if (state.value.connectionState != ConnectionState.DISCONNECTED || connectionJob?.isActive == true) {
            return true
        }
        if (
            ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showConnectionError("Open Elizabeth Live on the phone and allow Bluetooth access first.")
            return false
        }
        val adapter = application.getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled) {
            showConnectionError("Bluetooth is unavailable or disabled on the phone.")
            return false
        }
        val remembered = selectedDevice
        val decision = RememberedAdapterPolicy.resolve(
            remembered,
            adapter.bondedDevices.map { it.address },
        )
        if (decision !is RememberedAdapterDecision.Connect) {
            mutableState.update {
                it.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    connectionDetail = "Waiting for adapter",
                    lastConnectionError = if (remembered == null) {
                        "No remembered adapter. Tap Change adapter to select the paired vLinker."
                    } else {
                        "The remembered adapter is no longer paired. Tap Change adapter."
                    },
                    driveAutomation = it.driveAutomation.copy(
                        phase = DriveAutomationPhase.WAITING_FOR_ADAPTER,
                        statusText = "Waiting for adapter",
                    ),
                )
            }
            return false
        }
        val device = decision.device
        mutableState.update { it.copy(adapterName = device.name, lastConnectionError = null) }
        connectToSelectedDevice(reconnecting = false)
        return true
    }

    private fun rememberDevice(device: PairedObdDevice) {
        selectedDevice = device
        preferences.edit()
            .putString("obd_device_name", device.name)
            .putString("obd_device_address", device.address)
            .apply()
    }

    override fun disconnect() {
        userDisconnected = true
        disconnectInternal(manual = true)
    }

    private fun disconnectInternal(manual: Boolean) {
        connectionJob?.cancel()
        pollingJob?.cancel()
        diagnosticsJob?.cancel()
        reconnectJob?.cancel()
        connectionJob = null
        pollingJob = null
        diagnosticsJob = null
        reconnectJob = null
        scope.launch { transport.disconnect() }
        mutableState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectionDetail = if (manual) "Connection unavailable" else "Waiting for adapter",
                showDevicePicker = false,
                driveAutomation = it.driveAutomation.copy(
                    phase = if (manual) {
                        DriveAutomationPhase.CONNECTION_UNAVAILABLE
                    } else {
                        DriveAutomationPhase.WAITING_FOR_ADAPTER
                    },
                    statusText = if (manual) "Connection unavailable" else "Waiting for adapter",
                ),
            )
        }
    }

    private fun connectToSelectedDevice(reconnecting: Boolean) {
        val device = selectedDevice ?: return
        if (userDisconnected) return
        connectionJob?.cancel()
        pollingJob?.cancel()
        connectionJob = scope.launch {
            recoveryComplete.await()
            mutableState.update {
                it.copy(
                    connectionState = if (reconnecting) ConnectionState.RECONNECTING else ConnectionState.CONNECTING,
                    connectionDetail = if (reconnecting) "Reconnecting to adapter" else "Connecting to adapter",
                    lastConnectionError = null,
                    driveAutomation = it.driveAutomation.copy(
                        phase = if (reconnecting) {
                            DriveAutomationPhase.RECONNECTING
                        } else {
                            DriveAutomationPhase.CONNECTING_TO_ADAPTER
                        },
                        statusText = if (reconnecting) {
                            "Connection interrupted · retrying"
                        } else {
                            "Connecting to adapter"
                        },
                    ),
                )
            }
            val connected = transport.connect(device.address)
            if (connected.isFailure) {
                handleConnectionFailure(
                    "Adapter connection failed: ${connected.exceptionOrNull()?.message ?: "unknown error"}",
                    ignitionOff = false,
                )
                return@launch
            }
            mutableState.update {
                it.copy(
                    connectionDetail = "Adapter connected · connecting to ECU",
                    driveAutomation = it.driveAutomation.copy(
                        phase = DriveAutomationPhase.CONNECTING_TO_ECU,
                        statusText = "Adapter connected · connecting to ECU",
                    ),
                )
            }
            val initialized = elm.initialize { detail ->
                mutableState.update { it.copy(connectionDetail = detail) }
            }
            if (initialized.isFailure) {
                transport.disconnect()
                val reason = initialized.exceptionOrNull()?.message ?: "unknown error"
                handleConnectionFailure(
                    "ELM327 initialization failed: $reason",
                    ignitionOff = reason.contains("ignition", ignoreCase = true) ||
                        reason.contains("NO DATA", ignoreCase = true),
                )
                return@launch
            }
            val result = initialized.getOrThrow()
            reconnectBackoffAttempt = 0
            mutableState.update {
                val confirmingRecoveredTrip =
                    it.trip.isRecording && automationEngine.graceStartedAtMillis != null
                it.copy(
                    connectionState = ConnectionState.CONNECTED,
                    connectionDetail = if (confirmingRecoveredTrip) {
                        "ECU connected · confirming engine"
                    } else if (it.trip.isRecording) {
                        "Elizabeth connected · trip recording"
                    } else {
                        "Connected"
                    },
                    supportedPids = result.supportedPids,
                    pidDiagnostics = emptyMap(),
                    diagnostics = VehicleDiagnostics(),
                    protocolName = result.protocolName,
                    lastConnectionError = null,
                    driveAutomation = it.driveAutomation.copy(
                        phase = if (confirmingRecoveredTrip) {
                            DriveAutomationPhase.HOLDING_TRIP
                        } else if (it.trip.isRecording) {
                            DriveAutomationPhase.RECORDING
                        } else {
                            DriveAutomationPhase.CONNECTED
                        },
                        statusText = if (confirmingRecoveredTrip) {
                            "ECU connected · confirming engine"
                        } else if (it.trip.isRecording) {
                            "Elizabeth connected · trip recording"
                        } else {
                            "Connected"
                        },
                    ),
                )
            }
            startPolling()
            refreshDiagnostics()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            /*
             * The Mode 01 support bitmap is useful guidance, but some vehicles/adapters return an
             * incomplete bitmap even though direct requests succeed. Probe only our small curated
             * registry at its assigned rate and promote every successful reply into supportedPids.
             * This avoids both blank real sensors and the "poll everything" behavior we do not want.
             */
            // Poll both classic and newer multi-sensor forms. A support bitmap can merge ECU
            // replies or omit a directly readable PID, so the first successful form wins.
            val initiallyReported = state.value.supportedPids
            val registry = StandardPids.registry.filter { definition ->
                definition.pid in initiallyReported || definition.pid in CoreDirectProbePids
            }
            // Optional extended SAE values are polled only when the ECU advertises them. This keeps
            // an unsupported convenience gauge from triggering a long 29-bit route search and
            // starving RPM, MAP, throttle, or speed updates.
            val fast = registry.filter { it.priority == PollPriority.FAST }
            val medium = registry.filter { it.priority == PollPriority.MEDIUM }
            val slow = registry.filter { it.priority == PollPriority.SLOW }
            val values = mutableMapOf<Int, Double>()
            val payloads = mutableMapOf<Int, List<Int>>()
            var cycle = 0
            var mediumIndex = 0
            var slowIndex = 0
            var consecutiveFailures = 0
            var missingRpmCycles = 0
            var previousTimestamp = System.currentTimeMillis()

            while (isActive) {
                val batch = buildList {
                    addAll(fast)
                    if (medium.isNotEmpty()) {
                        add(medium[mediumIndex % medium.size])
                        mediumIndex++
                    }
                    if (cycle % 8 == 0 && slow.isNotEmpty()) {
                        add(slow[slowIndex % slow.size])
                        slowIndex++
                    }
                }.distinctBy { it.pid }

                for (definition in batch) {
                    val result = elm.readObserved(definition)
                    if (result.isFailure) {
                        values.remove(definition.pid)
                        payloads.remove(definition.pid)
                        consecutiveFailures++
                        updatePidDiagnostic(
                            definition.pid,
                            PidDiagnostic(
                                command = "%02X%02X".format(definition.mode, definition.pid),
                                name = definition.name,
                                status = "TRANSPORT ERROR",
                                response = result.exceptionOrNull()?.message ?: "Unknown transport error",
                            ),
                        )
                        if (consecutiveFailures >= 3) {
                            beginReconnect()
                            return@launch
                        }
                    } else {
                        consecutiveFailures = 0
                        val observation = result.getOrThrow()
                        updatePidDiagnostic(
                            definition.pid,
                            PidDiagnostic(
                                command = "%02X%02X".format(definition.mode, definition.pid),
                                name = definition.name,
                                status = observation.status.name.replace('_', ' '),
                                response = observation.response,
                                value = observation.value,
                            ),
                        )
                        if (observation.value == null) {
                            values.remove(definition.pid)
                            payloads.remove(definition.pid)
                        } else {
                            val value = observation.value
                            values[definition.pid] = value
                            observation.payload?.let { payloads[definition.pid] = it }
                            mutableState.update {
                                if (definition.pid in it.supportedPids) {
                                    it
                                } else {
                                    it.copy(supportedPids = it.supportedPids + definition.pid)
                                }
                            }
                        }
                    }
                    delay(15)
                }

                val now = System.currentTimeMillis()
                val map = values[0x0B]
                val barometric = values[0x33]
                val maf = values[0x66] ?: values[0x10]
                val intakeTemperatures = payloads[0x68]
                    ?.let(StandardPids::intakeAirTemperatures).orEmpty()
                val equivalenceRatio = values[0x44]
                val reportedFuelRate = values[0x5E]
                val estimatedFuelRate = if (reportedFuelRate == null) {
                    FuelEfficiencyCalculator.fuelRateFromMaf(maf, equivalenceRatio)
                } else null
                val sample = TelemetrySample(
                    timestampMillis = now,
                    rpm = values[0x0C],
                    speedKph = values[0x0D],
                    boostPsi = if (map != null && barometric != null) {
                        StandardPids.calculatedBoostPsi(map, barometric)
                    } else null,
                    throttlePercent = values[0x11],
                    coolantC = values[0x67] ?: values[0x05],
                    intakeC = intakeTemperatures.firstOrNull() ?: values[0x68] ?: values[0x0F],
                    shortFuelTrim = values[0x06],
                    longFuelTrim = values[0x07],
                    voltage = values[0x42],
                    engineLoad = values[0x04],
                    timingAdvance = values[0x0E],
                    fuelRateLitersPerHour = reportedFuelRate ?: estimatedFuelRate,
                    massAirFlowGramsPerSecond = maf,
                    commandedEquivalenceRatio = equivalenceRatio,
                    actualEquivalenceRatio = values[0x24] ?: values[0x34],
                    fuelRateEstimated = reportedFuelRate == null && estimatedFuelRate != null,
                    manifoldPressureKpa = map,
                    barometricPressureKpa = barometric,
                    chargeAirC = intakeTemperatures.getOrNull(1),
                    fuelRailPressureKpa = values[0x23] ?: values[0x59],
                    acceleratorPedalPercent = values[0x49] ?: values[0x5A],
                    commandedThrottlePercent = values[0x4C],
                    ambientC = values[0x46],
                    oilC = values[0x5C],
                    driverDemandTorquePercent = values[0x61],
                    actualTorquePercent = values[0x62],
                    referenceTorqueNm = values[0x63],
                )
                if (sample.rpm != null && sample.rpm > 0.0) {
                    missingRpmCycles = 0
                    handleValidRpm(sample.rpm)
                } else {
                    missingRpmCycles++
                    if (missingRpmCycles >= 3) markEcuUnavailable()
                }
                val recordingInterval = state.value.settings.recordingIntervalMillis
                if (now - previousTimestamp >= recordingInterval) {
                    val elapsedHours = (now - previousTimestamp).coerceAtLeast(0L) / 3_600_000.0
                    previousTimestamp = now
                    addSample(sample, elapsedHours)
                }
                cycle++
                delay(25)
            }
        }
    }

    private fun updatePidDiagnostic(pid: Int, diagnostic: PidDiagnostic) {
        if (pid !in DiagnosticPids) return
        mutableState.update {
            if (it.pidDiagnostics[pid] == diagnostic) {
                it
            } else {
                it.copy(pidDiagnostics = it.pidDiagnostics + (pid to diagnostic))
            }
        }
    }

    fun refreshDiagnostics() {
        if (state.value.connectionState != ConnectionState.CONNECTED) {
            mutableState.update {
                it.copy(
                    diagnostics = it.diagnostics.copy(
                        isLoading = false,
                        error = "Connect to Elizabeth before refreshing diagnostics.",
                    )
                )
            }
            return
        }
        diagnosticsJob?.cancel()
        diagnosticsJob = scope.launch {
            mutableState.update {
                it.copy(diagnostics = it.diagnostics.copy(isLoading = true, error = null))
            }
            val result = elm.readVehicleDiagnostics { status ->
                mutableState.update { it.copy(connectionDetail = status) }
            }
            result.fold(
                onSuccess = { snapshot ->
                    mutableState.update {
                        it.copy(
                            connectionDetail = "${it.protocolName ?: "OBD"} · live",
                            diagnostics = VehicleDiagnostics(
                                vin = snapshot.vin,
                                storedDtcs = snapshot.storedDtcs,
                                pendingDtcs = snapshot.pendingDtcs,
                                permanentDtcs = snapshot.permanentDtcs,
                                readinessMonitors = snapshot.readinessMonitors,
                                milOn = snapshot.milOn,
                                freezeFrameAvailable = snapshot.freezeFrameAvailable,
                                lastCheckedMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            diagnostics = it.diagnostics.copy(
                                isLoading = false,
                                error = error.message ?: "Diagnostic refresh failed.",
                            )
                        )
                    }
                },
            )
        }
    }

    private fun beginReconnect() {
        pollingJob?.cancel()
        scope.launch {
            markEcuUnavailable()
            transport.disconnect()
            scheduleReconnect("Connection interrupted", ignitionOff = false)
        }
    }

    private fun addSample(sample: TelemetrySample, elapsedHours: Double) {
        val rawEvent = detectEvent(sample)
        val event = rawEvent?.takeIf {
            lastTripEventMillis[it.label]
                ?.let { previous -> sample.timestampMillis - previous >= 30_000L }
                ?: true
        }
        event?.let { lastTripEventMillis[it.label] = sample.timestampMillis }
        if (state.value.trip.isRecording) {
            recordedTripSamples += sample
            pendingTripSamples += sample
            fuelDataSource = when {
                sample.fuelRateLitersPerHour == null -> fuelDataSource
                !sample.fuelRateEstimated -> FuelDataSource.ECU_REPORTED
                fuelDataSource != FuelDataSource.ECU_REPORTED -> FuelDataSource.MAF_ESTIMATED
                else -> fuelDataSource
            }
            event?.let {
                recordedTripEvents += it
                pendingTripEvents += it
                if (recordedTripEvents.size > 500) recordedTripEvents.removeAt(0)
            }
            if (pendingTripSamples.size >= FLUSH_SAMPLE_BATCH_SIZE) requestTripFlush()
        }
        mutableState.update {
            it.copy(
                samples = if (it.graphPaused) it.samples else (it.samples + sample).takeLast(2_400),
                liveFuelUsedLiters = it.liveFuelUsedLiters +
                    ((sample.fuelRateLitersPerHour ?: 0.0) * elapsedHours),
                liveDistanceKm = it.liveDistanceKm +
                    ((sample.speedKph ?: 0.0) * elapsedHours),
                trip = if (it.trip.isRecording && event != null) {
                    it.trip.copy(events = recordedTripEvents.takeLast(50))
                } else it.trip,
                driveAutomation = it.driveAutomation.copy(
                    lastSampleMillis = sample.timestampMillis,
                    pendingSamples = pendingTripSamples.size,
                ),
            )
        }
    }

    private fun handleConnectionFailure(message: String, ignitionOff: Boolean) {
        if (state.value.trip.isRecording) markEcuUnavailable()
        scheduleReconnect(message, ignitionOff)
    }

    private fun scheduleReconnect(message: String, ignitionOff: Boolean) {
        if (userDisconnected) return
        reconnectJob?.cancel()
        reconnectCount++
        val delayMillis = ReconnectBackoffMillis[
            reconnectBackoffAttempt.coerceAtMost(ReconnectBackoffMillis.lastIndex)
        ]
        reconnectBackoffAttempt++
        mutableState.update {
            val holding = it.trip.isRecording && automationEngine.graceStartedAtMillis != null
            val status = when {
                holding -> "Connection interrupted · holding trip open"
                ignitionOff -> "Adapter connected · waiting for ignition"
                else -> "Connection interrupted · retrying"
            }
            it.copy(
                connectionState = ConnectionState.RECONNECTING,
                connectionDetail = status,
                lastConnectionError = message,
                driveAutomation = it.driveAutomation.copy(
                    phase = when {
                        holding -> DriveAutomationPhase.HOLDING_TRIP
                        ignitionOff -> DriveAutomationPhase.WAITING_FOR_IGNITION
                        else -> DriveAutomationPhase.RECONNECTING
                    },
                    statusText = status,
                    reconnectCount = reconnectCount,
                ),
            )
        }
        reconnectJob = scope.launch {
            delay(delayMillis)
            connectToSelectedDevice(reconnecting = true)
        }
    }

    private fun showConnectionError(message: String) {
        mutableState.update {
            it.copy(lastConnectionError = message, connectionDetail = message, showDevicePicker = false)
        }
    }

    fun setWindow(window: TimeWindow) = mutableState.update { it.copy(timeWindow = window) }
    fun togglePaused() = mutableState.update {
        it.copy(graphPaused = !it.graphPaused, inspectedSample = if (it.graphPaused) null else it.samples.lastOrNull())
    }
    fun inspect(sample: TelemetrySample?) = mutableState.update { it.copy(inspectedSample = sample) }
    fun toggleChannel(channel: String) = mutableState.update {
        val changed = it.selectedChannels.toMutableSet().apply { if (!add(channel)) remove(channel) }
        it.copy(selectedChannels = changed)
    }
    override fun toggleTrip() {
        scope.launch {
            if (state.value.trip.isRecording) {
                if (automationEngine.manualStop(hasActiveTrip = true) == AutomationDecision.FINALIZE_TRIP) {
                    finalizeActiveTrip()
                }
            } else if (
                automationEngine.manualStart(hasActiveTrip = activeTripId != null) ==
                AutomationDecision.START_MANUAL_TRIP
            ) {
                startActiveTrip(automatic = false, startedAtMillis = System.currentTimeMillis())
            }
        }
    }

    private fun handleValidRpm(rpm: Double) {
        when (
            automationEngine.onRpm(
                rpm = rpm,
                hasActiveTrip = activeTripId != null,
                automaticTripsEnabled = state.value.settings.automaticTrips,
            )
        ) {
            AutomationDecision.START_AUTOMATIC_TRIP -> scope.launch {
                startActiveTrip(
                    automatic = true,
                    startedAtMillis = automationEngine.confirmedStartMillis ?: System.currentTimeMillis(),
                )
            }
            AutomationDecision.RESUME_TRIP -> {
                graceJob?.cancel()
                val event = TripEvent(System.currentTimeMillis(), "Connection restored", "Trip continued after ECU gap")
                recordedTripEvents += event
                pendingTripEvents += event
                mutableState.update {
                    it.copy(
                        connectionDetail = "Elizabeth connected · trip recording",
                        driveAutomation = it.driveAutomation.copy(
                            phase = DriveAutomationPhase.RECORDING,
                            statusText = "Elizabeth connected · trip recording",
                            graceStartedAtMillis = null,
                            graceEndsAtMillis = null,
                        ),
                    )
                }
                requestTripFlush()
            }
            else -> Unit
        }
    }

    private fun markEcuUnavailable() {
        val decision = automationEngine.onEcuUnavailable(hasActiveTrip = activeTripId != null)
        if (decision == AutomationDecision.HOLD_TRIP_OPEN) {
            val started = automationEngine.graceStartedAtMillis ?: return
            val ends = started + gracePeriodMillis()
            val event = TripEvent(started, "Connection gap", "ECU unavailable · trip held open")
            recordedTripEvents += event
            pendingTripEvents += event
            mutableState.update {
                it.copy(
                    connectionDetail = "ECU unavailable · holding trip open",
                    driveAutomation = it.driveAutomation.copy(
                        phase = DriveAutomationPhase.HOLDING_TRIP,
                        statusText = "ECU unavailable · holding trip open",
                        graceStartedAtMillis = started,
                        graceEndsAtMillis = ends,
                    ),
                )
            }
            requestTripFlush()
            startGraceCountdown()
        } else if (activeTripId == null && state.value.connectionState == ConnectionState.CONNECTED) {
            mutableState.update {
                it.copy(
                    connectionDetail = "Adapter connected · waiting for ignition",
                    driveAutomation = it.driveAutomation.copy(
                        phase = DriveAutomationPhase.WAITING_FOR_IGNITION,
                        statusText = "Adapter connected · waiting for ignition",
                    ),
                )
            }
        }
    }

    private suspend fun startActiveTrip(automatic: Boolean, startedAtMillis: Long) {
        recoveryComplete.await()
        tripMutex.withLock {
            if (activeTripId != null || state.value.trip.isRecording) return
            val tripId = tripRepository.beginActive(startedAtMillis, automatic)
            activeTripId = tripId
            activeTripAutomatic = automatic
            activeTripRecovered = false
            fuelDataSource = FuelDataSource.UNAVAILABLE
            recordedTripSamples.clear()
            recordedTripEvents.clear()
            pendingTripSamples.clear()
            pendingTripEvents.clear()
            lastTripEventMillis.clear()
            reconnectCount = 0
            automationEngine.clearTripState()
            mutableState.update {
                it.copy(
                    liveDriveStartedAtMillis = startedAtMillis,
                    liveFuelUsedLiters = 0.0,
                    liveDistanceKm = 0.0,
                    samples = emptyList(),
                    selectedTrip = null,
                    trip = TripSummary(isRecording = true, startedAtMillis = startedAtMillis),
                    driveAutomation = it.driveAutomation.copy(
                        phase = DriveAutomationPhase.RECORDING,
                        statusText = "Elizabeth connected · trip recording",
                        activeTripId = tripId,
                        activeTripAutomatic = automatic,
                        lastSampleMillis = null,
                        pendingSamples = 0,
                        reconnectCount = 0,
                        graceStartedAtMillis = null,
                        graceEndsAtMillis = null,
                        recoveredAfterProcessDeath = false,
                    ),
                )
            }
            startPeriodicFlush()
            if (
                automatic &&
                state.value.settings.overlayDuringAutomaticTrips &&
                FloatingTripOverlayService.canStart(application)
            ) {
                FloatingTripOverlayService.startAutomatic(application)
            }
        }
    }

    private suspend fun finalizeActiveTrip(endAtMillis: Long? = null) {
        tripMutex.withLock {
            val tripId = activeTripId ?: return
            flushActiveTripLocked()
            val current = state.value
            val endedAt = endAtMillis
                ?: recordedTripSamples.lastOrNull()?.timestampMillis
                ?: System.currentTimeMillis()
            val completed = TripSummaryCalculator.summarize(
                startedAtMillis = current.trip.startedAtMillis,
                samples = recordedTripSamples.toList(),
                events = recordedTripEvents.toList(),
                fuelUsedLiters = current.liveFuelUsedLiters,
                isRecording = false,
                endedAtMillis = endedAt,
            )
            tripRepository.finalizeActive(
                tripId = tripId,
                summary = completed,
                endedAtMillis = endedAt,
                lastSampleMillis = recordedTripSamples.lastOrNull()?.timestampMillis,
                fuelDataSource = fuelDataSource.name,
                reconnectCount = reconnectCount,
                recovered = activeTripRecovered,
            )
            activeTripId = null
            activeTripAutomatic = false
            flushJob?.cancel()
            graceJob?.cancel()
            automationEngine.clearTripState()
            mutableState.update {
                it.copy(
                    trip = completed,
                    tripHistoryLoading = false,
                    connectionDetail = if (it.connectionState == ConnectionState.CONNECTED) {
                        "Connected"
                    } else {
                        "Trip saved"
                    },
                    driveAutomation = it.driveAutomation.copy(
                        phase = if (it.connectionState == ConnectionState.CONNECTED) {
                            DriveAutomationPhase.CONNECTED
                        } else {
                            DriveAutomationPhase.WAITING_FOR_ADAPTER
                        },
                        statusText = "Trip saved",
                        activeTripId = null,
                        activeTripAutomatic = false,
                        pendingSamples = 0,
                        graceStartedAtMillis = null,
                        graceEndsAtMillis = null,
                        recoveredAfterProcessDeath = false,
                        lastSavedTripId = tripId,
                        lastSavedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            FloatingTripOverlayService.stopAutomatic(application)
        }
    }

    private fun requestTripFlush() {
        if (activeTripId == null) return
        scope.launch { flushActiveTrip() }
    }

    private suspend fun flushActiveTrip() {
        tripMutex.withLock { flushActiveTripLocked() }
    }

    private suspend fun flushActiveTripLocked() {
        val tripId = activeTripId ?: return
        val samples = pendingTripSamples.toList()
        val events = pendingTripEvents.toList()
        val current = state.value
        val summary = TripSummaryCalculator.summarize(
            startedAtMillis = current.trip.startedAtMillis,
            samples = recordedTripSamples.toList(),
            events = recordedTripEvents.toList(),
            fuelUsedLiters = current.liveFuelUsedLiters,
            isRecording = true,
        )
        tripRepository.flushActive(
            tripId = tripId,
            summary = summary,
            samples = samples,
            events = events,
            lastSampleMillis = recordedTripSamples.lastOrNull()?.timestampMillis,
            fuelDataSource = fuelDataSource.name,
            reconnectCount = reconnectCount,
            graceStartedAtMillis = automationEngine.graceStartedAtMillis,
            recovered = activeTripRecovered,
        )
        pendingTripSamples.removeAll(samples.toSet())
        pendingTripEvents.removeAll(events.toSet())
        val flushedAt = System.currentTimeMillis()
        mutableState.update {
            it.copy(
                driveAutomation = it.driveAutomation.copy(
                    pendingSamples = pendingTripSamples.size,
                    lastFlushMillis = flushedAt,
                    lastSampleMillis = recordedTripSamples.lastOrNull()?.timestampMillis,
                ),
            )
        }
    }

    private fun startPeriodicFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            while (isActive && activeTripId != null) {
                delay(FLUSH_INTERVAL_MILLIS)
                flushActiveTrip()
            }
        }
    }

    private fun startGraceCountdown() {
        graceJob?.cancel()
        graceJob = scope.launch {
            while (isActive && activeTripId != null && automationEngine.graceStartedAtMillis != null) {
                if (
                    automationEngine.tick(
                        hasActiveTrip = true,
                        gracePeriodMillis = gracePeriodMillis(),
                    ) == AutomationDecision.FINALIZE_TRIP
                ) {
                    val lastValid = recordedTripSamples.lastOrNull()?.timestampMillis
                    finalizeActiveTrip(lastValid)
                    return@launch
                }
                val ends = (automationEngine.graceStartedAtMillis ?: break) + gracePeriodMillis()
                val remaining = (ends - System.currentTimeMillis()).coerceAtLeast(0L)
                mutableState.update {
                    it.copy(
                        connectionDetail = "Engine off · saving trip in ${formatCountdown(remaining)}",
                        driveAutomation = it.driveAutomation.copy(
                            phase = DriveAutomationPhase.HOLDING_TRIP,
                            statusText = "Engine off · saving trip in ${formatCountdown(remaining)}",
                            graceEndsAtMillis = ends,
                        ),
                    )
                }
                delay(1_000)
            }
        }
    }

    private suspend fun recoverActiveTrip() {
        val recovered = tripRepository.loadActive() ?: return
        tripMutex.withLock {
            activeTripId = recovered.trip.id
            activeTripAutomatic = recovered.trip.isAutomatic
            activeTripRecovered = true
            fuelDataSource = runCatching {
                FuelDataSource.valueOf(recovered.trip.fuelDataSource)
            }.getOrDefault(FuelDataSource.UNAVAILABLE)
            reconnectCount = recovered.trip.reconnectCount
            recordedTripSamples.clear()
            recordedTripSamples.addAll(recovered.samples)
            recordedTripEvents.clear()
            recordedTripEvents.addAll(recovered.events)
            val recoveryGraceStartedAt = recovered.trip.graceStartedAtMillis
                ?: recovered.trip.lastSampleMillis
                ?: System.currentTimeMillis()
            automationEngine.restoreGracePeriod(recoveryGraceStartedAt)
            val summary = recovered.summary.copy(isRecording = true, events = recovered.events)
            mutableState.update {
                it.copy(
                    trip = summary,
                    samples = recovered.samples.takeLast(2_400),
                    liveDriveStartedAtMillis = recovered.trip.startedAtMillis,
                    liveFuelUsedLiters = recovered.trip.fuelUsedLiters,
                    liveDistanceKm = recovered.trip.distanceKm,
                    driveAutomation = it.driveAutomation.copy(
                        phase = DriveAutomationPhase.HOLDING_TRIP,
                        statusText = "Recovered trip · reconnecting and holding trip open",
                        activeTripId = recovered.trip.id,
                        activeTripAutomatic = recovered.trip.isAutomatic,
                        lastSampleMillis = recovered.trip.lastSampleMillis,
                        reconnectCount = recovered.trip.reconnectCount,
                        graceStartedAtMillis = recoveryGraceStartedAt,
                        graceEndsAtMillis = recoveryGraceStartedAt.plus(gracePeriodMillis()),
                        recoveredAfterProcessDeath = true,
                    ),
                )
            }
            startPeriodicFlush()
            startGraceCountdown()
        }
    }

    private fun gracePeriodMillis(): Long =
        state.value.settings.automaticTripEndDelayMinutes * 60_000L

    private fun formatCountdown(remainingMillis: Long): String {
        val seconds = remainingMillis / 1_000L
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
    fun deleteTrip() {
        scope.launch {
            tripMutex.withLock {
                activeTripId?.let { tripRepository.delete(it) }
                activeTripId = null
                activeTripAutomatic = false
                recordedTripSamples.clear()
                recordedTripEvents.clear()
                pendingTripSamples.clear()
                pendingTripEvents.clear()
                flushJob?.cancel()
                graceJob?.cancel()
                automationEngine.clearTripState()
                mutableState.update {
                    it.copy(
                        trip = TripSummary(),
                        samples = emptyList(),
                        liveFuelUsedLiters = 0.0,
                        liveDistanceKm = 0.0,
                        liveDriveStartedAtMillis = System.currentTimeMillis(),
                        driveAutomation = it.driveAutomation.copy(
                            activeTripId = null,
                            activeTripAutomatic = false,
                            pendingSamples = 0,
                            graceStartedAtMillis = null,
                            graceEndsAtMillis = null,
                            recoveredAfterProcessDeath = false,
                        ),
                    )
                }
            }
        }
    }

    fun selectTrip(tripId: Long) {
        mutableState.update { it.copy(tripHistoryLoading = true) }
        scope.launch {
            val trip = tripRepository.load(tripId)
            mutableState.update {
                it.copy(selectedTrip = trip, tripHistoryLoading = false)
            }
        }
    }

    fun closeTripDetail() = mutableState.update { it.copy(selectedTrip = null) }

    fun deleteSavedTrip(tripId: Long) {
        scope.launch {
            tripRepository.delete(tripId)
            mutableState.update {
                it.copy(
                    selectedTrip = if (it.selectedTrip?.id == tripId) null else it.selectedTrip,
                )
            }
        }
    }
    fun setTheme(theme: ThemeSetting) {
        preferences.edit().putString("theme", theme.name).apply()
        mutableState.update { it.copy(settings = it.settings.copy(theme = theme)) }
    }
    fun setUnits(units: UnitSystem) {
        preferences.edit().putString("units", units.name).apply()
        mutableState.update { it.copy(settings = it.settings.copy(units = units)) }
    }
    fun toggleSmoothing() = mutableState.update {
        val smoothing = !it.settings.smoothing
        preferences.edit().putBoolean("graph_smoothing", smoothing).apply()
        it.copy(settings = it.settings.copy(smoothing = smoothing))
    }
    fun setDefaultWindow(window: TimeWindow) = mutableState.update {
        preferences.edit().putString("default_window", window.name).apply()
        it.copy(settings = it.settings.copy(defaultWindow = window), timeWindow = window)
    }
    fun setRecordingInterval(intervalMillis: Long) = mutableState.update {
        preferences.edit().putLong("recording_interval_millis", intervalMillis).apply()
        it.copy(settings = it.settings.copy(recordingIntervalMillis = intervalMillis))
    }
    fun setFuelPricePerGallon(price: Double) {
        val safePrice = price.coerceIn(0.50, 15.00)
        preferences.edit().putFloat("fuel_price_per_gallon", safePrice.toFloat()).apply()
        mutableState.update {
            it.copy(
                settings = it.settings.copy(
                    fuelPricePerGallon = safePrice,
                    fuelPriceSource = "Manually entered local price",
                )
            )
        }
    }
    fun toggleAutoStart() = mutableState.update {
        val autoStart = !it.settings.autoStartRecording
        preferences.edit().putBoolean("auto_start_recording", autoStart).apply()
        it.copy(settings = it.settings.copy(autoStartRecording = autoStart))
    }

    fun toggleAutomaticConnection() {
        val enabled = !state.value.settings.automaticConnection
        preferences.edit().putBoolean("automatic_connection", enabled).apply()
        mutableState.update {
            it.copy(settings = it.settings.copy(automaticConnection = enabled))
        }
        if (enabled) {
            userDisconnected = false
            if (
                ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                DriveAutomationService.start(application)
            } else {
                showConnectionError("Bluetooth permission is required for automatic connection.")
            }
        } else if (!enabled && !state.value.trip.isRecording) {
            disconnectInternal(manual = true)
        }
    }

    fun toggleAutomaticTrips() {
        val enabled = !state.value.settings.automaticTrips
        preferences.edit().putBoolean("automatic_trips", enabled).apply()
        mutableState.update { it.copy(settings = it.settings.copy(automaticTrips = enabled)) }
    }

    fun setAutomaticTripEndDelay(minutes: Int) {
        val safe = minutes.takeIf { it in setOf(1, 2, 3, 5) } ?: 3
        preferences.edit().putInt("automatic_trip_end_delay_minutes", safe).apply()
        mutableState.update {
            it.copy(settings = it.settings.copy(automaticTripEndDelayMinutes = safe))
        }
        if (automationEngine.graceStartedAtMillis != null) startGraceCountdown()
    }

    fun toggleOverlayDuringAutomaticTrips() {
        val enabled = !state.value.settings.overlayDuringAutomaticTrips
        preferences.edit().putBoolean("overlay_during_automatic_trips", enabled).apply()
        mutableState.update {
            it.copy(settings = it.settings.copy(overlayDuringAutomaticTrips = enabled))
        }
        if (
            enabled &&
            activeTripAutomatic &&
            state.value.trip.isRecording &&
            FloatingTripOverlayService.canStart(application)
        ) {
            FloatingTripOverlayService.startAutomatic(application)
        } else if (!enabled && !state.value.settings.overlayEnabled) {
            FloatingTripOverlayService.stop(application)
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("floating_trip_overlay", enabled).apply()
        mutableState.update { it.copy(settings = it.settings.copy(overlayEnabled = enabled)) }
    }

    fun startDriveAutomation() {
        serviceRunning = true
        mutableState.update {
            it.copy(
                driveAutomation = it.driveAutomation.copy(backgroundServiceRunning = true),
            )
        }
        if (state.value.settings.automaticConnection || activeTripId != null) {
            userDisconnected = false
            connectSavedDevice()
        }
    }

    fun stopDriveAutomation() {
        serviceRunning = false
        mutableState.update {
            it.copy(
                driveAutomation = it.driveAutomation.copy(backgroundServiceRunning = false),
            )
        }
        requestTripFlush()
    }

    fun stopTripFromNotification() {
        if (state.value.trip.isRecording) toggleTrip()
    }

    fun setCompanionAssociated(associated: Boolean) {
        mutableState.update {
            it.copy(
                driveAutomation = it.driveAutomation.copy(companionAssociated = associated),
            )
        }
    }

    fun rememberedDevice(): PairedObdDevice? = selectedDevice

    private fun detectEvent(sample: TelemetrySample): TripEvent? = when {
        sample.voltage?.let { it < 11.5 } == true ->
            TripEvent(sample.timestampMillis, "Low voltage", "${"%.1f".format(sample.voltage)} V")
        sample.rpm?.let { it > 4_500 } == true ->
            TripEvent(sample.timestampMillis, "High RPM", "${sample.rpm.toInt()} rpm")
        sample.boostPsi?.let { it > 14.8 } == true ->
            TripEvent(sample.timestampMillis, "Peak boost", "${"%.1f".format(sample.boostPsi)} psi")
        sample.throttlePercent?.let { it > 75 } == true &&
            sample.engineLoad?.let { it > 60 } == true ->
            TripEvent(
                sample.timestampMillis,
                "Hard acceleration",
                "${sample.throttlePercent.toInt()}% throttle",
            )
        sample.coolantC?.let { it > 108 } == true ->
            TripEvent(sample.timestampMillis, "High coolant temperature", "${sample.coolantC.toInt()} °C")
        sample.intakeC?.let { it > 55 } == true ->
            TripEvent(sample.timestampMillis, "High intake temperature", "${sample.intakeC.toInt()} °C")
        listOfNotNull(sample.shortFuelTrim, sample.longFuelTrim).any { kotlin.math.abs(it) > 20 } ->
            TripEvent(
                sample.timestampMillis,
                "Large fuel trim",
                listOfNotNull(sample.shortFuelTrim, sample.longFuelTrim)
                    .joinToString(" / ") { "%+.1f%%".format(it) },
            )
        else -> null
    }

    private companion object {
        val DiagnosticPids = setOf(0x05, 0x0F, 0x10, 0x44, 0x5E, 0x66, 0x67, 0x68)
        val CoreDirectProbePids = setOf(
            0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
            0x33, 0x42, 0x44, 0x5E, 0x66, 0x67, 0x68,
        )
        val ReconnectBackoffMillis = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 60_000L)
        const val FLUSH_SAMPLE_BATCH_SIZE = 10
        const val FLUSH_INTERVAL_MILLIS = 5_000L
    }
}
