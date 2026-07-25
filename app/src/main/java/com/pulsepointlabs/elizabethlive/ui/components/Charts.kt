package com.pulsepointlabs.elizabethlive.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.ui.theme.BoostTeal
import com.pulsepointlabs.elizabethlive.ui.theme.RpmBlue
import com.pulsepointlabs.elizabethlive.ui.theme.ThrottleAmber
import kotlin.math.max

@Composable
fun RollingTelemetryChart(
    samples: List<TelemetrySample>,
    channels: Set<String>,
    inspected: TelemetrySample?,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 288.dp,
    onTap: () -> Unit,
    onInspect: (TelemetrySample?) -> Unit,
) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = .22f)
    val cursor = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
    val chartBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
    Box(
        modifier = modifier
            .height(chartHeight)
            .background(chartBackground, RoundedCornerShape(22.dp))
            .pointerInput(samples) {
                detectTapGestures { offset ->
                    onTap()
                    sampleAtX(samples, offset.x, size.width.toFloat())?.let(onInspect)
                }
            }
            .pointerInput(samples) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onInspect(sampleAtX(samples, offset.x, size.width.toFloat()))
                    },
                    onDragEnd = { },
                    onDrag = { change, _ ->
                        onInspect(sampleAtX(samples, change.position.x, size.width.toFloat()))
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 14.dp.toPx()
            val right = size.width - 14.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            repeat(5) { row ->
                val y = top + (bottom - top) * row / 4f
                drawLine(grid, Offset(left, y), Offset(right, y), 1.dp.toPx())
            }
            repeat(7) { column ->
                val x = left + (right - left) * column / 6f
                drawLine(grid, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            }
            if (samples.size > 1) {
                if ("RPM" in channels) drawSeries(samples, left, right, top, bottom, RpmBlue) {
                    it.rpm?.let { rpm -> (rpm / 6_500.0).toFloat() }
                }
                if ("Boost" in channels) drawSeries(samples, left, right, top, bottom, BoostTeal) {
                    it.boostPsi?.let { boost -> ((boost + 12.0) / 30.0).toFloat() }
                }
                if ("Throttle" in channels) drawSeries(samples, left, right, top, bottom, ThrottleAmber) {
                    it.throttlePercent?.let { throttle -> (throttle / 100.0).toFloat() }
                }
            }
            inspected?.let { selected ->
                val index = samples.indexOfLast { it.timestampMillis <= selected.timestampMillis }
                if (index >= 0 && samples.size > 1) {
                    val x = left + (right - left) * index / (samples.size - 1f)
                    drawLine(cursor, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx())
                    drawCircle(cursor, 4.dp.toPx(), Offset(x, top + 5.dp.toPx()))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    samples: List<TelemetrySample>,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    color: Color,
    value: (TelemetrySample) -> Float?,
) {
    val path = Path()
    var hasPreviousPoint = false
    samples.forEachIndexed { index, sample ->
        val x = left + (right - left) * index / max(1, samples.lastIndex).toFloat()
        val normalized = value(sample)?.coerceIn(0f, 1f)
        if (normalized == null) {
            hasPreviousPoint = false
            return@forEachIndexed
        }
        val y = bottom - (bottom - top) * normalized
        if (!hasPreviousPoint) path.moveTo(x, y) else path.lineTo(x, y)
        hasPreviousPoint = true
    }
    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun sampleAtX(samples: List<TelemetrySample>, x: Float, width: Float): TelemetrySample? {
    if (samples.isEmpty() || width <= 0f) return null
    val index = ((x / width).coerceIn(0f, 1f) * samples.lastIndex).toInt()
    return samples[index]
}

@Composable
fun DualTemperatureBars(
    coolantC: Double,
    intakeC: Double,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val cool = RpmBlue
    val warm = ThrottleAmber
    Canvas(modifier.height(58.dp)) {
        val radius = 7.dp.toPx()
        val barHeight = 11.dp.toPx()
        val maxC = 120f
        drawRoundRect(track, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, barHeight), androidx.compose.ui.geometry.CornerRadius(radius))
        drawRoundRect(cool, Offset.Zero, androidx.compose.ui.geometry.Size(size.width * (coolantC / maxC).toFloat().coerceIn(0f, 1f), barHeight), androidx.compose.ui.geometry.CornerRadius(radius))
        val y = 35.dp.toPx()
        drawRoundRect(track, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, barHeight), androidx.compose.ui.geometry.CornerRadius(radius))
        drawRoundRect(warm, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width * (intakeC / maxC).toFloat().coerceIn(0f, 1f), barHeight), androidx.compose.ui.geometry.CornerRadius(radius))
    }
}

@Composable
fun FuelTrimBalance(stft: Double, ltft: Double, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val center = MaterialTheme.colorScheme.outline
    Canvas(modifier.height(58.dp)) {
        val centerX = size.width / 2
        val maxTrim = 25f
        listOf(stft to RpmBlue, ltft to BoostTeal).forEachIndexed { i, (trim, color) ->
            val y = (8 + i * 34).dp.toPx()
            drawLine(track, Offset(0f, y), Offset(size.width, y), 9.dp.toPx(), StrokeCap.Round)
            drawLine(center, Offset(centerX, y - 9.dp.toPx()), Offset(centerX, y + 9.dp.toPx()), 1.dp.toPx())
            val end = centerX + centerX * (trim / maxTrim).toFloat().coerceIn(-1f, 1f)
            drawLine(color, Offset(centerX, y), Offset(end, y), 9.dp.toPx(), StrokeCap.Round)
            drawCircle(color, 5.dp.toPx(), Offset(end, y))
        }
    }
}

@Composable
fun VoltageSparkline(samples: List<TelemetrySample>, modifier: Modifier = Modifier) {
    val color = BoostTeal
    val baseline = MaterialTheme.colorScheme.outline.copy(alpha = .35f)
    Canvas(modifier.height(58.dp)) {
        drawLine(baseline, Offset(0f, size.height * .72f), Offset(size.width, size.height * .72f), 1.dp.toPx())
        if (samples.size < 2) return@Canvas
        val points = samples.takeLast(120)
        val path = Path()
        points.forEachIndexed { index, sample ->
            val x = size.width * index / max(1, points.lastIndex).toFloat()
            val voltage = sample.voltage ?: return@forEachIndexed
            val normalized = ((voltage - 9.5) / 5.5).toFloat().coerceIn(0f, 1f)
            val y = size.height - size.height * normalized
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}
