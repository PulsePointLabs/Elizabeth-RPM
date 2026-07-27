package com.pulsepointlabs.elizabethlive

import android.annotation.SuppressLint
import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.pulsepointlabs.elizabethlive.obd.elm327.Elm327Client
import com.pulsepointlabs.elizabethlive.obd.pid.PollPriority
import com.pulsepointlabs.elizabethlive.obd.pid.StandardPids
import com.pulsepointlabs.elizabethlive.obd.transport.BluetoothClassicObdTransport
import com.pulsepointlabs.elizabethlive.trip.FuelEfficiencyCalculator
import kotlinx.coroutines.CoroutineScope
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
    private var reconnectAttempts = 0

    @SuppressLint("MissingPermission")
    fun prepareConnection() {
        if (state.value.connectionState != ConnectionState.DISCONNECTED) {
            disconnect()
            return
        }
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
                lastConnectionError = if (devices.isEmpty()) {
                    "No paired Bluetooth devices found. Pair the vLinker MC+ in Android Settings first."
                } else null,
            )
        }
    }

    fun dismissDevicePicker() = mutableState.update { it.copy(showDevicePicker = false) }

    fun selectDevice(device: PairedObdDevice) {
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
        if (state.value.connectionState != ConnectionState.DISCONNECTED) return true
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
        val device = selectedDevice ?: adapter.bondedDevices
            .firstOrNull { it.name?.contains("vLinker", ignoreCase = true) == true }
            ?.let { PairedObdDevice(it.name ?: "vLinker MC+", it.address) }
            ?.also(::rememberDevice)
        if (device == null) {
            showConnectionError("Open Elizabeth Live on the phone and select the paired vLinker first.")
            return false
        }
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
        connectionJob?.cancel()
        pollingJob?.cancel()
        diagnosticsJob?.cancel()
        connectionJob = null
        pollingJob = null
        diagnosticsJob = null
        scope.launch { transport.disconnect() }
        mutableState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectionDetail = "Disconnected",
                showDevicePicker = false,
            )
        }
    }

    private fun connectToSelectedDevice(reconnecting: Boolean) {
        val device = selectedDevice ?: return
        connectionJob?.cancel()
        pollingJob?.cancel()
        connectionJob = scope.launch {
            mutableState.update {
                it.copy(
                    connectionState = if (reconnecting) ConnectionState.RECONNECTING else ConnectionState.CONNECTING,
                    connectionDetail = if (reconnecting) "Reconnecting to ${device.name}…" else "Opening Bluetooth Classic connection…",
                    lastConnectionError = null,
                )
            }
            val connected = transport.connect(device.address)
            if (connected.isFailure) {
                handleConnectionFailure(
                    "Adapter connection failed: ${connected.exceptionOrNull()?.message ?: "unknown error"}",
                    reconnecting,
                )
                return@launch
            }
            val initialized = elm.initialize { detail ->
                mutableState.update { it.copy(connectionDetail = detail) }
            }
            if (initialized.isFailure) {
                transport.disconnect()
                handleConnectionFailure(
                    "ELM327 initialization failed: ${initialized.exceptionOrNull()?.message ?: "unknown error"}",
                    reconnecting,
                )
                return@launch
            }
            val result = initialized.getOrThrow()
            reconnectAttempts = 0
            mutableState.update {
                val autoStart = !reconnecting && it.settings.autoStartRecording
                it.copy(
                    connectionState = ConnectionState.CONNECTED,
                    connectionDetail = "${result.protocolName} · ${result.supportedPids.size} PIDs reported",
                    supportedPids = result.supportedPids,
                    pidDiagnostics = emptyMap(),
                    diagnostics = VehicleDiagnostics(),
                    protocolName = result.protocolName,
                    lastConnectionError = null,
                    liveDriveStartedAtMillis = if (reconnecting) {
                        it.liveDriveStartedAtMillis
                    } else {
                        System.currentTimeMillis()
                    },
                    liveFuelUsedLiters = if (reconnecting) it.liveFuelUsedLiters else 0.0,
                    liveDistanceKm = if (reconnecting) it.liveDistanceKm else 0.0,
                    samples = if (autoStart) emptyList() else it.samples,
                    trip = if (autoStart) {
                        TripSummary(
                            isRecording = true,
                            startedAtMillis = System.currentTimeMillis(),
                        )
                    } else {
                        it.trip
                    },
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
            // Elizabeth's newer Honda PCM exposes the SAE multi-sensor forms (66/67/68).
            // The older single-sensor forms (10/05/0F) were verified to return NO DATA.
            val registry = StandardPids.registry.filterNot {
                it.pid in setOf(0x05, 0x0F, 0x10)
            }
            val fast = registry.filter { it.priority == PollPriority.FAST }
            val medium = registry.filter { it.priority == PollPriority.MEDIUM }
            val slow = registry.filter { it.priority == PollPriority.SLOW }
            val values = mutableMapOf<Int, Double>()
            var cycle = 0
            var mediumIndex = 0
            var consecutiveFailures = 0
            var previousTimestamp = System.currentTimeMillis()

            while (isActive) {
                val batch = buildList {
                    addAll(fast)
                    if (medium.isNotEmpty()) {
                        add(medium[mediumIndex % medium.size])
                        mediumIndex++
                    }
                    if (cycle % 8 == 0) addAll(slow)
                }.distinctBy { it.pid }

                for (definition in batch) {
                    val result = elm.readObserved(definition)
                    if (result.isFailure) {
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
                        observation.value?.let { value ->
                            values[definition.pid] = value
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
                    intakeC = values[0x68] ?: values[0x0F],
                    shortFuelTrim = values[0x06],
                    longFuelTrim = values[0x07],
                    voltage = values[0x42],
                    engineLoad = values[0x04],
                    timingAdvance = values[0x0E],
                    fuelRateLitersPerHour = reportedFuelRate ?: estimatedFuelRate,
                    massAirFlowGramsPerSecond = maf,
                    commandedEquivalenceRatio = equivalenceRatio,
                    fuelRateEstimated = reportedFuelRate == null && estimatedFuelRate != null,
                )
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
        reconnectAttempts = 0
        scope.launch {
            mutableState.update {
                it.copy(
                    connectionState = ConnectionState.RECONNECTING,
                    connectionDetail = "Connection lost · retrying automatically…",
                )
            }
            transport.disconnect()
            delay(1_500)
            connectToSelectedDevice(reconnecting = true)
        }
    }

    private fun addSample(sample: TelemetrySample, elapsedHours: Double) {
        if (state.value.graphPaused) return
        val event = detectEvent(sample)
        mutableState.update {
            it.copy(
                samples = (it.samples + sample).takeLast(2_400),
                liveFuelUsedLiters = it.liveFuelUsedLiters +
                    ((sample.fuelRateLitersPerHour ?: 0.0) * elapsedHours),
                liveDistanceKm = it.liveDistanceKm +
                    ((sample.speedKph ?: 0.0) * elapsedHours),
                trip = if (it.trip.isRecording && event != null) {
                    it.trip.copy(events = (it.trip.events + event).takeLast(20))
                } else it.trip,
            )
        }
    }

    private fun failConnection(message: String) {
        mutableState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectionDetail = "Connection failed",
                lastConnectionError = message,
            )
        }
    }

    private fun handleConnectionFailure(message: String, reconnecting: Boolean) {
        if (!reconnecting || reconnectAttempts >= 3) {
            failConnection(message)
            return
        }
        reconnectAttempts++
        mutableState.update {
            it.copy(
                connectionState = ConnectionState.RECONNECTING,
                connectionDetail = "Reconnect attempt $reconnectAttempts of 3…",
                lastConnectionError = message,
            )
        }
        scope.launch {
            delay(1_000L * reconnectAttempts)
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
    override fun toggleTrip() = mutableState.update {
        val recording = !it.trip.isRecording
        it.copy(
            liveDriveStartedAtMillis = if (recording) System.currentTimeMillis() else it.liveDriveStartedAtMillis,
            liveFuelUsedLiters = if (recording) 0.0 else it.liveFuelUsedLiters,
            liveDistanceKm = if (recording) 0.0 else it.liveDistanceKm,
            samples = if (recording) emptyList() else it.samples,
            trip = if (recording) {
                TripSummary(isRecording = true, startedAtMillis = System.currentTimeMillis())
            } else it.trip.copy(isRecording = false),
        )
    }
    fun deleteTrip() = mutableState.update {
        it.copy(
            trip = TripSummary(),
            samples = emptyList(),
            liveFuelUsedLiters = 0.0,
            liveDistanceKm = 0.0,
            liveDriveStartedAtMillis = System.currentTimeMillis(),
        )
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

    fun setOverlayEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("floating_trip_overlay", enabled).apply()
        mutableState.update { it.copy(settings = it.settings.copy(overlayEnabled = enabled)) }
    }

    private fun detectEvent(sample: TelemetrySample): TripEvent? = when {
        sample.voltage?.let { it < 11.5 } == true ->
            TripEvent(sample.timestampMillis, "Low voltage", "${"%.1f".format(sample.voltage)} V")
        sample.rpm?.let { it > 4_500 } == true ->
            TripEvent(sample.timestampMillis, "High RPM", "${sample.rpm.toInt()} rpm")
        sample.boostPsi?.let { it > 14.8 } == true ->
            TripEvent(sample.timestampMillis, "Peak boost", "${"%.1f".format(sample.boostPsi)} psi")
        sample.intakeC?.let { it > 55 } == true ->
            TripEvent(sample.timestampMillis, "High intake temperature", "${sample.intakeC.toInt()} °C")
        else -> null
    }

    private companion object {
        val DiagnosticPids = setOf(0x05, 0x0F, 0x10, 0x44, 0x5E, 0x66, 0x67, 0x68)
    }
}
