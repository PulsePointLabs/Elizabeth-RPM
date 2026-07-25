package com.pulsepointlabs.elizabethlive.trip

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

data class FuelUsageRecord(
    val startedAtMillis: Long,
    val fuelUsedLiters: Double,
)

data class FuelCostPeriods(
    val today: Double,
    val thisWeek: Double,
    val thisMonth: Double,
)

object FuelCostCalculator {
    const val LITERS_PER_US_GALLON = 3.785411784

    fun gallons(liters: Double): Double = liters.coerceAtLeast(0.0) / LITERS_PER_US_GALLON

    fun cost(liters: Double, pricePerGallon: Double): Double =
        gallons(liters) * pricePerGallon.coerceAtLeast(0.0)

    fun aggregate(
        records: List<FuelUsageRecord>,
        pricePerGallon: Double,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): FuelCostPeriods {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val weekFields = WeekFields.of(Locale.getDefault())
        var today = 0.0
        var week = 0.0
        var month = 0.0
        records.forEach { record ->
            val time = Instant.ofEpochMilli(record.startedAtMillis).atZone(zoneId)
            val recordCost = cost(record.fuelUsedLiters, pricePerGallon)
            if (time.toLocalDate() == now.toLocalDate()) today += recordCost
            if (
                time.get(weekFields.weekOfWeekBasedYear()) == now.get(weekFields.weekOfWeekBasedYear()) &&
                time.get(weekFields.weekBasedYear()) == now.get(weekFields.weekBasedYear())
            ) week += recordCost
            if (time.year == now.year && time.month == now.month) month += recordCost
        }
        return FuelCostPeriods(today, week, month)
    }
}

