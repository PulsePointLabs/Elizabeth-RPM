package com.pulsepointlabs.elizabethlive.obd.pid

data class PidDefinition(
    val mode: Int,
    val pid: Int,
    val name: String,
    val unit: String,
    val priority: PollPriority,
    val decoder: (List<Int>) -> Double?,
)

enum class PollPriority { FAST, MEDIUM, SLOW, ON_DEMAND }

object StandardPids {
    val registry = listOf(
        PidDefinition(1, 0x0C, "Engine RPM", "rpm", PollPriority.FAST) {
            if (it.size >= 2) ((it[0] * 256) + it[1]) / 4.0 else null
        },
        PidDefinition(1, 0x0D, "Vehicle speed", "km/h", PollPriority.MEDIUM) {
            it.firstOrNull()?.toDouble()
        },
        PidDefinition(1, 0x05, "Coolant temperature", "°C", PollPriority.MEDIUM) {
            it.firstOrNull()?.minus(40)?.toDouble()
        },
        PidDefinition(1, 0x0F, "Intake-air temperature", "°C", PollPriority.MEDIUM) {
            it.firstOrNull()?.minus(40)?.toDouble()
        },
        PidDefinition(1, 0x10, "Mass air flow", "g/s", PollPriority.MEDIUM) {
            if (it.size >= 2) ((it[0] * 256) + it[1]) / 100.0 else null
        },
        PidDefinition(1, 0x66, "Mass air flow sensor A/B", "g/s", PollPriority.MEDIUM) {
            when {
                // Byte A reports installed sensors; B/C and D/E contain their readings.
                it.size >= 3 && it[0] and 0x01 != 0 ->
                    ((it[1] * 256) + it[2]) / 32.0
                it.size >= 5 && it[0] and 0x02 != 0 ->
                    ((it[3] * 256) + it[4]) / 32.0
                else -> null
            }
        },
        PidDefinition(1, 0x67, "Engine coolant temperature sensor", "°C", PollPriority.MEDIUM) {
            when {
                // Byte A reports installed sensors; prefer ECT1 (B), then ECT2 (C).
                it.size >= 2 && it[0] and 0x01 != 0 -> it[1].minus(40).toDouble()
                it.size >= 3 && it[0] and 0x02 != 0 -> it[2].minus(40).toDouble()
                else -> null
            }
        },
        PidDefinition(1, 0x68, "Intake-air temperature sensor", "°C", PollPriority.MEDIUM) { payload ->
            // Byte A reports up to six installed IAT sensors. Use the first reported sensor.
            (0 until 6).firstNotNullOfOrNull { sensor ->
                payload.getOrNull(sensor + 1)
                    ?.takeIf { payload[0] and (1 shl sensor) != 0 }
                    ?.minus(40)
                    ?.toDouble()
            }
        },
        PidDefinition(1, 0x11, "Throttle position", "%", PollPriority.FAST) {
            it.firstOrNull()?.times(100.0)?.div(255.0)
        },
        PidDefinition(1, 0x04, "Calculated engine load", "%", PollPriority.MEDIUM) {
            it.firstOrNull()?.times(100.0)?.div(255.0)
        },
        PidDefinition(1, 0x0B, "Manifold pressure", "kPa", PollPriority.FAST) {
            it.firstOrNull()?.toDouble()
        },
        PidDefinition(1, 0x33, "Barometric pressure", "kPa", PollPriority.SLOW) {
            it.firstOrNull()?.toDouble()
        },
        PidDefinition(1, 0x06, "Short-term fuel trim B1", "%", PollPriority.SLOW) {
            it.firstOrNull()?.let { a -> (a - 128.0) * 100.0 / 128.0 }
        },
        PidDefinition(1, 0x07, "Long-term fuel trim B1", "%", PollPriority.SLOW) {
            it.firstOrNull()?.let { a -> (a - 128.0) * 100.0 / 128.0 }
        },
        PidDefinition(1, 0x0E, "Ignition timing advance", "°", PollPriority.MEDIUM) {
            it.firstOrNull()?.div(2.0)?.minus(64.0)
        },
        PidDefinition(1, 0x42, "Control-module voltage", "V", PollPriority.SLOW) {
            if (it.size >= 2) ((it[0] * 256) + it[1]) / 1000.0 else null
        },
        PidDefinition(1, 0x44, "Commanded equivalence ratio", "λ", PollPriority.SLOW) {
            if (it.size >= 2) ((it[0] * 256) + it[1]) / 32768.0 else null
        },
        PidDefinition(1, 0x5E, "Engine fuel rate", "L/h", PollPriority.MEDIUM) {
            if (it.size >= 2) ((it[0] * 256) + it[1]) / 20.0 else null
        },
    )

    fun calculatedBoostPsi(mapKpa: Double, barometricKpa: Double): Double =
        (mapKpa - barometricKpa) * 0.1450377377
}

object Elm327ResponseParser {
    private val statusLines = setOf("SEARCHING...", "SEARCHING", "STOPPED", "?")

    /**
     * Removes prompts, echo, whitespace, CAN header noise, and duplicates while retaining
     * complete hexadecimal payload rows. Partial odd-nibble rows are ignored safely.
     */
    fun clean(raw: String, command: String? = null): List<String> {
        val echo = command?.replace(" ", "")?.uppercase()
        return raw
            .replace(">", "\n")
            .split('\r', '\n')
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() && it !in statusLines }
            .filterNot { it.replace(" ", "") == echo }
            .filterNot { it == "NO DATA" || it == "UNABLE TO CONNECT" }
            .mapNotNull { line ->
                val tokens = line.split(Regex("\\s+"))
                val withoutCanHeader = if (
                    tokens.size > 1 &&
                    (tokens.first().length == 3 || tokens.first().length == 8) &&
                    tokens.first().all { it in '0'..'9' || it in 'A'..'F' }
                ) {
                    tokens.drop(1)
                } else {
                    tokens
                }
                val compact = withoutCanHeader.joinToString("").replace(Regex("[^0-9A-F]"), "")
                when {
                    compact.length < 4 || compact.length % 2 != 0 -> null
                    else -> compact.chunked(2).joinToString(" ")
                }
            }
            .distinct()
    }

    fun payloadFor(raw: String, mode: Int, pid: Int, command: String? = null): List<Int>? {
        return payloadsFor(raw, mode, pid, command).firstOrNull()
    }

    fun payloadsFor(raw: String, mode: Int, pid: Int, command: String? = null): List<List<Int>> {
        val responseMode = mode + 0x40
        val payloads = mutableListOf<List<Int>>()
        for (line in clean(raw, command)) {
            val bytes = line.split(' ').mapNotNull { it.toIntOrNull(16) }
            val index = bytes.indices.firstOrNull { i ->
                i + 1 < bytes.size && bytes[i] == responseMode && bytes[i + 1] == pid
            } ?: continue
            bytes.drop(index + 2).takeIf { it.isNotEmpty() }?.let(payloads::add)
        }
        return payloads
    }
}
