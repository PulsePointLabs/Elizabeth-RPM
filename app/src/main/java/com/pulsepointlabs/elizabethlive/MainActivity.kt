package com.pulsepointlabs.elizabethlive

import android.Manifest
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.pulsepointlabs.elizabethlive.ui.LandscapeDashboard
import com.pulsepointlabs.elizabethlive.ui.components.DualTemperatureBars
import com.pulsepointlabs.elizabethlive.ui.components.FuelTrimBalance
import com.pulsepointlabs.elizabethlive.ui.components.RollingTelemetryChart
import com.pulsepointlabs.elizabethlive.ui.components.VoltageSparkline
import com.pulsepointlabs.elizabethlive.trip.FuelCostCalculator
import com.pulsepointlabs.elizabethlive.ui.theme.BoostTeal
import com.pulsepointlabs.elizabethlive.ui.theme.ElizabethTheme
import com.pulsepointlabs.elizabethlive.ui.theme.GoodGreen
import com.pulsepointlabs.elizabethlive.ui.theme.RpmBlue
import com.pulsepointlabs.elizabethlive.ui.theme.ThrottleAmber
import com.pulsepointlabs.elizabethlive.ui.theme.WarningRed
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ElizabethViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            ElizabethTheme(state.settings.theme) {
                ElizabethApp(state, viewModel)
            }
        }
    }
}
private data class Destination(val label: String, val icon: ImageVector)

@Composable
private fun ElizabethApp(state: ElizabethUiState, viewModel: ElizabethViewModel) {
    val context = LocalContext.current
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.prepareConnection() else viewModel.onBluetoothPermissionDenied()
    }
    val onConnectionControl = {
        if (state.connectionState != ConnectionState.DISCONNECTED) {
            viewModel.disconnect()
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.prepareConnection()
        } else {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    val destinations = listOf(
        Destination("Live", Icons.Rounded.QueryStats),
        Destination("Trip", Icons.Rounded.DirectionsCar),
        Destination("Health", Icons.Rounded.FavoriteBorder),
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var dashboardDismissed by rememberSaveable { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscape) {
        if (!isLandscape) dashboardDismissed = false
    }
    if (isLandscape && !dashboardDismissed) {
        LandscapeDashboard(
            state = state,
            onExit = { dashboardDismissed = true },
            onToggleTrip = viewModel::toggleTrip,
            onConnectionControl = onConnectionControl,
        )
        if (state.showDevicePicker) {
            PairedDeviceDialog(
                devices = state.pairedDevices,
                onSelect = viewModel::selectDevice,
                onDismiss = viewModel::dismissDevicePicker,
            )
        }
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, null) },
                        label = { Text(destination.label, fontSize = 14.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
        ) {
            ConnectionHeader(state, onConnectionControl)
            if (isLandscape && dashboardDismissed) {
                FilledTonalButton(
                    onClick = { dashboardDismissed = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).height(50.dp),
                ) {
                    Text("Open driving dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            when (selected) {
                0 -> LiveScreen(state, viewModel)
                1 -> TripScreen(state, viewModel)
                else -> HealthScreen(state, viewModel)
            }
        }
    }
    if (state.showDevicePicker) {
        PairedDeviceDialog(
            devices = state.pairedDevices,
            onSelect = viewModel::selectDevice,
            onDismiss = viewModel::dismissDevicePicker,
        )
    }
}

@Composable
private fun PairedDeviceDialog(
    devices: List<PairedObdDevice>,
    onSelect: (PairedObdDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select paired OBD adapter", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose the vLinker MC+ already paired in Android Bluetooth settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                devices.forEach { device ->
                    OutlinedButton(
                        onClick = { onSelect(device) },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(device.name, fontWeight = FontWeight.Bold)
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConnectionHeader(state: ElizabethUiState, onConnect: () -> Unit) {
    val statusColor = when (state.connectionState) {
        ConnectionState.CONNECTED -> GoodGreen
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> ThrottleAmber
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        when (state.connectionState) {
                            ConnectionState.CONNECTED -> "Connected"
                            ConnectionState.CONNECTING -> "Connecting"
                            ConnectionState.RECONNECTING -> "Reconnecting"
                            ConnectionState.DISCONNECTED -> "Disconnected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "${state.adapterName}  ·  ${state.connectionDetail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onConnect, modifier = Modifier.height(48.dp)) {
                Text(
                    if (state.connectionState == ConnectionState.DISCONNECTED) "Connect" else "Disconnect",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveScreen(state: ElizabethUiState, viewModel: ElizabethViewModel) {
    val visible = remember(state.samples, state.timeWindow) {
        val seconds = state.timeWindow.seconds
        if (seconds == null) state.samples else {
            val cutoff = System.currentTimeMillis() - seconds * 1_000L
            state.samples.filter { it.timestampMillis >= cutoff }
        }
    }
    val latest = state.inspectedSample ?: visible.lastOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.lastConnectionError?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Connection problem", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Elizabeth Live", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.graphPaused) "Inspection paused — drag across the graph" else "Live vehicle timeline",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = viewModel::toggleTrip, modifier = Modifier.height(52.dp)) {
                    Text(if (state.trip.isRecording) "Stop recording" else "Start recording")
                }
            }
        }
        item {
            SectionLabel("Channels")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelChip("RPM", RpmBlue, "rpm", state.selectedChannels, viewModel::toggleChannel)
                ChannelChip("Boost", BoostTeal, if (state.settings.units == UnitSystem.US) "psi" else "kPa", state.selectedChannels, viewModel::toggleChannel)
                ChannelChip("Throttle", ThrottleAmber, "%", state.selectedChannels, viewModel::toggleChannel)
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TimeWindow.entries.forEach { window ->
                            val selected = state.timeWindow == window
                            if (selected) {
                                FilledTonalButton(
                                    onClick = { viewModel.setWindow(window) },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp),
                                ) { Text(window.label, fontSize = 12.sp) }
                            } else {
                                TextButton(
                                    onClick = { viewModel.setWindow(window) },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp),
                                ) { Text(window.label, fontSize = 12.sp) }
                            }
                        }
                    }
                    RollingTelemetryChart(
                        samples = visible,
                        channels = state.selectedChannels,
                        inspected = state.inspectedSample,
                        onTap = viewModel::togglePaused,
                        onInspect = viewModel::inspect,
                    )
                    Spacer(Modifier.height(12.dp))
                    GraphLegend(latest, state.selectedChannels, state.settings.units)
                }
            }
        }
        item {
            BoxWithConstraints {
                val landscape = maxWidth > 700.dp
                if (landscape) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TemperatureCard(latest, state.settings.units, Modifier.weight(1f))
                        FuelTrimCard(latest, Modifier.weight(1f))
                        ElectricalCard(visible, latest, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TemperatureCard(latest, state.settings.units)
                        FuelTrimCard(latest)
                        ElectricalCard(visible, latest)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun GraphLegend(latest: TelemetrySample?, selected: Set<String>, units: UnitSystem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if ("RPM" in selected) LegendValue("RPM", latest?.rpm?.toInt()?.toString() ?: "—", "rpm", RpmBlue)
        if ("Boost" in selected) LegendValue(
            "Boost/Vacuum",
            latest?.boostPsi?.let { if (units == UnitSystem.US) it.oneDecimal() else (it * 6.89476).oneDecimal() } ?: "—",
            if (units == UnitSystem.US) "psi" else "kPa",
            BoostTeal,
        )
        if ("Throttle" in selected) LegendValue("Throttle", latest?.throttlePercent?.toInt()?.toString() ?: "—", "%", ThrottleAmber)
    }
}

@Composable
private fun LegendValue(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$value $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TemperatureCard(sample: TelemetrySample?, units: UnitSystem, modifier: Modifier = Modifier) {
    MetricCard("Temperatures", statusForTemperature(sample?.coolantC), modifier) {
        Row(Modifier.fillMaxWidth()) {
            MetricText("Coolant", sample?.coolantC?.temperature(units) ?: "—", Modifier.weight(1f))
            MetricText("Intake air", sample?.intakeC?.temperature(units) ?: "—", Modifier.weight(1f))
        }
        DualTemperatureBars(sample?.coolantC ?: 0.0, sample?.intakeC ?: 0.0, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth()) {
            Text("Coolant", style = MaterialTheme.typography.labelSmall, color = RpmBlue, modifier = Modifier.weight(1f))
            Text("Intake air", style = MaterialTheme.typography.labelSmall, color = ThrottleAmber)
        }
    }
}

@Composable
private fun FuelTrimCard(sample: TelemetrySample?, modifier: Modifier = Modifier) {
    val hasTrim = sample?.shortFuelTrim != null || sample?.longFuelTrim != null
    val max = maxOf(kotlin.math.abs(sample?.shortFuelTrim ?: 0.0), kotlin.math.abs(sample?.longFuelTrim ?: 0.0))
    MetricCard("Fuel trim", if (!hasTrim) "Not reported" else if (max < 10) "Within expected range" else "Review", modifier) {
        Row(Modifier.fillMaxWidth()) {
            MetricText("Short-term", sample?.shortFuelTrim?.signedPercent() ?: "—", Modifier.weight(1f))
            MetricText("Long-term", sample?.longFuelTrim?.signedPercent() ?: "—", Modifier.weight(1f))
        }
        FuelTrimBalance(sample?.shortFuelTrim ?: 0.0, sample?.longFuelTrim ?: 0.0, Modifier.fillMaxWidth())
        Text("−25%                         0                         +25%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ElectricalCard(samples: List<TelemetrySample>, sample: TelemetrySample?, modifier: Modifier = Modifier) {
    val status = when {
        sample == null -> "Waiting for data"
        sample.voltage?.let { it < 11.8 } == true -> "Low"
        sample.voltage?.let { it > 15.0 } == true -> "High"
        else -> "Normal"
    }
    MetricCard("Electrical", status, modifier) {
        MetricText("Control-module voltage", sample?.voltage?.let { "${it.oneDecimal()} V" } ?: "—")
        VoltageSparkline(samples, Modifier.fillMaxWidth())
        Text("Rolling voltage trend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ElevatedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelLarge, color = GoodGreen)
            }
            content()
        }
    }
}

@Composable
private fun MetricText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripScreen(state: ElizabethUiState, viewModel: ElizabethViewModel) {
    val trip = liveTripSummary(state)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Trip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Recorded drive summary", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::toggleTrip, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(if (trip.isRecording) "Stop Trip" else "Start Trip")
                }
                OutlinedButton(onClick = { }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Export CSV") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = viewModel::deleteTrip, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Delete Trip", color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            if (trip.isRecording) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Recording trip · Live OBD data",
                        Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Card {
                    Text(
                        if (trip.startedAtMillis == null) "Start a trip to build a live summary." else "Live trip stopped · summary remains on this device session.",
                        Modifier.padding(16.dp),
                    )
                }
            }
        }
        item {
            FuelCostSummaryCard(state, viewModel)
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Full-trip timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    RollingTelemetryChart(
                        samples = state.samples,
                        channels = setOf("RPM", "Boost", "Throttle"),
                        inspected = null,
                        modifier = Modifier.height(230.dp),
                        onTap = { },
                        onInspect = { },
                    )
                }
            }
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryTile("Duration", "${trip.durationSeconds / 60} min ${trip.durationSeconds % 60} sec")
                SummaryTile("Distance", if (state.settings.units == UnitSystem.US) "${(trip.distanceKm * .621371).oneDecimal()} mi" else "${trip.distanceKm.oneDecimal()} km")
                SummaryTile("Average speed", speedText(trip.averageSpeedKph, state.settings.units))
                SummaryTile("Maximum speed", speedText(trip.maximumSpeedKph, state.settings.units))
                SummaryTile("Average RPM", trip.averageRpm.toInt().toString())
                SummaryTile("Maximum RPM", trip.maximumRpm.toInt().toString())
                SummaryTile("Peak boost", if (state.settings.units == UnitSystem.US) "${trip.maximumBoostPsi.oneDecimal()} psi" else "${(trip.maximumBoostPsi * 6.89476).oneDecimal()} kPa")
                SummaryTile("Average throttle", "${trip.averageThrottle.toInt()}%")
            }
        }
        item {
            RangeSummaryCard(trip, state.settings.units)
        }
        item {
            Text("Notable events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        val visibleEvents = trip.events
        if (visibleEvents.isEmpty()) {
            item {
                Text("No notable live events recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visibleEvents, key = { "${it.timestampMillis}-${it.label}" }) { event ->
            EventRow(event)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelCostSummaryCard(state: ElizabethUiState, viewModel: ElizabethViewModel) {
    val settings = state.settings
    var priceText by remember(settings.fuelPricePerGallon) {
        mutableStateOf(settings.fuelPricePerGallon.money())
    }
    val liveCost = FuelCostCalculator.cost(state.liveFuelUsedLiters, settings.fuelPricePerGallon)
    val hasLiveFuelRate = state.samples.lastOrNull()?.fuelRateLitersPerHour != null
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fuel cost", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        settings.fuelPriceSource,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CostTile("Current trip", if (hasLiveFuelRate) "$${liveCost.money()}" else "—", Modifier.weight(1f), GoodGreen)
                CostTile(
                    "Fuel-rate status",
                    if (hasLiveFuelRate) {
                        "Measured"
                    } else {
                        "Not reported"
                    },
                    Modifier.weight(1f),
                    BoostTeal,
                )
            }
            Text(
                "Set your local regular-gas price",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { char -> char.isDigit() || char == '.' }.take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Dollars per gallon") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Button(
                    onClick = { priceText.toDoubleOrNull()?.let(viewModel::setFuelPricePerGallon) },
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("Use price", fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Costs use the ECU-reported fuel rate when available. Unsupported fuel rate is shown as unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CostTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String) {
    Card(
        modifier = Modifier.width(166.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RangeSummaryCard(trip: TripSummary, units: UnitSystem) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Trip ranges", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            RangeLine("Coolant", "${trip.coolantRangeC.start.temperature(units)}–${trip.coolantRangeC.endInclusive.temperature(units)}", .58f, .76f, RpmBlue)
            RangeLine("Intake air", "${trip.intakeRangeC.start.temperature(units)}–${trip.intakeRangeC.endInclusive.temperature(units)}", .18f, .48f, ThrottleAmber)
            RangeLine("Fuel trim", "${trip.fuelTrimRange.start.oneDecimal()}% to +${trip.fuelTrimRange.endInclusive.oneDecimal()}%", .38f, .66f, BoostTeal)
            Text("Minimum voltage  ${trip.minimumVoltage.oneDecimal()} V", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RangeLine(label: String, value: String, start: Float, end: Float, color: Color) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp)) {
            drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            drawRoundRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * start, 0f),
                size = androidx.compose.ui.geometry.Size(size.width * (end - start), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
        }
    }
}

@Composable
private fun EventRow(event: TripEvent) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(ThrottleAmber))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.label, fontWeight = FontWeight.SemiBold)
                Text(event.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("+${event.timestampMillis / 1_000}s", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthScreen(state: ElizabethUiState, viewModel: ElizabethViewModel) {
    val latest = state.samples.lastOrNull()
    val healthItems = listOf(
        HealthItem("Diagnostic trouble codes", "Not checked yet in this build.", HealthStatus.NOTICE),
        HealthItem("Emissions readiness", "Not checked yet in this build.", HealthStatus.NOTICE),
        HealthItem(
            "Control-module voltage",
            latest?.voltage?.let { if (it < 11.8) "Voltage is low." else "Voltage is within the expected range." }
                ?: "This parameter has not been reported.",
            if (latest?.voltage == null) HealthStatus.UNSUPPORTED else HealthStatus.GOOD,
        ),
        HealthItem(
            "Coolant temperature",
            latest?.coolantC?.let { "Coolant is ${statusForTemperature(it).lowercase()} at ${it.temperature(state.settings.units)}." }
                ?: "This parameter has not been reported.",
            if (latest?.coolantC == null) HealthStatus.UNSUPPORTED else HealthStatus.GOOD,
        ),
        HealthItem(
            "Intake temperature",
            latest?.intakeC?.let { if (it > 55) "Intake air is unusually warm." else "Intake-air temperature is reasonable." }
                ?: "This parameter has not been reported.",
            if (latest?.intakeC == null) HealthStatus.UNSUPPORTED else HealthStatus.GOOD,
        ),
        HealthItem(
            "Long-term fuel trim",
            latest?.longFuelTrim?.let { "Long-term fuel trim is ${it.signedPercent()}." }
                ?: "This parameter has not been reported.",
            if (latest?.longFuelTrim == null) HealthStatus.UNSUPPORTED else HealthStatus.GOOD,
        ),
        HealthItem("CVT fluid temperature", "This parameter is not reported by Elizabeth.", HealthStatus.UNSUPPORTED),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Health", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Plain-English vehicle and adapter status", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vehicle & adapter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    DetailRow("Vehicle", "Elizabeth · 2021 Honda Accord EX-L")
                    DetailRow("Engine", "1.5T · CVT")
                    DetailRow("App version", BuildConfig.VERSION_NAME)
                    DetailRow("Adapter", "vLinker MC+")
                    DetailRow("Protocol", state.protocolName ?: "Awaiting live connection")
                    DetailRow("VIN", "Not queried yet")
                    DetailRow(
                        "Supported PID scan",
                        if (state.supportedPids.isEmpty()) "Not completed" else "${state.supportedPids.size} PIDs reported by ECU",
                    )
                    DetailRow(
                        "Connection",
                        if (state.connectionState == ConnectionState.CONNECTED) "Live · ${state.adapterName}" else "Disconnected",
                    )
                    DetailRow(
                        "Last recorded trip",
                        state.trip.startedAtMillis?.let { "${state.trip.durationSeconds / 60} min ${state.trip.durationSeconds % 60} sec" }
                            ?: "No trip recorded this session",
                    )
                }
            }
        }
        item {
            PidDiagnosticsCard(state)
        }
        items(healthItems.filter { it.status != HealthStatus.UNSUPPORTED }) {
            HealthRow(it)
        }
        item {
            Text("Not reported", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Unsupported parameters stay out of the dashboard.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(healthItems.filter { it.status == HealthStatus.UNSUPPORTED }) {
            HealthRow(it)
        }
        item {
            SettingsCard(state.settings, viewModel)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PidDiagnosticsCard(state: ElizabethUiState) {
    val watched = listOf(
        0x05 to "Coolant",
        0x0F to "Intake air",
        0x10 to "Mass air flow",
        0x5E to "Engine fuel rate",
        0x44 to "Equivalence ratio",
    )
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live PID diagnostics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Sanitized adapter replies · no VIN or trip data",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            watched.forEachIndexed { index, (pid, label) ->
                if (index > 0) HorizontalDivider()
                val diagnostic = state.pidDiagnostics[pid]
                val command = "01%02X".format(pid)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("$command · $label", fontWeight = FontWeight.Bold)
                        Text(
                            diagnostic?.status ?: "WAITING FOR REQUEST",
                            color = when (diagnostic?.status) {
                                "VALUE" -> GoodGreen
                                "NO DATA" -> ThrottleAmber
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> WarningRed
                            },
                            fontWeight = FontWeight.Black,
                        )
                    }
                    diagnostic?.value?.let {
                        Text(it.oneDecimal(), fontWeight = FontWeight.Black, color = GoodGreen)
                    }
                }
                Text(
                    diagnostic?.response ?: "No reply captured yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(settings: AppSettings, viewModel: ElizabethViewModel) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Theme", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeSetting.entries.forEach {
                    FilterChip(
                        selected = settings.theme == it,
                        onClick = { viewModel.setTheme(it) },
                        label = { Text(it.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            HorizontalDivider()
            Text("Units", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.units == UnitSystem.US, onClick = { viewModel.setUnits(UnitSystem.US) }, label = { Text("US customary") })
                FilterChip(selected = settings.units == UnitSystem.METRIC, onClick = { viewModel.setUnits(UnitSystem.METRIC) }, label = { Text("Metric") })
            }
            DetailRow("Graph smoothing", if (settings.smoothing) "On" else "Off")
            OutlinedButton(onClick = viewModel::toggleSmoothing, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (settings.smoothing) "Turn smoothing off" else "Turn smoothing on")
            }
            HorizontalDivider()
            Text("Default graph window", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeWindow.entries.forEach {
                    FilterChip(selected = settings.defaultWindow == it, onClick = { viewModel.setDefaultWindow(it) }, label = { Text(it.label) })
                }
            }
            Text("Recording interval", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(250L, 500L, 1_000L).forEach {
                    FilterChip(selected = settings.recordingIntervalMillis == it, onClick = { viewModel.setRecordingInterval(it) }, label = { Text("$it ms") })
                }
            }
            DetailRow("Auto-start recording", if (settings.autoStartRecording) "On" else "Off")
            OutlinedButton(onClick = viewModel::toggleAutoStart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (settings.autoStartRecording) "Turn auto-start off" else "Turn auto-start on")
            }
            DetailRow("Connection device", "vLinker MC+")
            DetailRow("Android Auto", "Four live gauges · RPM, boost, coolant, voltage")
            OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Export all local data")
            }
        }
    }
}

@Composable
private fun HealthRow(item: HealthItem) {
    val color = when (item.status) {
        HealthStatus.GOOD -> GoodGreen
        HealthStatus.NOTICE -> ThrottleAmber
        HealthStatus.WARNING -> WarningRed
        HealthStatus.UNSUPPORTED -> MaterialTheme.colorScheme.outline
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(11.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(.42f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(.58f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChannelChip(
    label: String,
    color: Color,
    unit: String,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val active = label in selected
    AssistChip(
        onClick = { onToggle(label) },
        label = { Text("$label · $unit", fontWeight = FontWeight.SemiBold) },
        leadingIcon = {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) color else MaterialTheme.colorScheme.outline))
        },
    )
}

private fun statusForTemperature(coolant: Double?): String = when {
    coolant == null -> "Waiting for data"
    coolant < 60 -> "Cold"
    coolant < 100 -> "Normal"
    coolant < 108 -> "Warm"
    else -> "Unusually hot"
}

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
private fun Double.money(): String = String.format(Locale.US, "%.2f", this)
private fun Double.signedPercent(): String = String.format(Locale.US, "%+.1f%%", this)
private fun Double.temperature(units: UnitSystem): String =
    if (units == UnitSystem.US) "${(this * 9 / 5 + 32).toInt()} °F" else "${toInt()} °C"
private fun speedText(kph: Double, units: UnitSystem): String =
    if (units == UnitSystem.US) "${(kph * .621371).toInt()} mph" else "${kph.toInt()} km/h"

private fun liveTripSummary(state: ElizabethUiState): TripSummary {
    val startedAt = state.trip.startedAtMillis ?: return TripSummary(
        isRecording = false,
        startedAtMillis = null,
        durationSeconds = 0,
        distanceKm = 0.0,
        averageSpeedKph = 0.0,
        maximumSpeedKph = 0.0,
        averageRpm = 0.0,
        maximumRpm = 0.0,
        maximumBoostPsi = 0.0,
        coolantRangeC = 0.0..0.0,
        intakeRangeC = 0.0..0.0,
        averageThrottle = 0.0,
        fuelTrimRange = 0.0..0.0,
        minimumVoltage = 0.0,
        fuelUsedLiters = 0.0,
        events = emptyList(),
    )
    val samples = state.samples.filter { it.timestampMillis >= startedAt }
    fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
    fun List<Double>.maxOrZero() = maxOrNull() ?: 0.0
    fun List<Double>.rangeOrZero(): ClosedFloatingPointRange<Double> =
        if (isEmpty()) 0.0..0.0 else (minOrNull() ?: 0.0)..(maxOrNull() ?: 0.0)
    val speeds = samples.mapNotNull { it.speedKph }
    val rpms = samples.mapNotNull { it.rpm }
    val boost = samples.mapNotNull { it.boostPsi }
    val coolant = samples.mapNotNull { it.coolantC }
    val intake = samples.mapNotNull { it.intakeC }
    val throttle = samples.mapNotNull { it.throttlePercent }
    val trims = samples.flatMap { listOfNotNull(it.shortFuelTrim, it.longFuelTrim) }
    val voltage = samples.mapNotNull { it.voltage }
    val distance = samples.zipWithNext().sumOf { (first, second) ->
        val speed = first.speedKph ?: second.speedKph ?: 0.0
        speed * ((second.timestampMillis - first.timestampMillis).coerceAtLeast(0L) / 3_600_000.0)
    }
    val end = if (state.trip.isRecording) System.currentTimeMillis() else samples.lastOrNull()?.timestampMillis ?: startedAt
    return state.trip.copy(
        durationSeconds = ((end - startedAt).coerceAtLeast(0L) / 1_000L),
        distanceKm = distance,
        averageSpeedKph = speeds.averageOrZero(),
        maximumSpeedKph = speeds.maxOrZero(),
        averageRpm = rpms.averageOrZero(),
        maximumRpm = rpms.maxOrZero(),
        maximumBoostPsi = boost.maxOrZero(),
        coolantRangeC = coolant.rangeOrZero(),
        intakeRangeC = intake.rangeOrZero(),
        averageThrottle = throttle.averageOrZero(),
        fuelTrimRange = trims.rangeOrZero(),
        minimumVoltage = voltage.minOrNull() ?: 0.0,
        fuelUsedLiters = state.liveFuelUsedLiters,
    )
}
