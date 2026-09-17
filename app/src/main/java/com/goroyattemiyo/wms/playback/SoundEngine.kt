package com.goroyattemiyo.wms.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionResult
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns WMS sound. Renderer notifications are always routed onto the service main handler. */
@UnstableApi
internal class SoundEngine(
    context: Context,
    private val player: ExoPlayer,
    private val processor: SafeBoostProcessor,
    private val handler: Handler,
    private val preferences: SharedPreferences,
) : AutoCloseable {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var closed = false
    private var routeMonitoring = false
    private var sessionId = 0
    private var equalizer: Equalizer? = null
    private var manualProfile: SoundPreset? = null
    private var volume = AppVolumeState()
    private var eq = SoundEqState()

    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onRouteChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onRouteChanged()
    }

    init {
        val saved = preferences.getInt(KEY_VOLUME, 100).coerceIn(0, 100)
        val lastAudible = preferences.getInt(KEY_LAST_AUDIBLE, 100).coerceIn(1, 100)
        volume = AppVolumeState(saved, lastAudible)
        val custom = SoundPresetStore.decode(preferences.getString(KEY_CUSTOM, null))
            .filter { it.id.startsWith("user:") }
        manualProfile = SoundPresetStore.decode(preferences.getString(KEY_MANUAL, null))
            .firstOrNull { it.id == "manual" }
        val savedSelection = preferences.getString(KEY_SELECTED, "builtin:flat")
        val selected = if (savedSelection == "manual") null else
            (savedSelection?.takeIf { id -> (SoundPresets.builtIns + custom).any { it.id == id } } ?: "builtin:flat")
        eq = SoundEqState(
            enabled = preferences.getBoolean(KEY_EQ_ENABLED, false),
            selectedPresetId = selected,
            customPresets = custom,
        )
        processor.onPcmSupportChanged = { supported ->
            handler.post { if (!closed) onPcmSupportChanged(supported) }
        }
        routeMonitoring = runCatching { audioManager.registerAudioDeviceCallback(routeCallback, handler) }.isSuccess
        player.volume = saved / 100f
        if (!routeMonitoring) volume = volume.copy(message = "出力先を監視できないためBOOSTを無効化しました。")
        publish()
    }

    private fun publish() {
        AppVolumeBus.publish(volume)
        SoundEqBus.publish(eq)
    }

    private fun status(message: String): Int {
        volume = volume.copy(message = message)
        publish()
        return SessionResult.RESULT_ERROR_BAD_VALUE
    }

    private fun persistVolume() {
        preferences.edit()
            .putInt(KEY_VOLUME, volume.percent.coerceAtMost(100))
            .putInt(KEY_LAST_AUDIBLE, volume.lastAudiblePercent.coerceAtMost(100))
            .putBoolean(KEY_BOOST, false)
            .apply()
    }

    private fun applyVolume() {
        val requested = volume.percent
        processor.setBoostGain(
            if (requested > 100 && volume.boostEnabled && volume.boostAvailable && !eq.enabled)
                requested / 100f else 1f,
        )
        val expected = requested.coerceAtMost(100) / 100f
        if (abs(player.volume - expected) > 0.0001f) player.volume = expected
        persistVolume()
        publish()
    }

    fun onPlayerVolumeChanged(value: Float) {
        if (closed) return
        val expected = volume.percent.coerceAtMost(100) / 100f
        if (abs(expected - value) < 0.005f) return
        volume = volume.withBoost(false).withPercent((value * 100f).roundToInt())
            .copy(message = "外部の音量操作に合わせました。")
        applyVolume()
    }

    private fun onPcmSupportChanged(supported: Boolean) {
        volume = volume.copy(boostAvailable = supported && routeMonitoring)
        if (!volume.boostAvailable && volume.boostEnabled) {
            volume = volume.withBoost(false).copy(message = "この出力ではBOOSTを利用できません。100%以下に戻しました。")
            applyVolume()
        } else publish()
    }

    private fun onRouteChanged() {
        if (closed || !volume.boostEnabled) return
        volume = volume.withBoost(false).copy(message = "出力先が変わったためBOOSTを解除しました。")
        applyVolume()
    }

    fun reportLimiterActivity() {
        if (closed || volume.limitedFrames == processor.limitedFrames) return
        volume = volume.copy(limitedFrames = processor.limitedFrames)
        AppVolumeBus.publish(volume)
    }

    fun onAudioSessionIdChanged(newSessionId: Int) {
        if (closed || (newSessionId == sessionId && equalizer != null)) return
        releaseEq()
        sessionId = newSessionId
        if (newSessionId <= 0) {
            eq = eq.copy(supported = false, bands = emptyList(), message = "音声セッション待機中です。")
            publish()
            return
        }
        var candidate: Equalizer? = null
        try {
            val effect = Equalizer(0, newSessionId)
            candidate = effect
            check(effect.hasControl()) { "EQの制御権がありません" }
            val count = effect.numberOfBands.toInt()
            check(count in 1..32) { "対応帯域を取得できません" }
            val range = effect.bandLevelRange
            check(range.size >= 2 && range[0] < range[1]) { "EQ調整幅が不正です" }
            equalizer = effect
            val bands = (0 until count).map { index ->
                SoundBand((effect.getCenterFreq(index.toShort()) / 1000).coerceAtLeast(1), 0)
            }
            eq = eq.copy(supported = true, bands = bands,
                minGainMb = range[0].toInt(), maxGainMb = range[1].toInt(),
                message = "${count}バンドのイコライザーに接続しました。")
            val profile = (SoundPresets.builtIns + eq.customPresets)
                .firstOrNull { it.id == eq.selectedPresetId } ?: manualProfile ?: SoundPresets.builtIns.first()
            val mapped = bands.map { band ->
                band.copy(gainMb = SoundPresets.gainAt(profile, band.frequencyHz, eq.minGainMb, eq.maxGainMb))
            }
            writeEq(mapped, eq.enabled)
            eq = eq.copy(bands = mapped)
        } catch (error: Exception) {
            runCatching { candidate?.setEnabled(false) }
            runCatching { candidate?.release() }
            equalizer = null
            eq = eq.copy(supported = false, enabled = false, bands = emptyList(),
                message = "EQを利用できません: ${error.javaClass.simpleName}")
        }
        publish()
    }

    private fun writeEq(bands: List<SoundBand>, enabled: Boolean) {
        val effect = equalizer ?: error("EQ未接続")
        bands.forEachIndexed { index, band -> effect.setBandLevel(index.toShort(), band.gainMb.toShort()) }
        check(effect.setEnabled(enabled) == AudioEffect.SUCCESS) { "EQの切り替えに失敗しました" }
    }

    private fun persistEq() {
        preferences.edit()
            .putBoolean(KEY_EQ_ENABLED, eq.enabled)
            .putString(KEY_SELECTED, eq.selectedPresetId ?: "manual")
            .putString(KEY_MANUAL, SoundPresetStore.encode(listOf(SoundPreset("manual", "手動設定",
                eq.bands.map { SoundPoint(it.frequencyHz, it.gainMb) }))))
            .apply()
        manualProfile = SoundPreset("manual", "手動設定", eq.bands.map { SoundPoint(it.frequencyHz, it.gainMb) })
    }

    private fun setEqEnabled(enabled: Boolean): Int {
        if (!eq.supported || equalizer == null) return status("この端末ではEQを利用できません。")
        if (enabled && volume.boostEnabled) return status("EQを使う前にBOOSTをOFFにしてください。")
        return try {
            check(equalizer!!.setEnabled(enabled) == AudioEffect.SUCCESS)
            eq = eq.copy(enabled = enabled, message = if (enabled) "EQ ON" else "EQ OFF")
            persistEq()
            publish()
            SessionResult.RESULT_SUCCESS
        } catch (_: Exception) { status("EQ切り替え失敗。再生は継続します。") }
    }

    private fun setBand(index: Int, gainMb: Int): Int {
        if (!eq.supported || !eq.enabled || index !in eq.bands.indices) return status("EQをONにしてください。")
        return try {
            val level = gainMb.coerceIn(eq.minGainMb, eq.maxGainMb)
            equalizer!!.setBandLevel(index.toShort(), level.toShort())
            eq = eq.copy(bands = eq.bands.toMutableList().also { it[index] = it[index].copy(gainMb = level) },
                selectedPresetId = null, message = "カスタム（未保存）")
            persistEq()
            publish()
            SessionResult.RESULT_SUCCESS
        } catch (_: Exception) { status("帯域調整に失敗しました。") }
    }

    private fun selectPreset(id: String): Int {
        val preset = (SoundPresets.builtIns + eq.customPresets).firstOrNull { it.id == id }
            ?: return status("プリセットが見つかりません。")
        if (!eq.supported || equalizer == null) return status("EQに接続できていません。")
        if (volume.boostEnabled) return status("プリセットを使う前にBOOSTをOFFにしてください。")
        return try {
            val mapped = eq.bands.map { band ->
                band.copy(gainMb = SoundPresets.gainAt(preset, band.frequencyHz, eq.minGainMb, eq.maxGainMb))
            }
            writeEq(mapped, true)
            eq = eq.copy(enabled = true, bands = mapped, selectedPresetId = id, message = "${preset.name} を適用しました。")
            persistEq()
            publish()
            SessionResult.RESULT_SUCCESS
        } catch (_: Exception) { status("プリセットの適用に失敗しました。") }
    }

    private fun savePreset(rawName: String): Int {
        if (!eq.supported || eq.bands.isEmpty()) return status("EQ接続後に保存できます。")
        val name = SoundPresets.uniqueName(rawName, eq.customPresets)
            ?: return status("プリセット名は1〜40文字かつ重複なしにしてください。")
        val preset = SoundPreset("user:${UUID.randomUUID()}", name,
            eq.bands.map { SoundPoint(it.frequencyHz, it.gainMb) })
        val revised = eq.customPresets + preset
        if (!preferences.edit().putString(KEY_CUSTOM, SoundPresetStore.encode(revised)).commit())
            return status("プリセットを保存できませんでした。")
        eq = eq.copy(customPresets = revised, selectedPresetId = preset.id, message = "$name を保存しました。")
        persistEq()
        publish()
        return SessionResult.RESULT_SUCCESS
    }

    private fun renamePreset(id: String, rawName: String): Int {
        val old = eq.customPresets.firstOrNull { it.id == id }
            ?: return status("変更できるマイプリセットがありません。")
        val name = SoundPresets.uniqueName(rawName, eq.customPresets, id)
            ?: return status("プリセット名は1〜40文字かつ重複なしにしてください。")
        val revised = eq.customPresets.map { if (it.id == old.id) it.copy(name = name) else it }
        if (!preferences.edit().putString(KEY_CUSTOM, SoundPresetStore.encode(revised)).commit())
            return status("名前の変更を保存できませんでした。")
        eq = eq.copy(customPresets = revised, message = "名前を変更しました。")
        publish()
        return SessionResult.RESULT_SUCCESS
    }

    private fun deletePreset(id: String): Int {
        if (eq.customPresets.none { it.id == id }) return status("削除できるマイプリセットがありません。")
        val revised = eq.customPresets.filterNot { it.id == id }
        if (!preferences.edit().putString(KEY_CUSTOM, SoundPresetStore.encode(revised)).commit())
            return status("削除を保存できませんでした。")
        eq = eq.copy(customPresets = revised,
            selectedPresetId = if (eq.selectedPresetId == id) null else eq.selectedPresetId,
            message = "削除しました。現在のEQ設定は未保存として残ります。")
        persistEq()
        publish()
        return SessionResult.RESULT_SUCCESS
    }

    fun execute(action: String, args: Bundle): Int = when (action) {
        SoundCommand.VOLUME -> {
            val requested = args.getInt(SoundCommand.PERCENT, -1)
            if (requested !in 0..volume.maximum) status("BOOSTをONにしてから100%を超える音量を指定してください。")
            else { volume = volume.withPercent(requested).copy(message = ""); applyVolume(); SessionResult.RESULT_SUCCESS }
        }
        SoundCommand.BOOST -> {
            val enabled = args.getBoolean(SoundCommand.ENABLED)
            when {
                enabled && !volume.boostAvailable -> status("PCM出力や出力先の監視を確認できないためBOOSTを使えません。")
                enabled && eq.enabled -> status("BOOSTを使う前にEQをOFFにしてください。")
                else -> {
                    volume = volume.withBoost(enabled).copy(message = if (enabled) "BOOST ON。小さい音量から試してください。" else "BOOST OFF")
                    applyVolume()
                    SessionResult.RESULT_SUCCESS
                }
            }
        }
        SoundCommand.EQ_ENABLED -> setEqEnabled(args.getBoolean(SoundCommand.ENABLED))
        SoundCommand.EQ_BAND -> setBand(args.getInt(SoundCommand.INDEX, -1), args.getInt(SoundCommand.GAIN_MB))
        SoundCommand.PRESET -> selectPreset(args.getString(SoundCommand.ID).orEmpty())
        SoundCommand.SAVE -> savePreset(args.getString(SoundCommand.NAME).orEmpty())
        SoundCommand.RENAME -> renamePreset(args.getString(SoundCommand.ID).orEmpty(), args.getString(SoundCommand.NAME).orEmpty())
        SoundCommand.DELETE -> deletePreset(args.getString(SoundCommand.ID).orEmpty())
        SoundCommand.RESET -> {
            volume = volume.withBoost(false).withPercent(100).copy(message = "100%（原音量）に戻しました。")
            applyVolume()
            SessionResult.RESULT_SUCCESS
        }
        else -> SessionResult.RESULT_ERROR_BAD_VALUE
    }

    private fun releaseEq() {
        runCatching { equalizer?.setEnabled(false) }
        runCatching { equalizer?.release() }
        equalizer = null
    }

    override fun close() {
        if (closed) return
        closed = true
        processor.onPcmSupportChanged = null
        processor.setBoostGain(1f)
        if (routeMonitoring) runCatching { audioManager.unregisterAudioDeviceCallback(routeCallback) }
        releaseEq()
        preferences.edit().putBoolean(KEY_BOOST, false).apply()
        AppVolumeBus.publish(AppVolumeState())
        SoundEqBus.publish(SoundEqState())
    }

    private companion object {
        const val KEY_VOLUME = "wms_volume_percent"
        const val KEY_LAST_AUDIBLE = "wms_last_audible_percent"
        const val KEY_BOOST = "wms_boost_enabled"
        const val KEY_EQ_ENABLED = "wms_eq_enabled"
        const val KEY_CUSTOM = "wms_eq_presets_v1"
        const val KEY_MANUAL = "wms_eq_manual_v1"
        const val KEY_SELECTED = "wms_eq_selected_id"
    }
}
