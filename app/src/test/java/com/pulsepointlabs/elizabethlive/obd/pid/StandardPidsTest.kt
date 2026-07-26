package com.pulsepointlabs.elizabethlive.obd.pid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardPidsTest {
    private fun decoder(pid: Int) = StandardPids.registry.first { it.pid == pid }.decoder

    @Test
    fun `rpm formula decodes two bytes`() {
        assertEquals(3_000.0, decoder(0x0C)(listOf(0x2E, 0xE0))!!, 0.001)
    }

    @Test
    fun `speed formula uses first byte`() {
        assertEquals(100.0, decoder(0x0D)(listOf(0x64))!!, 0.001)
    }

    @Test
    fun `temperature formulas apply negative forty offset`() {
        assertEquals(90.0, decoder(0x05)(listOf(0x82))!!, 0.001)
        assertEquals(30.0, decoder(0x0F)(listOf(0x46))!!, 0.001)
    }

    @Test
    fun `throttle formula scales zero to one hundred percent`() {
        assertEquals(50.196, decoder(0x11)(listOf(0x80))!!, 0.001)
    }

    @Test
    fun `fuel trim formula is centered at zero`() {
        assertEquals(0.0, decoder(0x06)(listOf(0x80))!!, 0.001)
        assertEquals(-100.0, decoder(0x07)(listOf(0x00))!!, 0.001)
    }

    @Test
    fun `control module voltage decodes millivolts`() {
        assertEquals(14.0, decoder(0x42)(listOf(0x36, 0xB0))!!, 0.001)
    }

    @Test
    fun `engine fuel rate decodes liters per hour`() {
        assertEquals(10.0, decoder(0x5E)(listOf(0x00, 0xC8))!!, 0.001)
    }

    @Test
    fun `mass air flow and equivalence ratio decode standard formulas`() {
        assertEquals(10.0, decoder(0x10)(listOf(0x03, 0xE8))!!, 0.001)
        assertEquals(1.0, decoder(0x44)(listOf(0x80, 0x00))!!, 0.001)
    }

    @Test
    fun `calculated boost uses map minus barometric pressure`() {
        assertEquals(14.5038, StandardPids.calculatedBoostPsi(200.0, 100.0), 0.001)
        assertEquals(-7.2519, StandardPids.calculatedBoostPsi(50.0, 100.0), 0.001)
    }

    @Test
    fun `short payload returns null instead of crashing`() {
        assertNull(decoder(0x0C)(listOf(0x2E)))
        assertNull(decoder(0x42)(emptyList()))
    }
}
