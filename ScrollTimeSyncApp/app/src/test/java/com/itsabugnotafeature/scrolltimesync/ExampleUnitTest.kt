package com.itsabugnotafeature.scrolltimesync

import com.itsabugnotafeature.scrolltimesync.ble.SyncProtocol
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SyncProtocolTest {

    private fun buildHeader(
        histStart: Int = 1700000000,
        histLen: Int = 3,
        battery: Int = 85,
        bpmAvgX10: Int = 720,
        bpmRestingX10: Int = 600,
        stepCalDay: Int = 1500,
        bpmCalDay: Int = 400,
        sleepTotal: Int = 420,
    ): ByteArray {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(histStart)
        buf.put(histLen.toByte())
        buf.put(battery.toByte())
        buf.putShort(bpmAvgX10.toShort())
        buf.putShort(bpmRestingX10.toShort())
        buf.putShort(stepCalDay.toShort())
        buf.putShort(bpmCalDay.toShort())
        buf.putShort(sleepTotal.toShort())
        return buf.array()
    }

    @Test
    fun `parse header extracts fields correctly including battery`() {
        val header = buildHeader()
        val payload = ByteArray(16 + 3 * 4)
        header.copyInto(payload)

        // Record 0: BPM=72, steps=100, awake, movement=10
        payload[16] = 72.toByte()
        payload[17] = ((100 shr 8) shl 1).toByte()
        payload[18] = (100 and 0xFF).toByte()
        payload[19] = 10.toByte()

        // Record 1: BPM=65, steps=50, asleep, movement=3
        payload[20] = 65.toByte()
        payload[21] = (((50 shr 8) shl 1) or 1).toByte()
        payload[22] = (50 and 0xFF).toByte()
        payload[23] = 3.toByte()

        // Record 2: BPM=80, steps=200, awake, movement=45
        payload[24] = 80.toByte()
        payload[25] = ((200 shr 8) shl 1).toByte()
        payload[26] = (200 and 0xFF).toByte()
        payload[27] = 45.toByte()

        val result = SyncProtocol.parse(payload)

        assertEquals(1700000000L, result.header.histStart)
        assertEquals(3, result.header.histLen)
        assertEquals(85, result.header.batteryPercent)
        assertEquals(72.0f, result.header.avgBpm)
        assertEquals(60.0f, result.header.restingBpm)
        assertEquals(1500, result.header.stepCalories)
        assertEquals(400, result.header.bpmCalories)
        assertEquals(420, result.header.totalSleepMinutes)

        assertEquals(3, result.records.size)

        assertEquals(72, result.records[0].bpm)
        assertEquals(100, result.records[0].steps)
        assertEquals(false, result.records[0].isSleeping)
        assertEquals(10, result.records[0].movement)
        assertEquals(1700000000L, result.records[0].timestamp)

        assertEquals(65, result.records[1].bpm)
        assertEquals(50, result.records[1].steps)
        assertEquals(true, result.records[1].isSleeping)
        assertEquals(3, result.records[1].movement)
        assertEquals(1700000600L, result.records[1].timestamp)

        assertEquals(80, result.records[2].bpm)
        assertEquals(200, result.records[2].steps)
        assertEquals(false, result.records[2].isSleeping)
        assertEquals(45, result.records[2].movement)
        assertEquals(1700001200L, result.records[2].timestamp)
    }

    @Test
    fun `parseHistLen reads uint8 at offset 4`() {
        val header = buildHeader(histLen = 150)
        assertEquals(150, SyncProtocol.parseHistLen(header))
    }

    @Test
    fun `expectedPayloadSize calculates correctly`() {
        val header = buildHeader(histLen = 150)
        assertEquals(16 + 150 * 4, SyncProtocol.expectedPayloadSize(header))
    }

    @Test
    fun `battery boundary values`() {
        val header0 = buildHeader(battery = 0, histLen = 0)
        val result0 = SyncProtocol.parse(header0)
        assertEquals(0, result0.header.batteryPercent)

        val header99 = buildHeader(battery = 99, histLen = 0)
        val result99 = SyncProtocol.parse(header99)
        assertEquals(99, result99.header.batteryPercent)
    }
}
