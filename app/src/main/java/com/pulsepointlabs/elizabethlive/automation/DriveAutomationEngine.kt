package com.pulsepointlabs.elizabethlive.automation

interface AutomationClock {
    fun nowMillis(): Long
}

object SystemAutomationClock : AutomationClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

enum class AutomationDecision {
    NONE,
    START_AUTOMATIC_TRIP,
    START_MANUAL_TRIP,
    HOLD_TRIP_OPEN,
    RESUME_TRIP,
    FINALIZE_TRIP,
}

class DriveAutomationEngine(
    private val clock: AutomationClock = SystemAutomationClock,
    private val requiredRpmSamples: Int = 3,
    private val confirmationWindowMillis: Long = 5_000L,
) {
    private val validRpmTimes = ArrayDeque<Long>()
    var graceStartedAtMillis: Long? = null
        private set
    var confirmedStartMillis: Long? = null
        private set

    fun onRpm(
        rpm: Double?,
        hasActiveTrip: Boolean,
        automaticTripsEnabled: Boolean,
    ): AutomationDecision {
        val now = clock.nowMillis()
        if (rpm == null || rpm <= 0.0) return AutomationDecision.NONE

        if (hasActiveTrip) {
            validRpmTimes.clear()
            return if (graceStartedAtMillis != null) {
                graceStartedAtMillis = null
                AutomationDecision.RESUME_TRIP
            } else {
                AutomationDecision.NONE
            }
        }
        if (!automaticTripsEnabled) return AutomationDecision.NONE

        validRpmTimes.addLast(now)
        while (validRpmTimes.isNotEmpty() && now - validRpmTimes.first() > confirmationWindowMillis) {
            validRpmTimes.removeFirst()
        }
        return if (validRpmTimes.size >= requiredRpmSamples) {
            confirmedStartMillis = validRpmTimes.first()
            validRpmTimes.clear()
            AutomationDecision.START_AUTOMATIC_TRIP
        } else {
            AutomationDecision.NONE
        }
    }

    fun onEcuUnavailable(hasActiveTrip: Boolean): AutomationDecision {
        validRpmTimes.clear()
        if (!hasActiveTrip || graceStartedAtMillis != null) return AutomationDecision.NONE
        graceStartedAtMillis = clock.nowMillis()
        return AutomationDecision.HOLD_TRIP_OPEN
    }

    fun tick(hasActiveTrip: Boolean, gracePeriodMillis: Long): AutomationDecision {
        val startedAt = graceStartedAtMillis ?: return AutomationDecision.NONE
        if (!hasActiveTrip) {
            graceStartedAtMillis = null
            return AutomationDecision.NONE
        }
        return if (clock.nowMillis() - startedAt >= gracePeriodMillis) {
            graceStartedAtMillis = null
            AutomationDecision.FINALIZE_TRIP
        } else {
            AutomationDecision.NONE
        }
    }

    fun manualStart(hasActiveTrip: Boolean): AutomationDecision =
        if (hasActiveTrip) AutomationDecision.NONE else AutomationDecision.START_MANUAL_TRIP

    fun manualStop(hasActiveTrip: Boolean): AutomationDecision =
        if (hasActiveTrip) AutomationDecision.FINALIZE_TRIP else AutomationDecision.NONE

    fun restoreGracePeriod(startedAtMillis: Long?) {
        graceStartedAtMillis = startedAtMillis
    }

    fun clearTripState() {
        validRpmTimes.clear()
        graceStartedAtMillis = null
        confirmedStartMillis = null
    }
}
