package com.goroyattemiyo.wms.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goroyattemiyo.wms.WmsApplication
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WmsApplication).mediaRepository
    private val preferences = application.getSharedPreferences(PREFS_NAME, 0)

    val media = repository.allMedia.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    private val _selectedMediaId = MutableStateFlow(preferences.getString(KEY_SELECTED_MEDIA_ID, null))
    val selectedMediaId = _selectedMediaId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.importExistingFiles() }
                .onFailure { _errorMessage.value = "既存メディアの確認に失敗しました" }
        }
    }

    fun select(media: MediaEntity) {
        _selectedMediaId.value = media.id
        preferences.edit().putString(KEY_SELECTED_MEDIA_ID, media.id).apply()
    }

    fun selectByPath(path: String) {
        media.value.firstOrNull { File(it.localPath).absolutePath == File(path).absolutePath }?.let(::select)
    }

    fun delete(media: MediaEntity) {
        viewModelScope.launch {
            repository.delete(media)
                .onSuccess {
                    if (_selectedMediaId.value == media.id) {
                        _selectedMediaId.value = null
                        preferences.edit().remove(KEY_SELECTED_MEDIA_ID).apply()
                    }
                    _errorMessage.value = null
                }
                .onFailure { _errorMessage.value = it.message ?: "メディアを削除できませんでした" }
        }
    }

    fun savePosition(mediaId: String, positionMs: Long) {
        viewModelScope.launch {
            runCatching { repository.updateLastPosition(mediaId, positionMs) }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private companion object {
        const val PREFS_NAME = "wms_library"
        const val KEY_SELECTED_MEDIA_ID = "selected_media_id"
    }
}
