package com.goroyattemiyo.wms.appearance

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goroyattemiyo.wms.playback.VisualizerMode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class WmsSurfaceStyle {
    NEON,
    CLEAN,
    WARM,
    RETRO,
}

enum class WmsBackgroundStyle(val id: String, val displayName: String) {
    PLAIN("plain", "プレーン"),
    GRID("grid", "グリッド"),
    DOTS("dots", "ドット"),
    SCANLINES("scanlines", "走査線"),
}

data class WmsSkin(
    val id: String,
    val displayName: String,
    val description: String,
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val primary: Long,
    val secondary: Long,
    val onBackground: Long,
    val onSurfaceVariant: Long,
    val isLight: Boolean = false,
    val surfaceStyle: WmsSurfaceStyle = WmsSurfaceStyle.NEON,
)

object WmsSkinCatalog {
    const val DEFAULT_ID = "midnight-neon"

    val skins = listOf(
        skin(DEFAULT_ID, "Midnight Neon", "WMSのシアンと紫が光る標準テーマ", 0xFF05070D, 0xFF111620, 0xFF1A2230, 0xFF57D8FF, 0xFF9B6BFF),
        skin("obsidian", "Obsidian", "黒曜石のような深い黒と銀", 0xFF050505, 0xFF111111, 0xFF202124, 0xFFE3E6EA, 0xFF8EA4BD, WmsSurfaceStyle.CLEAN),
        skin("studio-light", "Studio Light", "明るく整理されたスタジオ", 0xFFF5F7FC, 0xFFFFFFFF, 0xFFE7EBF4, 0xFF006A84, 0xFF6650A4, WmsSurfaceStyle.CLEAN, true),
        skin("analog-warm", "Analog Warm", "琥珀色のアナログ機材", 0xFF17100B, 0xFF2A1C13, 0xFF3B291C, 0xFFFFB45C, 0xFFD9895B, WmsSurfaceStyle.WARM),
        skin("cyber-blue", "Cyber Blue", "高コントラストの電脳ブルー", 0xFF020914, 0xFF071A2B, 0xFF0C2942, 0xFF2DE2FF, 0xFF3478FF),
        skin("aurora-purple", "Aurora Purple", "オーロラを思わせる紫と水色", 0xFF0E0718, 0xFF1D102D, 0xFF2E1943, 0xFFD18CFF, 0xFF67E8F9),
        skin("emerald-night", "Emerald Night", "深夜のエメラルドグリーン", 0xFF03110D, 0xFF0B2119, 0xFF133529, 0xFF54E6A5, 0xFF43B9C5),
        skin("crimson-noir", "Crimson Noir", "深紅が差すノワール", 0xFF100609, 0xFF241014, 0xFF37171D, 0xFFFF6B7A, 0xFFC586FF),
        skin("sunset-glow", "Sunset Glow", "夕焼けの橙とマゼンタ", 0xFF170A12, 0xFF2B1420, 0xFF402032, 0xFFFF9D57, 0xFFFF69B4, WmsSurfaceStyle.WARM),
        skin("sakura", "Sakura", "淡い桜色のライトテーマ", 0xFFFFF7FA, 0xFFFFFFFF, 0xFFFFE6EF, 0xFFB82D68, 0xFF7651A6, WmsSurfaceStyle.CLEAN, true),
        skin("pixel-arcade", "Pixel Arcade", "ゲームセンターの緑と紫", 0xFF050611, 0xFF101126, 0xFF1A1C39, 0xFF55FF88, 0xFFFF5CF4, WmsSurfaceStyle.RETRO),
        skin("led-marquee", "LED Marquee", "LED表示の赤とアンバー", 0xFF090400, 0xFF1A0C03, 0xFF301707, 0xFFFFB000, 0xFFFF4A32, WmsSurfaceStyle.RETRO),
        skin("retro-terminal", "Retro Terminal", "モノクロ端末のグリーン", 0xFF020805, 0xFF07130D, 0xFF0D2418, 0xFF5CFF8A, 0xFFA8FFBD, WmsSurfaceStyle.RETRO),
        skin("cassette-deck", "Cassette Deck", "カセットデッキの金属と橙", 0xFF11100E, 0xFF25231F, 0xFF39352F, 0xFFFFA552, 0xFF84C9D8, WmsSurfaceStyle.WARM),
    )

    fun byId(id: String?): WmsSkin = skins.firstOrNull { it.id == id } ?: skins.first()

    private fun skin(
        id: String,
        name: String,
        description: String,
        background: Long,
        surface: Long,
        surfaceVariant: Long,
        primary: Long,
        secondary: Long,
        style: WmsSurfaceStyle = WmsSurfaceStyle.NEON,
        light: Boolean = false,
    ) = WmsSkin(
        id = id,
        displayName = name,
        description = description,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        primary = primary,
        secondary = secondary,
        onBackground = if (light) 0xFF171A20 else 0xFFF4F7FF,
        onSurfaceVariant = if (light) 0xFF535965 else 0xFFAEBBCB,
        isLight = light,
        surfaceStyle = style,
    )
}

data class AppearanceSettings(
    val skinId: String = WmsSkinCatalog.DEFAULT_ID,
    val backgroundId: String = WmsBackgroundStyle.PLAIN.id,
    val visualizerId: String = VisualizerMode.EMBLEM.id,
    val reducedMotion: Boolean = false,
    val backgroundImagePath: String? = null,
    val backgroundBlur: Float = 0f,
) {
    val skin: WmsSkin get() = WmsSkinCatalog.byId(skinId)
    val visualizer: VisualizerMode
        get() = VisualizerMode.fromPersistedId(visualizerId)
    val backgroundStyle: WmsBackgroundStyle
        get() = WmsBackgroundStyle.entries.firstOrNull { it.id == backgroundId } ?: WmsBackgroundStyle.PLAIN
}

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

class AppearanceRepository(private val context: Context) {
    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppearanceSettings(
                skinId = WmsSkinCatalog.byId(preferences[SKIN_ID]).id,
                backgroundId = WmsBackgroundStyle.entries
                    .firstOrNull { it.id == preferences[BACKGROUND_ID] }
                    ?.id
                    ?: WmsBackgroundStyle.PLAIN.id,
                visualizerId = VisualizerMode.entries
                    .firstOrNull { it.id == preferences[VISUALIZER_ID] }
                    ?.id
                    ?: VisualizerMode.EMBLEM.id,
                reducedMotion = preferences[REDUCED_MOTION] ?: false,
                backgroundImagePath = preferences[BACKGROUND_IMAGE_PATH],
                backgroundBlur = (preferences[BACKGROUND_BLUR] ?: 0f).coerceIn(0f, 24f),
            )
        }

    suspend fun selectSkin(id: String) {
        context.appearanceDataStore.edit { it[SKIN_ID] = WmsSkinCatalog.byId(id).id }
    }

    suspend fun selectBackground(id: String) {
        val safeId = WmsBackgroundStyle.entries.firstOrNull { it.id == id }?.id
            ?: WmsBackgroundStyle.PLAIN.id
        context.appearanceDataStore.edit { it[BACKGROUND_ID] = safeId }
    }

    suspend fun selectVisualizer(id: String) {
        val safeId = VisualizerMode.entries.firstOrNull { it.id == id }?.id ?: VisualizerMode.EMBLEM.id
        context.appearanceDataStore.edit { it[VISUALIZER_ID] = safeId }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.appearanceDataStore.edit { it[REDUCED_MOTION] = enabled }
    }

    suspend fun importBackgroundImage(uri: Uri) {
        val destination = File(context.filesDir, BACKGROUND_IMAGE_FILE).apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use(input::copyTo) }
            ?: return
        context.appearanceDataStore.edit { it[BACKGROUND_IMAGE_PATH] = destination.absolutePath }
    }

    suspend fun clearBackgroundImage() {
        context.appearanceDataStore.edit { preferences ->
            preferences.remove(BACKGROUND_IMAGE_PATH)
            preferences[BACKGROUND_BLUR] = 0f
        }
        File(context.filesDir, BACKGROUND_IMAGE_FILE).delete()
    }

    suspend fun setBackgroundBlur(blur: Float) {
        context.appearanceDataStore.edit { it[BACKGROUND_BLUR] = blur.coerceIn(0f, 24f) }
    }

    private companion object {
        const val BACKGROUND_IMAGE_FILE = "appearance/background-image"
        val SKIN_ID = stringPreferencesKey("skin_id")
        val BACKGROUND_ID = stringPreferencesKey("background_id")
        val VISUALIZER_ID = stringPreferencesKey("visualizer_id")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")
        val BACKGROUND_BLUR = floatPreferencesKey("background_blur")
    }
}

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppearanceRepository(application.applicationContext)
    val settings = repository.settings

    fun selectSkin(id: String) = viewModelScope.launch { repository.selectSkin(id) }
    fun selectBackground(id: String) = viewModelScope.launch { repository.selectBackground(id) }
    fun selectVisualizer(id: String) = viewModelScope.launch { repository.selectVisualizer(id) }
    fun setReducedMotion(enabled: Boolean) = viewModelScope.launch { repository.setReducedMotion(enabled) }
    fun importBackgroundImage(uri: Uri) = viewModelScope.launch(Dispatchers.IO) { repository.importBackgroundImage(uri) }
    fun clearBackgroundImage() = viewModelScope.launch(Dispatchers.IO) { repository.clearBackgroundImage() }
    fun setBackgroundBlur(blur: Float) = viewModelScope.launch { repository.setBackgroundBlur(blur) }
}
