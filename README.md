# Elizabeth Live

A private, read-only Android OBD-II monitor for Elizabeth, a 2021 Honda Accord EX-L 1.5T CVT.

Elizabeth Live is built with Kotlin, Jetpack Compose, Material 3, coroutines, StateFlow,
Room-ready storage seams, and Compose Canvas charts. No account, cloud service, advertising,
analytics, or vehicle write commands are present.

Version 0.8.0 adds a compact Sarah Vital Signs-style floating trip overlay. A visible control on
the Live screen handles Android's one-time display-over-other-apps permission and then shows or
hides the pill without hidden gestures. The overlay displays average MPG as the large primary
value, with live MPG and trip cost beside it. It can be dragged, tapped to reopen Elizabeth, or
closed with its visible `×`. A low-priority connected-device foreground service keeps both the
Bluetooth-backed session and overlay alive while another app such as navigation is onscreen.
Unavailable economy remains `—`, never a fake zero.

Version 0.7.2 corrects the live-data definitions for Elizabeth's newer Honda PCM. The vehicle
does not answer the older single-sensor PIDs `0105`, `010F`, and `0110`; it uses the SAE J1979
multi-sensor forms `0167` (coolant), `0168` (intake air), and `0166` (mass air flow). These
responses begin with a sensor-support byte, so their temperature and airflow data require
different byte positions and scaling. Elizabeth Live now polls and decodes those commands
directly, uses `0166` for its clearly labeled MAF-based fuel estimate, and shows their sanitized
raw replies in Health.

Version 0.7.1 replaces the single-ECU assumption with automatic per-PID routing on 29-bit CAN.
Each missing standard Mode 01 value is tried through PCM target `10`, the functional OBD address,
and the other normal physical ECU targets `11` through `1F`. Successful routes are cached so
steady live polling remains fast. The Health diagnostic response identifies a discovered fallback
route, while a genuinely unanswered PID lists every route that was tried. Route changes and reads
are serialized so live polling cannot corrupt VIN, DTC, or readiness commands. All traffic remains
strictly read-only.

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

Version 0.5.0 adds a functional Android Auto surface using the Android for Cars App Library. The
car host displays four stable, colored instruments for engine RPM, calculated boost/vacuum,
coolant temperature, and control-module voltage. Values refresh from the same application-scoped
Bluetooth/ELM327 session used by the phone, and the car screen can reconnect to the last adapter,
start or stop a trip, and disconnect. Android Auto controls the final typography and layout; its
template system does not permit Elizabeth Live to draw the phone dashboard's custom Canvas gauges.

Version 0.6.0 keeps the immersive landscape dashboard awake and replaces the speed panel with
average and real-time fuel economy. ECU-reported fuel rate is preferred; when it is unavailable,
the app can clearly label and use a standard MAF-based estimate. The tachometer now places its
label below the live value and adds visible average and maximum RPM markers. Supported-PID
discovery also merges replies from multiple ECUs instead of trusting only the first bitmap.

Version 0.6.1 treats the supported-PID bitmap as a hint instead of a gate. The live poller directly
probes only the curated dashboard registry at its fast, medium, and slow rates, then confirms every
PID that returns a real value. This recovers standard sensors that an adapter or ECU omitted from
its bitmap without continuously polling a giant PID list.

Version 0.6.2 adds visible read-only PID diagnostics to Health. For the missing temperature and
fuel inputs, it distinguishes a decoded value, `NO DATA`, a malformed payload, and a transport
error while showing only a short sanitized adapter reply. The Health screen also displays the
installed app version so vehicle reports can be tied to the exact build.

Version 0.7.0 uses physical engine-ECU addressing when automatic detection selects ISO 15765-4
CAN 29/500. It sends read-only requests to PCM address `18DA10F1` and accepts engine replies from
`18DAF110`, recovering standard values that the Accord returned as `NO DATA` to functional
broadcasts. VIN, stored/pending/permanent DTCs, emissions readiness, MIL state, and freeze-frame
availability now refresh automatically and on demand. Both CSV buttons create real files. Graph
smoothing, recording interval, auto-start recording, and persisted settings are fully wired.

## Build

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Run `gradlew.bat testDebugUnitTest assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk`.

Bluetooth Classic ELM327 transport and live standard-PID polling are implemented. Room-backed
trip persistence and full diagnostics remain staged for later implementation.
