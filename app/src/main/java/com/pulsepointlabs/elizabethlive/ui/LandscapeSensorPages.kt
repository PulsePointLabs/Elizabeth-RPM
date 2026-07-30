package com.pulsepointlabs.elizabethlive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
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
            rows = economyTilesToRows(
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
            columns = 3,
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
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(9.dp))
            Text(
                subtitle,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 13.sp,
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
    columns: Int = 4,
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
                repeat((columns - row.size).coerceAtLeast(0)) {
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
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    tile.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(5.dp))
                LandscapeParameterInfoButton(
                    label = tile.label,
                    value = tile.value,
                    unit = tile.unit,
                    source = tile.pidLabel?.let { "OBD-II $it" } ?: tile.source.badge,
                    accent = tile.accent,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    tile.value ?: "—",
                    fontSize = 27.sp,
                    lineHeight = 28.sp,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    availability,
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tile.value == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
                    } else tile.accent.copy(alpha = .88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tile.pidLabel ?: tile.source.badge,
                    modifier = Modifier
                        .background(tile.accent.copy(alpha = .11f), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = tile.accent,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class ParameterHelp(
    val meaning: String,
    val reading: String,
)

@Composable
internal fun LandscapeParameterInfoButton(
    label: String,
    value: String?,
    unit: String,
    source: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        ParameterInfoDialog(
            label = label,
            value = value,
            unit = unit,
            source = source,
            accent = accent,
            onDismiss = { showInfo = false },
        )
    }
    Surface(
        modifier = modifier
            .size(28.dp)
            .semantics { contentDescription = "Information about $label" }
            .clickable { showInfo = true },
        shape = CircleShape,
        color = accent.copy(alpha = .15f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "i",
                fontSize = 15.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
        }
    }
}

@Composable
private fun ParameterInfoDialog(
    label: String,
    value: String?,
    unit: String,
    source: String,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val help = parameterHelp(label)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                label,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value ?: "Not available",
                        fontSize = 27.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                        color = if (value == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            accent
                        },
                    )
                    if (value != null && unit.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            unit,
                            modifier = Modifier.padding(bottom = 2.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text(
                    source,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "WHAT IT MEANS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                Text(
                    help.meaning,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                )
                Text(
                    "HOW TO READ IT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                Text(
                    help.reading,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
    )
}

private fun parameterHelp(label: String): ParameterHelp = when (label) {
    "AVERAGE FUEL ECONOMY" -> ParameterHelp(
        "Average fuel efficiency accumulated over the current trip.",
        "It combines trip distance with fuel used. The source line tells you whether fuel use is ECU-reported, estimated from mass airflow, or unavailable.",
    )
    "REAL-TIME FUEL ECONOMY" -> ParameterHelp(
        "Fuel efficiency at the current speed and fuel-use rate.",
        "It changes quickly and becomes unavailable when the car is stopped or the required speed or fuel data is missing. Use average economy for the steadier trip picture.",
    )
    "TRIP DISTANCE" -> ParameterHelp(
        "Distance accumulated during the current recorded trip.",
        "Elizabeth integrates live vehicle speed over time. Short connection gaps do not intentionally split the trip.",
    )
    "FUEL USED" -> ParameterHelp(
        "Fuel accumulated during the current recorded trip.",
        "The source is either the ECU fuel-rate PID or Elizabeth's labeled mass-airflow estimate. Missing fuel data remains unavailable rather than becoming zero.",
    )
    "TRIP COST" -> ParameterHelp(
        "Estimated fuel cost for the current trip, based on accumulated fuel use and your configured fuel price.",
        "It is an estimate. Accuracy depends on the selected fuel price and whether fuel use comes directly from the ECU or is estimated from airflow.",
    )
    "AVERAGE SPEED" -> ParameterHelp(
        "Distance traveled divided by elapsed trip time.",
        "Stops and idling are included, so this is normally lower than your typical cruising speed.",
    )
    "DURATION" -> ParameterHelp(
        "Elapsed time since the current trip began.",
        "The timer continues while an automatic trip is being held open during a short connection gap.",
    )
    "FUEL RATE" -> ParameterHelp(
        "The current volume of fuel used per hour.",
        "It rises with engine load. The badge shows whether Elizabeth received it from the ECU or estimated it from mass airflow.",
    )
    "MASS AIR FLOW" -> ParameterHelp(
        "The mass of air entering the engine each second.",
        "It generally rises with RPM, throttle, engine load, and boost. Elizabeth can use it to estimate fuel use when direct fuel rate is unavailable.",
    )
    "COMMAND λ" -> ParameterHelp(
        "The air-fuel ratio requested by the ECU, expressed relative to stoichiometric.",
        "1.000 is stoichiometric. Below 1.000 is richer; above 1.000 is leaner.",
    )
    "ACTUAL λ" -> ParameterHelp(
        "The air-fuel ratio reported by the wideband air-fuel sensor, relative to stoichiometric.",
        "Compare it with commanded lambda. Some difference during rapid throttle changes is normal because the values do not update at exactly the same instant.",
    )
    "SHORT TRIM" -> ParameterHelp(
        "The ECU's immediate fuel correction for the current conditions.",
        "Positive adds fuel and negative removes fuel. Values moving around zero are expected; a persistent large correction deserves context from long-term trim and operating conditions.",
    )
    "LONG TRIM" -> ParameterHelp(
        "The ECU's learned fuel correction over time.",
        "Positive means the ECU has learned to add fuel; negative means it has learned to remove fuel. Interpret persistent changes alongside short-term trim.",
    )
    "BOOST / VACUUM" -> ParameterHelp(
        "Calculated manifold pressure relative to outside atmospheric pressure.",
        "Negative values are intake vacuum. Positive values are turbo boost. Elizabeth calculates this from manifold and barometric pressure.",
    )
    "MANIFOLD" -> ParameterHelp(
        "Absolute pressure inside the intake manifold.",
        "This includes atmospheric pressure, so it is not the same as boost. Elizabeth subtracts barometric pressure to calculate boost or vacuum.",
    )
    "BAROMETRIC" -> ParameterHelp(
        "The atmospheric pressure reported by the ECU.",
        "It changes with altitude and weather and is used as the reference for calculated boost or vacuum.",
    )
    "ENGINE LOAD" -> ParameterHelp(
        "The ECU's calculated engine-load percentage for the current operating point.",
        "It generally rises with throttle, grade, acceleration, and boost. It is not a direct horsepower measurement.",
    )
    "INTAKE AIR" -> ParameterHelp(
        "The first intake-air temperature reported by Elizabeth's multi-sensor OBD response.",
        "It reflects the air at that sensor's location. Compare it with charge air and ambient air to understand heat soak.",
    )
    "CHARGE AIR" -> ParameterHelp(
        "The second intake-temperature channel reported by Elizabeth's multi-sensor OBD response.",
        "It represents another point in the intake path. Compare it with intake and ambient temperature; the exact physical sensor location is vehicle-defined.",
    )
    "FUEL RAIL" -> ParameterHelp(
        "Fuel pressure supplied to the engine's injection system.",
        "The ECU actively changes this pressure with operating demand. It is not expected to remain at one fixed value.",
    )
    "ENGINE RPM" -> ParameterHelp(
        "Crankshaft revolutions per minute.",
        "RPM shows engine speed, not vehicle speed. It changes with load and CVT operation.",
    )
    "ABSOLUTE LOAD" -> ParameterHelp(
        "A standardized airflow-based load value referenced to the engine's expected maximum airflow.",
        "Unlike calculated engine load, this SAE value can exceed 100 percent on some boosted engines.",
    )
    "PEDAL REQUEST" -> ParameterHelp(
        "The driver's accelerator-pedal input reported by the ECU.",
        "This is a request for power, not the physical throttle-plate opening.",
    )
    "THROTTLE ACTUAL" -> ParameterHelp(
        "The measured position of the electronic throttle plate.",
        "It may differ from pedal request because the ECU manages torque, emissions, traction, and drivability.",
    )
    "THROTTLE COMMAND" -> ParameterHelp(
        "The throttle-plate position commanded by the ECU.",
        "Compare it with actual throttle position. Small differences and brief response lag are normal.",
    )
    "IGNITION TIMING" -> ParameterHelp(
        "Spark timing in crankshaft degrees relative to top dead center.",
        "Timing changes constantly with RPM, load, temperature, fuel quality, and knock control; one isolated number is not a diagnosis.",
    )
    "TORQUE REQUEST" -> ParameterHelp(
        "The ECU's requested engine torque as a percentage of its reference torque.",
        "It represents a control request, not measured torque at the wheels.",
    )
    "TORQUE ACTUAL" -> ParameterHelp(
        "The ECU's estimate of delivered engine torque as a percentage of reference torque.",
        "It is a modeled control value, not a dynamometer measurement.",
    )
    "REFERENCE TORQUE" -> ParameterHelp(
        "The ECU's full-scale torque reference used by the standardized percentage-torque PIDs.",
        "It provides context for requested and actual torque percentages; it is not current wheel torque.",
    )
    "OIL TEMP", "ENGINE OIL" -> ParameterHelp(
        "Engine-oil temperature reported through standard OBD-II.",
        "It helps show oil warm-up and sustained heat load. The card appears only when Elizabeth actually reports the value.",
    )
    "MODULE VOLTAGE" -> ParameterHelp(
        "Electrical voltage measured by the control module.",
        "It reflects vehicle-system voltage at the ECU. Honda's intelligent charging can vary voltage while the engine is running.",
    )
    "AMBIENT AIR" -> ParameterHelp(
        "Outside-air temperature reported by the ECU.",
        "Use it as environmental context for intake and charge-air temperatures.",
    )
    "COOLANT" -> ParameterHelp(
        "Engine-coolant temperature reported by the ECU.",
        "It shows engine warm-up and cooling-system heat. Watch the trend and status rather than judging a single brief reading.",
    )
    "IAT DELTA" -> ParameterHelp(
        "Charge-air temperature minus ambient-air temperature.",
        "A larger positive difference means the intake charge is warmer than outside air, often from heat soak or compression.",
    )
    else -> ParameterHelp(
        "A live value reported by Elizabeth's ECU or calculated from clearly identified live inputs.",
        "Use the current value together with its trend and operating conditions. Missing data is never replaced with zero.",
    )
}

private fun tilesToRows(vararg tiles: SensorTile?): List<List<SensorTile>> =
    tiles.filterNotNull().chunked(4)

private fun economyTilesToRows(vararg tiles: SensorTile?): List<List<SensorTile>> =
    tiles.filterNotNull().chunked(3)

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
