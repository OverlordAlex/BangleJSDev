package com.itsabugnotafeature.scrolltimesync.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.CardInsight
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.Trend

@Composable
fun HealthCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    insight: CardInsight?,
    records: List<HealthRecordEntity>,
    chartValueSelector: (HealthRecordEntity) -> Float,
    onExploreClick: () -> Unit,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    customExpandedContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (insight != null) {
                Text(
                    text = insight.primaryValue,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trendIcon = when (insight.trend) {
                        Trend.UP -> Icons.AutoMirrored.Filled.TrendingUp
                        Trend.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
                        Trend.NEUTRAL -> Icons.AutoMirrored.Filled.TrendingFlat
                    }
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = insight.comparison,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "No data yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(modifier = Modifier.clickable { onExploreClick() }) {
                        if (customExpandedContent != null) {
                            customExpandedContent()
                        } else if (records.isNotEmpty()) {
                            SparklineChart(
                                records = records,
                                valueSelector = chartValueSelector,
                                color = accentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = onExploreClick,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Explore")
                    }
                }
            }
        }
    }
}
