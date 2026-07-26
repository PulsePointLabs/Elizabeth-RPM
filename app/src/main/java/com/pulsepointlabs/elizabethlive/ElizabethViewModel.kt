package com.pulsepointlabs.elizabethlive

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepointlabs.elizabethlive.obd.elm327.Elm327Client
import com.pulsepointlabs.elizabethlive.obd.pid.PollPriority
import com.pulsepointlabs.elizabethlive.obd.pid.StandardPids
import com.pulsepointlabs.elizabethlive.obd.transport.BluetoothClassicObdTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ElizabethViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("elizabeth_settings", 0)
    private val hasSavedFuelPrice = preferences.contains("fuel_price_per_gallon")
    private val savedFuelPrice = preferences.getFloat("fuel_price_per_gallon", 4.00f).toDouble()
    private val mutableState = MutableStateFlow(
        ElizabethUiState().let { initial ->
            initial.copy(
                settings = initial.settings.copy(
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
    val state: StateFlow<ElizabethUiState> = mutableState.asStateFlow()

    private val transport = BluetoothClassicObdTransport(application)
    private val elm = Elm327Client(transport)
    private var selectedDevice: PairedObdDevice? = null
    private var connectionJob: Job? = null
    private var pollingJob: Job? = null
    private var reconnectAttempts = 0

    @SuppressLint("MissingPermission")
    fun prepareConnection() {
        if (state.value.connectionState != ConnectionState.DISCONNECTED) {
            disconnect()
            return
        }
        val adapter = getApplication<Application>()
            .getSystemService(BluetoothManager::class.java)
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
        selectedDevice = device
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

    fun disconnect() {
        connectionJob?.cancel()
        pollingJob?.cancel()
        connectionJob = null
        pollingJob = null
        viewModelScope.launch { transport.disconnect() }
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
        connectionJob = viewModelScope.launch {
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
                it.copy(
                    connectionState = ConnectionState.CONNECTED,
                    connectionDetail = "${result.protocolName} · ${result.supportedPids.size} PIDs reported",
                    supportedPids = result.supportedPids,
                    protocolName = result.protocolName,
                    lastConnectionError = null,
                    liveDriveStartedAtMillis = System.currentTimeMillis(),
                    liveFuelUsedLiters = 0.0,
                )
            }
            startPolling(result.supportedPids)
        }
    }

    private fun startPolling(supported: Set<Int>) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val fast = StandardPids.registry.filter { it.priority == PollPriority.FAST && it.pid in supported }
            val medium = StandardPids.registry.filter { it.priority == PollPriority.MEDIUM && it.pid in supported }
            val slow = StandardPids.registry.filter { it.priority == PollPriority.SLOW && it.pid in supported }
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
                    val result = elm.read(definition)
                    if (result.isFailure) {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            beginReconnect()
                            return@launch
                        }
                    } else {
                        consecutiveFailures = 0
                        result.getOrNull()?.let { values[definition.pid] = it }
                    }
                    delay(15)
                }

                val now = System.currentTimeMillis()
                val map = values[0x0B]
                val barometric = values[0x33]
                val sample = TelemetrySample(
                    timestampMillis = now,
                    rpm = values[0x0C],
                    speedKph = values[0x0D],
                    boostPsi = if (map != null && barometric != null) {
                        StandardPids.calculatedBoostPsi(map, barometric)
                    } else null,
                    throttlePercent = values[0x11],
                    coolantC = values[0x05],
                    intakeC = values[0x0F],
                    shortFuelTrim = values[0x06],
                    longFuelTrim = values[0x07],
                    voltage = values[0x42],
                    engineLoad = values[0x04],
                    timingAdvance = values[0x0E],
                    fuelRateLitersPerHour = values[0x5E],
                )
                val elapsedHours = (now - previousTimestamp).coerceAtLeast(0L) / 3_600_000.0
                previousTimestamp = now
                addSample(sample, elapsedHours)
                cycle++
                delay(25)
            }
        }
    }

    private fun beginReconnect() {
        pollingJob?.cancel()
        reconnectAttempts = 0
        viewModelScope.launch {
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
        viewModelScope.launch {
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
    fun toggleTrip() = mutableState.update {
        val recording = !it.trip.isRecording
        it.copy(
            liveDriveStartedAtMillis = if (recording) System.currentTimeMillis() else it.liveDriveStartedAtMillis,
            liveFuelUsedLiters = if (recording) 0.0 else it.liveFuelUsedLiters,
            trip = if (recording) {
                it.trip.copy(isRecording = true, startedAtMillis = System.currentTimeMillis(), events = emptyList())
            } else it.trip.copy(isRecording = false),
        )
    }
    fun deleteTrip() = mutableState.update { it.copy(trip = TripSummary()) }
    fun setTheme(theme: ThemeSetting) = mutableState.update { it.copy(settings = it.settings.copy(theme = theme)) }
    fun setUnits(units: UnitSystem) = mutableState.update { it.copy(settings = it.settings.copy(units = units)) }
    fun toggleSmoothing() = mutableState.update { it.copy(settings = it.settings.copy(smoothing = !it.settings.smoothing)) }
    fun setDefaultWindow(window: TimeWindow) = mutableState.update {
        it.copy(settings = it.settings.copy(defaultWindow = window), timeWindow = window)
    }
    fun setRecordingInterval(intervalMillis: Long) = mutableState.update {
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
        it.copy(settings = it.settings.copy(autoStartRecording = !it.settings.autoStartRecording))
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

}
