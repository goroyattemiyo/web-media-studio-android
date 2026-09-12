package com.goroyattemiyo.wms.acquisition

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedAcquisitionStateTest {
    @Test
    fun startedCreatesRunningState() {
        ManagedAcquisitionBus.started("https://example.com/media")

        val state = ManagedAcquisitionBus.state.value
        assertEquals(AcquisitionJobStatus.RUNNING, state.status)
        assertEquals("https://example.com/media", state.sourceUrl)
        assertEquals(0f, state.progressPercent)
        assertEquals("取得を開始しています", state.progressMessage)
        assertNull(state.savedPath)
        assertNull(state.errorCode)
    }

    @Test
    fun progressIsClampedWhileRunning() {
        ManagedAcquisitionBus.started("https://example.com/media")

        ManagedAcquisitionBus.progress(125f, "処理中")

        val state = ManagedAcquisitionBus.state.value
        assertEquals(AcquisitionJobStatus.RUNNING, state.status)
        assertEquals(100f, state.progressPercent)
        assertEquals("処理中", state.progressMessage)
    }

    @Test
    fun successKeepsSourceAndPublishesFinalMetadata() {
        ManagedAcquisitionBus.started("https://example.com/media")

        ManagedAcquisitionBus.succeeded(
            AcquisitionResult(
                file = File("/tmp/final.mp3"),
                title = "Test title",
                provider = "YouTube",
            ),
        )

        val state = ManagedAcquisitionBus.state.value
        assertEquals(AcquisitionJobStatus.SUCCEEDED, state.status)
        assertEquals("https://example.com/media", state.sourceUrl)
        assertEquals(100f, state.progressPercent)
        assertEquals("保存完了", state.progressMessage)
        assertEquals("/tmp/final.mp3", state.savedPath)
        assertEquals("Test title", state.savedTitle)
        assertEquals("YouTube", state.savedProvider)
        assertNull(state.errorCode)
    }

    @Test
    fun failurePublishesErrorAndStopsRunningState() {
        ManagedAcquisitionBus.started("https://example.com/media")

        ManagedAcquisitionBus.failed(
            sourceUrl = "https://example.com/media",
            code = "STORAGE_FULL",
            message = "端末の空き容量が不足しています。",
        )

        val state = ManagedAcquisitionBus.state.value
        assertEquals(AcquisitionJobStatus.FAILED, state.status)
        assertEquals("https://example.com/media", state.sourceUrl)
        assertEquals("STORAGE_FULL", state.errorCode)
        assertEquals("端末の空き容量が不足しています。", state.errorMessage)
        assertNull(state.savedPath)
    }

    @Test
    fun canceledPublishesCanceledState() {
        ManagedAcquisitionBus.started("https://example.com/media")

        ManagedAcquisitionBus.canceled("https://example.com/media")

        val state = ManagedAcquisitionBus.state.value
        assertEquals(AcquisitionJobStatus.CANCELED, state.status)
        assertEquals("https://example.com/media", state.sourceUrl)
        assertEquals("キャンセルしました", state.progressMessage)
        assertNull(state.savedPath)
    }
}
