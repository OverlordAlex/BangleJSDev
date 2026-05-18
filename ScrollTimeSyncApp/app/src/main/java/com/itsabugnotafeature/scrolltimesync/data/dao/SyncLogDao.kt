package com.itsabugnotafeature.scrolltimesync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert
    suspend fun insert(entry: SyncLogEntry)

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSync(): Flow<SyncLogEntry?>

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SyncLogEntry>>

    @Query("SELECT * FROM sync_log WHERE watchBatteryPercent IS NOT NULL ORDER BY timestamp ASC")
    fun getSyncsWithBattery(): Flow<List<SyncLogEntry>>

    @Query("DELETE FROM sync_log WHERE timestamp < :beforeEpoch")
    suspend fun deleteBefore(beforeEpoch: Long)
}
