package com.itsabugnotafeature.scrolltimesync.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.ui.theme.CaloriesOrange
import com.itsabugnotafeature.scrolltimesync.ui.theme.HeartRed
import com.itsabugnotafeature.scrolltimesync.ui.theme.SleepIndigo
import com.itsabugnotafeature.scrolltimesync.ui.theme.StepsGreen

private val SedentaryColor = Color(0xFFE0E0E0)
private val LightActivityColor = Color(0xFFA5D6A7)
private val ModerateActivityColor = Color(0xFFFFF176)
private val VigorousActivityColor = Color(0xFFFFB74D)
private val PeakActivityColor = Color(0xFFEF5350)
private val SleepColor = Color(0xFF3949AB)

private enum class ActivityState { SLEEP, SEDENTARY, LIGHT, MODERATE, VIGOROUS, PEAK }

private fun classifySlot(record: HealthRecordEntity): ActivityState {
    if (record.isSleeping) return ActivityState.SLEEP
    val stepsPerSlot = record.steps
    val bpm = record.bpm
    return when {
        stepsPerSlot > 200 || bpm > 150 -> ActivityState.PEAK
        stepsPerSlot > 120 || bpm > 130 -> ActivityState.VIGOROUS
        stepsPerSlot > 60 || bpm > 110 -> ActivityState.MODERATE
        stepsPerSlot > 15 || bpm > 85 -> ActivityState.LIGHT
        else -> ActivityState.SEDENTARY
    }
}

private fun activityColor(state: ActivityState): Color = when (state) {
    ActivityState.SLEEP -> SleepColor
    ActivityState.SEDENTARY -> SedentaryColor
    ActivityState.LIGHT -> LightActivityColor
    ActivityState.MODERATE -> ModerateActivityColor
    ActivityState.VIGOROUS -> VigorousActivityColor
    ActivityState.PEAK -> PeakActivityColor
}

data class GlanceSummary(
    val totalSleepMinutes: Int,
    val totalSteps: Int,
    val avgBpm: Int,
    val totalCalories: Int,
)

@Composable
fun DayAtGlanceCard(
    records: List<HealthRecordEntity>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) return

    val summary = remember(records) {
        val sleepCount = records.count { it.isSleeping }
        val bpmValues = records.map { it.bpm }.filter { it > 0 }
        GlanceSummary(
            totalSleepMinutes = sleepCount * 10,
            totalSteps = records.sumOf { it.steps },
            avgBpm = if (bpmValues.isNotEmpty()) bpmValues.average().toInt() else 0,
            totalCalories = (records.sumOf { it.steps } * 0.04f).toInt(),
        )
    }

    val states = remember(records) { records.map { classifySlot(it) } }

    val hrColor = HeartRed

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Yesterday at a Glance",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                val w = size.width
                val h = size.height
                val slotCount = states.size
                if (slotCount == 0) return@Canvas

                val slotWidth = w / slotCount
                val activityBarHeight = h * 0.5f
                val stepBarMaxHeight = h * 0.2f

                states.forEachIndexed { index, state ->
                    val x = index * slotWidth
                    drawRect(
                        color = activityColor(state),
                        topLeft = Offset(x, 0f),
                        size = Size(slotWidth + 0.5f, activityBarHeight),
                    )
                }

                val maxSteps = records.maxOf { it.steps }.coerceAtLeast(1)
                records.forEachIndexed { index, record ->
                    val x = index * slotWidth
                    val barH = (record.steps.toFloat() / maxSteps) * stepBarMaxHeight
                    drawRect(
                        color = StepsGreen,
                        topLeft = Offset(x, h - barH),
                        size = Size(slotWidth + 0.5f, barH),
                        alpha = 0.6f,
                    )
                }

                val bpmValues = records.map { it.bpm }
                val bpmNonZero = bpmValues.filter { it > 0 }
                if (bpmNonZero.isNotEmpty()) {
                    val bpmMin = bpmNonZero.min().toFloat()
                    val bpmMax = bpmNonZero.max().toFloat()
                    val bpmRange = (bpmMax - bpmMin).coerceAtLeast(1f)
                    val hrTop = activityBarHeight + 4f
                    val hrBottom = h - stepBarMaxHeight - 4f
                    val hrHeight = (hrBottom - hrTop).coerceAtLeast(1f)

                    val path = Path()
                    var started = false
                    var lastBpm = 0
                    bpmValues.forEachIndexed { index, bpm ->
                        val effectiveBpm = if (bpm > 0) bpm else lastBpm
                        if (effectiveBpm <= 0) return@forEachIndexed
                        lastBpm = effectiveBpm
                        val x = index * slotWidth + slotWidth / 2f
                        val y = hrTop + hrHeight * (1f - (effectiveBpm - bpmMin) / bpmRange)
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    if (started) {
                        drawPath(path, hrColor, style = Stroke(width = 2f))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeLabel("00:00")
                TimeLabel("06:00")
                TimeLabel("12:00")
                TimeLabel("18:00")
                TimeLabel("00:00")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryChip(
                    label = "Sleep",
                    value = "${summary.totalSleepMinutes / 60}h ${summary.totalSleepMinutes % 60}m",
                    color = SleepIndigo,
                )
                SummaryChip(
                    label = "Steps",
                    value = "%,d".format(summary.totalSteps),
                    color = StepsGreen,
                )
                SummaryChip(
                    label = "Avg HR",
                    value = "${summary.avgBpm}",
                    color = HeartRed,
                )
                SummaryChip(
                    label = "Cal",
                    value = "%,d".format(summary.totalCalories),
                    color = CaloriesOrange,
                )
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
