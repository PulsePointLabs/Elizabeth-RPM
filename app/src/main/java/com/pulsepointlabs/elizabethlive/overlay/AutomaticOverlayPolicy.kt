package com.pulsepointlabs.elizabethlive.overlay

object AutomaticOverlayPolicy {
    fun shouldShow(
        tripRecording: Boolean,
        tripAutomatic: Boolean,
        settingEnabled: Boolean,
        permissionGranted: Boolean,
    ): Boolean = tripRecording && tripAutomatic && settingEnabled && permissionGranted
}
