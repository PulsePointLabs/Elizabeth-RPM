# Elizabeth Live

A private, read-only Android OBD-II monitor for Elizabeth, a 2021 Honda Accord EX-L 1.5T CVT.

Stage 1 is a complete simulated-data UI built with Kotlin, Jetpack Compose, Material 3,
coroutines, StateFlow, and Compose Canvas charts. No account, cloud service, advertising,
analytics, or vehicle write commands are present.

## Build

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Run `gradlew.bat testDebugUnitTest assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk`.

The app is currently always explicit about simulated data. Bluetooth Classic ELM327
transport, live PID polling, Room-backed trip recording, and diagnostics are staged behind
interfaces for the next implementation passes.

