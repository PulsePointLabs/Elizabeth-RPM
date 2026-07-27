package com.pulsepointlabs.elizabethlive.connection

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.pulsepointlabs.elizabethlive.PairedObdDevice

class CompanionDeviceCoordinator(private val context: Context) {
    private val manager: CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else {
            null
        }

    val supported: Boolean get() = manager != null

    @SuppressLint("MissingPermission")
    fun isAssociated(address: String?): Boolean {
        if (address == null) return false
        val companionManager = manager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionManager.myAssociations.any {
                it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
            }
        } else {
            @Suppress("DEPRECATION")
            companionManager.associations.any { it.equals(address, ignoreCase = true) }
        }
    }

    @SuppressLint("MissingPermission")
    fun ensurePresenceObservation(address: String?): Boolean {
        if (address == null) return false
        val companionManager = manager ?: return false
        val association = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionManager.myAssociations.firstOrNull {
                it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
            }
        } else {
            null
        }
        return try {
            if (Build.VERSION.SDK_INT >= 36 && association != null) {
                companionManager.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder()
                        .setAssociationId(association.id)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                companionManager.startObservingDevicePresence(address)
            }
            true
        } catch (_: IllegalStateException) {
            // Observation was already active, which is the desired state.
            true
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun associate(
        device: PairedObdDevice,
        onConsentRequired: (IntentSender) -> Unit,
        onAssociated: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val companionManager = manager ?: run {
            onFailure("Companion-device setup is not supported on this phone.")
            return
        }
        if (isAssociated(device.address)) {
            ensurePresenceObservation(device.address)
            onAssociated()
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(
                BluetoothDeviceFilter.Builder()
                    .setAddress(device.address)
                    .build()
            )
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    onConsentRequired(intentSender)
                }

                @Suppress("DEPRECATION")
                override fun onDeviceFound(intentSender: IntentSender) {
                    onConsentRequired(intentSender)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    ensurePresenceObservation(device.address)
                    onAssociated()
                }

                override fun onFailure(errorMessage: CharSequence?) {
                    onFailure(errorMessage?.toString() ?: "Companion association failed.")
                }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionManager.associate(request, context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            companionManager.associate(request, callback, Handler(Looper.getMainLooper()))
        }
    }
}
