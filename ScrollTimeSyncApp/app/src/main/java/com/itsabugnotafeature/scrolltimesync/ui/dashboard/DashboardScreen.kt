package com.itsabugnotafeature.scrolltimesync.ui.dashboard

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itsabugnotafeature.scrolltimesync.sync.SyncScheduler
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.ActivityZonesChart
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.ChartLine
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.DayAtGlanceCard
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.HealthCard
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.MultiLineChart
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.SleepTimelineBar
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.SyncStatusBar
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.computeActivityZones
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklyInsight
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklySummaryEntity
import com.itsabugnotafeature.scrolltimesync.ui.theme.CaloriesOrange
import com.itsabugnotafeature.scrolltimesync.ui.theme.HeartRed
import com.itsabugnotafeature.scrolltimesync.ui.theme.InsightNegative
import com.itsabugnotafeature.scrolltimesync.ui.theme.InsightNeutral
import com.itsabugnotafeature.scrolltimesync.ui.theme.InsightPositive
import com.itsabugnotafeature.scrolltimesync.ui.theme.SleepIndigo
import com.itsabugnotafeature.scrolltimesync.ui.theme.StepsGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWeeklySummary: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
) {
    val latestSync by viewModel.latestSync.collectAsState()
    val dashboardState by viewModel.dashboardState.collectAsState()
    val recentRecordsDesc by viewModel.recentRecords.collectAsState()
    val recentRecords = remember(recentRecordsDesc) { recentRecordsDesc.reversed() }
    val weeklySummary by viewModel.latestWeeklySummary.collectAsState()
    val context = LocalContext.current
    var expandedCard by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScrollTimeSync") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SyncStatusBar(
                    syncEntry = latestSync,
                    onSyncClick = { triggerManualSync(context) },
                )
            }

            if (recentRecords.isNotEmpty()) {
                item {
                    DayAtGlanceCard(records = recentRecords)
                }
            }

            weeklySummary?.let { summary ->
                val thisWeekStart = java.time.LocalDate.now().minusDays(7)
                val summaryStart = java.time.LocalDate.parse(summary.weekStartDate)
                if (summaryStart.isAfter(thisWeekStart.minusDays(1))) {
                    item {
                        WeeklySummaryCard(
                            summary = summary,
                            onExploreClick = onNavigateToWeeklySummary,
                        )
                    }
                }
            }

            item {
                HealthCard(
                    title = "Heart Rate",
                    icon = Icons.Default.MonitorHeart,
                    accentColor = HeartRed,
                    insight = dashboardState.heartRateInsight,
                    records = recentRecords,
                    chartValueSelector = { it.bpm.toFloat() },
                    onExploreClick = { onNavigateToDetail("heartrate") },
                    expanded = expandedCard == "heartrate",
                    onToggleExpand = { expandedCard = if (expandedCard == "heartrate") null else "heartrate" },
                    customExpandedContent = {
                        val zones = remember(recentRecords, viewModel.userAge) {
                            computeActivityZones(recentRecords, viewModel.userAge)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActivityZonesChart(zones = zones)
                    },
                )
            }

            item {
                HealthCard(
                    title = "Steps",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    accentColor = StepsGreen,
                    insight = dashboardState.stepsInsight,
                    records = recentRecords,
                    chartValueSelector = { it.steps.toFloat() },
                    onExploreClick = { onNavigateToDetail("steps") },
                    expanded = expandedCard == "steps",
                    onToggleExpand = { expandedCard = if (expandedCard == "steps") null else "steps" },
                )
            }

            item {
                HealthCard(
                    title = "Sleep",
                    icon = Icons.Default.Bedtime,
                    accentColor = SleepIndigo,
                    insight = dashboardState.sleepInsight,
                    records = recentRecords,
                    chartValueSelector = { if (it.isSleeping) 1f else 0f },
                    onExploreClick = { onNavigateToDetail("sleep") },
                    expanded = expandedCard == "sleep",
                    onToggleExpand = { expandedCard = if (expandedCard == "sleep") null else "sleep" },
                    customExpandedContent = {
                        SleepTimelineBar(
                            records = recentRecords,
                            sleepColor = SleepIndigo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }

            item {
                val breakdown = dashboardState.calorieBreakdown
                val age = viewModel.userAge
                val weight = viewModel.userWeightKg

                HealthCard(
                    title = "Calories",
                    icon = Icons.Default.LocalFireDepartment,
                    accentColor = CaloriesOrange,
                    insight = dashboardState.caloriesInsight,
                    records = recentRecords,
                    chartValueSelector = { it.steps.toFloat() * 0.04f },
                    onExploreClick = { onNavigateToDetail("calories") },
                    expanded = expandedCard == "calories",
                    onToggleExpand = { expandedCard = if (expandedCard == "calories") null else "calories" },
                    customExpandedContent = {
                        CaloriesExpandedContent(
                            breakdown = breakdown,
                            records = recentRecords,
                            userAge = age,
                            userWeightKg = weight,
                        )
                    },
                )
            }
        }
    }
}

private val StepCalColor = Color(0xFF66BB6A)
private val HrCalColor = Color(0xFFEF5350)
private val EstCalColor = Color(0xFFFFA726)
private val TotalCalColor = Color(0xFFAB47BC)

@Composable
private fun CaloriesExpandedContent(
    breakdown: CalorieBreakdown,
    records: List<com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity>,
    userAge: Int,
    userWeightKg: Float,
) {
    Column {
        if (breakdown.activeCalories != null || breakdown.bmrCalories != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (breakdown.bmrCalories != null) {
                    Column {
                        Text(
                            "%,d".format(breakdown.bmrCalories),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "BMR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (breakdown.activeCalories != null) {
                    Column {
                        Text(
                            "%,d".format(breakdown.activeCalories),
                            style = MaterialTheme.typography.titleMedium,
                            color = EstCalColor,
                        )
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column {
                    Text(
                        "%,d".format((breakdown.bmrCalories ?: 0) + (breakdown.activeCalories ?: 0)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CalorieLegendItem("Step", breakdown.stepCalories, StepCalColor)
            CalorieLegendItem("HR", breakdown.hrCalories, HrCalColor)
            if (breakdown.activeCalories != null) {
                CalorieLegendItem("Active", breakdown.activeCalories, EstCalColor)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val hasProfile = userAge > 0 && userWeightKg > 0f
        val chartLines = remember(hasProfile, userAge, userWeightKg) {
            val lines = mutableListOf(
                ChartLine("Step cal", StepCalColor) { r ->
                    r.steps * 0.04f
                },
                ChartLine("HR cal", HrCalColor) { r ->
                    if (r.bpm > 0) (r.bpm * 0.6f * (10f / 60f)) else 0f
                },
            )
            if (hasProfile) {
                val bmrPerSlot = ((10f * userWeightKg + 984.5f - 5f * userAge) / 144f)
                    .coerceAtLeast(0f)
                lines.add(
                    ChartLine("Active", EstCalColor) { r ->
                        if (!r.isSleeping && r.bpm > 72) {
                            val excessHr = r.bpm - 72
                            excessHr * userWeightKg * 0.001f * 10f
                        } else 0f
                    },
                )
                lines.add(
                    ChartLine("Total", TotalCalColor) { r ->
                        val active = if (!r.isSleeping && r.bpm > 72) {
                            val excessHr = r.bpm - 72
                            excessHr * userWeightKg * 0.001f * 10f
                        } else 0f
                        bmrPerSlot + active
                    },
                )
            }
            lines
        }

        if (records.isNotEmpty()) {
            MultiLineChart(
                records = records,
                lines = chartLines,
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }
    }
}

@Composable
private fun CalorieLegendItem(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "$label: %,d".format(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklySummaryCard(
    summary: WeeklySummaryEntity,
    onExploreClick: () -> Unit,
) {
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
    val startDate = java.time.LocalDate.parse(summary.weekStartDate)
    val endDate = java.time.LocalDate.parse(summary.weekEndDate)
    val dateRange = "${startDate.format(dateFormatter)} – ${endDate.format(dateFormatter)}"

    val trendStr = when {
        summary.restingBpmTrend < -1 -> "down ${(-summary.restingBpmTrend).toInt()}"
        summary.restingBpmTrend > 1 -> "up ${summary.restingBpmTrend.toInt()}"
        else -> "steady"
    }
    val avgSleepH = summary.avgSleepMinutes / 60
    val avgSleepM = summary.avgSleepMinutes % 60

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weekly Summary", style = MaterialTheme.typography.titleMedium)
            Text(dateRange, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Resting HR: ${summary.avgRestingBpm.toInt()} avg ($trendStr)")
            Text("Steps: %,d".format(summary.totalSteps))
            Text("Sleep: ${avgSleepH}h ${avgSleepM}m avg")
            Text("Active calories: %,d kcal".format(summary.totalActiveCalories))

            val insightsList = WeeklyInsight.listFromJson(summary.insights)
            val topInsights = insightsList
                .sortedBy { it.direction }
                .take(3)
            if (topInsights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                topInsights.forEach { insight ->
                    InsightRow(insight)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(onClick = onExploreClick) {
                Text("View History")
            }
        }
    }
}

@Composable
private fun InsightRow(insight: WeeklyInsight) {
    val indicatorColor = when (insight.direction) {
        -1 -> InsightNegative
        1 -> InsightPositive
        else -> InsightNeutral
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(indicatorColor)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = insight.displayText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun triggerManualSync(context: Context) {
    val prefs = context.getSharedPreferences("scrolltimesync", Context.MODE_PRIVATE)
    val address = prefs.getString("device_address", null) ?: return
    SyncScheduler.triggerManualSync(context, address)
}
