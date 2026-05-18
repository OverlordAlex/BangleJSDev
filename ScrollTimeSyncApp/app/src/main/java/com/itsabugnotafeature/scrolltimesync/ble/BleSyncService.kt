package com.itsabugnotafeature.scrolltimesync.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class BleSyncService : Service() {

    companion object {
        const val TAG = "BleSyncService"
        const val CHANNEL_ID = "ble_sync_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_TRIGGER_TYPE = "trigger_type"
        const val ACTION_SYNC_COMPLETE = "com.itsabugnotafeature.scrolltimesync.SYNC_COMPLETE"
        const val ACTION_SYNC_FAILED = "com.itsabugnotafeature.scrolltimesync.SYNC_FAILED"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        private const val SCAN_TIMEOUT_MS = 30_000L
        private const val SYNC_TIMEOUT_MS = 60_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private val rxBuffer = ByteArrayOutputStream()
    private var expectedSize: Int? = null
    private var payloadProcessed = false
    private var targetAddress: String? = null
    private var triggerType: String = "AUTOMATIC"
    private lateinit var repository: HealthRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = HealthRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (address == null) {
            failAndStop("No device address provided")
            return START_NOT_STICKY
        }

        targetAddress = address
        triggerType = intent?.getStringExtra(EXTRA_TRIGGER_TYPE) ?: "AUTOMATIC"
        startForeground(NOTIFICATION_ID, buildNotification("Syncing with watch..."))

        serviceScope.launch {
            try {
                startScan(address)
                delay(SYNC_TIMEOUT_MS)
                failAndStop("Sync timed out")
            } catch (e: Exception) {
                failAndStop("Sync error: ${e.message}")
            }
        }

        return START_NOT_STICKY
    }

    private fun startScan(address: String) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = bluetoothManager.adapter ?: run {
            failAndStop("Bluetooth not available")
            return
        }

        val device = adapter.getRemoteDevice(address)
        if (device != null) {
            connectToDevice(device)
            return
        }

        scanner = adapter.bluetoothLeScanner ?: run {
            failAndStop("BLE scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(NusUuids.NUS_SERVICE))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)

        serviceScope.launch {
            delay(SCAN_TIMEOUT_MS)
            scanner?.stopScan(scanCallback)
            if (bluetoothGatt == null) {
                failAndStop("Watch not found within scan timeout")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address == targetAddress) {
                scanner?.stopScan(this)
                connectToDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            failAndStop("BLE scan failed with error code: $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    gatt.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndStop("Service discovery failed: $status")
                return
            }

            val nusService = gatt.getService(NusUuids.NUS_SERVICE)
            if (nusService == null) {
                failAndStop("NUS service not found on device")
                return
            }

            val txChar = nusService.getCharacteristic(NusUuids.NUS_TX_CHAR)
            if (txChar == null) {
                failAndStop("TX characteristic not found")
                return
            }

            gatt.setCharacteristicNotification(txChar, true)
            val descriptor = txChar.getDescriptor(NusUuids.CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                sendSyncCommand(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendSyncCommand(gatt)
            } else {
                failAndStop("Failed to enable notifications: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == NusUuids.NUS_TX_CHAR) {
                handleReceivedData(gatt, value)
            }
        }
    }

    private fun sendSyncCommand(gatt: BluetoothGatt) {
        val nusService = gatt.getService(NusUuids.NUS_SERVICE) ?: return
        val rxChar = nusService.getCharacteristic(NusUuids.NUS_RX_CHAR) ?: return

        Log.d(TAG, "Sending SYNC command")
        gatt.writeCharacteristic(
            rxChar,
            SyncProtocol.SYNC_COMMAND.toByteArray(Charsets.US_ASCII),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
    }

    private fun handleReceivedData(gatt: BluetoothGatt, data: ByteArray) {
        Log.d(TAG, "BLE RX chunk (${data.size} bytes): ${data.toHexString()}")

        rxBuffer.write(data)
        val accumulated = rxBuffer.toByteArray()
        Log.d(TAG, "Buffer total: ${accumulated.size} bytes")

        if (expectedSize == null && accumulated.size >= SyncProtocol.HEADER_SIZE) {
            if (SyncProtocol.looksLikeAscii(accumulated)) {
                Log.w(TAG, "Received ASCII/REPL text instead of binary header — watch console may not be redirected")
                rxBuffer.reset()
                expectedSize = null
                failAndStop("Watch sent REPL text instead of sync data. Ensure the watch app is running.")
                return
            }
            Log.d(TAG, "Header bytes: ${accumulated.copyOf(SyncProtocol.HEADER_SIZE).toHexString()}")
            expectedSize = SyncProtocol.expectedPayloadSize(accumulated)
            Log.d(TAG, "Expected payload size: $expectedSize (histLen=${SyncProtocol.parseHistLen(accumulated)})")
        }

        val target = expectedSize
        if (target != null && accumulated.size >= target && !payloadProcessed) {
            payloadProcessed = true
            processPayload(gatt, accumulated.copyOf(target))
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }

    private fun processPayload(gatt: BluetoothGatt, payload: ByteArray) {
        serviceScope.launch {
            try {
                // #region agent log
                val headerHex = payload.take(SyncProtocol.HEADER_SIZE).joinToString(" ") { "%02X".format(it) }
                val firstRecordsHex = payload.drop(SyncProtocol.HEADER_SIZE).take(40).joinToString(" ") { "%02X".format(it) }
                Log.d(TAG, "Raw payload header: $headerHex")
                Log.d(TAG, "First 10 records raw: $firstRecordsHex")
                try {
                    val rawData = mapOf(
                        "sessionId" to "9bc10d",
                        "hypothesisId" to "A,B,C,D",
                        "location" to "BleSyncService.kt:processPayload",
                        "message" to "raw_payload_received",
                        "timestamp" to System.currentTimeMillis(),
                        "data" to mapOf(
                            "payloadSize" to payload.size,
                            "headerHex" to headerHex,
                            "first10RecordsHex" to firstRecordsHex,
                            "expectedSize" to expectedSize
                        )
                    )
                    java.net.URL("http://127.0.0.1:7556/ingest/67af5c8e-d66e-4b9b-a1d7-abb901b5d134").openConnection().let { conn ->
                        (conn as java.net.HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("X-Debug-Session-Id", "9bc10d")
                            doOutput = true
                            outputStream.write(org.json.JSONObject(rawData).toString().toByteArray())
                            inputStream.close()
                        }
                    }
                } catch (_: Exception) {}
                // #endregion

                val syncPayload = SyncProtocol.parse(payload)
                repository.insertSyncData(syncPayload, triggerType)
                sendTimeCorrection(gatt)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse/store payload", e)
                failAndStop("Failed to process sync data: ${e.message}")
            }
        }
    }

    private fun sendTimeCorrection(gatt: BluetoothGatt) {
        val nusService = gatt.getService(NusUuids.NUS_SERVICE) ?: return
        val rxChar = nusService.getCharacteristic(NusUuids.NUS_RX_CHAR) ?: return

        val epochSeconds = System.currentTimeMillis() / 1000
        val command = SyncProtocol.timeCommand(epochSeconds)
        Log.d(TAG, "Sending time correction: $command")

        gatt.writeCharacteristic(
            rxChar,
            command.toByteArray(Charsets.US_ASCII),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )

        serviceScope.launch {
            delay(2000)
            completeSync()
        }
    }

    private fun completeSync() {
        Log.d(TAG, "Sync completed successfully")
        sendBroadcast(Intent(ACTION_SYNC_COMPLETE))
        disconnect()
        stopSelf()
    }

    private fun failAndStop(message: String) {
        Log.e(TAG, message)
        serviceScope.launch {
            repository.logSyncFailure(message, triggerType)
        }
        val intent = Intent(ACTION_SYNC_FAILED).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        sendBroadcast(intent)

        showFailureNotification(message)
        disconnect()
        stopSelf()
    }

    private fun disconnect() {
        scanner?.stopScan(scanCallback)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onDestroy() {
        disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watch Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background sync with BangleJS watch"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ScrollTimeSync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
    }

    private fun showFailureNotification(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync Failed")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
    }
}
