package com.pulsepointlabs.elizabethlive.obd.elm327

import com.pulsepointlabs.elizabethlive.obd.pid.StandardPids
import com.pulsepointlabs.elizabethlive.obd.transport.ObdTransport
import com.pulsepointlabs.elizabethlive.obd.transport.TransportResult
import com.pulsepointlabs.elizabethlive.obd.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ClientTest {
    @Test
    fun `initialization scans supported PID bitmap and protocol`() = runTest {
        val transport = ScriptedTransport(
            mapOf(
                "ATZ" to "ELM327 v2.3\r>",
                "ATE0" to "OK\r>",
                "ATL0" to "OK\r>",
                "ATS0" to "OK\r>",
                "ATH0" to "OK\r>",
                "ATSP0" to "OK\r>",
                "0100" to "41 00 00 18 80 01\r>",
                "0120" to "41 20 80 00 00 00\r>",
                "ATDP" to "AUTO, ISO 15765-4 (CAN 11/500)\r>",
            )
        )
        val result = Elm327Client(transport).initialize { }.getOrThrow()

        assertTrue(0x0C in result.supportedPids)
        assertTrue(0x0D in result.supportedPids)
        assertTrue(0x11 in result.supportedPids)
        assertTrue(0x21 in result.supportedPids)
        assertEquals("AUTO, ISO 15765-4 (CAN 11/500)", result.protocolName)
    }

    @Test
    fun `initialization merges supported PID bitmaps from multiple ECUs`() = runTest {
        val transport = ScriptedTransport(
            mapOf(
                "ATZ" to "ELM327 v2.3\r>",
                "ATE0" to "OK\r>",
                "ATL0" to "OK\r>",
                "ATS0" to "OK\r>",
                "ATH0" to "OK\r>",
                "ATSP0" to "OK\r>",
                "0100" to "7E8 06 41 00 00 18 00 00\r7EA 06 41 00 08 00 80 00\r>",
                "ATDP" to "ISO 15765-4 (CAN 11/500)\r>",
            )
        )

        val supported = Elm327Client(transport).initialize { }.getOrThrow().supportedPids

        assertTrue(0x0C in supported)
        assertTrue(0x0D in supported)
        assertTrue(0x05 in supported)
        assertTrue(0x11 in supported)
    }

    @Test
    fun `29 bit CAN initialization targets the physical engine ECU`() = runTest {
        val transport = ScriptedTransport(
            mapOf(
                "ATZ" to "ELM327 v2.3\r>",
                "ATE0" to "OK\r>",
                "ATL0" to "OK\r>",
                "ATS0" to "OK\r>",
                "ATH0" to "OK\r>",
                "ATSP0" to "OK\r>",
                "0100" to "41 00 00 18 80 00\r>",
                "ATDP" to "AUTO, ISO 15765-4 (CAN 29/500)\r>",
                "ATCP18" to "OK\r>",
                "ATSHDA10F1" to "OK\r>",
                "ATCRA18DAF110" to "OK\r>",
            )
        )

        val result = Elm327Client(transport).initialize { }.getOrThrow()

        assertTrue(result.protocolName.endsWith("PCM 10"))
        assertTrue("ATCP18" in transport.commands)
        assertTrue("ATSHDA10F1" in transport.commands)
        assertTrue("ATCRA18DAF110" in transport.commands)
    }

    @Test
    fun `PID read decodes a real response`() = runTest {
        val transport = ScriptedTransport(mapOf("010C" to "41 0C 2E E0\r>"))
        val rpm = StandardPids.registry.first { it.pid == 0x0C }
        assertEquals(3_000.0, Elm327Client(transport).read(rpm).getOrThrow()!!, 0.001)
    }

    @Test
    fun `NO DATA becomes an unavailable value not zero`() = runTest {
        val transport = object : ScriptedTransport(emptyMap()) {
            override suspend fun send(command: String, timeoutMillis: Long): TransportResult =
                TransportResult.NoData
        }
        val fuelRate = StandardPids.registry.first { it.pid == 0x5E }
        assertEquals(null, Elm327Client(transport).read(fuelRate).getOrThrow())
    }

    @Test
    fun `diagnostic read distinguishes no data from parser rejection`() = runTest {
        val fuelRate = StandardPids.registry.first { it.pid == 0x5E }
        val noDataTransport = object : ScriptedTransport(emptyMap()) {
            override suspend fun send(command: String, timeoutMillis: Long): TransportResult =
                TransportResult.NoData
        }
        val noData = Elm327Client(noDataTransport).readObserved(fuelRate).getOrThrow()
        assertEquals(PidReadStatus.NO_DATA, noData.status)
        assertEquals("NO DATA", noData.response)

        val malformedTransport = ScriptedTransport(mapOf("015E" to "BUS INIT: OK\r>"))
        val malformed = Elm327Client(malformedTransport).readObserved(fuelRate).getOrThrow()
        assertEquals(PidReadStatus.PARSE_FAILED, malformed.status)
        assertEquals("BUS INIT: OK", malformed.response)
    }

    @Test
    fun `diagnostic read retains sanitized successful response`() = runTest {
        val coolant = StandardPids.registry.first { it.pid == 0x05 }
        val observation = Elm327Client(
            ScriptedTransport(mapOf("0105" to "0105\r41 05 7B\r>"))
        ).readObserved(coolant).getOrThrow()

        assertEquals(PidReadStatus.VALUE, observation.status)
        assertEquals(83.0, observation.value!!, 0.001)
        assertEquals("0105 41 05 7B", observation.response)
    }

    @Test
    fun `vehicle diagnostics decode VIN DTCs readiness and freeze frame`() = runTest {
        val transport = ScriptedTransport(
            mapOf(
                "0902" to
                    "49 02 01 31 48 47 43 56 31\r" +
                    "49 02 02 46 31 4D 41 30 30\r" +
                    "49 02 03 30 30 30 30 31\r>",
                "03" to "43 01 33 00 00\r>",
                "07" to "47 00 00\r>",
                "0A" to "4A 00 00\r>",
                "0101" to "41 01 81 07 65 00\r>",
                "0202" to "42 02 01 33\r>",
            )
        )

        val diagnostics = Elm327Client(transport).readVehicleDiagnostics().getOrThrow()

        assertEquals("1HGCV1F1MA0000001", diagnostics.vin)
        assertEquals(listOf("P0133"), diagnostics.storedDtcs)
        assertTrue(diagnostics.pendingDtcs.isEmpty())
        assertTrue(diagnostics.permanentDtcs.isEmpty())
        assertEquals(true, diagnostics.milOn)
        assertEquals(true, diagnostics.freezeFrameAvailable)
        assertTrue(diagnostics.readinessMonitors.any { it.name == "Catalyst" && it.complete })
        assertTrue(diagnostics.readinessMonitors.any { it.name == "Oxygen sensor" && it.complete })
    }

    private open class ScriptedTransport(
        private val responses: Map<String, String>,
    ) : ObdTransport {
        val commands = mutableListOf<String>()
        private val mutableState = MutableStateFlow(TransportState.CONNECTED)
        override val state: StateFlow<TransportState> = mutableState
        override suspend fun connect(deviceAddress: String): Result<Unit> = Result.success(Unit)
        override suspend fun send(command: String, timeoutMillis: Long): TransportResult {
            commands += command
            return responses[command]?.let(TransportResult::Response)
                ?: TransportResult.Failure("Unexpected command: $command", false)
        }
        override suspend fun disconnect() {
            mutableState.value = TransportState.DISCONNECTED
        }
    }
}

