package com.pulsepointlabs.elizabethlive.automation

import android.content.Context
import android.content.Intent
import com.pulsepointlabs.elizabethlive.MainActivity

enum class DriveNotificationAction {
    OPEN_DASHBOARD,
    STOP_TRIP,
}

object DriveNotificationPolicy {
    fun actions(recording: Boolean): List<DriveNotificationAction> =
        if (recording) {
            listOf(DriveNotificationAction.OPEN_DASHBOARD, DriveNotificationAction.STOP_TRIP)
        } else {
            listOf(DriveNotificationAction.OPEN_DASHBOARD)
        }
}

object DriveNotificationIntents {
    fun openDashboard(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DASHBOARD, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun stopTrip(context: Context): Intent =
        Intent(context, DriveAutomationService::class.java)
            .setAction(DriveAutomationService.ACTION_STOP_TRIP)
}
