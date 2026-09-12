package com.goroyattemiyo.wms.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media")
    suspend fun getAll(): List<MediaEntity>

    @Upsert
    suspend fun upsert(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE media SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updateLastPosition(id: String, positionMs: Long)
}
