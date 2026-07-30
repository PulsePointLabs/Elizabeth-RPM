package com.pulsepointlabs.elizabethlive.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pulsepointlabs.elizabethlive.ConnectionState
import com.pulsepointlabs.elizabethlive.ElizabethUiState
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.UnitSystem
import com.pulsepointlabs.elizabethlive.trip.FuelCostCalculator
import com.pulsepointlabs.elizabethlive.trip.FuelEfficiencyCalculator
import com.pulsepointlabs.elizabethlive.ui.components.RollingTelemetryChart
import com.pulsepointlabs.elizabethlive.ui.theme.BoostTeal
import com.pulsepointlabs.elizabethlive.ui.theme.GoodGreen
import com.pulsepointlabs.elizabethlive.ui.theme.RpmBlue
import com.pulsepointlabs.elizabethlive.ui.theme.ThrottleAmber
import com.pulsepointlabs.elizabethlive.ui.theme.WarningRed
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun LandscapeDashboard(
    state: ElizabethUiState,
    onExit: () -> Unit,
    onToggleTrip: () -> Unit,
    onConnectionControl: () -> Unit,
) {
    BackHandler(onBack = onExit)
    ImmersiveDashboardEffect()
    val sample = state.samples.lastOrNull()
    val units = state.settings.units
    val price = state.settings.fuelPricePerGallon
    val tripCost = if (sample?.fuelRateLitersPerHour != null) {
        FuelCostCalculator.cost(state.liveFuelUsedLiters, price)
    } else null
    val durationSeconds = max(0L, (System.currentTimeMillis() - state.liveDriveStartedAtMillis) / 1_000L)
    var menuVisible by rememberSaveable { mutableStateOf(true) }
    val pagerState = rememberPagerState(
        pageCount = { DashboardPage.entries.size },
    )
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(menuVisible) {
        if (menuVisible) {
            delay(4_500)
            menuVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(bottom = 42.dp),
            beyondViewportPageCount = 1,
            key = { DashboardPage.entries[it].name },
        ) {
            when (DashboardPage.entries[it]) {
                DashboardPage.DRIVE -> CurrentDashboardPage(
                    state = state,
                    sample = sample,
                    units = units,
                    tripCost = tripCost,
                    durationSeconds = durationSeconds,
                    fuelPrice = price,
                )
                DashboardPage.ECONOMY -> EconomyDashboardPage(
                    state = state,
                    sample = sample,
                    units = units,
                    tripCost = tripCost,
                    durationSeconds = durationSeconds,
                )
                DashboardPage.AIR_FUEL -> AirFuelDashboardPage(state, sample, units)
                DashboardPage.ENGINE_CONTROL -> EngineControlDashboardPage(state, sample, units)
                DashboardPage.ELECTRICAL -> ElectricalDashboardPage(state, sample, units)
            }
        }
        DashboardPageRail(
            currentPage = pagerState.currentPage,
            onPageSelected = { page ->
                pagerScope.launch { pagerState.animateScrollToPage(page) }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(Unit) {
                    var downwardDrag = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) downwardDrag += dragAmount
                            if (downwardDrag > 28f) menuVisible = true
                        },
                        onDragEnd = { downwardDrag = 0f },
                        onDragCancel = { downwardDrag = 0f },
                    )
                }
        )
        if (!menuVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(96.dp)
                    .height(32.dp)
                    .clickable { menuVisible = true },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(76.dp)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f), CircleShape)
                )
            }
        }
        AnimatedVisibility(
            visible = menuVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                shadowElevation = 10.dp,
            ) {
                DashboardHeader(
                    pageTitle = DashboardPage.entries[pagerState.currentPage].label,
                    state = state,
                    onExit = onExit,
                    onToggleTrip = onToggleTrip,
                    onConnectionControl = onConnectionControl,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun CurrentDashboardPage(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
    tripCost: Double?,
    durationSeconds: Long,
    fuelPrice: Double,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FuelEconomyPanel(
            sample = sample,
            units = units,
            distanceKm = state.liveDistanceKm,
            fuelUsedLiters = state.liveFuelUsedLiters,
            modifier = Modifier.weight(.27f).fillMaxHeight(),
        )
        CenterPanel(state, sample, units, Modifier.weight(.45f).fillMaxHeight())
        SupportingPanel(
            sample = sample,
            units = units,
            tripCost = tripCost,
            durationSeconds = durationSeconds,
            fuelPrice = fuelPrice,
            fuelUsedLiters = state.liveFuelUsedLiters,
            modifier = Modifier.weight(.28f).fillMaxHeight(),
        )
    }
}

@Composable
private fun DashboardHeader(
    pageTitle: String,
    state: ElizabethUiState,
    onExit: () -> Unit,
    onToggleTrip: () -> Unit,
    onConnectionControl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connectionState == ConnectionState.CONNECTED
    Row(
        modifier = modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ELIZABETH · $pageTitle",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            color = (if (connected) GoodGreen else ThrottleAmber).copy(alpha = .13f),
            shape = CircleShape,
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(if (connected) GoodGreen else ThrottleAmber, CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        connected -> "Live · ${state.adapterName}"
                        state.connectionState == ConnectionState.CONNECTING -> "Connecting · ${state.adapterName}"
                        state.connectionState == ConnectionState.RECONNECTING -> "Reconnecting · ${state.adapterName}"
                        else -> "Disconnected · ready to connect"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Spacer(Modifier.weight(1f))
        if (connected) {
            Button(onClick = onToggleTrip, modifier = Modifier.height(40.dp)) {
                Text(if (state.trip.isRecording) "Stop trip" else "Start trip", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
        }
        if (connected || state.connectionState != ConnectionState.DISCONNECTED) {
            OutlinedButton(onClick = onConnectionControl, modifier = Modifier.height(40.dp)) {
                Text(if (connected) "Disconnect" else "Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        } else {
            Button(onClick = onConnectionControl, modifier = Modifier.height(40.dp)) {
                Text("Connect", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.height(40.dp)) {
            Text("Exit dashboard", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ImmersiveDashboardEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            view.keepScreenOn = wasKeepingScreenOn
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
internal fun FuelEconomyPanel(
    sample: TelemetrySample?,
    units: UnitSystem,
    distanceKm: Double,
    fuelUsedLiters: Double,
    modifier: Modifier = Modifier,
) {
    val average = when (units) {
        UnitSystem.US -> FuelEfficiencyCalculator.averageMpg(distanceKm, fuelUsedLiters)
        UnitSystem.METRIC ->
            FuelEfficiencyCalculator.averageLitersPer100Km(distanceKm, fuelUsedLiters)
    }
    val instantaneous = when (units) {
        UnitSystem.US ->
            FuelEfficiencyCalculator.instantaneousMpg(sample?.speedKph, sample?.fuelRateLitersPerHour)
        UnitSystem.METRIC ->
            FuelEfficiencyCalculator.instantaneousLitersPer100Km(
                sample?.speedKph,
                sample?.fuelRateLitersPerHour,
            )
    }
    val economyUnit = if (units == UnitSystem.US) "MPG" else "L/100 KM"
    val averageSeverity = economySeverity(average, units)
    val liveSeverity = economySeverity(instantaneous, units)
    val averageColor = average?.let { animatedStatusColor(averageSeverity) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val liveColor = instantaneous?.let { animatedStatusColor(liveSeverity) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val averageProgress = when (units) {
        UnitSystem.US -> ((average ?: 0.0) / 50.0).toFloat().coerceIn(0f, 1f)
        UnitSystem.METRIC -> ((average ?: 0.0) / 20.0).toFloat().coerceIn(0f, 1f)
    }
    val liveProgress = when (units) {
        UnitSystem.US -> ((instantaneous ?: 0.0) / 50.0).toFloat().coerceIn(0f, 1f)
        UnitSystem.METRIC -> ((instantaneous ?: 0.0) / 20.0).toFloat().coerceIn(0f, 1f)
    }
    val distanceValue = when (units) {
        UnitSystem.US -> (distanceKm * 0.621371192).oneDecimal()
        UnitSystem.METRIC -> distanceKm.oneDecimal()
    }
    val distanceUnit = if (units == UnitSystem.US) "MI" else "KM"
    val fuelValue = when (units) {
        UnitSystem.US -> (fuelUsedLiters / 3.785411784).twoDecimals()
        UnitSystem.METRIC -> fuelUsedLiters.twoDecimals()
    }
    val fuelUnit = if (units == UnitSystem.US) "GAL" else "L"
    val fuelSource = when {
        sample?.fuelRateLitersPerHour == null -> "FUEL DATA UNAVAILABLE"
        sample.fuelRateEstimated -> "MAF ESTIMATE"
        else -> "ECU FUEL RATE"
    }

    DashboardCard(modifier, accentColor = averageColor) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "FUEL ECONOMY",
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    fuelSource,
                    modifier = Modifier
                        .background(averageColor.copy(alpha = .11f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = averageColor,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                LandscapeParameterInfoButton(
                    label = "AVERAGE FUEL ECONOMY",
                    value = average?.oneDecimal(),
                    unit = economyUnit,
                    source = fuelSource,
                    accent = averageColor,
                )
            }
            EconomyGauge(
                value = average,
                unit = economyUnit,
                progress = averageProgress,
                color = averageColor,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            LiveEconomyCard(
                value = instantaneous?.let { "${it.oneDecimal()} $economyUnit" } ?: "—",
                progress = liveProgress,
                color = liveColor,
                source = fuelSource,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                EconomyFact(
                    label = "TRIP DISTANCE",
                    value = distanceValue,
                    unit = distanceUnit,
                    source = "CALCULATED FROM VEHICLE SPEED",
                    modifier = Modifier.weight(1f),
                )
                EconomyFact(
                    label = "FUEL USED",
                    value = fuelValue,
                    unit = fuelUnit,
                    source = fuelSource,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EconomyGauge(
    value: Double?,
    unit: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "averageEconomyGauge",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 3.dp)) {
            val sweep = 210f
            val start = 165f
            val radius = min(size.width * .43f, size.height * .78f)
            val diameter = radius * 2f
            val center = Offset(size.width / 2f, size.height * .88f)
            val topLeft = Offset(center.x - diameter / 2f, center.y - diameter / 2f)
            val stroke = 8.dp.toPx()
            drawArc(
                color = track,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (value != null) {
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            val outerRadius = diameter / 2f - stroke * .15f
            repeat(11) { index ->
                val angle = start + sweep * index / 10f
                val radians = angle * PI / 180.0
                val innerRadius = outerRadius - if (index % 5 == 0) 12.dp.toPx() else 7.dp.toPx()
                drawLine(
                    color = tick.copy(alpha = if (index % 5 == 0) .9f else .45f),
                    start = Offset(
                        center.x + cos(radians).toFloat() * innerRadius,
                        center.y + sin(radians).toFloat() * innerRadius,
                    ),
                    end = Offset(
                        center.x + cos(radians).toFloat() * outerRadius,
                        center.y + sin(radians).toFloat() * outerRadius,
                    ),
                    strokeWidth = if (index % 5 == 0) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (value != null) {
                val needleAngle = (start + sweep * animatedProgress) * PI / 180.0
                val needleLength = outerRadius * .68f
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(
                        center.x + cos(needleAngle).toFloat() * needleLength,
                        center.y + sin(needleAngle).toFloat() * needleLength,
                    ),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color, radius = 5.dp.toPx(), center = center)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(45.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value?.oneDecimal() ?: "—",
                fontSize = 37.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    "AVERAGE",
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    unit,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LiveEconomyCard(
    value: String,
    progress: Float,
    color: Color,
    source: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = .09f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "REAL TIME",
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "CURRENT EFFICIENCY",
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
                    )
                }
                LandscapeParameterInfoButton(
                    label = "REAL-TIME FUEL ECONOMY",
                    value = value.takeUnless { it == "—" },
                    unit = "",
                    source = source,
                    accent = color,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    value,
                    fontSize = 22.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(5.dp))
            SegmentedBar(progress, color, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EconomyFact(
    label: String,
    value: String,
    unit: String,
    source: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                LandscapeParameterInfoButton(
                    label = label,
                    value = value,
                    unit = unit,
                    source = source,
                    accent = GoodGreen,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 18.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    modifier = Modifier.padding(bottom = 1.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun economySeverity(value: Double?, units: UnitSystem): Float {
    value ?: return 0f
    return when (units) {
        UnitSystem.US -> (1f - (value / 42.0).toFloat()).coerceIn(0f, 1f)
        UnitSystem.METRIC -> ((value - 5.0) / 12.0).toFloat().coerceIn(0f, 1f)
    }
}

private fun Double.twoDecimals(): String = String.format(Locale.US, "%.2f", this)

@Composable
private fun CenterPanel(
    state: ElizabethUiState,
    sample: TelemetrySample?,
    units: UnitSystem,
    modifier: Modifier = Modifier,
) {
    var inspectedSample by remember { mutableStateOf<TelemetrySample?>(null) }
    val rpmProgress = ((sample?.rpm ?: 0.0) / 6_500.0).toFloat().coerceIn(0f, 1f)
    val rpmColor = animatedStatusColor(((rpmProgress - .45f) / .5f).coerceIn(0f, 1f))
    val rpmValues = state.samples.mapNotNull { it.rpm }
    val averageRpm = rpmValues.takeIf { it.isNotEmpty() }?.average()
    val maximumRpm = rpmValues.maxOrNull()
    val boostPsi = sample?.boostPsi
    val boostProgress = (((boostPsi ?: -12.0) + 12.0) / 30.0).toFloat().coerceIn(0f, 1f)
    val boostLoad = ((boostPsi ?: 0.0).coerceAtLeast(0.0) / 18.0).toFloat().coerceIn(0f, 1f)
    val boostColor = animatedStatusColor(boostLoad)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardCard(Modifier.fillMaxWidth().weight(.47f), accentColor = maxColor(rpmColor, boostColor)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TachometerGauge(
                    rpm = sample?.rpm,
                    averageRpm = averageRpm,
                    maximumRpm = maximumRpm,
                    color = rpmColor,
                    modifier = Modifier.weight(.52f).fillMaxHeight(),
                )
                PrimaryMetric(
                    label = "CALCULATED\nBOOST / VACUUM",
                    value = if (units == UnitSystem.US) {
                        boostPsi?.oneDecimal() ?: "—"
                    } else {
                        boostPsi?.times(6.89476)?.oneDecimal() ?: "—"
                    },
                    unit = if (units == UnitSystem.US) "psi" else "kPa",
                    progress = boostProgress,
                    color = boostColor,
                    modifier = Modifier.weight(.48f).fillMaxHeight(),
                )
            }
        }
        DashboardCard(Modifier.fillMaxWidth().weight(.53f), accentColor = RpmBlue) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE TREND · 30 SEC", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    LegendDot(RpmBlue, "RPM")
                    Spacer(Modifier.width(10.dp))
                    LegendDot(BoostTeal, "BOOST")
                    Spacer(Modifier.width(10.dp))
                    LegendDot(ThrottleAmber, "THROTTLE")
                }
                RollingTelemetryChart(
                    samples = state.samples.takeLast(120),
                    channels = setOf("RPM", "Boost", "Throttle"),
                    inspected = inspectedSample,
                    modifier = Modifier.fillMaxWidth(),
                    chartHeight = 100.dp,
                    smoothing = state.settings.smoothing,
                    onTap = { inspectedSample = null },
                    onInspect = { inspectedSample = it },
                )
            }
        }
    }
}

@Composable
private fun SupportingPanel(
    sample: TelemetrySample?,
    units: UnitSystem,
    tripCost: Double?,
    durationSeconds: Long,
    fuelPrice: Double,
    fuelUsedLiters: Double,
    modifier: Modifier = Modifier,
) {
    val coolant = sample?.coolantC ?: 0.0
    val coolantSeverity = when {
        coolant <= 100 -> 0f
        else -> ((coolant - 100) / 15).toFloat().coerceIn(0f, 1f)
    }
    val intake = sample?.intakeC ?: 0.0
    val intakeSeverity = ((intake - 35) / 35).toFloat().coerceIn(0f, 1f)
    val voltage = sample?.voltage ?: 0.0
    val voltageSeverity = when {
        voltage == 0.0 -> 0f
        voltage < 12.5 -> ((12.5 - voltage) / 2.0).toFloat().coerceIn(0f, 1f)
        voltage > 14.9 -> ((voltage - 14.9) / .8).toFloat().coerceIn(0f, 1f)
        else -> 0f
    }
    val fuelRate = sample?.fuelRateLitersPerHour ?: 0.0
    val fuelLoad = (fuelRate / 30.0).toFloat().coerceIn(0f, 1f)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisualMetricTile(
                label = "COOLANT",
                value = sample?.coolantC?.temperature(units) ?: "—",
                progress = (coolant / 120.0).toFloat().coerceIn(0f, 1f),
                severity = coolantSeverity,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VisualMetricTile(
                label = "INTAKE AIR",
                value = sample?.intakeC?.temperature(units) ?: "—",
                progress = (intake / 80.0).toFloat().coerceIn(0f, 1f),
                severity = intakeSeverity,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisualMetricTile(
                label = "VOLTAGE",
                value = "${sample?.voltage?.oneDecimal() ?: "—"} V",
                progress = ((voltage - 9.0) / 7.0).toFloat().coerceIn(0f, 1f),
                severity = voltageSeverity,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VisualMetricTile(
                label = "FUEL RATE",
                value = sample?.fuelRateLitersPerHour?.let { "${it.oneDecimal()} L/h" } ?: "—",
                progress = fuelLoad,
                severity = fuelLoad,
                caption = if (sample?.fuelRateEstimated == true) "EST" else null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        val costProgress = (fuelUsedLiters / 11.36).toFloat().coerceIn(0f, 1f)
        val costColor = animatedStatusColor(costProgress)
        DashboardCard(Modifier.fillMaxWidth().weight(1.12f), accentColor = costColor) {
            Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 8.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TRIP COST", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            tripCost?.let { "$${it.money()}" } ?: "—",
                            fontSize = 31.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = costColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDuration(durationSeconds), fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(
                            "$${fuelPrice.money()} / gal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                StatusBar(costProgress, costColor, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PrimaryMetric(
    label: String,
    value: String,
    unit: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 42.sp,
                lineHeight = 43.sp,
                fontWeight = FontWeight.Black,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                unit,
                Modifier.padding(bottom = 5.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        StatusBar(progress, color, Modifier.fillMaxWidth())
    }
}

@Composable
private fun VisualMetricTile(
    label: String,
    value: String,
    progress: Float,
    severity: Float,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    val color = animatedStatusColor(severity)
    DashboardCard(modifier, accentColor = color) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                caption?.let {
                    Text(
                        it,
                        modifier = Modifier
                            .background(color.copy(alpha = .14f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = color,
                    )
                }
            }
            Text(
                value,
                fontSize = 23.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Black,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            StatusBar(progress, color, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MetricBarBox(
    label: String,
    value: String,
    progress: Float,
    color: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = .08f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            StatusBar(progress, color, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "metricFill",
    )
    Box(
        modifier
            .height(7.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animatedProgress)
                .height(7.dp)
                .background(
                    Brush.horizontalGradient(listOf(GoodGreen.copy(alpha = .7f), color)),
                    CircleShape,
                )
        )
    }
}

@Composable
private fun TachometerGauge(
    rpm: Double?,
    averageRpm: Double?,
    maximumRpm: Double?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = ((rpm ?: 0.0) / 7_000.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(260),
        label = "tachometerNeedle",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dialCenterColor = MaterialTheme.colorScheme.surface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "ENGINE RPM",
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 5.dp)) {
            val sweep = 270f
            val start = 135f
            val diameter = min(size.width, size.height) * .94f
            val center = Offset(size.width / 2f, size.height * .52f)
            val topLeft = Offset(center.x - diameter / 2f, center.y - diameter / 2f)
            val stroke = 6.dp.toPx()
            drawCircle(
                color = track.copy(alpha = .18f),
                radius = diameter * .43f,
                center = center,
            )
            drawArc(
                color = track,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = WarningRed.copy(alpha = .72f),
                startAngle = start + sweep * .80f,
                sweepAngle = sweep * .20f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep * progress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val outerRadius = diameter / 2f - stroke * .25f
            repeat(29) { index ->
                val angle = start + sweep * index / 28f
                val radians = angle * PI / 180.0
                val major = index % 4 == 0
                val innerRadius = outerRadius - if (major) 13.dp.toPx() else 7.dp.toPx()
                drawLine(
                    color = if (major) tickColor else tickColor.copy(alpha = .55f),
                    start = Offset(
                        center.x + cos(radians).toFloat() * innerRadius,
                        center.y + sin(radians).toFloat() * innerRadius,
                    ),
                    end = Offset(
                        center.x + cos(radians).toFloat() * outerRadius,
                        center.y + sin(radians).toFloat() * outerRadius,
                    ),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            listOf(
                averageRpm to RpmBlue,
                maximumRpm to ThrottleAmber,
            ).forEach { (markerRpm, markerColor) ->
                markerRpm ?: return@forEach
                val markerProgress = (markerRpm / 7_000.0).toFloat().coerceIn(0f, 1f)
                val markerRadians = (start + sweep * markerProgress) * PI / 180.0
                val markerInner = outerRadius - 17.dp.toPx()
                val markerOuter = outerRadius + 2.dp.toPx()
                drawLine(
                    color = markerColor,
                    start = Offset(
                        center.x + cos(markerRadians).toFloat() * markerInner,
                        center.y + sin(markerRadians).toFloat() * markerInner,
                    ),
                    end = Offset(
                        center.x + cos(markerRadians).toFloat() * markerOuter,
                        center.y + sin(markerRadians).toFloat() * markerOuter,
                    ),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val needleAngle = (start + sweep * progress) * PI / 180.0
            val needleLength = outerRadius * .72f
            val needleTail = outerRadius * .12f
            val tail = Offset(
                center.x - cos(needleAngle).toFloat() * needleTail,
                center.y - sin(needleAngle).toFloat() * needleTail,
            )
            drawLine(
                color = color,
                start = tail,
                end = Offset(
                    center.x + cos(needleAngle).toFloat() * needleLength,
                    center.y + sin(needleAngle).toFloat() * needleLength,
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(color = dialCenterColor, radius = 7.dp.toPx(), center = center)
            drawCircle(color = color, radius = 4.dp.toPx(), center = center)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(35.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                GaugeStatistic("AVG", averageRpm, RpmBlue)
                GaugeStatistic("MAX", maximumRpm, ThrottleAmber)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    rpm?.toInt()?.let { "%,d".format(it) } ?: "—",
                    fontSize = 27.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    " rpm",
                    modifier = Modifier.padding(bottom = 3.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GaugeStatistic(label: String, rpm: Double?, color: Color) {
    Text(
        "$label ${rpm?.toInt()?.let { "%,d".format(it) } ?: "—"}",
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Black,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun SegmentedBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        tween(300),
        label = "segmentedBar",
    )
    Row(modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(12) { index ->
            val active = index < (animatedProgress * 12f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (active) color else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(2.dp),
                    )
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, accentColor.copy(alpha = .14f), RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = .045f),
                            Color.Transparent,
                            Color.Transparent,
                        )
                    ),
                    RoundedCornerShape(18.dp),
                )
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor.copy(alpha = .8f))
            )
            content()
        }
    }
}

@Composable
private fun animatedStatusColor(severity: Float): Color {
    val target = statusColor(severity)
    return animateColorAsState(target, tween(320), label = "statusColor").value
}

private fun statusColor(severity: Float): Color {
    val value = severity.coerceIn(0f, 1f)
    return if (value <= .5f) {
        lerp(GoodGreen, ThrottleAmber, value * 2f)
    } else {
        lerp(ThrottleAmber, WarningRed, (value - .5f) * 2f)
    }
}

private fun maxColor(first: Color, second: Color): Color =
    if (colorHeat(first) >= colorHeat(second)) first else second

private fun colorHeat(color: Color): Float = color.red - color.green * .35f

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
private fun Double.money(): String = String.format(Locale.US, "%.2f", this)
private fun Double.temperature(units: UnitSystem): String =
    if (units == UnitSystem.US) "${(this * 9 / 5 + 32).toInt()}°F" else "${toInt()}°C"

private fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
