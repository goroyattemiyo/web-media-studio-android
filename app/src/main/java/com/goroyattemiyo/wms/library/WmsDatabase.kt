package com.goroyattemiyo.wms.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goroyattemiyo.wms.playlist.PlaylistDao
import com.goroyattemiyo.wms.playlist.PlaylistEntity
import com.goroyattemiyo.wms.playlist.PlaylistEntryEntity

@Database(
    entities = [MediaEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class WmsDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_entries` (
                        `playlistId` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `mediaId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_entries_mediaId` ON `playlist_entries` (`mediaId`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media` ADD COLUMN `author` TEXT")
            }
        }
    }
}
