package com.itsabugnotafeature.scrolltimesync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: WeeklySummaryEntity)

    @Query("SELECT * FROM weekly_summaries ORDER BY weekStartDate DESC")
    fun getAll(): Flow<List<WeeklySummaryEntity>>

    @Query("SELECT * FROM weekly_summaries ORDER BY weekStartDate DESC LIMIT 1")
    fun getLatest(): Flow<WeeklySummaryEntity?>

    @Query("SELECT * FROM weekly_summaries ORDER BY weekStartDate DESC LIMIT :count")
    suspend fun getRecentSync(count: Int): List<WeeklySummaryEntity>
}
