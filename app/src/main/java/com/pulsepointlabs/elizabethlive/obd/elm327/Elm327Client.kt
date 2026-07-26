package com.pulsepointlabs.elizabethlive.obd.elm327

import com.pulsepointlabs.elizabethlive.obd.pid.Elm327ResponseParser
import com.pulsepointlabs.elizabethlive.obd.pid.PidDefinition
import com.pulsepointlabs.elizabethlive.obd.transport.ObdTransport
import com.pulsepointlabs.elizabethlive.obd.transport.TransportResult
import kotlinx.coroutines.delay
import com.pulsepointlabs.elizabethlive.ReadinessMonitor

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

data class ElmVehicleDiagnostics(
    val vin: String?,
    val storedDtcs: List<String>,
    val pendingDtcs: List<String>,
    val permanentDtcs: List<String>,
    val readinessMonitors: List<ReadinessMonitor>,
    val milOn: Boolean?,
    val freezeFrameAvailable: Boolean?,
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
        val protocolName = protocol.ifBlank { "Automatic" }
        if (protocolName.contains("29/500", ignoreCase = true)) {
            onStatus("Targeting the 29-bit engine ECU…")
            // Physical ISO 15765 addressing for engine ECU 0x10:
            // request 18 DA 10 F1, accept replies from 18 DA F1 10.
            requireSuccessful("ATCP18", 2_000)
            requireSuccessful("ATSHDA10F1", 2_000)
            requireSuccessful("ATCRA18DAF110", 2_000)
        }
        ElmInitialization(
            supportedPids = supported,
            protocolName = if (protocolName.contains("29/500", ignoreCase = true)) {
                "$protocolName · PCM 10"
            } else {
                protocolName
            },
        )
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

    suspend fun readVehicleDiagnostics(
        onStatus: (String) -> Unit = {},
    ): Result<ElmVehicleDiagnostics> = runCatching {
        onStatus("Reading vehicle identification…")
        val vin = optionalResponse("0902", 5_000)?.let(::parseVin)

        onStatus("Reading stored trouble codes…")
        val stored = optionalResponse("03", 4_000)?.let { parseDtcs(it, 0x43) }.orEmpty()

        onStatus("Reading pending trouble codes…")
        val pending = optionalResponse("07", 4_000)?.let { parseDtcs(it, 0x47) }.orEmpty()

        onStatus("Reading permanent trouble codes…")
        val permanent = optionalResponse("0A", 4_000)?.let { parseDtcs(it, 0x4A) }.orEmpty()

        onStatus("Reading emissions readiness…")
        val readinessPayload = optionalResponse("0101", 4_000)?.let {
            Elm327ResponseParser.payloadFor(it, 1, 0x01, "0101")
        }
        val readiness = readinessPayload?.let(::parseReadiness)

        onStatus("Checking freeze-frame availability…")
        val freezeFrame = optionalResponse("0202", 4_000)?.let {
            parseDtcs(it, 0x42).isNotEmpty()
        }

        ElmVehicleDiagnostics(
            vin = vin,
            storedDtcs = stored,
            pendingDtcs = pending,
            permanentDtcs = permanent,
            readinessMonitors = readiness?.monitors.orEmpty(),
            milOn = readiness?.milOn,
            freezeFrameAvailable = freezeFrame,
        )
    }

    private suspend fun optionalResponse(command: String, timeoutMillis: Long): String? =
        when (val response = sendWithRetry(command, timeoutMillis, retries = 1)) {
            is TransportResult.Response -> response.raw
            TransportResult.NoData -> null
            is TransportResult.Failure -> error(response.message)
        }

    private fun parseVin(raw: String): String? {
        val chunks = raw.replace(">", "\n")
            .lineSequence()
            .mapNotNull { line ->
                val compact = line.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
                val marker = compact.indexOf("4902")
                if (marker < 0) return@mapNotNull null
                val bytes = compact.drop(marker + 4)
                    .chunked(2)
                    .mapNotNull { it.toIntOrNull(16) }
                if (bytes.isEmpty()) return@mapNotNull null
                val sequence = bytes.first()
                val data = if (sequence in 1..9) bytes.drop(1) else bytes
                sequence to data
            }
            .sortedBy { it.first }
            .flatMap { it.second }
        return chunks
            .filter { it in 0x20..0x7E }
            .map(Int::toChar)
            .joinToString("")
            .trim()
            .takeIf { it.length >= 11 }
            ?.take(17)
    }

    private fun parseDtcs(raw: String, responseMode: Int): List<String> {
        val compact = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val marker = "%02X".format(responseMode)
        val start = compact.indexOf(marker)
        if (start < 0) return emptyList()
        return compact.drop(start + marker.length)
            .chunked(4)
            .mapNotNull { pair ->
                if (pair.length != 4 || pair == "0000") return@mapNotNull null
                val a = pair.take(2).toIntOrNull(16) ?: return@mapNotNull null
                val b = pair.drop(2).toIntOrNull(16) ?: return@mapNotNull null
                val family = "PCBU"[(a shr 6) and 0x03]
                "%c%d%X%X%X".format(
                    family,
                    (a shr 4) and 0x03,
                    a and 0x0F,
                    (b shr 4) and 0x0F,
                    b and 0x0F,
                )
            }
            .distinct()
    }

    private data class ReadinessResult(
        val monitors: List<ReadinessMonitor>,
        val milOn: Boolean,
    )

    private fun parseReadiness(payload: List<Int>): ReadinessResult? {
        if (payload.size < 4) return null
        val a = payload[0]
        val b = payload[1]
        val c = payload[2]
        val d = payload[3]
        val monitors = mutableListOf<ReadinessMonitor>()
        fun addIfAvailable(name: String, available: Boolean, incomplete: Boolean) {
            if (available) monitors += ReadinessMonitor(name, complete = !incomplete)
        }
        addIfAvailable("Misfire", b and 0x01 != 0, b and 0x10 != 0)
        addIfAvailable("Fuel system", b and 0x02 != 0, b and 0x20 != 0)
        addIfAvailable("Comprehensive components", b and 0x04 != 0, b and 0x40 != 0)
        val compressionIgnition = b and 0x08 != 0
        if (!compressionIgnition) {
            val names = listOf(
                "Catalyst",
                "Heated catalyst",
                "Evaporative system",
                "Secondary air",
                "A/C refrigerant",
                "Oxygen sensor",
                "Oxygen-sensor heater",
                "EGR / VVT",
            )
            names.forEachIndexed { bit, name ->
                addIfAvailable(name, c and (1 shl bit) != 0, d and (1 shl bit) != 0)
            }
        }
        return ReadinessResult(monitors, milOn = a and 0x80 != 0)
    }

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
