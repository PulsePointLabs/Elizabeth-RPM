package com.pulsepointlabs.elizabethlive.trip

import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.TripEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TripSummaryCalculatorTest {
    @Test
    fun `completed trip summary preserves samples ranges events and distance`() {
        val start = 1_000_000L
        val samples = listOf(
            sample(start, rpm = 1_000.0, speed = 60.0, intake = 30.0),
            sample(start + 60_000L, rpm = 2_000.0, speed = 60.0, intake = 45.0),
        )
        val events = listOf(TripEvent(start + 30_000L, "Hard acceleration", "72% throttle"))

        val result = TripSummaryCalculator.summarize(
            startedAtMillis = start,
            samples = samples,
            events = events,
            fuelUsedLiters = 0.25,
            isRecording = false,
            endedAtMillis = start + 60_000L,
        )

        assertEquals(60L, result.durationSeconds)
        assertEquals(1.0, result.distanceKm, 0.001)
        assertEquals(1_500.0, result.averageRpm, 0.001)
        assertEquals(30.0, result.intakeRangeC.start, 0.001)
        assertEquals(45.0, result.intakeRangeC.endInclusive, 0.001)
        assertEquals(events, result.events)
        assertEquals(0.25, result.fuelUsedLiters, 0.001)
    }

    private fun sample(
        timestamp: Long,
        rpm: Double,
        speed: Double,
        intake: Double,
    ) = TelemetrySample(
        timestampMillis = timestamp,
        rpm = rpm,
        speedKph = speed,
        boostPsi = -3.0,
        throttlePercent = 20.0,
        coolantC = 90.0,
        intakeC = intake,
        shortFuelTrim = 1.0,
        longFuelTrim = -2.0,
        voltage = 13.8,
        engineLoad = 30.0,
        timingAdvance = 12.0,
        fuelRateLitersPerHour = 2.0,
    )
}
