package com.pulsepointlabs.elizabethlive.overlay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pulsepointlabs.elizabethlive.ElizabethApplication
import com.pulsepointlabs.elizabethlive.MainActivity
import com.pulsepointlabs.elizabethlive.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingTripOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: FloatingTripOverlay
    private var automaticRequest = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        overlay = FloatingTripOverlay(this) {
            (application as ElizabethApplication).obdSession.setOverlayEnabled(false)
            stopSelf()
        }
        scope.launch {
            (application as ElizabethApplication).obdSession.state.collectLatest { state ->
                val shouldShow = state.trip.isRecording &&
                    (
                        state.settings.overlayEnabled ||
                            (
                                automaticRequest &&
                                    AutomaticOverlayPolicy.shouldShow(
                                        tripRecording = state.trip.isRecording,
                                        tripAutomatic = state.driveAutomation.activeTripAutomatic,
                                        settingEnabled = state.settings.overlayDuringAutomaticTrips,
                                        permissionGranted = FloatingTripOverlay.canDraw(this@FloatingTripOverlayService),
                                    )
                            )
                        )
                if (shouldShow && FloatingTripOverlay.canDraw(this@FloatingTripOverlayService)) {
                    overlay.update(state.toTripOverlayMetrics())
                } else {
                    overlay.hide()
                    if (!state.trip.isRecording) {
                        automaticRequest = false
                        if (state.settings.overlayEnabled) {
                            (application as ElizabethApplication).obdSession.setOverlayEnabled(false)
                        }
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as ElizabethApplication).obdSession.setOverlayEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_AUTO_START) {
            automaticRequest = true
            return START_STICKY
        }
        if (intent?.action == ACTION_AUTO_STOP) {
            automaticRequest = false
            if (!(application as ElizabethApplication).obdSession.state.value.settings.overlayEnabled) {
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (!FloatingTripOverlay.canDraw(this)) {
            (application as ElizabethApplication).obdSession.setOverlayEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        (application as ElizabethApplication).obdSession.setOverlayEnabled(true)
        return START_STICKY
    }

    override fun onDestroy() {
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingTripOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_elizabeth)
            .setContentTitle("Elizabeth trip overlay")
            .setContentText("Average MPG, live MPG, and trip cost are visible over other apps.")
            .setContentIntent(openIntent)
            .addAction(0, "Hide overlay", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Floating trip overlay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps Elizabeth's trip values visible over navigation and other apps."
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val ACTION_STOP = "com.pulsepointlabs.elizabethlive.overlay.STOP"
        private const val ACTION_AUTO_START = "com.pulsepointlabs.elizabethlive.overlay.AUTO_START"
        private const val ACTION_AUTO_STOP = "com.pulsepointlabs.elizabethlive.overlay.AUTO_STOP"
        private const val CHANNEL_ID = "elizabeth_trip_overlay"
        private const val NOTIFICATION_ID = 2107

        fun canStart(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context): Boolean {
            if (!canStart(context)) return false
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingTripOverlayService::class.java),
            )
            return true
        }

        fun startAutomatic(context: Context): Boolean {
            if (!canStart(context)) return false
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingTripOverlayService::class.java).setAction(ACTION_AUTO_START),
            )
            return true
        }

        fun stopAutomatic(context: Context) {
            context.startService(
                Intent(context, FloatingTripOverlayService::class.java).setAction(ACTION_AUTO_STOP),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTripOverlayService::class.java))
        }
    }
}
