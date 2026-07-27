package com.pulsepointlabs.elizabethlive.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pulsepointlabs.elizabethlive.DriveAutomationPhase
import com.pulsepointlabs.elizabethlive.ElizabethApplication
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.MainActivity
import com.pulsepointlabs.elizabethlive.R
import com.pulsepointlabs.elizabethlive.trip.FuelCostCalculator
import com.pulsepointlabs.elizabethlive.trip.FuelEfficiencyCalculator
import com.pulsepointlabs.elizabethlive.overlay.FloatingTripOverlayService
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DriveAutomationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session by lazy { (application as ElizabethApplication).obdSession }
    private var lastSavedTripId: Long? = null
    private var automaticOverlayRunning = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWith(waitingNotification("Starting Elizabeth…"))
        session.startDriveAutomation()
        scope.launch {
            session.state.collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRIP -> session.stopTripFromNotification()
            ACTION_CONNECT -> session.startDriveAutomation()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        session.stopDriveAutomation()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(state: ElizabethUiState) {
        val shouldRunAutomaticOverlay =
            state.trip.isRecording &&
            state.driveAutomation.activeTripAutomatic &&
            state.settings.overlayDuringAutomaticTrips
        if (shouldRunAutomaticOverlay && !automaticOverlayRunning) {
            automaticOverlayRunning = FloatingTripOverlayService.startAutomatic(this)
        } else if (!shouldRunAutomaticOverlay && automaticOverlayRunning) {
            FloatingTripOverlayService.stopAutomatic(this)
            automaticOverlayRunning = false
        }
        if (!state.settings.automaticConnection && !state.trip.isRecording) {
            stopSelf()
            return
        }
        val savedId = state.driveAutomation.lastSavedTripId
        if (savedId != null && savedId != lastSavedTripId) {
            lastSavedTripId = savedId
            getSystemService(NotificationManager::class.java).notify(
                SAVED_NOTIFICATION_ID,
                savedTripNotification(state, savedId),
            )
        }
        val notification = when {
            state.trip.isRecording && state.driveAutomation.phase == DriveAutomationPhase.HOLDING_TRIP ->
                recordingNotification(
                    title = "Elizabeth connection interrupted",
                    text = "${state.driveAutomation.statusText} · continuing trip",
                )
            state.trip.isRecording ->
                recordingNotification("Elizabeth connected", "Trip recording")
            state.driveAutomation.phase == DriveAutomationPhase.RECONNECTING ->
                waitingNotification("Connection interrupted · retrying")
            state.driveAutomation.phase == DriveAutomationPhase.WAITING_FOR_IGNITION ->
                waitingNotification("Waiting for ignition")
            else -> waitingNotification(state.driveAutomation.statusText)
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun waitingNotification(text: String) =
        baseNotification("Elizabeth Live", text)
            .applyActions(recording = false)
            .build()

    private fun recordingNotification(title: String, text: String) =
        baseNotification(title, text)
            .applyActions(recording = true)
            .build()

    private fun NotificationCompat.Builder.applyActions(recording: Boolean) = apply {
        DriveNotificationPolicy.actions(recording).forEach { action ->
            when (action) {
                DriveNotificationAction.OPEN_DASHBOARD ->
                    addAction(0, "Open dashboard", openDashboardIntent())
                DriveNotificationAction.STOP_TRIP ->
                    addAction(0, "Stop trip", stopTripIntent())
            }
        }
    }

    private fun savedTripNotification(state: ElizabethUiState, tripId: Long): android.app.Notification {
        val summary = state.trip
        val distance = String.format(Locale.US, "%.1f mi", summary.distanceKm * .621371)
        val mpg = FuelEfficiencyCalculator.averageMpg(summary.distanceKm, summary.fuelUsedLiters)
        val cost = if (summary.fuelUsedLiters > 0.0) {
            FuelCostCalculator.cost(summary.fuelUsedLiters, state.settings.fuelPricePerGallon)
        } else {
            null
        }
        val details = buildList {
            add(distance)
            mpg?.let { add(String.format(Locale.US, "%.1f MPG", it)) }
            cost?.let { add(String.format(Locale.US, "$%.2f", it)) }
        }.joinToString(" · ")
        return baseNotification("Trip saved", details)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(openTripIntent(tripId))
            .build()
    }

    private fun baseNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_elizabeth)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openDashboardIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun openDashboardIntent() = PendingIntent.getActivity(
        this,
        100,
        DriveNotificationIntents.openDashboard(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openTripIntent(tripId: Long) = PendingIntent.getActivity(
        this,
        (tripId and 0x7FFFFFFF).toInt(),
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopTripIntent() = PendingIntent.getService(
        this,
        101,
        DriveNotificationIntents.stopTrip(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startForegroundWith(notification: android.app.Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Drive automation",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Connection, recording, and trip-save status for Elizabeth."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        const val ACTION_STOP_TRIP = "com.pulsepointlabs.elizabethlive.action.STOP_TRIP"
        const val ACTION_CONNECT = "com.pulsepointlabs.elizabethlive.action.CONNECT"
        private const val CHANNEL_ID = "elizabeth_drive_automation"
        private const val NOTIFICATION_ID = 2201
        private const val SAVED_NOTIFICATION_ID = 2202

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DriveAutomationService::class.java).setAction(ACTION_CONNECT),
            )
        }
    }
}
