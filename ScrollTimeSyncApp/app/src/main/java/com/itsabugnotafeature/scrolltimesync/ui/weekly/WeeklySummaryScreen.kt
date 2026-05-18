package com.itsabugnotafeature.scrolltimesync.ui.weekly

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklyInsight
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklySummaryEntity
import com.itsabugnotafeature.scrolltimesync.ui.theme.InsightNegative
import com.itsabugnotafeature.scrolltimesync.ui.theme.InsightNeutral
import com.itsabugnotafeature.scrolltimesync.ui.theme.InsightPositive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeeklySummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HealthRepository.getInstance(application)

    val summaries: StateFlow<List<WeeklySummaryEntity>> = repository.allWeeklySummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklySummaryScreen(
    onBack: () -> Unit,
    viewModel: WeeklySummaryViewModel = viewModel(),
) {
    val summaries by viewModel.summaries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Summaries") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (summaries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            ) {
                Text(
                    "No weekly summaries yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Summaries are generated automatically after the Monday midnight sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(summaries) { summary ->
                    WeeklySummaryCard(summary)
                }
            }
        }
    }
}

@Composable
private fun WeeklySummaryCard(summary: WeeklySummaryEntity) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val startDate = LocalDate.parse(summary.weekStartDate)
    val endDate = LocalDate.parse(summary.weekEndDate)
    val dateRange = "${startDate.format(dateFormatter)} – ${endDate.format(dateFormatter)}"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(dateRange, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Resting HR", style = MaterialTheme.typography.labelSmall)
                    val trendStr = when {
                        summary.restingBpmTrend < -1 -> " (↓${(-summary.restingBpmTrend).toInt()})"
                        summary.restingBpmTrend > 1 -> " (↑${summary.restingBpmTrend.toInt()})"
                        else -> " (steady)"
                    }
                    Text(
                        "${summary.avgRestingBpm.toInt()} bpm$trendStr",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column {
                    Text("Total Steps", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "%,d".format(summary.totalSteps),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Avg Sleep", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${summary.avgSleepMinutes / 60}h ${summary.avgSleepMinutes % 60}m",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column {
                    Text("Active Calories", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "%,d kcal".format(summary.totalActiveCalories),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val insightsList = WeeklyInsight.listFromJson(summary.insights)
            if (insightsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Insights", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                insightsList.forEach { insight ->
                    InsightRow(insight)
                }
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = insight.displayText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
