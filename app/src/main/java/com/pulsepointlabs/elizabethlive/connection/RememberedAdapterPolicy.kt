package com.pulsepointlabs.elizabethlive.connection

import com.pulsepointlabs.elizabethlive.PairedObdDevice

sealed interface RememberedAdapterDecision {
    data class Connect(val device: PairedObdDevice) : RememberedAdapterDecision
    data object ShowPicker : RememberedAdapterDecision
}

object RememberedAdapterPolicy {
    fun resolve(
        remembered: PairedObdDevice?,
        pairedAddresses: Collection<String>,
    ): RememberedAdapterDecision {
        if (
            remembered != null &&
            pairedAddresses.any { it.equals(remembered.address, ignoreCase = true) }
        ) {
            return RememberedAdapterDecision.Connect(remembered)
        }
        return RememberedAdapterDecision.ShowPicker
    }
}
