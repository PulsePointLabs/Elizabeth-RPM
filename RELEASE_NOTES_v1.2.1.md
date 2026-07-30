# Elizabeth Live v1.2.1 — Compact Fuel Panel

This hotfix repairs the narrow fuel-economy panel on the main landscape Drive dashboard.

## Fixed

- Added a dedicated compact layout for the narrow Drive-page fuel panel.
- Kept **Fuel Economy** on one line and moved its fuel-source badge to a separate readable line.
- Removed the extra nested help buttons from the compact real-time and trip-fact rows so their
  labels and values have enough width.
- Shortened **Trip Distance** to **Distance** only in the compact panel.
- Preserved the complete labels and individual information buttons on the wider Economy page.
- Preserved the top-level fuel-economy information button on the compact Drive panel.

## Validation

- 70 unit tests passed
- Android lint passed
- Debug APK and release AAB built successfully
- APK metadata and signing certificate verified

No OBD communication, trip storage, automation, diagnostics, Android Auto, or overlay behavior was
changed.
