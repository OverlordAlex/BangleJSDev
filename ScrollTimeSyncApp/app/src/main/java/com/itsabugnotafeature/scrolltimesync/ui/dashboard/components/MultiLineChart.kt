package com.itsabugnotafeature.scrolltimesync.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

data class ChartLine(
    val label: String,
    val color: Color,
    val valueSelector: (HealthRecordEntity) -> Float,
)

@Composable
fun MultiLineChart(
    records: List<HealthRecordEntity>,
    lines: List<ChartLine>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    interactiveLegend: Boolean = true,
    lineThickness: Float = 2f,
) {
    if (records.isEmpty() || lines.isEmpty()) return

    val hiddenLabels = remember { mutableStateListOf<String>() }

    val allValues = lines.flatMap { line -> records.map { line.valueSelector(it) } }
    val nonZero = allValues.filter { it > 0f }
    if (nonZero.isEmpty()) return

    val rawMin = nonZero.min()
    val rawMax = nonZero.max()
    val (niceMin, niceMax) = niceRangeMulti(rawMin, rawMax)

    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val zone = ZoneId.systemDefault()
    val hasTimestamps = records.first().timestamp > 0

    val yStep = niceStepMulti(niceMax - niceMin)
    val yTicks = remember(niceMin, niceMax, yStep) {
        val ticks = mutableListOf<Float>()
        var v = ceil(niceMin / yStep) * yStep
        while (v <= niceMax) {
            ticks.add(v)
            v += yStep
        }
        ticks
    }

    val xLabels = remember(records.firstOrNull()?.timestamp, records.lastOrNull()?.timestamp, hasTimestamps) {
        if (!hasTimestamps || records.size < 2) return@remember emptyList<Pair<Float, String>>()

        val firstTs = records.first().timestamp
        val lastTs = records.last().timestamp
        val span = lastTs - firstTs
        if (span <= 0) return@remember emptyList<Pair<Float, String>>()

        val labels = mutableListOf<Pair<Float, String>>()
        val firstZoned = Instant.ofEpochSecond(firstTs).atZone(zone)

        if (span < 172800) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val hourStep = when {
                span > 72000 -> 4
                span > 36000 -> 2
                else -> 1
            }
            var nextHour = firstZoned.withMinute(0).withSecond(0).withNano(0)
            if (nextHour.toEpochSecond() <= firstTs) nextHour = nextHour.plusHours(1)
            val startHour = nextHour.hour
            if (startHour % hourStep != 0) {
                nextHour = nextHour.plusHours((hourStep - startHour % hourStep).toLong())
            }
            while (nextHour.toEpochSecond() < lastTs) {
                val frac = (nextHour.toEpochSecond() - firstTs).toFloat() / span
                labels.add(frac to nextHour.format(formatter))
                nextHour = nextHour.plusHours(hourStep.toLong())
            }
        } else {
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            val dayStep = when {
                span > 2592000 -> 7
                span > 1209600 -> 3
                else -> 1
            }
            var nextDay = firstZoned.toLocalDate().plusDays(1).atStartOfDay(zone)
            while (nextDay.toEpochSecond() < lastTs) {
                val dayOfMonth = nextDay.dayOfMonth
                if (dayStep == 1 || dayOfMonth % dayStep == 1) {
                    val frac = (nextDay.toEpochSecond() - firstTs).toFloat() / span
                    labels.add(frac to nextDay.format(formatter))
                }
                nextDay = nextDay.plusDays(1)
            }
        }
        labels
    }

    val yLabelWidth = 36.dp
    val xLabelHeight = 14.dp

    Column(modifier = modifier) {
        if (showLegend) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                lines.forEach { line ->
                    val isHidden = line.label in hiddenLabels
                    val alpha = if (isHidden) 0.4f else 1f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (interactiveLegend) {
                            Modifier.clickable {
                                if (isHidden) hiddenLabels.remove(line.label)
                                else hiddenLabels.add(line.label)
                            }
                        } else Modifier,
                    ) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(line.color, alpha = alpha)
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = line.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = yLabelWidth, bottom = xLabelHeight),
            ) {
                val w = size.width
                val h = size.height
                val range = (niceMax - niceMin).coerceAtLeast(1f)

                for (tick in yTicks) {
                    val y = h * (1f - (tick - niceMin) / range)
                    drawLine(
                        color = surfaceColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                for (line in lines) {
                    if (line.label in hiddenLabels) continue
                    val values = records.map { line.valueSelector(it) }
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = if (values.size > 1) index * w / (values.size - 1) else w / 2f
                        val clamped = value.coerceIn(niceMin, niceMax)
                        val y = h * (1f - (clamped - niceMin) / range)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, line.color, style = Stroke(width = lineThickness))
                }
            }

            val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartLeft = yLabelWidth.toPx()
                val chartBottom = xLabelHeight.toPx()
                val chartH = size.height - chartBottom
                val chartW = size.width - chartLeft
                val range = (niceMax - niceMin).coerceAtLeast(1f)

                for (tick in yTicks) {
                    val y = chartH * (1f - (tick - niceMin) / range)
                    val label = if (tick == tick.toLong().toFloat()) tick.toLong().toString()
                    else "%.1f".format(tick)
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        measured,
                        topLeft = Offset(
                            chartLeft - measured.size.width - 4.dp.toPx(),
                            y - measured.size.height / 2f,
                        ),
                    )
                }

                for ((frac, label) in xLabels) {
                    val x = chartLeft + frac * chartW
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        measured,
                        topLeft = Offset(
                            x - measured.size.width / 2f,
                            size.height - measured.size.height,
                        ),
                    )
                }
            }
        }
    }
}

private fun niceRangeMulti(min: Float, max: Float): Pair<Float, Float> {
    if (max - min < 1f) {
        val mid = (min + max) / 2f
        return (mid - 5f) to (mid + 5f)
    }
    val padding = (max - min) * 0.15f
    val step = niceStepMulti(max - min + padding * 2)
    val lo = floor((min - padding) / step) * step
    val hi = ceil((max + padding) / step) * step
    return lo.coerceAtLeast(0f) to hi
}

private fun niceStepMulti(range: Float): Float {
    val candidates = floatArrayOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f, 200f, 500f, 1000f)
    val target = range / 4f
    return candidates.minByOrNull { kotlin.math.abs(it - target) } ?: 10f
}
