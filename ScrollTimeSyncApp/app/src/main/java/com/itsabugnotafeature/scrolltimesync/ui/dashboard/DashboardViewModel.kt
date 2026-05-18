package com.itsabugnotafeature.scrolltimesync.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklySummaryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class CardInsight(
    val primaryValue: String,
    val comparison: String,
    val trend: Trend = Trend.NEUTRAL,
)

enum class Trend { UP, DOWN, NEUTRAL }

data class CalorieBreakdown(
    val stepCalories: Int = 0,
    val hrCalories: Int = 0,
    val activeCalories: Int? = null,
    val bmrCalories: Int? = null,
)

data class DashboardState(
    val heartRateInsight: CardInsight? = null,
    val stepsInsight: CardInsight? = null,
    val sleepInsight: CardInsight? = null,
    val caloriesInsight: CardInsight? = null,
    val calorieBreakdown: CalorieBreakdown = CalorieBreakdown(),
    val recentRecords: List<HealthRecordEntity> = emptyList(),
    val sleepQuality: Int? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository.getInstance(application)
    private val profilePrefs = application.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    val userAge: Int get() = profilePrefs.getInt("user_age", 0)
    val userWeightKg: Float get() = profilePrefs.getFloat("user_weight_kg", 0f)

    val latestSync: StateFlow<SyncLogEntry?> = repository.latestSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestSummary: StateFlow<DailySummaryEntity?> = repository.latestSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentRecords: StateFlow<List<HealthRecordEntity>> = repository.getLatestRecords(144)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestWeeklySummary: StateFlow<WeeklySummaryEntity?> = repository.latestWeeklySummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.latestSummary.collect { summary ->
                if (summary != null) {
                    computeInsights(summary)
                }
            }
        }
    }

    private suspend fun computeInsights(latest: DailySummaryEntity) {
        val avgRestingBpm = repository.getAvgRestingBpm(7)
        val avgSteps = repository.getAvgSteps(7)
        val avgSleep = repository.getAvgSleepMinutes(7)
        val avgCalories = repository.getAvgCalories(7)

        val hrInsight = buildHeartRateInsight(latest, avgRestingBpm)
        val stepsInsight = buildStepsInsight(latest, avgSteps)
        val sleepQualityResult = repository.computeSleepQuality()
        val sleepInsight = buildSleepInsight(latest, avgSleep, sleepQualityResult?.score)
        val (caloriesInsight, calorieBreakdown) = buildCaloriesInsight(latest, avgCalories)

        _dashboardState.value = DashboardState(
            heartRateInsight = hrInsight,
            stepsInsight = stepsInsight,
            sleepInsight = sleepInsight,
            caloriesInsight = caloriesInsight,
            calorieBreakdown = calorieBreakdown,
            sleepQuality = sleepQualityResult?.score,
        )
    }

    private fun buildHeartRateInsight(summary: DailySummaryEntity, avg7d: Float?): CardInsight {
        val resting = summary.restingBpm
        val primary = "${resting.toInt()} bpm resting"
        val comparison = if (avg7d != null && avg7d > 0) {
            val diff = resting - avg7d
            val trend = when {
                diff < -2 -> Trend.DOWN
                diff > 2 -> Trend.UP
                else -> Trend.NEUTRAL
            }
            val arrow = when (trend) {
                Trend.DOWN -> "improving"
                Trend.UP -> "elevated"
                Trend.NEUTRAL -> "steady"
            }
            return CardInsight(primary, "vs ${avg7d.toInt()} avg (7d) — $arrow", trend)
        } else {
            "No comparison data yet"
        }
        return CardInsight(primary, comparison)
    }

    private fun buildStepsInsight(summary: DailySummaryEntity, avg7d: Float?): CardInsight {
        val steps = summary.totalSteps
        val primary = "%,d steps".format(steps)
        val comparison = if (avg7d != null && avg7d > 0) {
            val pct = ((steps - avg7d) / avg7d * 100).toInt()
            val descriptor = if (pct >= 0) "$pct% above" else "${-pct}% below"
            "$descriptor 7-day avg"
        } else {
            "No comparison data yet"
        }
        return CardInsight(primary, comparison)
    }

    private fun buildSleepInsight(summary: DailySummaryEntity, avg7d: Float?, quality: Int?): CardInsight {
        val mins = summary.totalSleepMinutes
        val hours = mins / 60
        val remainder = mins % 60
        val qualitySuffix = if (quality != null) " — Quality: $quality/100" else ""
        val primary = "${hours}h ${remainder}m$qualitySuffix"
        val comparison = if (avg7d != null && avg7d > 0) {
            val avgH = (avg7d / 60).toInt()
            val avgM = (avg7d % 60).toInt()
            "vs ${avgH}h ${avgM}m avg (7d)"
        } else {
            "No comparison data yet"
        }
        val trend = when {
            mins < 360 -> Trend.DOWN
            avg7d != null && mins > avg7d * 1.1 -> Trend.UP
            else -> Trend.NEUTRAL
        }
        return CardInsight(primary, comparison, trend)
    }

    private suspend fun buildCaloriesInsight(summary: DailySummaryEntity, avg7d: Float?): Pair<CardInsight, CalorieBreakdown> {
        val (activeCal, bmrCal) = computeAppEstimatedCalories()
        val breakdown = CalorieBreakdown(
            stepCalories = summary.stepCalories,
            hrCalories = summary.bpmCalories,
            activeCalories = activeCal,
            bmrCalories = bmrCal,
        )
        val primary = if (activeCal != null && bmrCal != null) {
            "%,d kcal total".format(activeCal + bmrCal)
        } else {
            "%,d kcal".format(summary.stepCalories + summary.bpmCalories)
        }
        val comparison = if (activeCal != null) {
            "Active: %,d / BMR: %,d".format(activeCal, bmrCal ?: 0)
        } else {
            "Step: %,d / HR: %,d".format(summary.stepCalories, summary.bpmCalories)
        }
        return CardInsight(primary, comparison) to breakdown
    }

    private suspend fun computeAppEstimatedCalories(): Pair<Int?, Int?> {
        val age = profilePrefs.getInt("user_age", 0)
        val height = profilePrefs.getInt("user_height_cm", 0)
        val weight = profilePrefs.getFloat("user_weight_kg", 0f)
        if (age <= 0 || weight <= 0f) return null to null

        val bmrPerDay = if (height > 0) {
            10.0 * weight + 6.25 * height - 5.0 * age - 78
        } else {
            10.0 * weight + 984.5 - 5.0 * age
        }

        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toEpochSecond()
        val tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay(zone).toEpochSecond()

        val todayRecords = repository.getRecordsBetween(todayStart, tomorrowStart).first()
        if (todayRecords.isEmpty()) return null to bmrPerDay.toInt()

        val restingBpm = todayRecords
            .filter { it.isSleeping && it.bpm > 0 }
            .map { it.bpm }
            .sorted()
            .let { sorted -> if (sorted.size >= 5) sorted.take(sorted.size / 4).average() else null }
            ?.toInt() ?: 60

        val activeThreshold = (restingBpm * 1.2).toInt()

        var totalActiveCal = 0.0
        for (record in todayRecords) {
            if (!record.isSleeping && record.bpm > activeThreshold) {
                val excessHr = record.bpm - activeThreshold
                totalActiveCal += excessHr * weight * 0.001 * 10.0
            }
        }
        return totalActiveCal.toInt() to bmrPerDay.toInt()
    }
}
