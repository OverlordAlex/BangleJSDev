package com.itsabugnotafeature.scrolltimesync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.itsabugnotafeature.scrolltimesync.data.dao.DailySummaryDao
import com.itsabugnotafeature.scrolltimesync.data.dao.HealthRecordDao
import com.itsabugnotafeature.scrolltimesync.data.dao.SyncLogDao
import com.itsabugnotafeature.scrolltimesync.data.dao.WeeklySummaryDao
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import com.itsabugnotafeature.scrolltimesync.data.entity.WeeklySummaryEntity

@Database(
    entities = [
        HealthRecordEntity::class,
        DailySummaryEntity::class,
        SyncLogEntry::class,
        WeeklySummaryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun weeklySummaryDao(): WeeklySummaryDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_log ADD COLUMN triggerType TEXT NOT NULL DEFAULT 'AUTOMATIC'")
            }
        }

        fun getInstance(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "scrolltimesync.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
