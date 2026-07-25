package com.pulsepointlabs.elizabethlive.obd.transport

import kotlinx.coroutines.flow.StateFlow

enum class TransportState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

sealed interface TransportResult {
    data class Response(val raw: String) : TransportResult
    data object NoData : TransportResult
    data class Failure(val message: String, val recoverable: Boolean) : TransportResult
}

/**
 * Read-only transport seam. Stage 2 will implement Bluetooth Classic RFCOMM here.
 * Commands are serialized by the implementation; callers never write directly to a socket.
 */
interface ObdTransport {
    val state: StateFlow<TransportState>
    suspend fun connect(deviceAddress: String): Result<Unit>
    suspend fun send(command: String, timeoutMillis: Long = 2_000): TransportResult
    suspend fun disconnect()
}

class FakeObdTransport : ObdTransport {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = mutableState

    override suspend fun connect(deviceAddress: String): Result<Unit> {
        mutableState.value = TransportState.CONNECTED
        return Result.success(Unit)
    }

    override suspend fun send(command: String, timeoutMillis: Long): TransportResult =
        when (command.trim().uppercase()) {
            "ATZ" -> TransportResult.Response("ELM327 v2.3\r>")
            "010C" -> TransportResult.Response("41 0C 0B B8\r>")
            else -> TransportResult.NoData
        }

    override suspend fun disconnect() {
        mutableState.value = TransportState.DISCONNECTED
    }
}

