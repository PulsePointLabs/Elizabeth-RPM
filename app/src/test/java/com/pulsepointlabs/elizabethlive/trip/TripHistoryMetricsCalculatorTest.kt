package com.pulsepointlabs.elizabethlive.trip

import com.pulsepointlabs.elizabethlive.FuelDataSource
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripHistoryMetricsCalculatorTest {
    @Test
    fun calculatesEconomyCostCoverageAndMovementFromSavedTrip() {
        val result = TripHistoryMetricsCalculator.calculate(
            summary = TripSummary(distanceKm = 100.0, fuelUsedLiters = 8.0),
            samples = listOf(sample(0, 0.0, 1.0), sample(1, 80.0, 7.0)),
            savedFuelSource = FuelDataSource.MAF_ESTIMATED,
            pricePerGallon = 4.0,
        )

        assertEquals(29.4, result.averageMpg!!, 0.1)
        assertEquals(8.0, result.averageLitersPer100Km!!, 0.01)
        assertEquals(2.11, result.fuelUsedGallons!!, 0.01)
        assertEquals(8.45, result.estimatedFuelCost!!, 0.01)
        assertEquals(4.0, result.averageFuelRateLitersPerHour!!, 0.01)
        assertEquals(100, result.fuelDataCoveragePercent)
        assertEquals(50, result.movingSamplePercent)
        assertEquals("Estimated from mass airflow", result.fuelSourceLabel)
    }

    @Test
    fun missingFuelStaysUnavailableInsteadOfBecomingZero() {
        val result = TripHistoryMetricsCalculator.calculate(
            summary = TripSummary(distanceKm = 25.0, fuelUsedLiters = 0.0),
            samples = listOf(sample(0, 50.0, null)),
            savedFuelSource = FuelDataSource.UNAVAILABLE,
            pricePerGallon = 4.0,
        )

        assertNull(result.averageMpg)
        assertNull(result.averageLitersPer100Km)
        assertNull(result.fuelUsedGallons)
        assertNull(result.estimatedFuelCost)
        assertNull(result.averageFuelRateLitersPerHour)
        assertEquals(0, result.fuelDataCoveragePercent)
        assertEquals("Fuel data unavailable", result.fuelSourceLabel)
    }

    @Test
    fun derivesEcuSourceForOlderTripFromItsSamples() {
        val result = TripHistoryMetricsCalculator.calculate(
            summary = TripSummary(distanceKm = 10.0, fuelUsedLiters = 1.0),
            samples = listOf(sample(0, 40.0, 3.0, estimated = false)),
            savedFuelSource = FuelDataSource.UNAVAILABLE,
            pricePerGallon = 4.0,
        )

        assertEquals("ECU-reported fuel rate", result.fuelSourceLabel)
    }

    private fun sample(
        timestamp: Long,
        speed: Double?,
        fuelRate: Double?,
        estimated: Boolean = true,
    ) = TelemetrySample(
        timestampMillis = timestamp,
        rpm = 1_500.0,
        speedKph = speed,
        boostPsi = -2.0,
        throttlePercent = 15.0,
        coolantC = 90.0,
        intakeC = 30.0,
        shortFuelTrim = 0.0,
        longFuelTrim = 0.0,
        voltage = 14.0,
        engineLoad = 20.0,
        timingAdvance = 15.0,
        fuelRateLitersPerHour = fuelRate,
        fuelRateEstimated = estimated,
    )
}
