/*
 * Copyright (c) 2026 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.ui.screen.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.odesli.OdesliClient
import com.mardous.booming.data.remote.odesli.ResolvedLink
import com.mardous.booming.data.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LinkResolverState {
    data object Loading : LinkResolverState
    data class Success(
        val resolved: ResolvedLink,
        val localSongs: List<Song>
    ) : LinkResolverState
    data class Error(val message: String?) : LinkResolverState
}

class LinkResolverViewModel(
    private val odesli: OdesliClient,
    private val repository: Repository
) : ViewModel() {

    private val _state = MutableStateFlow<LinkResolverState>(LinkResolverState.Loading)
    val state = _state.asStateFlow()

    fun resolve(rawText: String?) {
        val url = rawText.extractMusicUrl()
        if (url == null) {
            _state.value = LinkResolverState.Error(null)
            return
        }
        viewModelScope.launch {
            _state.value = LinkResolverState.Loading
            runCatching { odesli.resolve(url) }
                .onSuccess { resolved ->
                    val localSongs = findLocalSongs(resolved)
                    _state.value = LinkResolverState.Success(resolved, localSongs)
                }
                .onFailure { error ->
                    _state.value = LinkResolverState.Error(error.message)
                }
        }
    }

    private suspend fun findLocalSongs(resolved: ResolvedLink): List<Song> {
        val title = resolved.title?.takeIf { it.length >= 2 } ?: return emptyList()
        val candidates = withContext(Dispatchers.IO) {
            runCatching { repository.searchSongs(title) }.getOrDefault(emptyList())
        }
        if (candidates.isEmpty()) return emptyList()

        val artist = resolved.artist?.lowercase()
        val ranked = candidates.sortedByDescending { song ->
            var score = 0
            if (song.title.equals(title, ignoreCase = true)) score += 4
            if (!artist.isNullOrBlank()) {
                if (song.artistName.lowercase().contains(artist)) score += 3
                if (song.albumArtistName?.lowercase()?.contains(artist) == true) score += 1
            }
            score
        }
        val normalizedTitle = title
            .substringBefore(" (")
            .substringBefore(" [")
            .trim()
        val titleMatches = ranked.filter {
            it.title.contains(normalizedTitle, ignoreCase = true)
        }
        return (titleMatches.ifEmpty { ranked }).take(MAX_LOCAL_MATCHES)
    }

    companion object {
        private const val MAX_LOCAL_MATCHES = 6
        private val URL_PATTERN = Regex("https?://\\S+")

        fun extractMusicUrl(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            val candidate = URL_PATTERN.find(rawText)?.value
                ?.trimEnd('.', ',', ')', ']', '>', '"')
                ?: return null
            return candidate.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }
}

fun String?.extractMusicUrl(): String? = LinkResolverViewModel.extractMusicUrl(this)
