package com.itsabugnotafeature.scrolltimesync.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.ActivityZonesChart
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.ChartLine
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.MultiLineChart
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.SleepTimelineBar
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.SparklineChart
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.computeActivityZones
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.computeZoneBands
import com.itsabugnotafeature.scrolltimesync.ui.theme.CaloriesOrange
import com.itsabugnotafeature.scrolltimesync.ui.theme.HeartRed
import com.itsabugnotafeature.scrolltimesync.ui.theme.SleepIndigo
import com.itsabugnotafeature.scrolltimesync.ui.theme.StepsGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    dataType: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(dataType) {
        viewModel.loadPreset(dataType, TimePreset.WEEK)
    }

    val title = when (dataType) {
        "heartrate" -> "Heart Rate"
        "steps" -> "Steps"
        "sleep" -> "Sleep"
        "calories" -> "Calories"
        else -> "Detail"
    }

    val accentColor = when (dataType) {
        "heartrate" -> HeartRed
        "steps" -> StepsGreen
        "sleep" -> SleepIndigo
        "calories" -> CaloriesOrange
        else -> MaterialTheme.colorScheme.primary
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                showDatePicker = false
                viewModel.loadRange(dataType, start, end)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick date range")
                    }
                },
            )
        },
    ) { innerPadding ->
        var swipeAccumulator by remember { mutableFloatStateOf(0f) }
        val swipeThreshold = 150f

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(state.preset) {
                    if (state.preset == TimePreset.DAY) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipeAccumulator = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                swipeAccumulator += dragAmount
                            },
                            onDragEnd = {
                                if (swipeAccumulator > swipeThreshold) {
                                    viewModel.navigateDay(-1)
                                } else if (swipeAccumulator < -swipeThreshold) {
                                    viewModel.navigateDay(1)
                                }
                                swipeAccumulator = 0f
                            },
                            onDragCancel = { swipeAccumulator = 0f },
                        )
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                TimeRangeSelector(
                    selected = state.preset,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    dayLabel = state.dayLabel,
                    canGoForward = state.canGoForward,
                    onPreset = { viewModel.loadPreset(dataType, it) },
                    onCustom = { showDatePicker = true },
                    onNavigateDay = { viewModel.navigateDay(it) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.useRecords) {
                if (state.records.isNotEmpty()) {
                    item {
                        when (dataType) {
                            "calories" -> CaloriesMultiLineSection(
                                records = state.records,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 16.dp),
                            )
                            "sleep" -> SleepTimelineBar(
                                records = state.records,
                                sleepColor = accentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                            "heartrate" -> {
                                val context = LocalContext.current
                                val prefs = context.getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE)
                                val age = prefs.getInt("user_age", 0)
                                val bands = remember(age) { computeZoneBands(age) }
                                var selectedZone by rememberSaveable { mutableStateOf<String?>(null) }

                                SparklineChart(
                                    records = state.records,
                                    valueSelector = recordValueSelector(dataType),
                                    color = accentColor,
                                    fillZeros = true,
                                    zoneBands = bands,
                                    highlightedZone = selectedZone,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(horizontal = 16.dp),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val zones = remember(state.records, age) {
                                    computeActivityZones(state.records, age)
                                }
                                ActivityZonesChart(
                                    zones = zones,
                                    selectedZone = selectedZone,
                                    onZoneSelected = { selectedZone = it },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            else -> SparklineChart(
                                records = state.records,
                                valueSelector = recordValueSelector(dataType),
                                color = accentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }

                    item {
                        Text(
                            "${state.records.size} readings",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    items(state.records.reversed()) { record ->
                        RecordRow(record = record, dataType = dataType)
                    }
                } else {
                    item { EmptyState("No readings for this period") }
                }
            } else {
                if (state.summaries.isNotEmpty()) {
                    item {
                        when (dataType) {
                            "heartrate" -> HeartRateBandChart(
                                summaries = state.summaries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 16.dp),
                            )
                            "calories" -> CaloriesSummaryMultiChart(
                                summaries = state.summaries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 16.dp),
                            )
                            else -> SummaryChart(
                                summaries = state.summaries,
                                dataType = dataType,
                                color = accentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }

                    item {
                        Text(
                            "${state.summaries.size} days",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    items(state.summaries.reversed()) { summary ->
                        SummaryRow(
                            summary = summary,
                            dataType = dataType,
                            onDayClick = {
                                val date = LocalDate.parse(summary.date)
                                viewModel.loadRange(dataType, date, date, TimePreset.DAY)
                            },
                        )
                    }
                } else {
                    item { EmptyState("No data for this period") }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

private fun recordValueSelector(dataType: String): (HealthRecordEntity) -> Float = when (dataType) {
    "heartrate" -> { r -> r.bpm.toFloat() }
    "steps" -> { r -> r.steps.toFloat() }
    "sleep" -> { r -> if (r.isSleeping) 1f else 0f }
    "calories" -> { r -> r.steps * 0.04f }
    else -> { r -> r.bpm.toFloat() }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeRangeSelector(
    selected: TimePreset,
    startDate: LocalDate,
    endDate: LocalDate,
    dayLabel: String,
    canGoForward: Boolean,
    onPreset: (TimePreset) -> Unit,
    onCustom: () -> Unit,
    onNavigateDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(TimePreset.DAY, TimePreset.WEEK, TimePreset.MONTH).forEach { preset ->
                FilterChip(
                    selected = selected == preset,
                    onClick = { onPreset(preset) },
                    label = {
                        Text(
                            when (preset) {
                                TimePreset.DAY -> "Day"
                                TimePreset.WEEK -> "Week"
                                TimePreset.MONTH -> "Month"
                                else -> ""
                            }
                        )
                    },
                )
            }
            FilterChip(
                selected = selected == TimePreset.CUSTOM,
                onClick = onCustom,
                label = { Text("Custom") },
            )
        }

        if (selected == TimePreset.DAY && dayLabel.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onNavigateDay(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous day",
                    )
                }
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = { onNavigateDay(1) },
                    enabled = canGoForward,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next day",
                    )
                }
            }
        }

        if (selected == TimePreset.CUSTOM) {
            Text(
                text = "${startDate.format(dateFormatter)} — ${endDate.format(dateFormatter)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val pickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMs = pickerState.selectedStartDateMillis
                    val endMs = pickerState.selectedEndDateMillis
                    if (startMs != null && endMs != null) {
                        val start = Instant.ofEpochMilli(startMs).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(endMs).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(start, end)
                    }
                },
                enabled = pickerState.selectedStartDateMillis != null &&
                    pickerState.selectedEndDateMillis != null,
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.height(500.dp),
        )
    }
}

@Composable
private fun SummaryChart(
    summaries: List<DailySummaryEntity>,
    dataType: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val records = summaries.map { summary ->
        val value = when (dataType) {
            "heartrate" -> summary.restingBpm.toInt()
            "steps" -> summary.totalSteps
            "sleep" -> summary.totalSleepMinutes
            "calories" -> summary.stepCalories + summary.bpmCalories
            else -> 0
        }
        val epoch = LocalDate.parse(summary.date).atStartOfDay(zone).toEpochSecond()
        HealthRecordEntity(
            timestamp = epoch,
            bpm = value,
            steps = value,
            isSleeping = false,
        )
    }

    val valueSelector: (HealthRecordEntity) -> Float = when (dataType) {
        "sleep" -> { r -> r.bpm.toFloat() / 60f }
        else -> { r -> r.bpm.toFloat() }
    }

    SparklineChart(
        records = records,
        valueSelector = valueSelector,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun HeartRateBandChart(
    summaries: List<DailySummaryEntity>,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val hasMinMax = summaries.any { it.minBpm > 0 && it.maxBpm > 0 }
    val records = summaries.map { summary ->
        val epoch = LocalDate.parse(summary.date).atStartOfDay(zone).toEpochSecond()
        HealthRecordEntity(
            timestamp = epoch,
            bpm = summary.restingBpm.toInt(),
            steps = summary.minBpm * 10000 + summary.maxBpm,
            isSleeping = false,
        )
    }

    val chartLines = if (hasMinMax) {
        listOf(
            ChartLine("Min", Color(0xFF42A5F5)) { r -> (r.steps / 10000).toFloat() },
            ChartLine("Max", Color(0xFF00C853)) { r -> (r.steps % 10000).toFloat() },
            ChartLine("Resting", HeartRed) { r -> r.bpm.toFloat() },
        )
    } else {
        listOf(
            ChartLine("Resting", HeartRed) { r -> r.bpm.toFloat() },
        )
    }

    MultiLineChart(
        records = records,
        lines = chartLines,
        lineThickness = 4f,
        modifier = modifier,
    )
}

@Composable
private fun CaloriesSummaryMultiChart(
    summaries: List<DailySummaryEntity>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE)
    val weight = prefs.getFloat("user_weight_kg", 0f)
    val hasProfile = weight > 0f

    val zone = ZoneId.systemDefault()
    val records = summaries.map { summary ->
        val epoch = LocalDate.parse(summary.date).atStartOfDay(zone).toEpochSecond()
        val estimatedActive = if (hasProfile && summary.avgBpm > 72) {
            val awakeSlots = (1440 - summary.totalSleepMinutes) / 10
            val excessHr = summary.avgBpm - 72f
            (excessHr * weight * 0.001f * 10f * awakeSlots).toInt()
        } else 0
        HealthRecordEntity(
            id = estimatedActive.toLong(),
            timestamp = epoch,
            bpm = summary.stepCalories,
            steps = summary.bpmCalories,
            isSleeping = false,
        )
    }

    val age = prefs.getInt("user_age", 0)
    val bmrPerDay = if (hasProfile && age > 0) {
        val height = prefs.getInt("user_height_cm", 0)
        if (height > 0) (10f * weight + 6.25f * height - 5f * age - 78f)
        else (10f * weight + 984.5f - 5f * age)
    } else 0f

    val chartLines = listOfNotNull(
        ChartLine("Step cal", StepCalColor) { r -> r.bpm.toFloat() },
        ChartLine("HR cal", HrCalColor) { r -> r.steps.toFloat() },
        if (hasProfile) {
            ChartLine("Active", EstCalColor) { r -> r.id.toFloat() }
        } else null,
        if (hasProfile && bmrPerDay > 0f) {
            ChartLine("Total", TotalCalColor) { r -> r.id.toFloat() + bmrPerDay }
        } else null,
    )

    MultiLineChart(
        records = records,
        lines = chartLines,
        lineThickness = 4f,
        modifier = modifier,
    )
}

@Composable
private fun RecordRow(record: HealthRecordEntity, dataType: String) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()
    // Each record timestamp is the END of a 10-minute period
    val periodStart = Instant.ofEpochSecond(record.timestamp - 600).atZone(zone).format(timeFormatter)
    val periodEnd = Instant.ofEpochSecond(record.timestamp).atZone(zone).format(timeFormatter)
    val timeRange = "$periodStart–$periodEnd"

    val value = when (dataType) {
        "heartrate" -> "${record.bpm} bpm"
        "steps" -> "${record.steps} steps"
        "sleep" -> if (record.isSleeping) "Asleep" else "Awake"
        "calories" -> "${(record.steps * 0.04f).toInt()} kcal (est)"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(timeRange, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryRow(
    summary: DailySummaryEntity,
    dataType: String,
    onDayClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable { onDayClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = summary.date,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = when (dataType) {
                    "heartrate" -> "${summary.restingBpm.toInt()} bpm (resting)"
                    "steps" -> "%,d steps".format(summary.totalSteps)
                    "sleep" -> "${summary.totalSleepMinutes / 60}h ${summary.totalSleepMinutes % 60}m"
                    "calories" -> "Step: %,d / HR: %,d".format(summary.stepCalories, summary.bpmCalories)
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val StepCalColor = Color(0xFF66BB6A)
private val HrCalColor = Color(0xFFEF5350)
private val EstCalColor = Color(0xFFFFA726)
private val TotalCalColor = Color(0xFFAB47BC)

@Composable
private fun CaloriesMultiLineSection(
    records: List<HealthRecordEntity>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE)
    val age = prefs.getInt("user_age", 0)
    val weight = prefs.getFloat("user_weight_kg", 0f)
    val hasProfile = age > 0 && weight > 0f

    val bmrPerSlot = if (hasProfile) ((10f * weight + 984.5f - 5f * age) / 144f).coerceAtLeast(0f) else 0f

    val chartLines = listOfNotNull(
        ChartLine("Step cal", StepCalColor) { r -> r.steps * 0.04f },
        ChartLine("HR cal", HrCalColor) { r ->
            if (r.bpm > 0) (r.bpm * 0.6f * (10f / 60f)) else 0f
        },
        if (hasProfile) {
            ChartLine("Active", EstCalColor) { r ->
                if (!r.isSleeping && r.bpm > 72) {
                    val excessHr = r.bpm - 72
                    excessHr * weight * 0.001f * 10f
                } else 0f
            }
        } else null,
        if (hasProfile) {
            ChartLine("Total", TotalCalColor) { r ->
                val active = if (!r.isSleeping && r.bpm > 72) {
                    val excessHr = r.bpm - 72
                    excessHr * weight * 0.001f * 10f
                } else 0f
                bmrPerSlot + active
            }
        } else null,
    )

    MultiLineChart(
        records = records,
        lines = chartLines,
        lineThickness = 3f,
        modifier = modifier,
    )
}
