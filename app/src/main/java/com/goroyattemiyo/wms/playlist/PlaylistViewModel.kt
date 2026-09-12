package com.goroyattemiyo.wms.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goroyattemiyo.wms.WmsApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WmsApplication).playlistRepository
    private val preferences = application.getSharedPreferences(PREFS_NAME, 0)

    val playlists = repository.playlists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    private val _selectedPlaylistId = MutableStateFlow(preferences.getString(KEY_SELECTED_PLAYLIST_ID, null))
    val selectedPlaylistId = _selectedPlaylistId.asStateFlow()

    val selectedItems = _selectedPlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) flowOf(emptyList()) else repository.observeItems(playlistId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
        preferences.edit().apply {
            if (playlistId == null) remove(KEY_SELECTED_PLAYLIST_ID) else putString(KEY_SELECTED_PLAYLIST_ID, playlistId)
        }.apply()
    }

    fun create(name: String) {
        launchOperation {
            val playlist = repository.create(name)
            selectPlaylist(playlist.id)
        }
    }

    fun rename(playlistId: String, name: String) {
        launchOperation { repository.rename(playlistId, name) }
    }

    fun delete(playlistId: String) {
        launchOperation {
            repository.delete(playlistId)
            if (_selectedPlaylistId.value == playlistId) selectPlaylist(null)
        }
    }

    fun addMedia(playlistId: String, mediaId: String) {
        launchOperation { repository.addMedia(playlistId, mediaId) }
    }

    fun removeMedia(playlistId: String, mediaId: String) {
        launchOperation { repository.removeMedia(playlistId, mediaId) }
    }

    fun moveMedia(playlistId: String, mediaId: String, delta: Int) {
        launchOperation { repository.moveMedia(playlistId, mediaId, delta) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _errorMessage.value = null }
                .onFailure { _errorMessage.value = it.message ?: "プレイリストを更新できませんでした" }
        }
    }

    private companion object {
        const val PREFS_NAME = "wms_playlists"
        const val KEY_SELECTED_PLAYLIST_ID = "selected_playlist_id"
    }
}
