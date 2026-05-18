package com.itsabugnotafeature.scrolltimesync.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val date: String,
    val avgBpm: Float,
    val restingBpm: Float,
    val minBpm: Int = 0,
    val maxBpm: Int = 0,
    val stepCalories: Int,
    val bpmCalories: Int,
    val totalSleepMinutes: Int,
    val totalSteps: Int,
    val sleepQuality: Int? = null,
)
