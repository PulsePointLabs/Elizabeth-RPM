# Elizabeth Live v1.2.0 — Parameter Help

This release makes the landscape dashboards easier to understand and easier to read on the
Galaxy S25 Ultra.

## What changed

- Added a visible information button to every landscape sensor card.
- Added plain-English explanations for every displayed parameter, including what it measures,
  how to interpret it, the current value, and its OBD-II PID or calculated-data source.
- Added the same help controls to average fuel economy, real-time fuel economy, trip distance,
  and fuel used.
- Enlarged secondary labels, units, live-status text, page descriptions, and navigation labels.
- Changed the Economy metric grid from four cramped columns to three wider columns.
- Allowed parameter names to use two lines and moved PID badges below the value so names no
  longer collapse into ambiguous abbreviations.
- Kept help dialogs scrollable in landscape mode.

## Data integrity

Elizabeth Live remains strictly read-only. The release does not add proprietary Honda commands,
fake sensor values, actuator controls, ECU writes, DTC clearing, analytics, accounts, or cloud
services. Reported, calculated, estimated, and unavailable data remain clearly distinguished.

## Validation

- 70 unit tests passed
- Android lint passed
- Debug APK built successfully
- Release AAB built successfully
- APK package/version metadata and signing certificate verified

Real-device visual confirmation on the Galaxy S25 Ultra is still recommended because no physical
S25 Ultra was attached during the build.
