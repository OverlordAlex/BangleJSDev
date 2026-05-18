package com.itsabugnotafeature.scrolltimesync.ui.settings

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itsabugnotafeature.scrolltimesync.ble.NusUuids
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import com.itsabugnotafeature.scrolltimesync.sync.SyncScheduler
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScannedDevice(
    val name: String?,
    val address: String,
)

data class SettingsState(
    val pairedDeviceAddress: String? = null,
    val pairedDeviceName: String? = null,
    val autoSyncEnabled: Boolean = true,
    val isScanning: Boolean = false,
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val userAge: Int = 0,
    val userHeightCm: Int = 0,
    val userWeightKg: Float = 0f,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("scrolltimesync", Context.MODE_PRIVATE)
    private val profilePrefs = application.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    private val repository = HealthRepository.getInstance(application)

    val latestSync: StateFlow<SyncLogEntry?> = repository.latestSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val batteryHistory: StateFlow<List<SyncLogEntry>> = repository.syncsWithBattery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(
        SettingsState(
            pairedDeviceAddress = prefs.getString("device_address", null),
            pairedDeviceName = prefs.getString("device_name", null),
            autoSyncEnabled = prefs.getBoolean("auto_sync", true),
            userAge = profilePrefs.getInt("user_age", 0),
            userHeightCm = profilePrefs.getInt("user_height_cm", 0),
            userWeightKg = profilePrefs.getFloat("user_weight_kg", 0f),
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var scanCallback: ScanCallback? = null

    fun startScan() {
        val context = getApplication<Application>()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return

        _state.value = _state.value.copy(isScanning = true, scannedDevices = emptyList())

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = ScannedDevice(
                    name = result.device.name,
                    address = result.device.address,
                )
                val current = _state.value.scannedDevices
                if (current.none { it.address == device.address }) {
                    _state.value = _state.value.copy(scannedDevices = current + device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = _state.value.copy(isScanning = false)
            }
        }
        scanCallback = callback

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(NusUuids.NUS_SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, callback)

        viewModelScope.launch {
            delay(15_000)
            stopScan()
        }
    }

    fun stopScan() {
        val context = getApplication<Application>()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        _state.value = _state.value.copy(isScanning = false)
    }

    fun selectDevice(device: ScannedDevice) {
        prefs.edit()
            .putString("device_address", device.address)
            .putString("device_name", device.name ?: device.address)
            .apply()

        _state.value = _state.value.copy(
            pairedDeviceAddress = device.address,
            pairedDeviceName = device.name ?: device.address,
            isScanning = false,
        )
        stopScan()
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync", enabled).apply()
        _state.value = _state.value.copy(autoSyncEnabled = enabled)

        val context = getApplication<Application>()
        if (enabled) {
            SyncScheduler.schedule(context)
        } else {
            SyncScheduler.cancel(context)
        }
    }

    fun unpairDevice() {
        prefs.edit()
            .remove("device_address")
            .remove("device_name")
            .apply()
        _state.value = _state.value.copy(
            pairedDeviceAddress = null,
            pairedDeviceName = null,
        )
    }

    fun setUserAge(age: Int) {
        profilePrefs.edit().putInt("user_age", age).apply()
        _state.value = _state.value.copy(userAge = age)
    }

    fun setUserHeight(height: Int) {
        profilePrefs.edit().putInt("user_height_cm", height).apply()
        _state.value = _state.value.copy(userHeightCm = height)
    }

    fun setUserWeight(weight: Float) {
        profilePrefs.edit().putFloat("user_weight_kg", weight).apply()
        _state.value = _state.value.copy(userWeightKg = weight)
    }

    fun deleteOldestDay() {
        viewModelScope.launch {
            repository.deleteOldestDay()
        }
    }

    fun dumpDataToLog() {
        viewModelScope.launch {
            repository.dumpRecentDataToLog()
        }
    }

    fun loadSampleData() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val rng = Random(42)

            for (dayOffset in 10 downTo 0) {
                val date = today.minusDays(dayOffset.toLong())
                val dayStartEpoch = date.atStartOfDay(zone).toEpochSecond()
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

                val baseResting = 58f + rng.nextFloat() * 6f
                val isWeekend = date.dayOfWeek.value >= 6

                val records = mutableListOf<HealthRecordEntity>()
                var totalSteps = 0
                var sleepMinutes = 0

                for (slot in 0 until 144) {
                    val timestamp = dayStartEpoch + slot * 600L
                    val hour = (slot * 10) / 60

                    val isSleeping = hour < 7 || hour >= 23
                    val bpm: Int
                    val steps: Int

                    val movement: Int
                    when {
                        isSleeping -> {
                            bpm = (baseResting + 3 + rng.nextFloat() * 5f).toInt()
                            steps = if (rng.nextFloat() < 0.1f) rng.nextInt(3) else 0
                            movement = rng.nextInt(15)
                            sleepMinutes += 10
                        }
                        hour in 7..8 -> {
                            bpm = (70 + rng.nextFloat() * 20f).toInt()
                            steps = (20 + rng.nextFloat() * 80f).toInt()
                            movement = (30 + rng.nextFloat() * 60f).toInt()
                        }
                        hour in 12..13 -> {
                            bpm = (75 + rng.nextFloat() * 15f).toInt()
                            steps = (50 + rng.nextFloat() * 150f).toInt()
                            movement = (40 + rng.nextFloat() * 80f).toInt()
                        }
                        hour in 17..18 -> {
                            bpm = if (isWeekend) (100 + rng.nextFloat() * 40f).toInt()
                            else (80 + rng.nextFloat() * 30f).toInt()
                            steps = if (isWeekend) (200 + rng.nextFloat() * 400f).toInt()
                            else (100 + rng.nextFloat() * 200f).toInt()
                            movement = (80 + rng.nextFloat() * 170f).toInt()
                        }
                        else -> {
                            bpm = (65 + rng.nextFloat() * 20f).toInt()
                            steps = (10 + rng.nextFloat() * 60f).toInt()
                            movement = (20 + rng.nextFloat() * 50f).toInt()
                        }
                    }
                    totalSteps += steps
                    records.add(HealthRecordEntity(
                        timestamp = timestamp,
                        bpm = bpm,
                        steps = steps,
                        isSleeping = isSleeping,
                        movement = movement,
                    ))
                }

                repository.insertRecords(records)

                val bpmValues = records.filter { it.bpm > 0 }.map { it.bpm }
                val avgBpm = bpmValues.average().toFloat()
                val stepCal = (totalSteps * 0.04f).toInt()
                val bpmCal = (avgBpm * 0.6f * 24f).toInt()

                repository.insertSummary(DailySummaryEntity(
                    date = dateStr,
                    avgBpm = avgBpm,
                    restingBpm = baseResting,
                    minBpm = bpmValues.min(),
                    maxBpm = bpmValues.max(),
                    stepCalories = stepCal,
                    bpmCalories = bpmCal,
                    totalSleepMinutes = sleepMinutes,
                    totalSteps = totalSteps,
                ))

                val syncEpoch = dayStartEpoch + 86400 - 3600 + rng.nextInt(3600)
                repository.insertSyncLog(SyncLogEntry(
                    timestamp = syncEpoch,
                    recordsReceived = records.size,
                    watchBatteryPercent = 20 + rng.nextInt(60),
                    status = "SUCCESS",
                ))
            }
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
