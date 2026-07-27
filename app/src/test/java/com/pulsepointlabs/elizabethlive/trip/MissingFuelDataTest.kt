package com.pulsepointlabs.elizabethlive.trip

import org.junit.Assert.assertNull
import org.junit.Test

class MissingFuelDataTest {
    @Test
    fun unavailableFuelDoesNotBecomeZeroMpg() {
        assertNull(FuelEfficiencyCalculator.averageMpg(distanceKm = 25.0, fuelUsedLiters = 0.0))
    }
}
