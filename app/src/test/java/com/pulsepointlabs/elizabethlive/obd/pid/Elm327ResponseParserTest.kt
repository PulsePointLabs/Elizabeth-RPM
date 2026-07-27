package com.pulsepointlabs.elizabethlive.obd.pid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Elm327ResponseParserTest {
    @Test
    fun `cleanup removes echo prompt searching and duplicate frames`() {
        val raw = "010C\rSEARCHING...\r41 0C 2E E0\r41 0C 2E E0\r>"
        assertEquals(listOf("41 0C 2E E0"), Elm327ResponseParser.clean(raw, "010C"))
    }

    @Test
    fun `payload parser locates response after CAN header`() {
        val raw = "7E8 04 41 0C 2E E0\r>"
        assertEquals(listOf(0x2E, 0xE0), Elm327ResponseParser.payloadFor(raw, 1, 0x0C, "010C"))
    }

    @Test
    fun `payload parser retains responses from multiple ECUs`() {
        val raw = "7E8 06 41 00 18 00 00 01\r7EA 06 41 00 00 18 80 01\r>"
        assertEquals(
            listOf(
                listOf(0x18, 0x00, 0x00, 0x01),
                listOf(0x00, 0x18, 0x80, 0x01),
            ),
            Elm327ResponseParser.payloadsFor(raw, 1, 0x00, "0100"),
        )
    }

    @Test
    fun `no data returns no payload`() {
        assertNull(Elm327ResponseParser.payloadFor("010C\rNO DATA\r>", 1, 0x0C, "010C"))
    }

    @Test
    fun `malformed partial response is ignored`() {
        assertEquals(emptyList<String>(), Elm327ResponseParser.clean("41 0C 2E E\r>"))
        assertNull(Elm327ResponseParser.payloadFor("41 0C\r>", 1, 0x0C))
    }

    @Test
    fun `line breaks and lower case are normalized`() {
        assertEquals(listOf("41 0C 0B B8"), Elm327ResponseParser.clean("\n41 0c 0b b8\n>"))
    }

    @Test
    fun `stopped and unable to connect do not produce frames`() {
        assertEquals(emptyList<String>(), Elm327ResponseParser.clean("STOPPED\rUNABLE TO CONNECT\r>"))
    }

    @Test
    fun `ELM formatted CAN frames are reassembled and padding is trimmed`() {
        val raw = "0168\r009\r0:416803494800\r1:00000055555555\r>"

        assertEquals(
            listOf("41 68 03 49 48 00 00 00 00"),
            Elm327ResponseParser.clean(raw, "0168"),
        )
        assertEquals(
            listOf(0x03, 0x49, 0x48, 0x00, 0x00, 0x00, 0x00),
            Elm327ResponseParser.payloadFor(raw, 1, 0x68, "0168"),
        )
    }

    @Test
    fun `collapsed ELM formatted CAN diagnostic response is accepted`() {
        val raw = "009 0:416803494800 1:00000055555555"

        assertEquals(
            listOf(0x03, 0x49, 0x48, 0x00, 0x00, 0x00, 0x00),
            Elm327ResponseParser.payloadFor(raw, 1, 0x68, "0168"),
        )
    }

    @Test
    fun `partial ELM formatted CAN response remains unavailable`() {
        val raw = "009\r0:416803494800\r>"

        assertNull(Elm327ResponseParser.payloadFor(raw, 1, 0x68, "0168"))
    }

    @Test
    fun `ELM formatted CAN response with missing sequence remains unavailable`() {
        val raw = "009\r0:416803494800\r2:00000055555555\r>"

        assertNull(Elm327ResponseParser.payloadFor(raw, 1, 0x68, "0168"))
    }
}
