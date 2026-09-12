package com.goroyattemiyo.wms.library

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MediaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WmsDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
