package com.itsabugnotafeature.scrolltimesync.ble

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "SyncProtocol"

data class SyncHeader(
    val histStart: Long,
    val histLen: Int,
    val batteryPercent: Int,
    val avgBpm: Float,
    val restingBpm: Float,
    val stepCalories: Int,
    val bpmCalories: Int,
    val totalSleepMinutes: Int,
)

data class HealthRecord(
    val timestamp: Long,
    val bpm: Int,
    val steps: Int,
    val isSleeping: Boolean,
    val movement: Int = 0,
)

data class SyncPayload(
    val header: SyncHeader,
    val records: List<HealthRecord>,
)

object SyncProtocol {
    const val HEADER_SIZE = 16
    const val RECORD_SIZE = 4
    const val SYNC_COMMAND = "SYNC\n"
    const val OK_RESPONSE = "OK\n"

    fun timeCommand(epochSeconds: Long): String = "TIME:$epochSeconds\n"

    fun expectedPayloadSize(headerBytes: ByteArray): Int {
        require(headerBytes.size >= HEADER_SIZE) { "Header must be at least $HEADER_SIZE bytes" }
        val histLen = parseHistLen(headerBytes)
        return HEADER_SIZE + histLen * RECORD_SIZE
    }

    fun parseHistLen(headerBytes: ByteArray): Int {
        return headerBytes[4].toInt() and 0xFF
    }

    fun looksLikeAscii(data: ByteArray): Boolean {
        val sample = data.copyOf(minOf(data.size, HEADER_SIZE))
        return sample.all { b ->
            val c = b.toInt() and 0xFF
            c in 0x09..0x0D || c in 0x20..0x7E
        }
    }

    fun parse(payload: ByteArray): SyncPayload {
        require(payload.size >= HEADER_SIZE) { "Payload too small: ${payload.size} bytes" }

        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val histStart = buf.int.toLong() and 0xFFFFFFFFL  // offset 0-3
        val histLen = buf.get().toInt() and 0xFF            // offset 4
        val battery = buf.get().toInt() and 0xFF            // offset 5
        val bpmAvgX10 = buf.short.toInt() and 0xFFFF       // offset 6-7
        val bpmRestingX10 = buf.short.toInt() and 0xFFFF   // offset 8-9
        val stepCalDay = buf.short.toInt() and 0xFFFF       // offset 10-11
        val bpmCalDay = buf.short.toInt() and 0xFFFF        // offset 12-13
        val sleepTotal = buf.short.toInt() and 0xFFFF       // offset 14-15

        require(payload.size >= HEADER_SIZE + histLen * RECORD_SIZE) {
            "Payload too small for $histLen records: need ${HEADER_SIZE + histLen * RECORD_SIZE}, got ${payload.size}"
        }

        Log.d(TAG, "Raw header fields: histStart=$histStart histLen=$histLen battery=$battery " +
            "bpmAvgX10=$bpmAvgX10 bpmRestingX10=$bpmRestingX10 " +
            "stepCalDay=$stepCalDay bpmCalDay=$bpmCalDay sleepTotal=$sleepTotal")

        val header = SyncHeader(
            histStart = histStart,
            histLen = histLen,
            batteryPercent = battery,
            avgBpm = bpmAvgX10 / 10f,
            restingBpm = bpmRestingX10 / 10f,
            stepCalories = stepCalDay,
            bpmCalories = bpmCalDay,
            totalSleepMinutes = sleepTotal,
        )

        Log.d(TAG, "Parsed header: avgBpm=${header.avgBpm} restingBpm=${header.restingBpm} " +
            "stepCal=${header.stepCalories} bpmCal=${header.bpmCalories} " +
            "sleep=${header.totalSleepMinutes}min battery=${header.batteryPercent}%")

        val records = (0 until histLen).map { i ->
            val offset = HEADER_SIZE + i * RECORD_SIZE
            val byte0 = payload[offset].toInt() and 0xFF
            val byte1 = payload[offset + 1].toInt() and 0xFF
            val byte2 = payload[offset + 2].toInt() and 0xFF
            val byte3 = payload[offset + 3].toInt() and 0xFF

            val bpm = byte0
            val isSleeping = (byte1 and 0x01) == 1
            val steps = ((byte1 shr 1) shl 8) or byte2

            // #region agent log
            if (steps > 750 || (steps > 0 && bpm == 0)) {
                Log.w(TAG, "SUSPICIOUS record[$i]: raw=[${"%02X".format(byte0)},${"%02X".format(byte1)},${"%02X".format(byte2)},${"%02X".format(byte3)}] " +
                    "decoded: bpm=$bpm steps=$steps sleep=$isSleeping movement=$byte3 ts=${histStart + i * 600L}")
                try {
                    val logData = mapOf(
                        "sessionId" to "9bc10d",
                        "hypothesisId" to "A,B,C,D",
                        "location" to "SyncProtocol.kt:parse",
                        "message" to "suspicious_record",
                        "timestamp" to System.currentTimeMillis(),
                        "data" to mapOf(
                            "recordIndex" to i,
                            "rawBytes" to listOf(byte0, byte1, byte2, byte3),
                            "decodedBpm" to bpm,
                            "decodedSteps" to steps,
                            "decodedSleep" to isSleeping,
                            "decodedMovement" to byte3,
                            "recordTimestamp" to (histStart + i * 600L),
                            "histLen" to histLen,
                            "histStart" to histStart
                        )
                    )
                    java.net.URL("http://127.0.0.1:7556/ingest/67af5c8e-d66e-4b9b-a1d7-abb901b5d134").openConnection().let { conn ->
                        (conn as java.net.HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("X-Debug-Session-Id", "9bc10d")
                            doOutput = true
                            outputStream.write(org.json.JSONObject(logData).toString().toByteArray())
                            inputStream.close()
                        }
                    }
                } catch (_: Exception) {}
            }
            // #endregion

            HealthRecord(
                timestamp = histStart + i * 600L,
                bpm = bpm,
                steps = steps,
                isSleeping = isSleeping,
                movement = byte3,
            )
        }

        // #region agent log
        val suspiciousCount = records.count { it.steps > 750 }
        val sleepingCount = records.count { it.isSleeping }
        Log.d(TAG, "Sync summary: ${records.size} records, $suspiciousCount suspicious (steps>750), $sleepingCount sleeping")
        try {
            val summaryData = mapOf(
                "sessionId" to "9bc10d",
                "hypothesisId" to "A,B,C,D,E",
                "location" to "SyncProtocol.kt:parse_summary",
                "message" to "sync_payload_summary",
                "timestamp" to System.currentTimeMillis(),
                "data" to mapOf(
                    "histLen" to histLen,
                    "histStart" to histStart,
                    "totalRecords" to records.size,
                    "suspiciousRecords" to suspiciousCount,
                    "sleepingRecords" to sleepingCount,
                    "totalSteps" to records.sumOf { it.steps },
                    "avgStepsPerRecord" to if (records.isNotEmpty()) records.sumOf { it.steps } / records.size else 0,
                    "maxSteps" to (records.maxOfOrNull { it.steps } ?: 0),
                    "minBpm" to (records.filter { it.bpm > 0 }.minOfOrNull { it.bpm } ?: 0),
                    "maxBpm" to (records.maxOfOrNull { it.bpm } ?: 0),
                    "sleepTotal" to sleepTotal,
                    "batteryPercent" to battery
                )
            )
            java.net.URL("http://127.0.0.1:7556/ingest/67af5c8e-d66e-4b9b-a1d7-abb901b5d134").openConnection().let { conn ->
                (conn as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", "9bc10d")
                    doOutput = true
                    outputStream.write(org.json.JSONObject(summaryData).toString().toByteArray())
                    inputStream.close()
                }
            }
        } catch (_: Exception) {}
        // #endregion

        return SyncPayload(header = header, records = records)
    }
}
