package com.pulsepointlabs.elizabethlive.data

import kotlinx.coroutines.flow.Flow

data class FuelPriceQuote(
    val regularPricePerGallon: Double,
    val areaLabel: String,
    val sourceLabel: String,
    val observedAtMillis: Long,
)

/**
 * Pricing stays separate from trip telemetry. A future nearby-price implementation can use
 * a user-triggered coarse area lookup without ever uploading routes, VINs, or trip history.
 */
interface FuelPriceProvider {
    val quote: Flow<FuelPriceQuote?>
    suspend fun refreshForCoarseArea(): Result<FuelPriceQuote>
}

