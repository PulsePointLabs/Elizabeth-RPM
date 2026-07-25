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
        PidDefinition(1, 0x11, "Throttle position", "%", PollPriority.FAST) {
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
        PidDefinition(1, 0x42, "Control-module voltage", "V", PollPriority.SLOW) {
            if (it.size >= 2) ((it[0] * 256) + it[1]) / 1000.0 else null
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
        val responseMode = mode + 0x40
        for (line in clean(raw, command)) {
            val bytes = line.split(' ').mapNotNull { it.toIntOrNull(16) }
            val index = bytes.indices.firstOrNull { i ->
                i + 1 < bytes.size && bytes[i] == responseMode && bytes[i + 1] == pid
            } ?: continue
            return bytes.drop(index + 2).takeIf { it.isNotEmpty() }
        }
        return null
    }
}
