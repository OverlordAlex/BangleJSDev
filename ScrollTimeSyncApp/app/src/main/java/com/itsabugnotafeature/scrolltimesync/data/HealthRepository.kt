package com.itsabugnotafeature.scrolltimesync.data

import android.content.Context
import com.itsabugnotafeature.scrolltimesync.ble.SyncPayload
import com.itsabugnotafeature.scrolltimesync.data.dao.DailySummaryDao
import com.itsabugnotafeature.scrolltimesync.data.dao.HealthRecordDao
import com.itsabugnotafeature.scrolltimesync.data.dao.SyncLogDao
import com.itsabugnotafeature.scrolltimesync.data.dao.WeeklySummaryDao
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.InsightKind
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklyInsight
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklySummaryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthRepository private constructor(
    private val healthRecordDao: HealthRecordDao,
    private val dailySummaryDao: DailySummaryDao,
    private val syncLogDao: SyncLogDao,
    private val weeklySummaryDao: WeeklySummaryDao,
) {
    val latestSync: Flow<SyncLogEntry?> = syncLogDao.getLatestSync()
    val latestSummary: Flow<DailySummaryEntity?> = dailySummaryDao.getLatest()
    val syncsWithBattery: Flow<List<SyncLogEntry>> = syncLogDao.getSyncsWithBattery()
    val allSyncs: Flow<List<SyncLogEntry>> = syncLogDao.getAll()
    val latestWeeklySummary: Flow<WeeklySummaryEntity?> = weeklySummaryDao.getLatest()
    val allWeeklySummaries: Flow<List<WeeklySummaryEntity>> = weeklySummaryDao.getAll()

    fun getLatestRecords(count: Int) = healthRecordDao.getLatestRecords(count)
    fun getLatestRecord() = healthRecordDao.getLatestRecord()
    fun getLatestDays(count: Int) = dailySummaryDao.getLatestDays(count)

    fun getRecordsBetween(startEpoch: Long, endEpoch: Long) =
        healthRecordDao.getRecordsBetween(startEpoch, endEpoch)

    fun getSummariesBetween(startDate: String, endDate: String) =
        dailySummaryDao.getSummariesBetween(startDate, endDate)

    suspend fun getAvgRestingBpm(days: Int) = dailySummaryDao.getAvgRestingBpm(days)
    suspend fun getAvgSteps(days: Int) = dailySummaryDao.getAvgSteps(days)
    suspend fun getAvgSleepMinutes(days: Int) = dailySummaryDao.getAvgSleepMinutes(days)
    suspend fun getAvgCalories(days: Int) = dailySummaryDao.getAvgCalories(days)

    suspend fun deleteOldestDay() {
        val oldestTs = healthRecordDao.getOldestTimestamp() ?: return
        val oldestDate = Instant.ofEpochSecond(oldestTs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val nextDayEpoch = oldestDate.plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toEpochSecond()
        healthRecordDao.deleteRecordsBefore(nextDayEpoch)
        dailySummaryDao.deleteSummariesBefore(oldestDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
        syncLogDao.deleteBefore(nextDayEpoch)
    }

    suspend fun dumpRecentDataToLog() {
        val now = Instant.now().epochSecond
        val lookback = now - 48 * 3600
        val records = healthRecordDao.getRecordsBetweenSync(lookback, now)
        android.util.Log.d("ScrollTimeDump", "=== DUMP START: ${records.size} records, range ${lookback}-${now} ===")
        for (r in records) {
            android.util.Log.d("ScrollTimeDump", "DUMP|${r.timestamp}|${r.bpm}|${r.steps}|${r.isSleeping}|${r.movement}")
        }
        android.util.Log.d("ScrollTimeDump", "=== DUMP END ===")
    }

    suspend fun insertSyncData(payload: SyncPayload, triggerType: String = "AUTOMATIC") {
        val records = payload.records.map { record ->
            HealthRecordEntity(
                timestamp = record.timestamp,
                bpm = record.bpm,
                steps = record.steps,
                isSleeping = record.isSleeping,
                movement = record.movement,
            )
        }

        // #region agent log
        val firstTs = records.firstOrNull()?.timestamp ?: 0
        val lastTs = records.lastOrNull()?.timestamp ?: 0
        val existingInRange = healthRecordDao.getRecordsBetweenSync(firstTs, lastTs)
        val overlapCount = existingInRange.size
        val sleepingInPayload = records.count { it.isSleeping }
        android.util.Log.d("HealthRepository", "Insert: ${records.size} records, range $firstTs-$lastTs, $overlapCount existing overlap, $sleepingInPayload sleeping")
        try {
            val insertData = mapOf(
                "sessionId" to "9bc10d",
                "hypothesisId" to "E,overlap",
                "location" to "HealthRepository.kt:insertSyncData",
                "message" to "insert_sync_data",
                "timestamp" to System.currentTimeMillis(),
                "data" to mapOf(
                    "recordCount" to records.size,
                    "firstTimestamp" to firstTs,
                    "lastTimestamp" to lastTs,
                    "existingOverlapCount" to overlapCount,
                    "sleepingRecordsInPayload" to sleepingInPayload,
                    "headerSleepMinutes" to payload.header.totalSleepMinutes,
                    "headerRestingBpm" to payload.header.restingBpm,
                    "headerAvgBpm" to payload.header.avgBpm
                )
            )
            java.net.URL("http://127.0.0.1:7556/ingest/67af5c8e-d66e-4b9b-a1d7-abb901b5d134").openConnection().let { conn ->
                (conn as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", "9bc10d")
                    doOutput = true
                    outputStream.write(org.json.JSONObject(insertData).toString().toByteArray())
                    inputStream.close()
                }
            }
        } catch (_: Exception) {}
        // #endregion

        healthRecordDao.insertAll(records)

        val dateStr = Instant.ofEpochSecond(payload.header.histStart)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        val totalSteps = payload.records.sumOf { it.steps }
        val bpmValues = payload.records.map { it.bpm }.filter { it > 0 }

        val summary = DailySummaryEntity(
            date = dateStr,
            avgBpm = payload.header.avgBpm,
            restingBpm = payload.header.restingBpm,
            minBpm = bpmValues.minOrNull() ?: 0,
            maxBpm = bpmValues.maxOrNull() ?: 0,
            stepCalories = payload.header.stepCalories,
            bpmCalories = payload.header.bpmCalories,
            totalSleepMinutes = payload.header.totalSleepMinutes,
            totalSteps = totalSteps,
        )
        dailySummaryDao.insert(summary)

        syncLogDao.insert(
            SyncLogEntry(
                timestamp = System.currentTimeMillis() / 1000,
                recordsReceived = payload.header.histLen,
                watchBatteryPercent = payload.header.batteryPercent,
                status = "SUCCESS",
                triggerType = triggerType,
            )
        )
    }

    suspend fun insertRecords(records: List<HealthRecordEntity>) {
        healthRecordDao.insertAll(records)
    }

    suspend fun insertSummary(summary: DailySummaryEntity) {
        dailySummaryDao.insert(summary)
    }

    suspend fun insertSyncLog(entry: SyncLogEntry) {
        syncLogDao.insert(entry)
    }

    data class SleepQualityResult(
        val score: Int,
        val durationMinutes: Int,
        val interruptions: Int,
        val sleepStartHour: Float,
        val avgMovement: Float = 0f,
    )

    suspend fun computeSleepQuality(): SleepQualityResult? {
        val now = Instant.now().epochSecond
        val lookback = now - 48 * 3600
        val records = healthRecordDao.getRecordsBetweenSync(lookback, now)
        if (records.isEmpty()) return null

        val lastAwakeIdx = records.indexOfLast { !it.isSleeping }
        if (lastAwakeIdx < 0) return null

        var sleepEndIdx = -1
        for (i in lastAwakeIdx downTo 0) {
            if (records[i].isSleeping) { sleepEndIdx = i; break }
        }
        if (sleepEndIdx < 0) return null

        var sleepStartIdx = sleepEndIdx
        var interruptions = 0
        var awakeGap = 0
        for (i in sleepEndIdx downTo 0) {
            if (records[i].isSleeping) {
                if (awakeGap in 1..2) interruptions++
                awakeGap = 0
                sleepStartIdx = i
            } else {
                awakeGap++
                if (awakeGap >= 3) break
            }
        }

        val sleepRecords = records.subList(sleepStartIdx, sleepEndIdx + 1)
        val sleepingCount = sleepRecords.count { it.isSleeping }
        val durationMinutes = sleepingCount * 10
        if (durationMinutes < 30) return null

        val sleepStartInstant = Instant.ofEpochSecond(records[sleepStartIdx].timestamp)
        val sleepStartTime = sleepStartInstant.atZone(ZoneId.systemDefault()).toLocalTime()
        val sleepStartHour = sleepStartTime.hour + sleepStartTime.minute / 60f

        val sleepingRecordsOnly = sleepRecords.filter { it.isSleeping }
        val avgMovement = if (sleepingRecordsOnly.isNotEmpty()) {
            sleepingRecordsOnly.map { it.movement.toFloat() }.average().toFloat()
        } else 0f

        val durationPts = when {
            durationMinutes in 420..540 -> 30
            durationMinutes < 420 -> (30 * durationMinutes / 420).coerceIn(0, 30)
            else -> (30 - (durationMinutes - 540) / 6).coerceIn(0, 30)
        }

        val continuityPts = (25 - interruptions * 5).coerceIn(0, 25)

        val normalizedHour = if (sleepStartHour >= 12) sleepStartHour else sleepStartHour + 24
        val timingPts = when {
            normalizedHour in 21f..23f -> 20
            normalizedHour < 21f -> (20 - ((21f - normalizedHour) * 7).toInt()).coerceIn(0, 20)
            else -> (20 - ((normalizedHour - 23f) * 7).toInt()).coerceIn(0, 20)
        }

        val restlessPts = when {
            avgMovement < 20f -> 25
            avgMovement > 80f -> 0
            else -> (25 * (80f - avgMovement) / 60f).toInt().coerceIn(0, 25)
        }

        val score = (durationPts + continuityPts + timingPts + restlessPts).coerceIn(0, 100)

        return SleepQualityResult(
            score = score,
            durationMinutes = durationMinutes,
            interruptions = interruptions,
            sleepStartHour = sleepStartHour,
            avgMovement = avgMovement,
        )
    }

    suspend fun generateWeeklySummary(): WeeklySummaryEntity? {
        val today = java.time.LocalDate.now()
        val weekEnd = today.minusDays(1)
        val weekStart = weekEnd.minusDays(6)
        val prevWeekEnd = weekStart.minusDays(1)
        val prevWeekStart = prevWeekEnd.minusDays(6)

        val startStr = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endStr = weekEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val zone = ZoneId.systemDefault()
        val startEpoch = weekStart.atStartOfDay(zone).toEpochSecond()
        val endEpoch = weekEnd.plusDays(1).atStartOfDay(zone).toEpochSecond()

        val records = healthRecordDao.getRecordsBetweenSync(startEpoch, endEpoch)
        if (records.isEmpty()) return null

        val sleepingRecords = records.filter { it.isSleeping && it.bpm > 0 }
        val restingBpm = if (sleepingRecords.size >= 5) {
            sleepingRecords.map { it.bpm }.sorted().take(sleepingRecords.size / 4).average().toFloat()
        } else {
            records.filter { it.bpm > 0 }.map { it.bpm }.average().toFloat()
        }

        val prevStartEpoch = prevWeekStart.atStartOfDay(zone).toEpochSecond()
        val prevEndEpoch = prevWeekEnd.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val prevRecords = healthRecordDao.getRecordsBetweenSync(prevStartEpoch, prevEndEpoch)
        val prevRestingBpm = if (prevRecords.isNotEmpty()) {
            val prevSleeping = prevRecords.filter { it.isSleeping && it.bpm > 0 }
            if (prevSleeping.size >= 5) {
                prevSleeping.map { it.bpm }.sorted().take(prevSleeping.size / 4).average().toFloat()
            } else {
                prevRecords.filter { it.bpm > 0 }.map { it.bpm }.average().toFloat()
            }
        } else 0f

        val trend = if (prevRestingBpm > 0f) restingBpm - prevRestingBpm else 0f
        val totalSteps = records.sumOf { it.steps }
        val totalSleepMinutes = records.count { it.isSleeping } * 10
        val daysInWeek = 7
        val avgSleepMinutes = totalSleepMinutes / daysInWeek
        val totalActiveCalories = records.filter { !it.isSleeping && it.bpm > 72 }
            .sumOf { ((it.bpm - 72) * 0.05).toInt() }

        val insights = computeCorrelationInsights(
            records, prevRecords, startEpoch, endEpoch, zone, weekStart, restingBpm
        )

        val summary = WeeklySummaryEntity(
            weekStartDate = startStr,
            weekEndDate = endStr,
            avgRestingBpm = restingBpm,
            restingBpmTrend = trend,
            totalSteps = totalSteps,
            avgSleepMinutes = avgSleepMinutes,
            totalActiveCalories = totalActiveCalories,
            insights = WeeklyInsight.listToJson(insights),
        )
        weeklySummaryDao.insert(summary)
        return summary
    }

    private suspend fun computeCorrelationInsights(
        records: List<HealthRecordEntity>,
        prevRecords: List<HealthRecordEntity>,
        startEpoch: Long,
        endEpoch: Long,
        zone: ZoneId,
        weekStart: java.time.LocalDate,
        thisWeekRestingBpm: Float,
    ): List<WeeklyInsight> {
        val insights = mutableListOf<WeeklyInsight>()

        val dailyBuckets = records.groupBy { r ->
            Instant.ofEpochSecond(r.timestamp).atZone(zone).toLocalDate()
        }

        val prevDailyBuckets = prevRecords.groupBy { r ->
            Instant.ofEpochSecond(r.timestamp).atZone(zone).toLocalDate()
        }

        val priorSummaries = weeklySummaryDao.getRecentSync(5)

        // --- 1. SEDENTARY_STREAK (this-week observation) ---
        val fourWeekLookback = startEpoch - 28 * 86400L
        val fourWeekRecords = healthRecordDao.getRecordsBetweenSync(fourWeekLookback, endEpoch)
        val fourWeekAvgSteps = if (fourWeekRecords.isNotEmpty()) {
            val fourWeekDaily = fourWeekRecords.groupBy { r ->
                Instant.ofEpochSecond(r.timestamp).atZone(zone).toLocalDate()
            }.values.map { dayRecs -> dayRecs.sumOf { it.steps } }
            if (fourWeekDaily.isNotEmpty()) fourWeekDaily.average() else 0.0
        } else 0.0

        if (fourWeekAvgSteps > 0) {
            val threshold = fourWeekAvgSteps * 0.5
            val dailySteps = dailyBuckets.entries
                .sortedBy { it.key }
                .map { it.value.sumOf { r -> r.steps } }
            var maxStreak = 0; var streak = 0
            for (daySteps in dailySteps) {
                if (daySteps < threshold) { streak++; maxStreak = maxOf(maxStreak, streak) }
                else streak = 0
            }
            if (maxStreak >= 2) {
                insights.add(WeeklyInsight(
                    kind = InsightKind.SEDENTARY_STREAK,
                    magnitude = maxStreak.toFloat(),
                    direction = -1,
                    displayText = "$maxStreak consecutive low-activity days this week",
                    confidence = null,
                ))
            }
        }

        // --- 2. MULTI_METRIC_WELLNESS (this-week observation) ---
        val prevAvgSteps = prevDailyBuckets.values
            .map { dayRecs -> dayRecs.sumOf { it.steps } }
            .let { if (it.isNotEmpty()) it.average() else 0.0 }
        val thisAvgSteps = dailyBuckets.values
            .map { dayRecs -> dayRecs.sumOf { it.steps } }
            .let { if (it.isNotEmpty()) it.average() else 0.0 }
        val thisSleepMin = records.count { it.isSleeping } * 10
        val prevSleepMin = prevRecords.count { it.isSleeping } * 10
        val prevRestingBpm = if (prevRecords.isNotEmpty()) {
            val s = prevRecords.filter { it.isSleeping && it.bpm > 0 }
            if (s.size >= 5) s.map { it.bpm }.sorted().take(s.size / 4).average().toFloat()
            else prevRecords.filter { it.bpm > 0 }.map { it.bpm }.average().toFloat()
        } else 0f

        val hrUp = prevRestingBpm > 0f && thisWeekRestingBpm > prevRestingBpm + 2
        val sleepDown = prevSleepMin > 0 && thisSleepMin < prevSleepMin * 0.85
        val stepsDown = prevAvgSteps > 0 && thisAvgSteps < prevAvgSteps * 0.7

        if (hrUp && sleepDown && stepsDown) {
            insights.add(WeeklyInsight(
                kind = InsightKind.MULTI_METRIC_WELLNESS,
                magnitude = (thisWeekRestingBpm - prevRestingBpm),
                direction = -1,
                displayText = "Recovery suggested: HR up, sleep down, activity down vs last week",
                confidence = null,
            ))
        }

        // --- 3. SLEEP_HR_CORRELATION (rolling, need 4+ weeks) ---
        if (dailyBuckets.size >= 5) {
            val sortedDays = dailyBuckets.entries.sortedBy { it.key }
            val afterPoorSleepBpms = mutableListOf<Float>()
            val afterGoodSleepBpms = mutableListOf<Float>()
            for (i in 1 until sortedDays.size) {
                val prevDayRecs = sortedDays[i - 1].value
                val sleepMins = prevDayRecs.count { it.isSleeping } * 10
                val todayRecs = sortedDays[i].value
                val todaySleepBpm = todayRecs.filter { it.isSleeping && it.bpm > 0 }
                if (todaySleepBpm.isEmpty()) continue
                val todayResting = todaySleepBpm.map { it.bpm }.average().toFloat()
                if (sleepMins < 360) afterPoorSleepBpms.add(todayResting)
                else afterGoodSleepBpms.add(todayResting)
            }
            if (afterPoorSleepBpms.size >= 2 && afterGoodSleepBpms.size >= 2) {
                val diff = afterPoorSleepBpms.average() - afterGoodSleepBpms.average()
                val conf = minOf(1f, (afterPoorSleepBpms.size + afterGoodSleepBpms.size) / 10f)
                if (diff > 1 && conf >= 0.6f) {
                    insights.add(WeeklyInsight(
                        kind = InsightKind.SLEEP_HR_CORRELATION,
                        magnitude = diff.toFloat(),
                        direction = -1,
                        displayText = "Resting HR was ${diff.toInt()}bpm higher after poor sleep (<6h)",
                        confidence = conf,
                    ))
                }
            }
        }

        // --- 4. SLEEP_CONSISTENCY_HR (rolling) ---
        if (dailyBuckets.size >= 5) {
            val dailySleepMins = dailyBuckets.values.map { recs -> recs.count { it.isSleeping } * 10 }
            val dailyRestingHrs = dailyBuckets.values.mapNotNull { recs ->
                val s = recs.filter { it.isSleeping && it.bpm > 0 }
                if (s.size >= 3) s.map { it.bpm }.average().toFloat() else null
            }
            if (dailySleepMins.size >= 5 && dailyRestingHrs.size >= 5) {
                val sleepStdDev = stdDev(dailySleepMins.map { it.toFloat() })
                val hrStdDev = stdDev(dailyRestingHrs)
                val isConsistent = sleepStdDev < 60
                val conf = minOf(1f, dailySleepMins.size / 7f)
                if (conf >= 0.6f) {
                    if (isConsistent && hrStdDev < 3f) {
                        insights.add(WeeklyInsight(
                            kind = InsightKind.SLEEP_CONSISTENCY_HR,
                            magnitude = hrStdDev,
                            direction = 1,
                            displayText = "Consistent sleep schedule linked to stable HR (±${hrStdDev.toInt()}bpm)",
                            confidence = conf,
                        ))
                    } else if (!isConsistent && hrStdDev > 5f) {
                        insights.add(WeeklyInsight(
                            kind = InsightKind.SLEEP_CONSISTENCY_HR,
                            magnitude = hrStdDev,
                            direction = -1,
                            displayText = "Irregular sleep may be causing HR variability (±${hrStdDev.toInt()}bpm)",
                            confidence = conf,
                        ))
                    }
                }
            }
        }

        // --- 5. ACTIVITY_SLEEP_QUALITY (rolling) ---
        if (dailyBuckets.size >= 5) {
            val avgDailySteps = dailyBuckets.values
                .map { recs -> recs.sumOf { it.steps } }.average()
            val sortedDays = dailyBuckets.entries.sortedBy { it.key }
            val afterHighActivitySleep = mutableListOf<Int>()
            val afterLowActivitySleep = mutableListOf<Int>()
            for (i in 0 until sortedDays.size - 1) {
                val daySteps = sortedDays[i].value.sumOf { it.steps }
                val nextDaySleep = sortedDays[i + 1].value.count { it.isSleeping } * 10
                if (nextDaySleep == 0) continue
                if (daySteps > avgDailySteps * 1.2) afterHighActivitySleep.add(nextDaySleep)
                else if (daySteps < avgDailySteps * 0.8) afterLowActivitySleep.add(nextDaySleep)
            }
            if (afterHighActivitySleep.size >= 2 && afterLowActivitySleep.size >= 2) {
                val highAvg = afterHighActivitySleep.average()
                val lowAvg = afterLowActivitySleep.average()
                val diffPct = ((highAvg - lowAvg) / lowAvg * 100).toInt()
                val conf = minOf(1f, (afterHighActivitySleep.size + afterLowActivitySleep.size) / 8f)
                if (kotlin.math.abs(diffPct) > 5 && conf >= 0.6f) {
                    val better = diffPct > 0
                    insights.add(WeeklyInsight(
                        kind = InsightKind.ACTIVITY_SLEEP_QUALITY,
                        magnitude = diffPct.toFloat(),
                        direction = if (better) 1 else -1,
                        displayText = if (better) "High-activity days led to ${diffPct}% more sleep"
                        else "High-activity days led to ${-diffPct}% less sleep",
                        confidence = conf,
                    ))
                }
            }
        }

        // --- 6. LATE_ACTIVITY_IMPACT (rolling) ---
        if (dailyBuckets.size >= 5) {
            val sortedDays = dailyBuckets.entries.sortedBy { it.key }
            val lateActiveSleepOnsets = mutableListOf<Float>()
            val earlyQuietSleepOnsets = mutableListOf<Float>()
            for (entry in sortedDays) {
                val dayRecs = entry.value
                val lateRecs = dayRecs.filter { r ->
                    val hour = Instant.ofEpochSecond(r.timestamp).atZone(zone).hour
                    hour in 21..23 && !r.isSleeping
                }
                val lateMovement = if (lateRecs.isNotEmpty()) lateRecs.map { it.movement }.average() else 0.0
                val firstSleep = dayRecs.firstOrNull { it.isSleeping }
                if (firstSleep != null) {
                    val sleepHour = Instant.ofEpochSecond(firstSleep.timestamp).atZone(zone)
                        .let { it.hour + it.minute / 60f }
                    val normalized = if (sleepHour < 12) sleepHour + 24 else sleepHour
                    if (lateMovement > 40) lateActiveSleepOnsets.add(normalized)
                    else earlyQuietSleepOnsets.add(normalized)
                }
            }
            if (lateActiveSleepOnsets.size >= 2 && earlyQuietSleepOnsets.size >= 2) {
                val diff = (lateActiveSleepOnsets.average() - earlyQuietSleepOnsets.average()) * 60
                val conf = minOf(1f, (lateActiveSleepOnsets.size + earlyQuietSleepOnsets.size) / 8f)
                if (diff > 10 && conf >= 0.6f) {
                    insights.add(WeeklyInsight(
                        kind = InsightKind.LATE_ACTIVITY_IMPACT,
                        magnitude = diff.toFloat(),
                        direction = -1,
                        displayText = "Late evening activity linked to ${diff.toInt()}min later sleep onset",
                        confidence = conf,
                    ))
                }
            }
        }

        // --- 7. RESTING_HR_TREND (trend, need 4+ weeks) ---
        if (priorSummaries.size >= 3) {
            val recentBpms = priorSummaries.take(4).reversed().map { it.avgRestingBpm }
            val slopes = recentBpms.zipWithNext { a, b -> b - a }
            val avgSlope = slopes.average().toFloat()
            if (kotlin.math.abs(avgSlope) > 1f) {
                val weeksCount = slopes.size + 1
                val dir = if (avgSlope > 0) -1 else 1
                val label = if (avgSlope > 0) "up" else "down"
                insights.add(WeeklyInsight(
                    kind = InsightKind.RESTING_HR_TREND,
                    magnitude = avgSlope,
                    direction = dir,
                    displayText = "Resting HR trending $label by ${kotlin.math.abs(avgSlope).toInt()}bpm/week over $weeksCount weeks",
                    confidence = minOf(1f, weeksCount / 4f),
                ))
            }
        }

        // --- 8. SLEEP_QUALITY_TREND ---
        val thisWeekSleepMin = records.count { it.isSleeping } * 10
        val prevWeekSleepMin = prevRecords.count { it.isSleeping } * 10
        if (prevWeekSleepMin > 0 && thisWeekSleepMin > 0) {
            val thisAvgQuality = thisWeekSleepMin / 7
            val prevAvgQuality = prevWeekSleepMin / 7
            val diff = thisAvgQuality - prevAvgQuality
            if (kotlin.math.abs(diff) > 10) {
                val dir = if (diff > 0) 1 else -1
                val label = if (diff > 0) "improving" else "declining"
                insights.add(WeeklyInsight(
                    kind = InsightKind.SLEEP_QUALITY_TREND,
                    magnitude = diff.toFloat(),
                    direction = dir,
                    displayText = "Avg sleep ${label}: ${thisAvgQuality / 60}h ${thisAvgQuality % 60}m vs ${prevAvgQuality / 60}h ${prevAvgQuality % 60}m last week",
                    confidence = 0.8f,
                ))
            }
        }

        return insights
    }

    private fun stdDev(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }

    suspend fun insertWeeklySummary(summary: WeeklySummaryEntity) {
        weeklySummaryDao.insert(summary)
    }

    suspend fun logSyncFailure(errorMessage: String, triggerType: String = "AUTOMATIC") {
        syncLogDao.insert(
            SyncLogEntry(
                timestamp = System.currentTimeMillis() / 1000,
                recordsReceived = 0,
                status = "FAILED",
                errorMessage = errorMessage,
                triggerType = triggerType,
            )
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: HealthRepository? = null

        fun getInstance(context: Context): HealthRepository {
            return INSTANCE ?: synchronized(this) {
                val db = HealthDatabase.getInstance(context)
                val instance = HealthRepository(
                    healthRecordDao = db.healthRecordDao(),
                    dailySummaryDao = db.dailySummaryDao(),
                    syncLogDao = db.syncLogDao(),
                    weeklySummaryDao = db.weeklySummaryDao(),
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
