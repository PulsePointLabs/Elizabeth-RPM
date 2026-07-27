package com.pulsepointlabs.elizabethlive.overlay

import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.trip.FuelCostCalculator
import com.pulsepointlabs.elizabethlive.trip.FuelEfficiencyCalculator

data class TripOverlayMetrics(
    val averageMpg: Double?,
    val liveMpg: Double?,
    val tripCost: Double,
    val connected: Boolean,
)

fun ElizabethUiState.toTripOverlayMetrics(): TripOverlayMetrics {
    val latest = samples.lastOrNull()
    return TripOverlayMetrics(
        averageMpg = FuelEfficiencyCalculator.averageMpg(
            distanceKm = liveDistanceKm,
            fuelUsedLiters = liveFuelUsedLiters,
        ),
        liveMpg = FuelEfficiencyCalculator.instantaneousMpg(
            speedKph = latest?.speedKph,
            fuelRateLitersPerHour = latest?.fuelRateLitersPerHour,
        ),
        tripCost = FuelCostCalculator.cost(
            liters = liveFuelUsedLiters,
            pricePerGallon = settings.fuelPricePerGallon,
        ),
        connected = connectionState == com.pulsepointlabs.elizabethlive.ConnectionState.CONNECTED,
    )
}
