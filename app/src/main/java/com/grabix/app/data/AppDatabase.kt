package com.grabix.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, HistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    abstract fun playlists(): PlaylistDao
    abstract fun history(): HistoryDao

    companion object {
        /** Adds play history without touching existing downloads / playlists. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_history` (`uri` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`artist` TEXT, `isVideo` INTEGER NOT NULL, `artworkUri` TEXT, `lastPlayedAt` INTEGER NOT NULL, " +
                        "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        /** Playlist sub-folders and cached link info for faster downloads. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `subDir` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `infoJson` TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "grabix.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
