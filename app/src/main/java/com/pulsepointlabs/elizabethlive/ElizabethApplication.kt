package com.pulsepointlabs.elizabethlive

import android.app.Application

class ElizabethApplication : Application() {
    val obdSession: ElizabethObdSession by lazy { ElizabethObdSession(this) }
}
