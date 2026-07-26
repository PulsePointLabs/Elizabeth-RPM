package com.pulsepointlabs.elizabethlive.obd.elm327

import com.pulsepointlabs.elizabethlive.obd.pid.Elm327ResponseParser
import com.pulsepointlabs.elizabethlive.obd.pid.PidDefinition
import com.pulsepointlabs.elizabethlive.obd.transport.ObdTransport
import com.pulsepointlabs.elizabethlive.obd.transport.TransportResult
import kotlinx.coroutines.delay

data class ElmInitialization(
    val supportedPids: Set<Int>,
    val protocolName: String,
)

enum class PidReadStatus { VALUE, NO_DATA, PARSE_FAILED, DECODE_FAILED }

data class PidReadObservation(
    val value: Double?,
    val status: PidReadStatus,
    val response: String,
)

class Elm327Client(private val transport: ObdTransport) {
    suspend fun initialize(onStatus: (String) -> Unit): Result<ElmInitialization> = runCatching {
        val sequence = listOf(
            InitCommand("ATZ", "Resetting ELM327…", 5_000),
            InitCommand("ATE0", "Disabling command echo…", 2_000),
            InitCommand("ATL0", "Disabling line feeds…", 2_000),
            InitCommand("ATS0", "Normalizing response spacing…", 2_000),
            InitCommand("ATH0", "Using headerless standard PID responses…", 2_000),
            InitCommand("ATSP0", "Selecting vehicle protocol automatically…", 3_000),
        )
        sequence.forEach { step ->
            onStatus(step.status)
            requireSuccessful(step.command, step.timeoutMillis)
            delay(40)
        }
        onStatus("Detecting vehicle protocol…")
        val firstSupport = requireResponse("0100", 10_000)
        val supported = discoverSupportedPids(firstSupport, onStatus)
        val protocol = when (val response = transport.send("ATDP", 2_000)) {
            is TransportResult.Response -> cleanTextResponse(response.raw, "ATDP")
            else -> "Automatic"
        }
        ElmInitialization(supported, protocol.ifBlank { "Automatic" })
    }

    suspend fun read(definition: PidDefinition): Result<Double?> =
        readObserved(definition).map(PidReadObservation::value)

    suspend fun readObserved(definition: PidDefinition): Result<PidReadObservation> = runCatching {
        val command = "%02X%02X".format(definition.mode, definition.pid)
        when (val response = sendWithRetry(command, 2_500, retries = 1)) {
            is TransportResult.Response -> {
                val payload = Elm327ResponseParser.payloadFor(
                    response.raw,
                    definition.mode,
                    definition.pid,
                    command,
                )
                if (payload == null) {
                    PidReadObservation(
                        value = null,
                        status = PidReadStatus.PARSE_FAILED,
                        response = sanitizeForDiagnostic(response.raw),
                    )
                } else {
                    val value = definition.decoder(payload)
                    PidReadObservation(
                        value = value,
                        status = if (value == null) {
                            PidReadStatus.DECODE_FAILED
                        } else {
                            PidReadStatus.VALUE
                        },
                        response = sanitizeForDiagnostic(response.raw),
                    )
                }
            }
            TransportResult.NoData -> PidReadObservation(
                value = null,
                status = PidReadStatus.NO_DATA,
                response = "NO DATA",
            )
            is TransportResult.Failure -> error(response.message)
        }
    }

    private fun sanitizeForDiagnostic(raw: String): String =
        raw.replace('>', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)

    private suspend fun discoverSupportedPids(
        firstResponse: String,
        onStatus: (String) -> Unit,
    ): Set<Int> {
        val supported = linkedSetOf<Int>()
        var base = 0x00
        var response = firstResponse
        while (base <= 0x60) {
            onStatus("Scanning supported PIDs ${"%02X".format(base + 1)}–${"%02X".format(base + 0x20)}…")
            // Several ECUs can answer a support query. Merge their bitmaps so a sparse response
            // from one module cannot hide a standard PID reported by the engine ECU.
            val payloads = Elm327ResponseParser
                .payloadsFor(response, 1, base, "01%02X".format(base))
                .filter { it.size >= 4 }
            if (payloads.isEmpty()) break
            for (bit in 0 until 32) {
                if (payloads.any { it[bit / 8] and (1 shl (7 - bit % 8)) != 0 }) {
                    supported += base + bit + 1
                }
            }
            val nextPage = base + 0x20
            if (nextPage !in supported || nextPage >= 0x80) break
            base = nextPage
            response = when (val next = sendWithRetry("01%02X".format(base), 4_000, retries = 1)) {
                is TransportResult.Response -> next.raw
                else -> break
            }
        }
        return supported
    }

    private suspend fun requireSuccessful(command: String, timeoutMillis: Long) {
        when (val response = sendWithRetry(command, timeoutMillis, retries = 1)) {
            is TransportResult.Response -> Unit
            TransportResult.NoData -> error("ELM327 returned NO DATA for $command.")
            is TransportResult.Failure -> error(response.message)
        }
    }

    private suspend fun requireResponse(command: String, timeoutMillis: Long): String =
        when (val response = sendWithRetry(command, timeoutMillis, retries = 1)) {
            is TransportResult.Response -> response.raw
            TransportResult.NoData -> error("Vehicle ignition appears off or ECU returned NO DATA.")
            is TransportResult.Failure -> error(response.message)
        }

    private fun cleanTextResponse(raw: String, command: String): String =
        raw.replace(">", "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it.equals(command, ignoreCase = true) }
            .filterNot { it.equals("SEARCHING...", ignoreCase = true) }
            .joinToString(" ")

    private suspend fun sendWithRetry(
        command: String,
        timeoutMillis: Long,
        retries: Int,
    ): TransportResult {
        var response = transport.send(command, timeoutMillis)
        var attempt = 0
        while (response is TransportResult.Failure && response.recoverable && attempt < retries) {
            attempt++
            delay(100L * attempt)
            response = transport.send(command, timeoutMillis)
        }
        return response
    }

    private data class InitCommand(
        val command: String,
        val status: String,
        val timeoutMillis: Long,
    )
}
