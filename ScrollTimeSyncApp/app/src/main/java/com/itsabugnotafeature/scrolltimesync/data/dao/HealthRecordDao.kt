package com.itsabugnotafeature.scrolltimesync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<HealthRecordEntity>)

    @Query("SELECT * FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch ORDER BY timestamp ASC")
    fun getRecordsBetween(startEpoch: Long, endEpoch: Long): Flow<List<HealthRecordEntity>>

    @Query("SELECT * FROM health_records ORDER BY timestamp DESC LIMIT :count")
    fun getLatestRecords(count: Int): Flow<List<HealthRecordEntity>>

    @Query("SELECT * FROM health_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestRecord(): Flow<HealthRecordEntity?>

    @Query("SELECT AVG(bpm) FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch AND bpm > 0")
    suspend fun getAvgBpmBetween(startEpoch: Long, endEpoch: Long): Float?

    @Query("SELECT SUM(steps) FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch")
    suspend fun getTotalStepsBetween(startEpoch: Long, endEpoch: Long): Int?

    @Query("SELECT SUM(CASE WHEN isSleeping = 1 THEN 10 ELSE 0 END) FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch")
    suspend fun getTotalSleepMinutesBetween(startEpoch: Long, endEpoch: Long): Int?

    @Query("SELECT MIN(bpm) FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch AND bpm > 0")
    suspend fun getMinBpmBetween(startEpoch: Long, endEpoch: Long): Int?

    @Query("SELECT MAX(bpm) FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch AND bpm > 0")
    suspend fun getMaxBpmBetween(startEpoch: Long, endEpoch: Long): Int?

    @Query("SELECT COUNT(*) FROM health_records")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM health_records WHERE timestamp BETWEEN :startEpoch AND :endEpoch ORDER BY timestamp ASC")
    suspend fun getRecordsBetweenSync(startEpoch: Long, endEpoch: Long): List<HealthRecordEntity>

    @Query("SELECT MIN(timestamp) FROM health_records")
    suspend fun getOldestTimestamp(): Long?

    @Query("DELETE FROM health_records WHERE timestamp < :beforeEpoch")
    suspend fun deleteRecordsBefore(beforeEpoch: Long)
}
