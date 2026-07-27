package com.pulsepointlabs.elizabethlive.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticOverlayPolicyTest {
    @Test
    fun overlayStartsOnlyForEnabledAutomaticTripWithPermission() {
        assertTrue(AutomaticOverlayPolicy.shouldShow(true, true, true, true))
        assertFalse(AutomaticOverlayPolicy.shouldShow(true, false, true, true))
        assertFalse(AutomaticOverlayPolicy.shouldShow(true, true, false, true))
        assertFalse(AutomaticOverlayPolicy.shouldShow(true, true, true, false))
    }

    @Test
    fun overlayStopsWhenTripStops() {
        assertFalse(AutomaticOverlayPolicy.shouldShow(false, true, true, true))
    }
}
