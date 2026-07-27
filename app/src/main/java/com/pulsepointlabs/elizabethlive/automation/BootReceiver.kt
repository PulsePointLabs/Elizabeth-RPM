package com.pulsepointlabs.elizabethlive.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulsepointlabs.elizabethlive.data.ElizabethDatabase
import com.pulsepointlabs.elizabethlive.data.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val preferences = context.getSharedPreferences("elizabeth_settings", 0)
                val hasActiveTrip = TripRepository(ElizabethDatabase.get(context)).loadActive() != null
                if (hasActiveTrip || preferences.getBoolean("automatic_connection", true)) {
                    // Companion association normally permits this background FGS start. If an OEM
                    // temporarily rejects it during boot, the active Room row remains intact and
                    // MainActivity/service recreation will recover it on the next allowed launch.
                    runCatching { DriveAutomationService.start(context) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
