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

package com.mardous.booming.ui.screen.library.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mardous.booming.R
import com.mardous.booming.core.model.LibraryMargin
import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.ContentType
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.model.Suggestion
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.screen.library.LibraryViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object Empty : HomeUiState()
    data class Success(val suggestions: List<Suggestion>) : HomeUiState()
}

@Composable
fun HomeScreen(
    onActionClick: (ContentType) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onDeezerClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSongItemClick: (List<Song>, Int) -> Unit,
    onItemClick: (Any) -> Unit,
    onPlayAlbumClick: (Album) -> Unit,
    onOpenSuggestion: (Suggestion) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onDashboardClick: () -> Unit,
    scrollToTopTrigger: Boolean = false,
    onScrollToTopDone: () -> Unit = {},
    homeViewModel: HomeViewModel = koinViewModel(),
    libraryViewModel: LibraryViewModel = koinActivityViewModel()
) {
    val density = LocalDensity.current

    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerMargin by libraryViewModel.getMiniPlayerMargin().observeAsState(LibraryMargin(0))

    val listState = rememberLazyListState()
    val bottomContentPadding by remember {
        derivedStateOf { with(density) { miniPlayerMargin.totalMargin.toDp() } + 16.dp }
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger) {
            listState.animateScrollToItem(0)
            onScrollToTopDone()
        }
    }

    Scaffold(
        topBar = {
            ElyvaenHomeTopBar(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onStatsClick = onStatsClick
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f),
                            androidx.compose.material3.MaterialTheme.colorScheme.background,
                            androidx.compose.material3.MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            when (val currentState = uiState) {
                is HomeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is HomeUiState.Empty -> {
                    EmptyView(
                        icon = painterResource(R.drawable.ic_library_music_24dp),
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_subtitle),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is HomeUiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomContentPadding)
                    ) {
                        item {
                            ElyvaenStatusCard(
                                onDashboardClick = onDashboardClick,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                        item {
                            ElyvaenSourceRow(
                                onDeezerClick = onDeezerClick,
                                onDownloadsClick = onDownloadsClick,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                        item {
                            ElyvaenHomeIntro(
                                onPlayAllClick = onPlayAllClick,
                                onShuffleClick = onShuffleClick,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                        item {
                            ElyvaenHomeTopActions(
                                onActionClick = onActionClick,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                        items(currentState.suggestions) { suggestion ->
                            ElyvaenSuggestionSection(
                                suggestion = suggestion,
                                onSongItemClick = onSongItemClick,
                                onItemClick = onItemClick,
                                onPlayAlbumClick = onPlayAlbumClick,
                                onShuffleClick = onShuffleClick,
                                onOpenClick = { onOpenSuggestion(suggestion) }
                            )
                        }
                        item {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
