package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process state mirror for the playback service and the WMS player UI. */
object AbLoopStateBus {
    private val mutableState = MutableStateFlow(AbLoopState())
    val state = mutableState.asStateFlow()

    internal fun publish(value: AbLoopState) {
        mutableState.value = value
    }
}
