# Elizabeth Live

A private, read-only Android OBD-II monitor for Elizabeth, a 2021 Honda Accord EX-L 1.5T CVT.

Elizabeth Live is built with Kotlin, Jetpack Compose, Material 3, coroutines, StateFlow,
Room-ready storage seams, and Compose Canvas charts. No account, cloud service, advertising,
analytics, or vehicle write commands are present.

## Live OBD-II

Version 0.3.0 adds the first real acquisition path for a paired Vgate vLinker MC+:

- Android Bluetooth runtime permission and paired-device selector
- Bluetooth Classic RFCOMM using the standard Serial Port Profile UUID
- Conservative ELM327 reset and initialization sequence
- Automatic vehicle protocol selection
- Standard Mode 01 supported-PID bitmap discovery
- Prioritized polling for RPM, throttle, MAP, speed, temperatures, load, timing, trims,
  voltage, barometric pressure, and fuel rate
- Calculated boost/vacuum only when both MAP and barometric pressure are reported
- Nullable unsupported values; `NO DATA` is never converted into a fake zero
- Automatic reconnect attempts after a temporary transport loss

Demo Mode is off by default and must be started explicitly from the Live screen.

## Landscape driving dashboard

Rotating into landscape opens a dedicated, glanceable dashboard with oversized speed, RPM,
calculated boost/vacuum, throttle, temperatures, voltage, fuel rate, trip time, and estimated
trip cost. `Exit dashboard` is always visible, and the normal app shows an equally visible
`Open driving dashboard` control after exit.

Fuel costs use an editable local regular-gas price. Demo fuel history and fuel rate are always
labeled simulated. Live cost accounting will use standard Mode 01 PID `015E` when supported;
unsupported values are not replaced with fake measured data. Automatic nearby pricing remains
behind a provider interface until a reliable user-configured price source is available.

The v0.2.1 landscape refinement adds non-wrapping metric typography, a speed arc, animated
fill bars on every dashboard metric, rounded status instruments, and continuous green-to-amber-
to-red color transitions based on each parameter's load or status.

## Build

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Run `gradlew.bat testDebugUnitTest assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk`.

The app is currently always explicit about simulated data. Bluetooth Classic ELM327
transport, live PID polling, Room-backed trip recording, and diagnostics are staged behind
interfaces for the next implementation passes.
