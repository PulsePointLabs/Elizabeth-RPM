package com.pulsepointlabs.elizabethlive.trip

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FuelCostCalculatorTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `liters convert to US gallons`() {
        assertEquals(1.0, FuelCostCalculator.gallons(3.785411784), 0.000001)
    }

    @Test
    fun `trip cost uses configured price`() {
        assertEquals(8.0, FuelCostCalculator.cost(7.570823568, 4.0), 0.000001)
    }

    @Test
    fun `period totals separate today week and month`() {
        val now = time(2026, 7, 25, 12)
        val records = listOf(
            FuelUsageRecord(time(2026, 7, 25, 8), 3.785411784),
            FuelUsageRecord(time(2026, 7, 22, 8), 3.785411784),
            FuelUsageRecord(time(2026, 7, 2, 8), 3.785411784),
            FuelUsageRecord(time(2026, 6, 28, 8), 3.785411784),
        )
        val totals = FuelCostCalculator.aggregate(records, 4.0, now, zone)
        assertEquals(4.0, totals.today, 0.000001)
        assertEquals(8.0, totals.thisWeek, 0.000001)
        assertEquals(12.0, totals.thisMonth, 0.000001)
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
}

