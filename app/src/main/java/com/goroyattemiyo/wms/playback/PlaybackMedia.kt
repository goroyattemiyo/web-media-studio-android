package com.goroyattemiyo.wms.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.goroyattemiyo.wms.library.MediaEntity
import java.io.File

fun MediaEntity.toPlaybackMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(Uri.fromFile(File(localPath)))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author ?: provider)
            .setIsPlayable(true)
            .setArtworkUri(
                artworkUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
                    ?: Uri.parse("android.resource://com.goroyattemiyo.wms/drawable/wms_emblem"),
            )
            .build(),
    )
    .build()
