package com.pulsepointlabs.elizabethlive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsepointlabs.elizabethlive.ConnectionState
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.UnitSystem
import com.pulsepointlabs.elizabethlive.ui.theme.BoostTeal
import com.pulsepointlabs.elizabethlive.ui.theme.GoodGreen
import com.pulsepointlabs.elizabethlive.ui.theme.RpmBlue
import com.pulsepointlabs.elizabethlive.ui.theme.ThrottleAmber
import com.pulsepointlabs.elizabethlive.ui.theme.WarningRed
import java.util.Locale
import kotlin.math.abs

internal enum class DashboardPage(val label: String) {
    DRIVE("DRIVE"),
    ECONOMY("ECONOMY"),
    AIR_FUEL("AIR + FUEL"),
    ENGINE_CONTROL("ENGINE + CONTROL"),
    ELECTRICAL("ELECTRICAL"),
}

private enum class SensorSource(val badge: String) {
    STANDARD("SAE OBD"),
    CALCULATED("CALCULATED"),
}

private data class SensorTile(
    val label: String,
    val value: String?,
    val unit: String = "",
    val source: SensorSource,
    val pidLabel: String? = null,
    val accent: Color,
    val note: String? = null,
)

@Composable
internal fun DashboardPageRail(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
        shadowElevation = 5.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            DashboardPage.entries.forEachIndexed { index, page ->
                val selected = index == currentPage
                Surface(
                    modifier = Modifier
                        .height(30.dp)
                        .clickable { onPageSelected(index) },
                    shape = CircleShape,
                    color = if (selected) BoostTeal.copy(alpha = .17f) else Color.Transparent,
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier
                                .size(if (selected) 7.dp else 5.dp)
                                .background(
                                    if (selected) BoostTeal
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f),
                                    CircleShape,
                                )
                        )
                        Text(
                            page.label,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EconomyDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
    tripCost: Double?,
    durationSeconds: Long,
) {
    val connected = state.connectionState == ConnectionState.CONNECTED
    val averageSpeedKph = if (durationSeconds > 0 && state.liveDistanceKm > 0.01) {
        state.liveDistanceKm / (durationSeconds / 3_600.0)
    } else null
    Row(
        Modifier.fillMaxSize().padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FuelEconomyPanel(
            sample = sample,
            units = units,
            distanceKm = state.liveDistanceKm,
            fuelUsedLiters = state.liveFuelUsedLiters,
            modifier = Modifier.weight(.48f).fillMaxHeight(),
        )
        SensorGrid(
            rows = tilesToRows(
                tripCost?.let {
                    SensorTile("TRIP COST", it.money(), "$", SensorSource.CALCULATED, accent = GoodGreen)
                },
                averageSpeedKph?.let {
                    SensorTile("AVERAGE SPEED", it.speedValue(units), speedUnit(units), SensorSource.CALCULATED, accent = RpmBlue)
                },
                SensorTile("DURATION", formatClock(durationSeconds), "", SensorSource.CALCULATED, accent = BoostTeal),
                sample?.fuelRateLitersPerHour?.let {
                    SensorTile(
                        "FUEL RATE",
                        it.oneDecimal(),
                        "L/h",
                        if (sample.fuelRateEstimated) SensorSource.CALCULATED else SensorSource.STANDARD,
                        pidLabel = if (sample.fuelRateEstimated) "FROM MAF" else "01 5E",
                        accent = ThrottleAmber,
                    )
                },
                sample?.massAirFlowGramsPerSecond?.let {
                    SensorTile("MASS AIR FLOW", it.oneDecimal(), "g/s", SensorSource.STANDARD, "01 66", BoostTeal)
                },
                sample?.commandedEquivalenceRatio?.let {
                    SensorTile("COMMAND λ", it.threeDecimals(), "λ", SensorSource.STANDARD, "01 44", GoodGreen)
                },
                sample?.actualEquivalenceRatio?.let {
                    SensorTile("ACTUAL λ", it.threeDecimals(), "λ", SensorSource.STANDARD, "01 24 / 34", GoodGreen)
                },
                sample?.shortFuelTrim?.let {
                    SensorTile("SHORT TRIM", it.signedOneDecimal(), "%", SensorSource.STANDARD, "01 06", fuelTrimColor(it))
                },
                sample?.longFuelTrim?.let {
                    SensorTile("LONG TRIM", it.signedOneDecimal(), "%", SensorSource.STANDARD, "01 07", fuelTrimColor(it))
                },
            ),
            connected = connected,
            modifier = Modifier.weight(.52f).fillMaxHeight(),
        )
    }
}

@Composable
internal fun AirFuelDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
) {
    SensorPage(
        title = "AIR + FUEL",
        subtitle = "Turbo breathing, charge temperature, direct injection, lambda, and correction",
        rows = tilesToRows(
            sample?.boostPsi?.let {
                SensorTile(
                    "BOOST / VACUUM",
                    if (units == UnitSystem.US) it.oneDecimal() else (it * 6.89476).oneDecimal(),
                    if (units == UnitSystem.US) "psi" else "kPa",
                    SensorSource.CALCULATED,
                    "MAP − BARO",
                    BoostTeal,
                )
            },
            sample?.manifoldPressureKpa?.let {
                SensorTile("MANIFOLD", it.oneDecimal(), "kPa", SensorSource.STANDARD, "01 0B", BoostTeal)
            },
            sample?.barometricPressureKpa?.let {
                SensorTile("BAROMETRIC", it.oneDecimal(), "kPa", SensorSource.STANDARD, "01 33", RpmBlue)
            },
            sample?.engineLoad?.let {
                SensorTile("ENGINE LOAD", it.oneDecimal(), "%", SensorSource.STANDARD, "01 04", ThrottleAmber)
            },
            sample?.intakeC?.let {
                SensorTile("INTAKE AIR", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT1", RpmBlue)
            },
            sample?.chargeAirC?.let {
                SensorTile("CHARGE AIR", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT2", BoostTeal)
            },
            sample?.massAirFlowGramsPerSecond?.let {
                SensorTile("MASS AIR FLOW", it.oneDecimal(), "g/s", SensorSource.STANDARD, "01 66", BoostTeal)
            },
            sample?.fuelRailPressureKpa?.let {
                SensorTile("FUEL RAIL", it.pressureValue(units), pressureUnit(units), SensorSource.STANDARD, "01 23 / 59", WarningRed)
            },
            sample?.commandedEquivalenceRatio?.let {
                SensorTile("COMMAND λ", it.threeDecimals(), "λ", SensorSource.STANDARD, "01 44", GoodGreen)
            },
            sample?.actualEquivalenceRatio?.let {
                SensorTile("ACTUAL λ", it.threeDecimals(), "λ", SensorSource.STANDARD, "01 24 / 34", GoodGreen)
            },
            sample?.shortFuelTrim?.let {
                SensorTile("SHORT TRIM", it.signedOneDecimal(), "%", SensorSource.STANDARD, "01 06", fuelTrimColor(it))
            },
            sample?.longFuelTrim?.let {
                SensorTile("LONG TRIM", it.signedOneDecimal(), "%", SensorSource.STANDARD, "01 07", fuelTrimColor(it))
            },
        ),
        connected = state.connectionState == ConnectionState.CONNECTED,
    )
}

@Composable
internal fun EngineControlDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
) {
    SensorPage(
        title = "ENGINE + CONTROL",
        subtitle = "Driver request, throttle management, ignition, load, and reported torque",
        rows = tilesToRows(
            sample?.rpm?.let {
                SensorTile("ENGINE RPM", "%.0f".format(Locale.US, it), "rpm", SensorSource.STANDARD, "01 0C", RpmBlue)
            },
            sample?.engineLoad?.let {
                SensorTile("ENGINE LOAD", it.oneDecimal(), "%", SensorSource.STANDARD, "01 04", ThrottleAmber)
            },
            sample?.absoluteEngineLoad?.let {
                SensorTile("ABSOLUTE LOAD", it.oneDecimal(), "%", SensorSource.STANDARD, "01 43", ThrottleAmber)
            },
            sample?.acceleratorPedalPercent?.let {
                SensorTile("PEDAL REQUEST", it.oneDecimal(), "%", SensorSource.STANDARD, "01 49 / 5A", ThrottleAmber)
            },
            sample?.throttlePercent?.let {
                SensorTile("THROTTLE ACTUAL", it.oneDecimal(), "%", SensorSource.STANDARD, "01 11", ThrottleAmber)
            },
            sample?.commandedThrottlePercent?.let {
                SensorTile("THROTTLE COMMAND", it.oneDecimal(), "%", SensorSource.STANDARD, "01 4C", ThrottleAmber)
            },
            sample?.timingAdvance?.let {
                SensorTile("IGNITION TIMING", it.signedOneDecimal(), "°", SensorSource.STANDARD, "01 0E", RpmBlue)
            },
            sample?.driverDemandTorquePercent?.let {
                SensorTile("TORQUE REQUEST", it.signedOneDecimal(), "%", SensorSource.STANDARD, "01 61", BoostTeal)
            },
            sample?.actualTorquePercent?.let {
                SensorTile("TORQUE ACTUAL", it.signedOneDecimal(), "%", SensorSource.STANDARD, "01 62", BoostTeal)
            },
            sample?.referenceTorqueNm?.let {
                SensorTile("REFERENCE TORQUE", it.oneDecimal(), "N·m", SensorSource.STANDARD, "01 63", RpmBlue)
            },
            sample?.oilC?.let {
                SensorTile("OIL TEMP", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 5C", WarningRed)
            },
        ),
        connected = state.connectionState == ConnectionState.CONNECTED,
    )
}

@Composable
internal fun ElectricalDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
) {
    val voltageAccent = when {
        sample?.voltage == null -> RpmBlue
        sample.voltage < 12.0 || sample.voltage > 15.2 -> WarningRed
        else -> GoodGreen
    }
    SensorPage(
        title = "ELECTRICAL + THERMAL",
        subtitle = "Live voltage, pressure, and temperatures reported by Elizabeth",
        rows = tilesToRows(
            sample?.voltage?.let {
                SensorTile("MODULE VOLTAGE", it.twoDecimals(), "V", SensorSource.STANDARD, "01 42", voltageAccent)
            },
            sample?.ambientC?.let {
                SensorTile("AMBIENT AIR", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 46", RpmBlue)
            },
            sample?.intakeC?.let {
                SensorTile("INTAKE AIR", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT1", RpmBlue)
            },
            sample?.chargeAirC?.let {
                SensorTile("CHARGE AIR", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT2", BoostTeal)
            },
            sample?.coolantC?.let {
                SensorTile("COOLANT", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 67", WarningRed)
            },
            sample?.oilC?.let {
                SensorTile("ENGINE OIL", it.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 5C", WarningRed)
            },
            sample?.barometricPressureKpa?.let {
                SensorTile("BAROMETRIC", it.oneDecimal(), "kPa", SensorSource.STANDARD, "01 33", RpmBlue)
            },
            temperatureDelta(sample?.chargeAirC, sample?.ambientC, units)?.let {
                SensorTile(
                    "IAT DELTA",
                    it,
                    if (units == UnitSystem.US) "°F" else "°C",
                    SensorSource.CALCULATED,
                    "CHARGE − AMBIENT",
                    BoostTeal,
                )
            },
        ),
        connected = state.connectionState == ConnectionState.CONNECTED,
    )
}

@Composable
private fun SensorPage(
    title: String,
    subtitle: String,
    rows: List<List<SensorTile>>,
    connected: Boolean,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(9.dp))
            Text(
                subtitle,
                modifier = Modifier.weight(1f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SensorGrid(rows, connected, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun SensorGrid(
    rows: List<List<SensorTile>>,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(17.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (connected) "NO LIVE VALUES REPORTED ON THIS PAGE"
                    else "CONNECT TO ELIZABETH TO LOAD AVAILABLE VALUES",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { tile ->
                    SensorMetricTile(tile, connected, Modifier.weight(1f).fillMaxHeight())
                }
                repeat((4 - row.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SensorMetricTile(
    tile: SensorTile,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val availability = when {
        tile.value != null -> tile.note ?: "LIVE"
        !connected -> "WAITING FOR CONNECTION"
        else -> "NOT REPORTED BY ECU"
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(17.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(tile.accent.copy(alpha = .035f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tile.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tile.pidLabel ?: tile.source.badge,
                    modifier = Modifier
                        .background(tile.accent.copy(alpha = .11f), CircleShape)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = tile.accent,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    tile.value ?: "—",
                    fontSize = 25.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = if (tile.value == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
                    } else tile.accent,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (tile.unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tile.unit,
                        modifier = Modifier.padding(bottom = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                availability,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (tile.value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
                } else tile.accent.copy(alpha = .88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun tilesToRows(vararg tiles: SensorTile?): List<List<SensorTile>> =
    tiles.filterNotNull().chunked(4)

private fun Double.oneDecimal() = String.format(Locale.US, "%.1f", this)
private fun Double.twoDecimals() = String.format(Locale.US, "%.2f", this)
private fun Double.threeDecimals() = String.format(Locale.US, "%.3f", this)
private fun Double.signedOneDecimal() = String.format(Locale.US, "%+.1f", this)
private fun Double.money() = String.format(Locale.US, "%.2f", this)
private fun Double.temperatureValue(units: UnitSystem) =
    if (units == UnitSystem.US) (this * 9.0 / 5.0 + 32.0).oneDecimal() else oneDecimal()
private fun temperatureUnit(units: UnitSystem) = if (units == UnitSystem.US) "°F" else "°C"
private fun Double.speedValue(units: UnitSystem) =
    if (units == UnitSystem.US) (this * 0.621371192).oneDecimal() else oneDecimal()
private fun speedUnit(units: UnitSystem) = if (units == UnitSystem.US) "mph" else "km/h"
private fun Double.pressureValue(units: UnitSystem) =
    if (units == UnitSystem.US) (this * 0.145037738).oneDecimal() else oneDecimal()
private fun pressureUnit(units: UnitSystem) = if (units == UnitSystem.US) "psi" else "kPa"

private fun temperatureDelta(chargeC: Double?, ambientC: Double?, units: UnitSystem): String? {
    if (chargeC == null || ambientC == null) return null
    val deltaC = chargeC - ambientC
    return if (units == UnitSystem.US) (deltaC * 9.0 / 5.0).signedOneDecimal()
    else deltaC.signedOneDecimal()
}

private fun fuelTrimColor(value: Double?): Color = when {
    value == null -> RpmBlue
    abs(value) >= 20.0 -> WarningRed
    abs(value) >= 10.0 -> ThrottleAmber
    else -> GoodGreen
}

private fun formatClock(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
    else "%02d:%02d".format(minutes, remaining)
}
