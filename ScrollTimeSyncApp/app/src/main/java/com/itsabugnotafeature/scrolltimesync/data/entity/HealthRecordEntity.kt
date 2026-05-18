package com.itsabugnotafeature.scrolltimesync.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_records",
    indices = [Index(value = ["timestamp"], unique = true)]
)
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val steps: Int,
    val isSleeping: Boolean,
    val movement: Int = 0,
)
