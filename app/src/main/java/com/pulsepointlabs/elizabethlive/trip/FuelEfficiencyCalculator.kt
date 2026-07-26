package com.pulsepointlabs.elizabethlive.trip

object FuelEfficiencyCalculator {
    private const val LitersPerGallon = 3.785411784
    private const val MilesPerKilometer = 0.621371192
    private const val StoichiometricGasolineAfr = 14.7
    private const val GasolineDensityGramsPerLiter = 745.0

    /**
     * Estimates gasoline volume from standard Mode 01 MAF data. Lambda improves the estimate
     * during enrichment when PID 01 44 is reported; otherwise stoichiometric operation is used.
     */
    fun fuelRateFromMaf(
        massAirFlowGramsPerSecond: Double?,
        equivalenceRatio: Double?,
    ): Double? {
        val maf = massAirFlowGramsPerSecond?.takeIf { it >= 0.0 } ?: return null
        val lambda = equivalenceRatio?.takeIf { it in 0.5..1.5 } ?: 1.0
        val fuelGramsPerSecond = maf / (StoichiometricGasolineAfr * lambda)
        return fuelGramsPerSecond * 3_600.0 / GasolineDensityGramsPerLiter
    }

    fun instantaneousMpg(speedKph: Double?, fuelRateLitersPerHour: Double?): Double? {
        val speed = speedKph?.takeIf { it >= 2.0 } ?: return null
        val fuelRate = fuelRateLitersPerHour?.takeIf { it > 0.01 } ?: return null
        return (speed * MilesPerKilometer) / (fuelRate / LitersPerGallon)
    }

    fun averageMpg(distanceKm: Double, fuelUsedLiters: Double): Double? {
        if (distanceKm <= 0.05 || fuelUsedLiters <= 0.001) return null
        return (distanceKm * MilesPerKilometer) / (fuelUsedLiters / LitersPerGallon)
    }

    fun instantaneousLitersPer100Km(
        speedKph: Double?,
        fuelRateLitersPerHour: Double?,
    ): Double? {
        val speed = speedKph?.takeIf { it >= 2.0 } ?: return null
        val fuelRate = fuelRateLitersPerHour?.takeIf { it > 0.01 } ?: return null
        return fuelRate / speed * 100.0
    }

    fun averageLitersPer100Km(distanceKm: Double, fuelUsedLiters: Double): Double? {
        if (distanceKm <= 0.05 || fuelUsedLiters <= 0.001) return null
        return fuelUsedLiters / distanceKm * 100.0
    }
}
