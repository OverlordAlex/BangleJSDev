package com.itsabugnotafeature.scrolltimesync.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_log")
data class SyncLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val recordsReceived: Int,
    val watchBatteryPercent: Int? = null,
    val status: String,
    val errorMessage: String? = null,
    val triggerType: String = "AUTOMATIC",
)
