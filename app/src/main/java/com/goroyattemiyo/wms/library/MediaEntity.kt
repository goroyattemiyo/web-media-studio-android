package com.goroyattemiyo.wms.library

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val provider: String,
    val originalUrl: String,
    val localPath: String,
    val mimeType: String,
    val mediaType: String,
    val durationMs: Long,
    val fileSize: Long,
    val artworkUrl: String?,
    val createdAt: Long,
    val lastPositionMs: Long,
)
