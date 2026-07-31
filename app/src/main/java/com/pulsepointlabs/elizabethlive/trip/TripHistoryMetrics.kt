package com.pulsepointlabs.elizabethlive.trip

import com.pulsepointlabs.elizabethlive.FuelDataSource
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.TripSummary

data class TripHistoryMetrics(
    val averageMpg: Double?,
    val averageLitersPer100Km: Double?,
    val fuelUsedGallons: Double?,
    val estimatedFuelCost: Double?,
    val averageFuelRateLitersPerHour: Double?,
    val fuelDataCoveragePercent: Int,
    val movingSamplePercent: Int?,
    val fuelSourceLabel: String,
)

object TripHistoryMetricsCalculator {
    fun calculate(
        summary: TripSummary,
        samples: List<TelemetrySample>,
        savedFuelSource: FuelDataSource,
        pricePerGallon: Double,
    ): TripHistoryMetrics {
        val fuelSamples = samples.mapNotNull { it.fuelRateLitersPerHour }
        val speedSamples = samples.mapNotNull { it.speedKph }
        val hasAccumulatedFuel = summary.fuelUsedLiters > 0.001
        val inferredSource = when {
            savedFuelSource != FuelDataSource.UNAVAILABLE -> savedFuelSource
            samples.any { it.fuelRateLitersPerHour != null && !it.fuelRateEstimated } ->
                FuelDataSource.ECU_REPORTED
            samples.any { it.fuelRateLitersPerHour != null && it.fuelRateEstimated } ->
                FuelDataSource.MAF_ESTIMATED
            else -> FuelDataSource.UNAVAILABLE
        }
        val sourceLabel = when (inferredSource) {
            FuelDataSource.ECU_REPORTED -> "ECU-reported fuel rate"
            FuelDataSource.MAF_ESTIMATED -> "Estimated from mass airflow"
            FuelDataSource.UNAVAILABLE -> if (hasAccumulatedFuel) {
                "Fuel source not recorded"
            } else {
                "Fuel data unavailable"
            }
        }
        return TripHistoryMetrics(
            averageMpg = FuelEfficiencyCalculator.averageMpg(
                summary.distanceKm,
                summary.fuelUsedLiters,
            ),
            averageLitersPer100Km = FuelEfficiencyCalculator.averageLitersPer100Km(
                summary.distanceKm,
                summary.fuelUsedLiters,
            ),
            fuelUsedGallons = summary.fuelUsedLiters.takeIf { it > 0.001 }?.let {
                FuelCostCalculator.gallons(it)
            },
            estimatedFuelCost = summary.fuelUsedLiters.takeIf { it > 0.001 }?.let {
                FuelCostCalculator.cost(it, pricePerGallon)
            },
            averageFuelRateLitersPerHour = fuelSamples.takeIf { it.isNotEmpty() }?.average(),
            fuelDataCoveragePercent = if (samples.isEmpty()) 0 else {
                (fuelSamples.size * 100.0 / samples.size).toInt().coerceIn(0, 100)
            },
            movingSamplePercent = speedSamples.takeIf { it.isNotEmpty() }?.let { speeds ->
                (speeds.count { it >= 2.0 } * 100.0 / speeds.size).toInt().coerceIn(0, 100)
            },
            fuelSourceLabel = sourceLabel,
        )
    }
}
