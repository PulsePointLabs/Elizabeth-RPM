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
    CVT_CONTROL("CVT + CONTROL"),
    CHASSIS("CHASSIS"),
    ELECTRICAL("ELECTRICAL"),
}

private enum class SensorSource(val badge: String) {
    STANDARD("SAE OBD"),
    CALCULATED("CALCULATED"),
    HONDA("HONDA ENHANCED"),
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
    val averageSpeedKph = if (durationSeconds > 0) {
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
            rows = listOf(
                listOf(
                    SensorTile("TRIP COST", tripCost?.money(), "$", SensorSource.CALCULATED, accent = GoodGreen),
                    SensorTile(
                        "AVERAGE SPEED",
                        averageSpeedKph?.speedValue(units),
                        speedUnit(units),
                        SensorSource.CALCULATED,
                        accent = RpmBlue,
                    ),
                    SensorTile("DURATION", formatClock(durationSeconds), "", SensorSource.CALCULATED, accent = BoostTeal),
                ),
                listOf(
                    SensorTile(
                        "FUEL RATE",
                        sample?.fuelRateLitersPerHour?.oneDecimal(),
                        "L/h",
                        if (sample?.fuelRateEstimated == true) SensorSource.CALCULATED else SensorSource.STANDARD,
                        pidLabel = if (sample?.fuelRateEstimated == true) "FROM MAF" else "01 5E",
                        accent = ThrottleAmber,
                    ),
                    SensorTile(
                        "MASS AIR FLOW",
                        sample?.massAirFlowGramsPerSecond?.oneDecimal(),
                        "g/s",
                        SensorSource.STANDARD,
                        "01 66",
                        BoostTeal,
                    ),
                    SensorTile(
                        "COMMAND λ",
                        sample?.commandedEquivalenceRatio?.threeDecimals(),
                        "λ",
                        SensorSource.STANDARD,
                        "01 44",
                        GoodGreen,
                    ),
                ),
                listOf(
                    SensorTile(
                        "ACTUAL λ",
                        sample?.actualEquivalenceRatio?.threeDecimals(),
                        "λ",
                        SensorSource.STANDARD,
                        "01 24 / 34",
                        GoodGreen,
                    ),
                    SensorTile(
                        "SHORT TRIM",
                        sample?.shortFuelTrim?.signedOneDecimal(),
                        "%",
                        SensorSource.STANDARD,
                        "01 06",
                        fuelTrimColor(sample?.shortFuelTrim),
                    ),
                    SensorTile(
                        "LONG TRIM",
                        sample?.longFuelTrim?.signedOneDecimal(),
                        "%",
                        SensorSource.STANDARD,
                        "01 07",
                        fuelTrimColor(sample?.longFuelTrim),
                    ),
                ),
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
        rows = listOf(
            listOf(
                SensorTile(
                    "BOOST / VACUUM",
                    sample?.boostPsi?.let { if (units == UnitSystem.US) it.oneDecimal() else (it * 6.89476).oneDecimal() },
                    if (units == UnitSystem.US) "psi" else "kPa",
                    SensorSource.CALCULATED,
                    "MAP − BARO",
                    BoostTeal,
                ),
                SensorTile("MANIFOLD", sample?.manifoldPressureKpa?.oneDecimal(), "kPa", SensorSource.STANDARD, "01 0B", BoostTeal),
                SensorTile("BAROMETRIC", sample?.barometricPressureKpa?.oneDecimal(), "kPa", SensorSource.STANDARD, "01 33", RpmBlue),
                SensorTile("ENGINE LOAD", sample?.engineLoad?.oneDecimal(), "%", SensorSource.STANDARD, "01 04", ThrottleAmber),
            ),
            listOf(
                SensorTile("INTAKE AIR", sample?.intakeC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT1", RpmBlue),
                SensorTile("CHARGE AIR", sample?.chargeAirC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT2", BoostTeal),
                SensorTile("MASS AIR FLOW", sample?.massAirFlowGramsPerSecond?.oneDecimal(), "g/s", SensorSource.STANDARD, "01 66", BoostTeal),
                SensorTile("FUEL RAIL", sample?.fuelRailPressureKpa?.pressureValue(units), pressureUnit(units), SensorSource.STANDARD, "01 23 / 59", WarningRed),
            ),
            listOf(
                SensorTile("COMMAND λ", sample?.commandedEquivalenceRatio?.threeDecimals(), "λ", SensorSource.STANDARD, "01 44", GoodGreen),
                SensorTile("ACTUAL λ", sample?.actualEquivalenceRatio?.threeDecimals(), "λ", SensorSource.STANDARD, "01 24 / 34", GoodGreen),
                SensorTile("SHORT TRIM", sample?.shortFuelTrim?.signedOneDecimal(), "%", SensorSource.STANDARD, "01 06", fuelTrimColor(sample?.shortFuelTrim)),
                SensorTile("LONG TRIM", sample?.longFuelTrim?.signedOneDecimal(), "%", SensorSource.STANDARD, "01 07", fuelTrimColor(sample?.longFuelTrim)),
            ),
        ),
        connected = state.connectionState == ConnectionState.CONNECTED,
    )
}

@Composable
internal fun PowertrainDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
) {
    SensorPage(
        title = "CVT + CONTROL",
        subtitle = "Transmission condition, driver request, throttle management, ignition, and torque",
        rows = listOf(
            listOf(
                enhancedTemperature("CVT FLUID", sample?.cvtFluidC, units),
                enhancedNumber("CVT INPUT", sample?.cvtInputRpm, "rpm"),
                enhancedNumber("CVT OUTPUT", sample?.cvtOutputRpm, "rpm"),
                enhancedNumber("CVT RATIO", sample?.cvtRatio, ":1", decimals = 2),
            ),
            listOf(
                enhancedNumber("LOCKUP", sample?.cvtLockupPercent, "%"),
                SensorTile("PEDAL REQUEST", sample?.acceleratorPedalPercent?.oneDecimal(), "%", SensorSource.STANDARD, "01 49 / 5A", ThrottleAmber),
                SensorTile("THROTTLE ACTUAL", sample?.throttlePercent?.oneDecimal(), "%", SensorSource.STANDARD, "01 11", ThrottleAmber),
                SensorTile("THROTTLE COMMAND", sample?.commandedThrottlePercent?.oneDecimal(), "%", SensorSource.STANDARD, "01 4C", ThrottleAmber),
            ),
            listOf(
                SensorTile("IGNITION TIMING", sample?.timingAdvance?.signedOneDecimal(), "°", SensorSource.STANDARD, "01 0E", RpmBlue),
                enhancedNumber("KNOCK CONTROL", sample?.knockControlPercent, "%"),
                SensorTile("TORQUE REQUEST", sample?.driverDemandTorquePercent?.signedOneDecimal(), "%", SensorSource.STANDARD, "01 61", BoostTeal),
                SensorTile("TORQUE ACTUAL", sample?.actualTorquePercent?.signedOneDecimal(), "%", SensorSource.STANDARD, "01 62", BoostTeal),
            ),
            listOf(
                SensorTile("REFERENCE TORQUE", sample?.referenceTorqueNm?.oneDecimal(), "N·m", SensorSource.STANDARD, "01 63", RpmBlue),
                SensorTile("ENGINE RPM", sample?.rpm?.let { "%.0f".format(Locale.US, it) }, "rpm", SensorSource.STANDARD, "01 0C", RpmBlue),
                SensorTile("ENGINE LOAD", sample?.engineLoad?.oneDecimal(), "%", SensorSource.STANDARD, "01 04", ThrottleAmber),
                SensorTile("OIL TEMP", sample?.oilC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 5C", WarningRed),
            ),
        ),
        connected = state.connectionState == ConnectionState.CONNECTED,
    )
}

@Composable
internal fun ChassisDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
) {
    SensorPage(
        title = "CHASSIS",
        subtitle = "ABS/VSA wheel speed, steering, body motion, braking, traction, and TPMS",
        rows = listOf(
            listOf(
                enhancedSpeed("WHEEL · FRONT LEFT", sample?.wheelSpeedFrontLeftKph, units),
                enhancedSpeed("WHEEL · FRONT RIGHT", sample?.wheelSpeedFrontRightKph, units),
                enhancedSpeed("WHEEL · REAR LEFT", sample?.wheelSpeedRearLeftKph, units),
                enhancedSpeed("WHEEL · REAR RIGHT", sample?.wheelSpeedRearRightKph, units),
            ),
            listOf(
                enhancedNumber("STEERING ANGLE", sample?.steeringAngleDegrees, "°"),
                enhancedNumber("YAW RATE", sample?.yawRateDegreesPerSecond, "°/s"),
                enhancedNumber("LATERAL ACCEL", sample?.lateralAccelerationG, "g", decimals = 2),
                enhancedNumber("LONGITUDINAL ACCEL", sample?.longitudinalAccelerationG, "g", decimals = 2),
            ),
            listOf(
                enhancedNumber("BRAKE PRESSURE", sample?.brakePressureBar, "bar"),
                SensorTile(
                    "TRACTION / VSA",
                    sample?.tractionControlActive?.let { if (it) "ACTIVE" else "IDLE" },
                    "",
                    SensorSource.HONDA,
                    "ABS/VSA",
                    if (sample?.tractionControlActive == true) WarningRed else GoodGreen,
                ),
                enhancedPressure("TIRE · FRONT LEFT", sample?.tirePressureFrontLeftKpa, units),
                enhancedPressure("TIRE · FRONT RIGHT", sample?.tirePressureFrontRightKpa, units),
            ),
            listOf(
                enhancedPressure("TIRE · REAR LEFT", sample?.tirePressureRearLeftKpa, units),
                enhancedPressure("TIRE · REAR RIGHT", sample?.tirePressureRearRightKpa, units),
                SensorTile("VEHICLE SPEED", sample?.speedKph?.speedValue(units), speedUnit(units), SensorSource.STANDARD, "01 0D", RpmBlue),
                SensorTile(
                    "MODULE STATUS",
                    if (listOf(
                            sample?.wheelSpeedFrontLeftKph,
                            sample?.steeringAngleDegrees,
                            sample?.brakePressureBar,
                        ).any { it != null }
                    ) "LIVE" else null,
                    "",
                    SensorSource.HONDA,
                    "ABS / VSA / TPMS",
                    GoodGreen,
                ),
            ),
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
        subtitle = "Honda intelligent charging, battery state, and the temperatures that explain heat soak",
        rows = listOf(
            listOf(
                SensorTile("MODULE VOLTAGE", sample?.voltage?.twoDecimals(), "V", SensorSource.STANDARD, "01 42", voltageAccent),
                enhancedNumber("BATTERY CURRENT", sample?.batteryCurrentAmps, "A"),
                enhancedNumber("BATTERY SOC", sample?.batteryStateOfChargePercent, "%"),
                enhancedNumber("CHARGE COMMAND", sample?.chargingCommandPercent, "%"),
            ),
            listOf(
                SensorTile("AMBIENT AIR", sample?.ambientC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 46", RpmBlue),
                SensorTile("INTAKE AIR", sample?.intakeC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT1", RpmBlue),
                SensorTile("CHARGE AIR", sample?.chargeAirC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 68 · IAT2", BoostTeal),
                SensorTile("COOLANT", sample?.coolantC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 67", WarningRed),
            ),
            listOf(
                SensorTile("ENGINE OIL", sample?.oilC?.temperatureValue(units), temperatureUnit(units), SensorSource.STANDARD, "01 5C", WarningRed),
                SensorTile("BAROMETRIC", sample?.barometricPressureKpa?.oneDecimal(), "kPa", SensorSource.STANDARD, "01 33", RpmBlue),
                SensorTile(
                    "IAT DELTA",
                    temperatureDelta(sample?.chargeAirC, sample?.ambientC, units),
                    if (units == UnitSystem.US) "°F" else "°C",
                    SensorSource.CALCULATED,
                    "CHARGE − AMBIENT",
                    BoostTeal,
                ),
                SensorTile(
                    "CHARGING STATUS",
                    if (sample?.batteryCurrentAmps != null || sample?.batteryStateOfChargePercent != null) "LIVE" else null,
                    "",
                    SensorSource.HONDA,
                    "BATTERY SENSOR",
                    GoodGreen,
                ),
            ),
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { tile ->
                    SensorMetricTile(tile, connected, Modifier.weight(1f).fillMaxHeight())
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
        tile.source == SensorSource.HONDA -> "VERIFIED HONDA PROFILE REQUIRED"
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

private fun enhancedNumber(
    label: String,
    value: Double?,
    unit: String,
    decimals: Int = 1,
) = SensorTile(
    label = label,
    value = value?.let {
        when (decimals) {
            0 -> "%.0f".format(Locale.US, it)
            2 -> "%.2f".format(Locale.US, it)
            else -> "%.1f".format(Locale.US, it)
        }
    },
    unit = unit,
    source = SensorSource.HONDA,
    accent = BoostTeal,
)

private fun enhancedTemperature(label: String, value: Double?, units: UnitSystem) = SensorTile(
    label,
    value?.temperatureValue(units),
    temperatureUnit(units),
    SensorSource.HONDA,
    accent = WarningRed,
)

private fun enhancedSpeed(label: String, valueKph: Double?, units: UnitSystem) = SensorTile(
    label,
    valueKph?.speedValue(units),
    speedUnit(units),
    SensorSource.HONDA,
    accent = RpmBlue,
)

private fun enhancedPressure(label: String, valueKpa: Double?, units: UnitSystem) = SensorTile(
    label,
    valueKpa?.tirePressureValue(units),
    if (units == UnitSystem.US) "psi" else "kPa",
    SensorSource.HONDA,
    accent = GoodGreen,
)

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
private fun Double.tirePressureValue(units: UnitSystem) =
    if (units == UnitSystem.US) (this * 0.145037738).oneDecimal() else oneDecimal()

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
