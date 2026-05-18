package com.itsabugnotafeature.scrolltimesync.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_summaries")
data class WeeklySummaryEntity(
    @PrimaryKey val weekStartDate: String,
    val weekEndDate: String,
    val avgRestingBpm: Float,
    val restingBpmTrend: Float,
    val totalSteps: Int,
    val avgSleepMinutes: Int,
    val totalActiveCalories: Int,
    val avgSleepQuality: Int? = null,
    val insights: String? = null,
    val createdAt: Long = System.currentTimeMillis() / 1000,
)
