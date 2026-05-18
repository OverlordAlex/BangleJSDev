package com.itsabugnotafeature.scrolltimesync.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Watch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.components.SparklineChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToSyncHistory: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val latestSync by viewModel.latestSync.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Watch", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.pairedDeviceAddress != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.Watch,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.pairedDeviceName ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = state.pairedDeviceAddress!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.unpairDevice() }) {
                                    Text("Remove")
                                }
                            }
                        } else {
                            Text(
                                text = "No watch paired",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val perms = arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                    )
                                    val allGranted = perms.all {
                                        ContextCompat.checkSelfPermission(context, it) ==
                                            PackageManager.PERMISSION_GRANTED
                                    }
                                    if (allGranted) {
                                        viewModel.startScan()
                                    } else {
                                        permissionLauncher.launch(perms)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Scan for watch")
                            }
                        }
                    }
                }
            }

            if (state.isScanning || state.scannedDevices.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Nearby Devices",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.isScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.scannedDevices.isEmpty() && state.isScanning) {
                                Text(
                                    "Searching...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                items(state.scannedDevices) { device ->
                    Card(
                        modifier = Modifier.clickable { viewModel.selectDevice(device) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = device.name ?: "Unknown Device",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                ProfileSection(viewModel = viewModel)
            }

            item {
                HorizontalDivider()
            }

            item {
                Text("Sync", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Auto-sync at midnight", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Requires watch BLE to be active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.autoSyncEnabled,
                                onCheckedChange = { viewModel.setAutoSync(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToSyncHistory() },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Last sync details", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "View all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (latestSync != null) {
                                val sync = latestSync!!
                                val time = Instant.ofEpochSecond(sync.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))
                                Text(
                                    text = "Time: $time",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Status: ${sync.status}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Records: ${sync.recordsReceived}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (sync.watchBatteryPercent != null) {
                                    Text(
                                        text = "Watch battery: ${sync.watchBatteryPercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (sync.errorMessage != null) {
                                    Text(
                                        text = "Error: ${sync.errorMessage}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else {
                                Text(
                                    text = "No sync history",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                BatterySection(viewModel = viewModel)
            }

            item {
                HorizontalDivider()
            }

            item {
                DataManagementSection(viewModel = viewModel)
            }

            item {
                HorizontalDivider()
            }

            item {
                DebugSection(viewModel = viewModel)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfileSection(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    var ageText by rememberSaveable { mutableStateOf(if (state.userAge > 0) state.userAge.toString() else "") }
    var heightText by rememberSaveable { mutableStateOf(if (state.userHeightCm > 0) state.userHeightCm.toString() else "") }
    var weightText by rememberSaveable { mutableStateOf(if (state.userWeightKg > 0f) state.userWeightKg.toString() else "") }

    Text("Profile", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Used for BMR and active calorie estimation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ageText,
                onValueChange = { newVal ->
                    ageText = newVal.filter { it.isDigit() }
                    ageText.toIntOrNull()?.let { viewModel.setUserAge(it) }
                },
                label = { Text("Age (years)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = heightText,
                onValueChange = { newVal ->
                    heightText = newVal.filter { it.isDigit() }
                    heightText.toIntOrNull()?.let { viewModel.setUserHeight(it) }
                },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = weightText,
                onValueChange = { newVal ->
                    weightText = newVal.filter { it.isDigit() || it == '.' }
                    weightText.toFloatOrNull()?.let { viewModel.setUserWeight(it) }
                },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BatterySection(viewModel: SettingsViewModel) {
    val allBattery by viewModel.batteryHistory.collectAsState()

    Text("Watch Battery", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (allBattery.isEmpty()) {
                Text(
                    "No battery data yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val chargeIndices = detectChargeEvents(allBattery)
                val lastChargeIdx = chargeIndices.lastOrNull() ?: 0
                val dischargeSeries = allBattery.subList(lastChargeIdx, allBattery.size)

                if (dischargeSeries.size >= 2) {
                    val records = dischargeSeries.map { entry ->
                        HealthRecordEntity(
                            timestamp = entry.timestamp,
                            bpm = entry.watchBatteryPercent ?: 0,
                            steps = 0,
                            isSleeping = false,
                        )
                    }
                    SparklineChart(
                        records = records,
                        valueSelector = { it.bpm.toFloat() },
                        color = Color(0xFF4CAF50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val avgDaysBetweenCharges = computeAvgDaysBetweenCharges(allBattery, chargeIndices)
                val chargeCycles = chargeIndices.size

                if (chargeCycles > 0) {
                    Text(
                        "Avg %.1f days between charges".format(avgDaysBetweenCharges),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "$chargeCycles charge cycle${if (chargeCycles != 1) "s" else ""} recorded",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DataManagementSection(viewModel: SettingsViewModel) {
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    Text("Data Management", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Remove the oldest day of recorded data. Use this to clear junk data from initial setup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { showConfirmDialog = true }) {
                Text("Delete oldest day")
            }
        }
    }

    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Delete oldest day?") },
            text = { Text("This will permanently remove all health records, daily summary, and sync logs from the oldest day. This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.deleteOldestDay()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DebugSection(viewModel: SettingsViewModel) {
    Text("Debug", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Dump last 48h of health records to Android logcat for debugging sleep detection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = { viewModel.dumpDataToLog() }) {
                Text("Dump to logcat")
            }
        }
    }
}

private fun detectChargeEvents(entries: List<SyncLogEntry>): List<Int> {
    val indices = mutableListOf<Int>()
    for (i in 1 until entries.size) {
        val prev = entries[i - 1].watchBatteryPercent ?: continue
        val curr = entries[i].watchBatteryPercent ?: continue
        if (curr > prev + 10) {
            indices.add(i)
        }
    }
    return indices
}

private fun computeAvgDaysBetweenCharges(
    entries: List<SyncLogEntry>,
    chargeIndices: List<Int>,
): Float {
    if (chargeIndices.size < 2) {
        if (chargeIndices.size == 1) {
            val firstTs = entries.first().timestamp
            val chargeTs = entries[chargeIndices[0]].timestamp
            val days = (chargeTs - firstTs) / 86400f
            return if (days > 0) days else 0f
        }
        return 0f
    }
    val intervals = chargeIndices.zipWithNext { a, b ->
        (entries[b].timestamp - entries[a].timestamp) / 86400f
    }
    return intervals.average().toFloat()
}
