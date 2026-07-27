package com.pulsepointlabs.elizabethlive.overlay

import com.pulsepointlabs.elizabethlive.AppSettings
import com.pulsepointlabs.elizabethlive.ConnectionState
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripOverlayMetricsTest {
    @Test
    fun `overlay derives average live mpg and trip cost from real session totals`() {
        val state = ElizabethUiState(
            connectionState = ConnectionState.CONNECTED,
            liveDistanceKm = 10.0,
            liveFuelUsedLiters = 1.0,
            samples = listOf(sample(speedKph = 100.0, fuelRateLitersPerHour = 10.0)),
            settings = AppSettings(fuelPricePerGallon = 4.0),
        )

        val metrics = state.toTripOverlayMetrics()

        assertEquals(23.52, metrics.averageMpg!!, 0.01)
        assertEquals(23.52, metrics.liveMpg!!, 0.01)
        assertEquals(1.06, metrics.tripCost, 0.01)
        assertTrue(metrics.connected)
    }

    @Test
    fun `overlay leaves unavailable economy blank instead of showing zero`() {
        val metrics = ElizabethUiState().toTripOverlayMetrics()

        assertNull(metrics.averageMpg)
        assertNull(metrics.liveMpg)
        assertEquals(0.0, metrics.tripCost, 0.001)
        assertFalse(metrics.connected)
    }

    private fun sample(
        speedKph: Double?,
        fuelRateLitersPerHour: Double?,
    ) = TelemetrySample(
        timestampMillis = 1L,
        rpm = null,
        speedKph = speedKph,
        boostPsi = null,
        throttlePercent = null,
        coolantC = null,
        intakeC = null,
        shortFuelTrim = null,
        longFuelTrim = null,
        voltage = null,
        engineLoad = null,
        timingAdvance = null,
        fuelRateLitersPerHour = fuelRateLitersPerHour,
    )
}
