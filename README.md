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

Version 0.3.1 makes landscape a true driving surface even before connection: it always opens
the dashboard, provides Connect/Disconnect directly in the compact header, hides Android system
bars while active, removes portrait navigation and inset padding, and restores the normal app
and system bars through the visible Exit dashboard control.

## Landscape driving dashboard

Rotating into landscape opens a dedicated, glanceable dashboard with oversized speed, RPM,
calculated boost/vacuum, throttle, temperatures, voltage, fuel rate, trip time, and estimated
trip cost. `Exit dashboard` is always visible, and the normal app shows an equally visible
`Open driving dashboard` control after exit.

Fuel costs use an editable local regular-gas price. Live cost accounting uses standard Mode 01
PID `015E` when supported; unsupported values are not replaced with fake measured data. Automatic nearby pricing remains
behind a provider interface until a reliable user-configured price source is available.

Version 0.4.0 removes Demo Mode and all generated telemetry, example trip history, and sample
health results. Landscape now uses a conventional 270-degree tachometer with ticks, a needle,
and a large RPM readout, plus a cleaner digital speed instrument and segmented load display.
Rounded status instruments and continuous green-to-amber-to-red transitions remain.

Version 0.4.1 gives the landscape dashboard the full display by overlaying its control header.
The header introduces itself briefly, auto-hides, and returns with a downward pull from the top
edge; the small top-edge grabber remains visible. The calculated boost/vacuum label now uses two
clear lines instead of truncating.

## Build

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Run `gradlew.bat testDebugUnitTest assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk`.

Bluetooth Classic ELM327 transport and live standard-PID polling are implemented. Room-backed
trip persistence and full diagnostics remain staged for later implementation.
