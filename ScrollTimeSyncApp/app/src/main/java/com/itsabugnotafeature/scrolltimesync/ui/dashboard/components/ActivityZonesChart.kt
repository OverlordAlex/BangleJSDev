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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity

data class ActivityZone(
    val label: String,
    val color: Color,
    val minutes: Int,
)

val RestColor = Color(0xFF9E9E9E)
val LightColor = Color(0xFF42A5F5)
val ModerateColor = Color(0xFF66BB6A)
val VigorousColor = Color(0xFFFFA726)
val PeakColor = Color(0xFFEF5350)

fun computeZoneBands(userAge: Int): List<ZoneBand> {
    val maxHr = if (userAge > 0) 220 - userAge else 0
    return if (maxHr > 0) {
        listOf(
            ZoneBand("Rest", 0f, maxHr * 0.5f, RestColor),
            ZoneBand("Light", maxHr * 0.5f, maxHr * 0.6f, LightColor),
            ZoneBand("Moderate", maxHr * 0.6f, maxHr * 0.7f, ModerateColor),
            ZoneBand("Vigorous", maxHr * 0.7f, maxHr * 0.85f, VigorousColor),
            ZoneBand("Peak", maxHr * 0.85f, maxHr.toFloat(), PeakColor),
        )
    } else {
        listOf(
            ZoneBand("Rest", 0f, 100f, RestColor),
            ZoneBand("Light", 100f, 120f, LightColor),
            ZoneBand("Moderate", 120f, 140f, ModerateColor),
            ZoneBand("Vigorous", 140f, 160f, VigorousColor),
            ZoneBand("Peak", 160f, 220f, PeakColor),
        )
    }
}

fun computeActivityZones(records: List<HealthRecordEntity>, userAge: Int): List<ActivityZone> {
    val maxHr = if (userAge > 0) 220 - userAge else 0
    val useAge = maxHr > 0

    var rest = 0; var light = 0; var moderate = 0; var vigorous = 0; var peak = 0

    for (record in records) {
        if (record.bpm <= 0 || record.isSleeping) continue
        val bpm = record.bpm
        if (useAge) {
            when {
                bpm < (maxHr * 0.5).toInt() -> rest++
                bpm < (maxHr * 0.6).toInt() -> light++
                bpm < (maxHr * 0.7).toInt() -> moderate++
                bpm < (maxHr * 0.85).toInt() -> vigorous++
                else -> peak++
            }
        } else {
            when {
                bpm < 100 -> rest++
                bpm < 120 -> light++
                bpm < 140 -> moderate++
                bpm < 160 -> vigorous++
                else -> peak++
            }
        }
    }

    return listOf(
        ActivityZone("Rest", RestColor, rest * 10),
        ActivityZone("Light", LightColor, light * 10),
        ActivityZone("Moderate", ModerateColor, moderate * 10),
        ActivityZone("Vigorous", VigorousColor, vigorous * 10),
        ActivityZone("Peak", PeakColor, peak * 10),
    )
}

@Composable
fun ActivityZonesChart(
    zones: List<ActivityZone>,
    modifier: Modifier = Modifier,
    selectedZone: String? = null,
    onZoneSelected: ((String?) -> Unit)? = null,
) {
    val totalMinutes = zones.sumOf { it.minutes }
    if (totalMinutes == 0) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val strokeWidth = 24.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f,
                )
                var startAngle = -90f
                for (zone in zones) {
                    if (zone.minutes <= 0) continue
                    val sweep = 360f * zone.minutes / totalMinutes
                    val alpha = when {
                        selectedZone == null -> 1f
                        selectedZone == zone.label -> 1f
                        else -> 0.3f
                    }
                    drawArc(
                        color = zone.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth),
                        alpha = alpha,
                    )
                    startAngle += sweep
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (zone in zones) {
                if (zone.minutes <= 0) continue
                val isSelected = selectedZone == zone.label
                val alpha = when {
                    selectedZone == null -> 1f
                    isSelected -> 1f
                    else -> 0.4f
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (onZoneSelected != null) {
                        Modifier.clickable {
                            onZoneSelected(if (isSelected) null else zone.label)
                        }
                    } else Modifier,
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = zone.color, alpha = alpha)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${zone.label}: ${formatZoneTime(zone.minutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}

private fun formatZoneTime(minutes: Int): String {
    return if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}
