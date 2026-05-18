package com.itsabugnotafeature.scrolltimesync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summaries ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summaries ORDER BY date DESC LIMIT :count")
    fun getLatestDays(count: Int): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getSummariesBetween(startDate: String, endDate: String): Flow<List<DailySummaryEntity>>

    @Query("SELECT AVG(restingBpm) FROM (SELECT restingBpm FROM daily_summaries ORDER BY date DESC LIMIT :days)")
    suspend fun getAvgRestingBpm(days: Int): Float?

    @Query("SELECT AVG(totalSteps) FROM (SELECT totalSteps FROM daily_summaries ORDER BY date DESC LIMIT :days)")
    suspend fun getAvgSteps(days: Int): Float?

    @Query("SELECT AVG(totalSleepMinutes) FROM (SELECT totalSleepMinutes FROM daily_summaries ORDER BY date DESC LIMIT :days)")
    suspend fun getAvgSleepMinutes(days: Int): Float?

    @Query("SELECT AVG(stepCalories + bpmCalories) FROM (SELECT stepCalories, bpmCalories FROM daily_summaries ORDER BY date DESC LIMIT :days)")
    suspend fun getAvgCalories(days: Int): Float?

    @Query("DELETE FROM daily_summaries WHERE date < :beforeDate")
    suspend fun deleteSummariesBefore(beforeDate: String)
}
