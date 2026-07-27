package com.pulsepointlabs.elizabethlive.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DriveAutomationEngineTest {
    private class FakeClock(var now: Long = 1_000L) : AutomationClock {
        override fun nowMillis(): Long = now
    }

    @Test
    fun bluetoothWithoutRpmDoesNotStartTrip() {
        val engine = DriveAutomationEngine(FakeClock())

        repeat(5) {
            assertEquals(AutomationDecision.NONE, engine.onRpm(null, false, true))
        }
    }

    @Test
    fun threeConsecutiveRpmSamplesStartExactlyOneTrip() {
        val clock = FakeClock()
        val engine = DriveAutomationEngine(clock)

        assertEquals(AutomationDecision.NONE, engine.onRpm(750.0, false, true))
        clock.now += 500
        assertEquals(AutomationDecision.NONE, engine.onRpm(760.0, false, true))
        clock.now += 500
        assertEquals(AutomationDecision.START_AUTOMATIC_TRIP, engine.onRpm(770.0, false, true))
        assertNotNull(engine.confirmedStartMillis)
        assertEquals(AutomationDecision.NONE, engine.onRpm(780.0, true, true))
    }

    @Test
    fun rpmSamplesOutsideConfirmationWindowDoNotStart() {
        val clock = FakeClock()
        val engine = DriveAutomationEngine(clock)

        assertEquals(AutomationDecision.NONE, engine.onRpm(750.0, false, true))
        clock.now += 6_000
        assertEquals(AutomationDecision.NONE, engine.onRpm(760.0, false, true))
        clock.now += 250
        assertEquals(AutomationDecision.NONE, engine.onRpm(770.0, false, true))
    }

    @Test
    fun temporaryDisconnectHoldsAndResumeKeepsSameTrip() {
        val clock = FakeClock()
        val engine = DriveAutomationEngine(clock)

        assertEquals(AutomationDecision.HOLD_TRIP_OPEN, engine.onEcuUnavailable(true))
        clock.now += 60_000
        assertEquals(AutomationDecision.NONE, engine.tick(true, 180_000))
        assertEquals(AutomationDecision.RESUME_TRIP, engine.onRpm(800.0, true, true))
        assertEquals(null, engine.graceStartedAtMillis)
    }

    @Test
    fun graceExpiryFinalizesTrip() {
        val clock = FakeClock()
        val engine = DriveAutomationEngine(clock)

        engine.onEcuUnavailable(true)
        clock.now += 180_000

        assertEquals(AutomationDecision.FINALIZE_TRIP, engine.tick(true, 180_000))
    }

    @Test
    fun manualAndAutomaticLifecycleCannotCreateDuplicateTrip() {
        val engine = DriveAutomationEngine(FakeClock())

        assertEquals(AutomationDecision.START_MANUAL_TRIP, engine.manualStart(false))
        assertEquals(AutomationDecision.NONE, engine.manualStart(true))
        assertEquals(AutomationDecision.NONE, engine.onRpm(900.0, true, true))
    }

    @Test
    fun restoredGraceFinalizesRecoveredTripWhenEcuStaysUnavailable() {
        val clock = FakeClock(now = 500_000)
        val engine = DriveAutomationEngine(clock)
        engine.restoreGracePeriod(300_000)

        assertEquals(AutomationDecision.FINALIZE_TRIP, engine.tick(true, 180_000))
    }
}
