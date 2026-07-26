package com.pulsepointlabs.elizabethlive.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEfficiencyCalculatorTest {
    @Test
    fun `MAF estimates gasoline fuel rate`() {
        assertEquals(
            3.287,
            FuelEfficiencyCalculator.fuelRateFromMaf(10.0, 1.0)!!,
            0.001,
        )
    }

    @Test
    fun `instantaneous and average MPG use distance over fuel`() {
        assertEquals(
            29.402,
            FuelEfficiencyCalculator.instantaneousMpg(100.0, 8.0)!!,
            0.001,
        )
        assertEquals(
            29.402,
            FuelEfficiencyCalculator.averageMpg(100.0, 8.0)!!,
            0.001,
        )
    }

    @Test
    fun `fuel economy is unavailable while stopped or before fuel accumulates`() {
        assertNull(FuelEfficiencyCalculator.instantaneousMpg(0.0, 1.0))
        assertNull(FuelEfficiencyCalculator.averageMpg(0.0, 0.0))
    }
}
