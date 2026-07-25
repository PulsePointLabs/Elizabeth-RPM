package com.pulsepointlabs.elizabethlive.obd.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.util.UUID

class BluetoothClassicObdTransport(context: Context) : ObdTransport {
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val mutableState = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = mutableState
    private val commandMutex = Mutex()
    @Volatile private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutableState.value = TransportState.CONNECTING
        runCatching {
            disconnect()
            runCatching { adapter.cancelDiscovery() }
            val device = adapter.getRemoteDevice(deviceAddress)
            val candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket = candidate
            candidate.connect()
            mutableState.value = TransportState.CONNECTED
        }.onFailure {
            runCatching { socket?.close() }
            socket = null
            mutableState.value = TransportState.DISCONNECTED
        }
    }

    override suspend fun send(command: String, timeoutMillis: Long): TransportResult =
        commandMutex.withLock {
            val activeSocket = socket
                ?: return@withLock TransportResult.Failure("Adapter is not connected.", true)
            try {
                withTimeout(timeoutMillis) {
                    withContext(Dispatchers.IO) {
                        val input = activeSocket.inputStream
                        val output = activeSocket.outputStream
                        while (input.available() > 0) input.read()
                        output.write("${command.trim()}\r".toByteArray(Charsets.US_ASCII))
                        output.flush()
                        val buffer = ByteArray(512)
                        val collected = ByteArrayOutputStream()
                        while (true) {
                            if (input.available() > 0) {
                                val count = input.read(buffer)
                                if (count < 0) error("Bluetooth stream closed.")
                                collected.write(buffer, 0, count)
                                if (buffer.copyOf(count).contains('>'.code.toByte())) break
                            } else {
                                delay(10)
                            }
                        }
                        classify(collected.toString(Charsets.US_ASCII.name()))
                    }
                }
            } catch (error: Throwable) {
                mutableState.value = TransportState.DISCONNECTED
                TransportResult.Failure(
                    if (error is kotlinx.coroutines.TimeoutCancellationException) {
                        "ELM327 command timed out."
                    } else {
                        error.message ?: "Bluetooth connection lost."
                    },
                    recoverable = true,
                )
            }
        }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }
        socket = null
        mutableState.value = TransportState.DISCONNECTED
    }

    private fun classify(raw: String): TransportResult {
        val normalized = raw.uppercase()
        return when {
            "NO DATA" in normalized -> TransportResult.NoData
            "UNABLE TO CONNECT" in normalized ->
                TransportResult.Failure("Vehicle ECU is not responding. Check ignition.", true)
            "STOPPED" in normalized ->
                TransportResult.Failure("ELM327 command was stopped.", true)
            else -> TransportResult.Response(raw)
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
