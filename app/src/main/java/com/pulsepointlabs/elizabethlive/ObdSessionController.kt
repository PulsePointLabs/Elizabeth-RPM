package com.pulsepointlabs.elizabethlive

import kotlinx.coroutines.flow.StateFlow

interface ObdSessionController {
    val state: StateFlow<ElizabethUiState>
    fun connectSavedDevice(): Boolean
    fun disconnect()
    fun toggleTrip()
}
