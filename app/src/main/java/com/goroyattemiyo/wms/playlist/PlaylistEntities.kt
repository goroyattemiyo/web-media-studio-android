package com.goroyattemiyo.wms.playlist

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.goroyattemiyo.wms.library.MediaEntity

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlistId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId")],
)
data class PlaylistEntryEntity(
    val playlistId: String,
    val mediaId: String,
    val position: Int,
)

data class PlaylistSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int,
)

data class PlaylistMediaItem(
    @androidx.room.Embedded val media: MediaEntity,
    val playlistPosition: Int,
)
