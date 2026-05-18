package com.itsabugnotafeature.scrolltimesync.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import java.time.Duration
import java.time.Instant

@Composable
fun SyncStatusBar(
    syncEntry: SyncLogEntry?,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val statusText = if (syncEntry == null) {
                "Never synced"
            } else {
                val elapsed = Duration.between(
                    Instant.ofEpochSecond(syncEntry.timestamp),
                    Instant.now()
                )
                val status = if (syncEntry.status == "SUCCESS") {
                    formatDuration(elapsed)
                } else {
                    "Failed — ${syncEntry.errorMessage ?: "unknown error"}"
                }
                "Last sync: $status"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (syncEntry?.status == "FAILED")
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (syncEntry?.watchBatteryPercent != null) {
                Text(
                    text = "Watch battery: ${syncEntry.watchBatteryPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncEntry.watchBatteryPercent < 20)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onSyncClick) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Sync now",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${hours / 24}d ago"
    }
}
