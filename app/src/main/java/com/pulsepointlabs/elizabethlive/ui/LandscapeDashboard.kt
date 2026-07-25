package com.pulsepointlabs.elizabethlive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsepointlabs.elizabethlive.ConnectionState
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.UnitSystem
import com.pulsepointlabs.elizabethlive.trip.FuelCostCalculator
import com.pulsepointlabs.elizabethlive.ui.components.RollingTelemetryChart
import com.pulsepointlabs.elizabethlive.ui.theme.BoostTeal
import com.pulsepointlabs.elizabethlive.ui.theme.GoodGreen
import com.pulsepointlabs.elizabethlive.ui.theme.RpmBlue
import com.pulsepointlabs.elizabethlive.ui.theme.ThrottleAmber
import java.util.Locale
import kotlin.math.max

@Composable
fun LandscapeDashboard(
    state: ElizabethUiState,
    onExit: () -> Unit,
    onToggleTrip: () -> Unit,
) {
    BackHandler(onBack = onExit)
    val sample = state.samples.lastOrNull()
    val units = state.settings.units
    val price = state.settings.fuelPricePerGallon
    val tripCost = FuelCostCalculator.cost(state.liveFuelUsedLiters, price)
    val durationSeconds = max(0L, (System.currentTimeMillis() - state.liveDriveStartedAtMillis) / 1_000L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardHeader(state, onExit, onToggleTrip)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeedPanel(sample, units, Modifier.weight(.28f).fillMaxHeight())
            CenterPanel(state, sample, units, Modifier.weight(.44f).fillMaxHeight())
            SupportingPanel(
                sample = sample,
                units = units,
                tripCost = tripCost,
                durationSeconds = durationSeconds,
                fuelPrice = price,
                modifier = Modifier.weight(.28f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    state: ElizabethUiState,
    onExit: () -> Unit,
    onToggleTrip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ELIZABETH",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp,
        )
        Spacer(Modifier.width(14.dp))
        val connected = state.connectionState == ConnectionState.CONNECTED
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (connected) GoodGreen else ThrottleAmber)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (connected) "Connected · ${state.adapterName}" else "Demo source · ${state.adapterName}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Text(
                "SIMULATED",
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onToggleTrip, modifier = Modifier.height(50.dp)) {
            Text(if (state.trip.isRecording) "Stop trip" else "Start trip", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onExit, modifier = Modifier.height(50.dp)) {
            Text("Exit dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpeedPanel(sample: TelemetrySample?, units: UnitSystem, modifier: Modifier = Modifier) {
    DashboardCard(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("SPEED", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = when (units) {
                    UnitSystem.US -> ((sample?.speedKph ?: 0.0) * .621371).toInt().toString()
                    UnitSystem.METRIC -> (sample?.speedKph ?: 0.0).toInt().toString()
                },
                fontSize = 88.sp,
                lineHeight = 88.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (units == UnitSystem.US) "MPH" else "KM/H",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RpmBlue,
            )
            Spacer(Modifier.height(12.dp))
            ValueStrip(
                "THROTTLE",
                "${sample?.throttlePercent?.toInt() ?: 0}%",
                ThrottleAmber,
            )
        }
    }
}

@Composable
private fun CenterPanel(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardCard(Modifier.fillMaxWidth().weight(.43f)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ENGINE", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${sample?.rpm?.toInt() ?: 0}",
                            fontSize = 52.sp,
                            lineHeight = 54.sp,
                            fontWeight = FontWeight.Black,
                            color = RpmBlue,
                        )
                        Text(" RPM", Modifier.padding(bottom = 7.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CALCULATED BOOST / VACUUM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (units == UnitSystem.US) {
                            "${sample?.boostPsi?.oneDecimal() ?: "0.0"} psi"
                        } else {
                            "${sample?.boostPsi?.times(6.89476)?.oneDecimal() ?: "0.0"} kPa"
                        },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = BoostTeal,
                    )
                }
            }
        }
        DashboardCard(Modifier.fillMaxWidth().weight(.57f)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("LAST 30 SECONDS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("RPM   BOOST   THROTTLE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                RollingTelemetryChart(
                    samples = state.samples.takeLast(120),
                    channels = setOf("RPM", "Boost", "Throttle"),
                    inspected = null,
                    modifier = Modifier.fillMaxWidth(),
                    chartHeight = 104.dp,
                    onTap = { },
                    onInspect = { },
                )
            }
        }
    }
}

@Composable
private fun SupportingPanel(
    sample: TelemetrySample?,
    units: UnitSystem,
    tripCost: Double,
    durationSeconds: Long,
    fuelPrice: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallMetric(
                "COOLANT",
                sample?.coolantC?.temperature(units) ?: "—",
                RpmBlue,
                Modifier.weight(1f).fillMaxHeight(),
            )
            SmallMetric(
                "INTAKE AIR",
                sample?.intakeC?.temperature(units) ?: "—",
                ThrottleAmber,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallMetric(
                "VOLTAGE",
                "${sample?.voltage?.oneDecimal() ?: "—"} V",
                BoostTeal,
                Modifier.weight(1f).fillMaxHeight(),
            )
            SmallMetric(
                "FUEL RATE",
                "${sample?.fuelRateLitersPerHour?.oneDecimal() ?: "—"} L/h",
                GoodGreen,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        DashboardCard(Modifier.fillMaxWidth().weight(1.18f)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TRIP COST", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${tripCost.money()}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = GoodGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDuration(durationSeconds), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "$${fuelPrice.money()} / gal",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    DashboardCard(modifier) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(value, fontSize = 25.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, color = color, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ValueStrip(label: String, value: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { content() },
    )
}

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
private fun Double.money(): String = String.format(Locale.US, "%.2f", this)
private fun Double.temperature(units: UnitSystem): String =
    if (units == UnitSystem.US) "${(this * 9 / 5 + 32).toInt()}°F" else "${toInt()}°C"

private fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
