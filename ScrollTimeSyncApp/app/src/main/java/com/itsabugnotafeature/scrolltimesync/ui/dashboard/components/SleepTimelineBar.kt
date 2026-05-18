package com.itsabugnotafeature.scrolltimesync.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SleepTimelineBar(
    records: List<HealthRecordEntity>,
    sleepColor: Color,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) return

    val zone = ZoneId.systemDefault()
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val sleepSummary = remember(records) {
        computeSleepSummary(records, zone)
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        ) {
            val w = size.width
            val h = size.height
            val barTop = 0f
            val barHeight = h * 0.6f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, barTop),
                size = Size(w, barHeight),
                cornerRadius = CornerRadius(4f),
            )

            if (records.isNotEmpty()) {
                val firstTs = records.first().timestamp
                val lastTs = records.last().timestamp
                val span = (lastTs - firstTs).coerceAtLeast(1)

                records.forEachIndexed { i, record ->
                    if (record.isSleeping) {
                        val startFrac = (record.timestamp - firstTs).toFloat() / span
                        val endTs = if (i + 1 < records.size) records[i + 1].timestamp
                        else record.timestamp + 600
                        val endFrac = ((endTs - firstTs).toFloat() / span).coerceAtMost(1f)

                        val x = startFrac * w
                        val blockW = ((endFrac - startFrac) * w).coerceAtLeast(2f)

                        drawRoundRect(
                            color = sleepColor,
                            topLeft = Offset(x, barTop),
                            size = Size(blockW, barHeight),
                            cornerRadius = CornerRadius(2f),
                        )
                    }
                }
            }

            val labelStyle = TextStyle(fontSize = 9.sp, color = axisColor)
            val tickY = barTop + barHeight + 2.dp.toPx()
            for (hour in listOf(0, 6, 12, 18, 24)) {
                if (records.isEmpty()) break
                val firstTs = records.first().timestamp
                val lastTs = records.last().timestamp
                val span = (lastTs - firstTs).coerceAtLeast(1)

                val firstZoned = Instant.ofEpochSecond(firstTs).atZone(zone)
                val dayStart = firstZoned.toLocalDate().atStartOfDay(zone)
                val tickEpoch = dayStart.plusHours(hour.toLong()).toEpochSecond()
                val frac = (tickEpoch - firstTs).toFloat() / span

                if (frac in 0.02f..0.98f) {
                    val x = frac * w
                    val label = if (hour == 24) "0:00" else "$hour:00"
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        measured,
                        topLeft = Offset(x - measured.size.width / 2f, tickY),
                    )
                }
            }
        }

        if (sleepSummary != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sleepSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun computeSleepSummary(records: List<HealthRecordEntity>, zone: ZoneId): String? {
    if (records.none { it.isSleeping }) return null

    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    var longestStart = -1
    var longestLen = 0
    var currentStart = -1
    var currentLen = 0

    records.forEachIndexed { i, record ->
        if (record.isSleeping) {
            if (currentStart == -1) currentStart = i
            currentLen++
        } else {
            if (currentLen > longestLen) {
                longestStart = currentStart
                longestLen = currentLen
            }
            currentStart = -1
            currentLen = 0
        }
    }
    if (currentLen > longestLen) {
        longestStart = currentStart
        longestLen = currentLen
    }

    if (longestStart < 0) return null

    // Each record timestamp is the END of a 10-minute period
    // So a sleep record at 6:30 means sleeping during 6:20-6:30
    // Bedtime: start of first sleep period (timestamp - 600)
    // Waketime: end of last sleep period (timestamp itself, conservatively they woke ~9 min earlier)
    val bedtime = Instant.ofEpochSecond(records[longestStart].timestamp - 600)
        .atZone(zone).format(formatter)
    val endIdx = (longestStart + longestLen - 1).coerceAtMost(records.lastIndex)
    val waketime = Instant.ofEpochSecond(records[endIdx].timestamp)
        .atZone(zone).format(formatter)
    val totalMin = longestLen * 10
    val hours = totalMin / 60
    val mins = totalMin % 60

    return "$bedtime — $waketime (${hours}h ${mins}m)"
}
