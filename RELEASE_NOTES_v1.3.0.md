# Elizabeth Live v1.3.0 — Trip Insights

This release makes saved trips substantially more useful to review.

## Interactive timeline

- Added visible RPM, boost/vacuum, and throttle legend values.
- Added tappable channel controls; at least one channel always remains visible.
- Tap or drag across the chart to inspect the values and trip-relative time at that moment.
- Added visible event markers to the trip timeline.
- Inspection now uses timestamps, improving selection across uneven sampling intervals.

## Fuel economy and history

- Added average MPG or L/100 km using the selected unit system.
- Added fuel used, sampled average fuel rate, estimated cost, and fuel-data coverage.
- Shows ECU-reported, MAF-estimated, unavailable, or legacy unrecorded fuel provenance honestly.
- Existing saved trips remain readable; no Room migration is required.
- History rows now show fuel-source/cost context and respect metric units.
- Added start/end times, sample count, event count, moving-sample percentage, reconnect count, and
  process-recovery status to trip details.

## Back navigation

- Android Back returns from saved-trip detail to the trip list.
- Back then follows the prior bottom-navigation destination instead of immediately closing the app.
- Tapping the selected Trip tab from a detail screen also returns to all trips.

## Validation

- Unit tests, including new fuel-history and persistence checks
- Android lint
- Debug APK
- Release AAB
- APK version and signing certificate verification

Fuel cost uses the app's currently configured per-gallon price; it is labeled estimated. No OBD
commands, trip recording behavior, Android Auto behavior, overlays, or database schema were changed.
