package com.pulsepointlabs.elizabethlive.automation

import androidx.test.core.app.ApplicationProvider
import com.pulsepointlabs.elizabethlive.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveNotificationPolicyTest {
    @Test
    fun waitingNotificationOnlyOpensDashboard() {
        assertEquals(
            listOf(DriveNotificationAction.OPEN_DASHBOARD),
            DriveNotificationPolicy.actions(recording = false),
        )
    }

    @Test
    fun recordingNotificationOpensDashboardAndStopsTrip() {
        assertEquals(
            listOf(
                DriveNotificationAction.OPEN_DASHBOARD,
                DriveNotificationAction.STOP_TRIP,
            ),
            DriveNotificationPolicy.actions(recording = true),
        )
    }

    @Test
    fun notificationIntentsTargetDashboardAndStopAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val open = DriveNotificationIntents.openDashboard(context)
        val stop = DriveNotificationIntents.stopTrip(context)

        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertTrue(open.getBooleanExtra(MainActivity.EXTRA_OPEN_DASHBOARD, false))
        assertEquals(DriveAutomationService::class.java.name, stop.component?.className)
        assertEquals(DriveAutomationService.ACTION_STOP_TRIP, stop.action)
    }
}
