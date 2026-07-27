package com.pulsepointlabs.elizabethlive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class ElizabethViewModel(application: Application) : AndroidViewModel(application) {
    private val session = (application as ElizabethApplication).obdSession
    val state: StateFlow<ElizabethUiState> = session.state

    fun prepareConnection() = session.prepareConnection()
    fun dismissDevicePicker() = session.dismissDevicePicker()
    fun selectDevice(device: PairedObdDevice) = session.selectDevice(device)
    fun onBluetoothPermissionDenied() = session.onBluetoothPermissionDenied()
    fun disconnect() = session.disconnect()
    fun setWindow(window: TimeWindow) = session.setWindow(window)
    fun togglePaused() = session.togglePaused()
    fun inspect(sample: TelemetrySample?) = session.inspect(sample)
    fun toggleChannel(channel: String) = session.toggleChannel(channel)
    fun toggleTrip() = session.toggleTrip()
    fun deleteTrip() = session.deleteTrip()
    fun setTheme(theme: ThemeSetting) = session.setTheme(theme)
    fun setUnits(units: UnitSystem) = session.setUnits(units)
    fun toggleSmoothing() = session.toggleSmoothing()
    fun setDefaultWindow(window: TimeWindow) = session.setDefaultWindow(window)
    fun setRecordingInterval(intervalMillis: Long) = session.setRecordingInterval(intervalMillis)
    fun setFuelPricePerGallon(price: Double) = session.setFuelPricePerGallon(price)
    fun toggleAutoStart() = session.toggleAutoStart()
    fun setOverlayEnabled(enabled: Boolean) = session.setOverlayEnabled(enabled)
    fun refreshDiagnostics() = session.refreshDiagnostics()
}
