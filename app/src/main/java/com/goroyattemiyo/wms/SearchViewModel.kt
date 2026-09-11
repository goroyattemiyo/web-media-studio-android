package com.goroyattemiyo.wms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goroyattemiyo.wms.search.SearchMediaItem
import com.goroyattemiyo.wms.search.SearchProvider
import com.goroyattemiyo.wms.search.WmsMediaSearchProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val searching: Boolean = false,
    val query: String = "",
    val results: List<SearchMediaItem> = emptyList(),
    val errorMessage: String? = null,
)

class SearchViewModel(
    private val provider: SearchProvider = WmsMediaSearchProvider(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    fun search(query: String) {
        val value = query.trim()
        if (value.length < 2 || _uiState.value.searching) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searching = true,
                    query = value,
                    results = emptyList(),
                    errorMessage = null,
                )
            }

            provider.search(value)
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            searching = false,
                            results = results,
                            errorMessage = if (results.isEmpty()) "該当する動画が見つかりませんでした。" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            searching = false,
                            results = emptyList(),
                            errorMessage = error.message?.take(180) ?: "動画検索に失敗しました。",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
