# Elizabeth Live

Elizabeth Live is a private, strictly read-only Android OBD-II monitor for Elizabeth, a
2021 Honda Accord EX-L 1.5T CVT. It is designed for a Samsung Galaxy S25 Ultra and a paired
Vgate vLinker MC+ using Bluetooth Classic and ELM327.

Current release: **v1.1.0-sensor-pages**

## Swipeable landscape sensor pages

Landscape mode now keeps the existing Drive dashboard intact and adds horizontally swipeable
pages for Economy, Air/Fuel, CVT/Control, Chassis, and Electrical data. A small bottom rail shows
the current page without consuming dashboard space.

The standard OBD reader now probes additional SAE Mode 01 values for fuel-rail pressure, measured
lambda, accelerator-pedal position, commanded throttle, ambient and oil temperature, and engine
torque. The second temperature exposed by Elizabeth's multi-sensor `0168` response is retained as
charge-air temperature instead of being discarded.

Honda-only data such as CVT fluid temperature, individual wheel speeds, steering/yaw, battery
current/state of charge, brake pressure, and TPMS pressure is represented explicitly but remains
unavailable until a command is verified for this exact platform. Elizabeth Live does not send
guessed proprietary commands or display invented values. Unsupported measurements stay visibly
marked as unavailable.

There are no accounts, cloud services, analytics, ads, subscriptions, actuator commands,
DTC clearing, coding functions, ECU writes, or invented Honda commands.

## Landscape fuel-economy dashboard

Version 1.0.2 replaces the plain landscape fuel-economy readout with a dedicated average-economy
dial. The value sits below the needle for clean glanceability, while the arc, ticks, and status
color show relative economy without covering the measurement.

The panel also includes a large real-time efficiency value with a segmented visual bar, plus
compact trip-distance and fuel-used cards. Values adapt to US customary and metric units. Fuel
provenance remains explicit as ECU-reported, MAF-estimated, or unavailable; missing fuel data is
never displayed as zero.

## Sensor framing correction

Version 1.0.1 reassembles ELM327 CAN-formatted multiline replies before standard PID decoding.
Elizabeth's `0168` intake response uses the ELM length-and-sequence form:

```text
009
0:416803494800
1:00000055555555
```

The parser now validates sequence continuity, joins the segments, honors the hexadecimal payload
length, and discards trailing CAN padding. This yields the complete standard PID `0168` payload
and reports the first supported intake sensor correctly. In the captured response, byte `03`
marks sensors 1 and 2 as supported and byte `49` reports 33 °C / 91 °F for sensor 1.

The change also handles compact one-line diagnostic renderings and safely rejects incomplete or
missing-sequence responses. Existing `0167` coolant and `0166` MAF decoding is unchanged. Direct
engine fuel-rate PID `015E` remains unavailable when the ECU returns `NO DATA`; Elizabeth continues
to label and use its MAF-based fuel estimate instead of presenting invented ECU data.

## Drive automation

With **Automatic connection** enabled, opening Elizabeth Live connects directly to the remembered
adapter if it is still paired. The device picker appears only when there is no remembered adapter,
the remembered adapter is no longer paired, or **Change adapter** is tapped. Android Companion
Device Manager associates the selected vLinker for permitted background presence behavior.

Connection status distinguishes the actual layer involved:

- Waiting for adapter
- Connecting to adapter
- Adapter connected, waiting for ignition
- Connecting to ECU
- Connected
- Connected and recording
- Reconnecting
- Connection unavailable

Retries use a single connection job and ELM327 queue with exponential delays of 1, 2, 4, 8, 15,
30, and then 60 seconds. Elizabeth never launches the phone dashboard over navigation or another
foreground app. A quiet connected-device foreground-service notification provides an explicit
**Open dashboard** action instead.

### Automatic trip start

Bluetooth connection alone does not start a trip. When **Automatic trips** is enabled, one trip is
created only after three valid RPM readings above zero arrive within five seconds. The active Room
record is created before recording begins. Manual and automatic starts share the same lifecycle
lock, so they cannot create duplicate trips.

### Automatic trip end

Loss of one PID, a traffic-light stop, or a short Bluetooth interruption does not split the drive.
When valid ECU data disappears, the active trip is held open while Elizabeth reconnects. The Live
status and notification show the remaining grace time. The selectable delay is 1, 2, 3, or 5
minutes; the default is 3 minutes.

If RPM returns during the grace period, recording resumes under the same trip ID and a connection
gap marker is retained. If it does not return, Elizabeth flushes and finalizes the trip at the last
valid sample. The saved-trip notification includes distance and includes average MPG and estimated
cost only when the necessary fuel data exists.

### Persistence and recovery

Active-trip metadata is written to Room immediately. Telemetry is buffered in small batches,
flushed after 10 samples and at least every five seconds, and flushed before finalization or service
shutdown. The active row retains:

- Start and latest-sample timestamps
- Accumulated distance and fuel
- ECU-reported, MAF-estimated, or unavailable fuel provenance
- Samples, events, and reconnect gaps
- Reconnect count and grace-period state
- Automatic and recovered-trip flags

After process death, service recreation, an app crash, or phone restart, Elizabeth loads the
unfinished Room row before accepting RPM start signals. It reconnects using the same trip ID. If
the recovery window has already expired, the trip is finalized using its last valid sample rather
than discarded. Database migration 1→2 preserves all trips saved by v0.9.0.

### Automatic overlay

**Show overlay during automatic trips** uses the existing Android overlay permission. When enabled,
the Sarah Vital Signs-style economy/cost pill appears only for an active automatic trip and hides
when that trip finishes. Missing permission never interrupts recording and is not requested
repeatedly. Manual overlay controls continue to work during an active trip.

## Existing surfaces

- Portrait Live, Trip, Health, trip history, and saved-trip detail
- Immersive glanceable landscape dashboard with visible exit
- Android Auto read-only instruments and trip controls
- Rolling Compose Canvas charts
- VIN, DTC, emissions-readiness, and supported-PID diagnostics
- CSV export
- Fuel-rate provenance: Mode 01 PID `015E`, MAF estimate, or unavailable
- New Health **Drive Automation** diagnostics for service, association, active trip, batch flush,
  reconnect, grace, and recovery state

Unsupported values remain absent. They are never converted to zero or displayed as measured data.
MPG and cost are not calculated when their required inputs are missing.

## Build and test

Requirements: Android SDK 36 and JDK 17.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\benja\AppData\Local\Android\Sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease
```

The automated suite covers ELM cleanup/parsing, standard PID formulas, fake transport behavior,
automatic start and grace decisions, duplicate-trip prevention, incremental Room writes,
process-recreation recovery, migration from database v1, unavailable fuel handling, automatic
overlay policy, remembered-adapter selection, and notification actions.

Install the debug APK from:

`app/build/outputs/apk/debug/app-debug.apk`

Real-vehicle verification still requires the paired vLinker MC+, Elizabeth with ignition/engine
state changes, Android background restrictions, overlay permission, and the Android Auto host.
