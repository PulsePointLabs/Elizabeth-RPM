package com.pulsepointlabs.elizabethlive.trip

import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.TripEvent
import com.pulsepointlabs.elizabethlive.TripSummary

object TripSummaryCalculator {
    fun summarize(
        startedAtMillis: Long?,
        samples: List<TelemetrySample>,
        events: List<TripEvent>,
        fuelUsedLiters: Double,
        isRecording: Boolean,
        endedAtMillis: Long = System.currentTimeMillis(),
    ): TripSummary {
        val startedAt = startedAtMillis ?: return TripSummary()
        val tripSamples = samples.filter { it.timestampMillis >= startedAt }
        fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
        fun List<Double>.maxOrZero() = maxOrNull() ?: 0.0
        fun List<Double>.rangeOrZero(): ClosedFloatingPointRange<Double> =
            if (isEmpty()) 0.0..0.0 else (minOrNull() ?: 0.0)..(maxOrNull() ?: 0.0)

        val speeds = tripSamples.mapNotNull { it.speedKph }
        val rpms = tripSamples.mapNotNull { it.rpm }
        val boost = tripSamples.mapNotNull { it.boostPsi }
        val coolant = tripSamples.mapNotNull { it.coolantC }
        val intake = tripSamples.mapNotNull { it.intakeC }
        val throttle = tripSamples.mapNotNull { it.throttlePercent }
        val trims = tripSamples.flatMap { listOfNotNull(it.shortFuelTrim, it.longFuelTrim) }
        val voltage = tripSamples.mapNotNull { it.voltage }
        val distance = tripSamples.zipWithNext().sumOf { (first, second) ->
            val speed = first.speedKph ?: second.speedKph ?: 0.0
            speed * ((second.timestampMillis - first.timestampMillis).coerceAtLeast(0L) / 3_600_000.0)
        }
        val actualEnd = if (isRecording) {
            endedAtMillis
        } else {
            tripSamples.lastOrNull()?.timestampMillis ?: endedAtMillis
        }
        return TripSummary(
            isRecording = isRecording,
            startedAtMillis = startedAt,
            durationSeconds = ((actualEnd - startedAt).coerceAtLeast(0L) / 1_000L),
            distanceKm = distance,
            averageSpeedKph = speeds.averageOrZero(),
            maximumSpeedKph = speeds.maxOrZero(),
            averageRpm = rpms.averageOrZero(),
            maximumRpm = rpms.maxOrZero(),
            maximumBoostPsi = boost.maxOrZero(),
            coolantRangeC = coolant.rangeOrZero(),
            intakeRangeC = intake.rangeOrZero(),
            averageThrottle = throttle.averageOrZero(),
            fuelTrimRange = trims.rangeOrZero(),
            minimumVoltage = voltage.minOrNull() ?: 0.0,
            fuelUsedLiters = fuelUsedLiters,
            events = events,
        )
    }
}
