package com.goroyattemiyo.wms.ui.playback

import com.goroyattemiyo.wms.library.MediaEntity

data class PlaybackRequest(
    val requestId: Int,
    val mediaId: String,
    val queue: List<MediaEntity>,
)
