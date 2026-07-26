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
}
