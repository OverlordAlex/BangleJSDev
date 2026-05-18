package com.itsabugnotafeature.scrolltimesync.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
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
import kotlin.math.roundToInt

data class ZoneBand(
    val label: String,
    val minBpm: Float,
    val maxBpm: Float,
    val color: Color,
)

private fun forwardFillZeros(values: List<Float>): List<Float> {
    val result = values.toMutableList()
    var lastNonZero = result.firstOrNull { it > 0f } ?: return result
    for (i in result.indices) {
        if (result[i] <= 0f) {
            result[i] = lastNonZero
        } else {
            lastNonZero = result[i]
        }
    }
    return result
}

@Composable
fun SparklineChart(
    records: List<HealthRecordEntity>,
    valueSelector: (HealthRecordEntity) -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    showAxes: Boolean = true,
    fillZeros: Boolean = false,
    zoneBands: List<ZoneBand> = emptyList(),
    highlightedZone: String? = null,
) {
    if (records.isEmpty()) return

    val rawValues = records.map(valueSelector)
    val values = if (fillZeros) forwardFillZeros(rawValues) else rawValues
    val nonZeroValues = values.filter { it > 0f }
    if (nonZeroValues.isEmpty()) return

    val rawMin = nonZeroValues.min()
    val rawMax = nonZeroValues.max()
    val avg = nonZeroValues.average().toFloat()

    val (niceMin, niceMax) = niceRange(rawMin, rawMax)

    if (showAxes) {
        FullChart(
            records = records,
            values = values,
            niceMin = niceMin,
            niceMax = niceMax,
            avg = avg,
            color = color,
            zoneBands = zoneBands,
            highlightedZone = highlightedZone,
            modifier = modifier,
        )
    } else {
        SimpleSparkline(
            values = values,
            rangeMin = niceMin,
            rangeMax = niceMax,
            color = color,
            modifier = modifier,
        )
    }
}

private fun niceRange(min: Float, max: Float): Pair<Float, Float> {
    if (max - min < 1f) {
        val mid = (min + max) / 2f
        return (mid - 5f) to (mid + 5f)
    }
    val padding = (max - min) * 0.15f
    val step = niceStep((max - min + padding * 2))
    val lo = floor((min - padding) / step) * step
    val hi = ceil((max + padding) / step) * step
    return lo.coerceAtLeast(0f) to hi
}

private fun niceStep(range: Float): Float {
    val candidates = floatArrayOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f, 200f, 500f, 1000f)
    val target = range / 4f
    return candidates.minByOrNull { kotlin.math.abs(it - target) } ?: 10f
}

@Composable
private fun FullChart(
    records: List<HealthRecordEntity>,
    values: List<Float>,
    niceMin: Float,
    niceMax: Float,
    avg: Float,
    color: Color,
    zoneBands: List<ZoneBand> = emptyList(),
    highlightedZone: String? = null,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val avgColor = MaterialTheme.colorScheme.outline

    val zone = ZoneId.systemDefault()
    val hasTimestamps = records.first().timestamp > 0

    val yStep = niceStep(niceMax - niceMin)
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

    val yLabelWidth = 32.dp
    val xLabelHeight = 14.dp

    Column(modifier = modifier) {
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

                for (band in zoneBands) {
                    val bandTop = h * (1f - (band.maxBpm.coerceIn(niceMin, niceMax) - niceMin) / range)
                    val bandBottom = h * (1f - (band.minBpm.coerceIn(niceMin, niceMax) - niceMin) / range)
                    val alpha = when {
                        highlightedZone == null -> 0.12f
                        highlightedZone == band.label -> 0.3f
                        else -> 0.04f
                    }
                    if (bandBottom > bandTop) {
                        drawRect(
                            color = band.color,
                            topLeft = Offset(0f, bandTop),
                            size = Size(w, bandBottom - bandTop),
                            alpha = alpha,
                        )
                    }
                }

                for (tick in yTicks) {
                    val y = h * (1f - (tick - niceMin) / range)
                    drawLine(
                        color = surfaceColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = if (values.size > 1) index * w / (values.size - 1) else w / 2f
                    val clamped = value.coerceIn(niceMin, niceMax)
                    val y = h * (1f - (clamped - niceMin) / range)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.5f))

                val avgY = h * (1f - (avg.coerceIn(niceMin, niceMax) - niceMin) / range)
                drawLine(
                    color = avgColor,
                    start = Offset(0f, avgY),
                    end = Offset(w, avgY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }

            val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
            Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
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

                val avgLabel = "avg ${if (avg == avg.toLong().toFloat()) avg.toLong().toString() else "%.1f".format(avg)}"
                val avgMeasured = textMeasurer.measure(avgLabel, labelStyle.copy(color = avgColor))
                val avgY = chartH * (1f - (avg.coerceIn(niceMin, niceMax) - niceMin) / range)
                drawText(
                    avgMeasured,
                    topLeft = Offset(
                        chartLeft + chartW - avgMeasured.size.width,
                        avgY - avgMeasured.size.height - 2.dp.toPx(),
                    ),
                )

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

@Composable
private fun SimpleSparkline(
    values: List<Float>,
    rangeMin: Float,
    rangeMax: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val range = (rangeMax - rangeMin).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 4f
        val usableHeight = height - padding * 2
        val stepX = if (values.size > 1) width / (values.size - 1) else width

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val clamped = value.coerceIn(rangeMin, rangeMax)
            val y = padding + usableHeight * (1f - (clamped - rangeMin) / range)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color, style = Stroke(width = 2.5f))

        if (values.isNotEmpty()) {
            val lastX = (values.size - 1) * stepX
            val lastClamped = values.last().coerceIn(rangeMin, rangeMax)
            val lastY = padding + usableHeight * (1f - (lastClamped - rangeMin) / range)
            drawCircle(color, radius = 4f, center = Offset(lastX, lastY))
        }
    }
}
