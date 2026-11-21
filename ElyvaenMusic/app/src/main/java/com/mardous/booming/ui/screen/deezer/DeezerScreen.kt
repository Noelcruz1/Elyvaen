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

package com.mardous.booming.ui.screen.deezer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mardous.booming.R
import com.mardous.booming.data.remote.deezer.DeezerAccount
import com.mardous.booming.data.remote.deezer.DeezerDownloadState
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumDetail
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumItem
import com.mardous.booming.data.remote.deezer.model.DeezerArtistItem
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistDetail
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistItem
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem
import com.mardous.booming.extensions.media.asReadableDuration
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.component.compose.MediaImage
import com.mardous.booming.ui.screen.player.PlayerViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel

private val DeezerCardShape = RoundedCornerShape(20.dp)
private val DeezerRowShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeezerScreen(
    onBack: () -> Unit,
    viewModel: DeezerViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = koinActivityViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(DeezerLoginActivity.EXTRA_ARL)
                ?.let(viewModel::connect)
        }
    }
    var previewTrack by remember { mutableStateOf<DeezerTrackItem?>(null) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var libraryTab by remember { mutableStateOf(DeezerLibraryTab.Artists) }
    var previousDownloads by remember {
        mutableStateOf<Map<Long, DeezerDownloadState>>(emptyMap())
    }

    LaunchedEffect(state.account) {
        if (state.account != null) {
            showConnectDialog = false
        } else {
            showLibrary = false
        }
    }
    LaunchedEffect(state.favoriteError) {
        if (state.favoriteError) {
            Toast.makeText(
                context,
                context.getString(R.string.deezer_favorite_error),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.clearFavoriteError()
        }
    }
    LaunchedEffect(downloadStates) {        downloadStates.forEach { (id, current) ->
            val previous = previousDownloads[id]
            if (current is DeezerDownloadState.Done && previous !is DeezerDownloadState.Done) {
                Toast.makeText(
                    context,
                    context.getString(R.string.deezer_download_complete),
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (current is DeezerDownloadState.Failed && previous !is DeezerDownloadState.Failed) {
                Toast.makeText(
                    context,
                    context.getString(R.string.deezer_download_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        previousDownloads = downloadStates
    }

    val openPreview: (DeezerTrackItem) -> Unit = { track ->
        if (playerViewModel.isPlaying) {
            playerViewModel.togglePlayPause()
        }
        previewTrack = track
    }
    val openDownload: (DeezerTrackItem) -> Unit = { track ->
        if (state.account == null) {
            showConnectDialog = true
        } else {
            viewModel.downloadTrack(track)
        }
    }
    val openDownloadAll: (List<DeezerTrackItem>) -> Unit = { tracks ->
        if (state.account == null) {
            showConnectDialog = true
        } else {
            viewModel.downloadAll(tracks)
        }
    }

    val handleBack: () -> Unit = {
        when {
            state.detail != null -> viewModel.closeDetail()
            showLibrary -> showLibrary = false
            else -> onBack()
        }
    }

    BackHandler(enabled = state.detail != null || showLibrary) {
        when {
            state.detail != null -> viewModel.closeDetail()
            showLibrary -> showLibrary = false
        }
    }

    Scaffold(
        topBar = {
            DeezerTopBar(
                query = state.query,
                showSearch = state.detail == null && !showLibrary,
                isSearching = state.searchLoading,
                account = state.account,
                libraryActive = showLibrary,
                onQueryChange = viewModel::onQueryChange,
                onClearSearch = viewModel::clearSearch,
                onLibraryClick = {
                    viewModel.closeDetail()
                    showLibrary = !showLibrary
                    if (showLibrary) viewModel.loadLibrary()
                },
                onAccountClick = {
                    if (state.account != null) {
                        showAccountDialog = true
                    } else {
                        showConnectDialog = true
                    }
                },
                onBack = handleBack
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            when {
                state.detailLoading -> DeezerLoading()
                state.detailError -> DeezerDetailError(onBack = viewModel::closeDetail)
                state.detail != null -> DeezerDetailContent(
                    detail = state.detail!!,
                    library = state.library,
                    downloadStates = downloadStates,
                    onPreview = openPreview,
                    onDownload = openDownload,
                    onDownloadAll = openDownloadAll,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist,
                    onToggleAlbumSaved = viewModel::setAlbumSaved,
                    onToggleArtistSaved = viewModel::setArtistSaved
                )

                showLibrary -> DeezerLibraryContent(
                    state = state,
                    downloadStates = downloadStates,
                    tab = libraryTab,
                    onTabChange = { libraryTab = it },
                    onRefresh = viewModel::loadLibrary,
                    onPreview = openPreview,
                    onDownload = openDownload,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist
                )

                state.chartLoading -> DeezerLoading()
                state.chartError -> DeezerError(onRetry = viewModel::loadChart)
                state.query.isBlank() -> DeezerChartContent(
                    chart = state.chart,
                    onPreview = openPreview,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist
                )

                else -> DeezerSearchContent(
                    state = state,
                    downloadStates = downloadStates,
                    onTypeChange = viewModel::onSearchTypeChange,
                    onRetry = { viewModel.onSearchTypeChange(state.searchType) },
                    onPreview = openPreview,
                    onDownload = openDownload,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist
                )
            }
        }
    }

    previewTrack?.let { track ->
        DeezerPreviewSheet(
            track = track,
            downloadState = downloadStates[track.id],
            isSaved = state.library?.tracks?.any { it.id == track.id },
            onToggleSaved = viewModel::setTrackSaved,
            onDownload = { openDownload(track) },
            onDismiss = { previewTrack = null }
        )
    }

    if (showConnectDialog) {
        DeezerConnectDialog(
            loading = state.connectLoading,
            error = state.connectError,
            onDismiss = { showConnectDialog = false },
            onLogin = {
                loginLauncher.launch(Intent(context, DeezerLoginActivity::class.java))
            },
            onConnect = viewModel::connect
        )
    }
    if (showAccountDialog && state.account != null) {
        DeezerAccountDialog(
            account = state.account!!,
            onDismiss = { showAccountDialog = false },
            onDisconnect = {
                viewModel.disconnect()
                showAccountDialog = false
            }
        )
    }
}

@Composable
private fun DeezerTopBar(
    query: String,
    showSearch: Boolean,
    isSearching: Boolean,
    account: DeezerAccount?,
    libraryActive: Boolean,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLibraryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back_24dp),
                        contentDescription = stringResource(R.string.back_action)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DEEZER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.6.sp
                    )
                    Text(
                        text = stringResource(R.string.deezer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (account != null) {
                    IconButton(
                        onClick = onLibraryClick,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_library_music_24dp),
                            contentDescription = stringResource(R.string.deezer_my_library),
                            tint = if (libraryActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Surface(
                        onClick = onAccountClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_person_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = account.userName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                        }
                    }
                } else {
                    TextButton(onClick = onAccountClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.deezer_connect),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (showSearch) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = {
                        Text(text = stringResource(R.string.deezer_search_placeholder))
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24dp),
                                    contentDescription = stringResource(R.string.clear_action)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun DeezerConnectDialog(
    loading: Boolean,
    error: Boolean,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onConnect: (String) -> Unit
) {
    var arl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.deezer_connect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.deezer_login_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onLogin,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.deezer_login_button))
                }
                Text(
                    text = stringResource(R.string.deezer_login_or),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = arl,
                    onValueChange = { arl = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.deezer_connect_arl)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        text = stringResource(R.string.deezer_connect_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(arl) },
                enabled = !loading && arl.isNotBlank()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(text = stringResource(R.string.deezer_connect_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeezerAccountDialog(
    account: DeezerAccount,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.deezer_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.deezer_connected_as, account.userName),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.deezer_plan, account.plan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDisconnect) {
                Text(text = stringResource(R.string.deezer_disconnect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close_action))
            }
        }
    )
}

@Composable
private fun DeezerLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeezerError(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.deezer_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.deezer_retry))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DeezerDetailError(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.deezer_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onBack) {
            Text(text = stringResource(R.string.back_action))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DeezerChartContent(
    chart: DeezerChart,
    onPreview: (DeezerTrackItem) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (chart.tracks.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_trending_tracks))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.tracks, key = { it.id }) { track ->
                        DeezerTrackCard(track = track, onClick = { onPreview(track) })
                    }
                }
            }
        }
        if (chart.albums.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_featured_albums))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.albums, key = { it.id }) { album ->
                        DeezerAlbumCard(album = album, onClick = { onAlbum(album.id) })
                    }
                }
            }
        }
        if (chart.playlists.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_editorial_playlists))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.playlists, key = { it.id }) { playlist ->
                        DeezerPlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylist(playlist.id) }
                        )
                    }
                }
            }
        }
        if (chart.artists.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_popular_artists))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.artists, key = { it.id }) { artist ->
                        DeezerArtistCard(artist = artist, onClick = { onArtist(artist.id) })
                    }
                }
            }
        }
    }
}

private enum class DeezerLibraryTab(val labelRes: Int) {
    Artists(R.string.deezer_artists_tab),
    Albums(R.string.deezer_albums_tab),
    Playlists(R.string.deezer_playlists_tab),
    Tracks(R.string.deezer_tracks_tab)
}

@Composable
private fun DeezerLibraryContent(
    state: DeezerUiState,
    downloadStates: Map<Long, DeezerDownloadState>,
    tab: DeezerLibraryTab,
    onTabChange: (DeezerLibraryTab) -> Unit,
    onRefresh: () -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit
) {
    val library = state.library
    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.deezer_library_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.deezer_library_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.libraryLoading) {
                    if (state.libraryLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_update_24dp),
                            contentDescription = stringResource(R.string.deezer_library_refresh)
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                DeezerLibraryTab.entries.forEach { entry ->
                    FilterChip(
                        selected = tab == entry,
                        onClick = { onTabChange(entry) },
                        label = {
                            Text(
                                text = stringResource(entry.labelRes),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }
        }

        val isEmpty = when (tab) {
            DeezerLibraryTab.Artists -> library?.artists.isNullOrEmpty()
            DeezerLibraryTab.Albums -> library?.albums.isNullOrEmpty()
            DeezerLibraryTab.Playlists -> library?.playlists.isNullOrEmpty()
            DeezerLibraryTab.Tracks -> library?.tracks.isNullOrEmpty()
        }

        when {
            library == null && state.libraryLoading -> item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                ) {
                    CircularProgressIndicator()
                }
            }

            library == null && state.libraryError -> item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.deezer_error),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRefresh) {
                        Text(text = stringResource(R.string.deezer_retry))
                    }
                }
            }

            library == null -> Unit

            isEmpty -> item {
                EmptyView(
                    icon = painterResource(R.drawable.ic_library_music_24dp),
                    title = stringResource(R.string.deezer_library_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                )
            }

            else -> when (tab) {
                DeezerLibraryTab.Artists -> items(library.artists, key = { it.id }) { artist ->
                    DeezerArtistRow(artist = artist, onClick = { onArtist(artist.id) })
                }

                DeezerLibraryTab.Albums -> items(library.albums, key = { it.id }) { album ->
                    DeezerAlbumRow(album = album, onClick = { onAlbum(album.id) })
                }

                DeezerLibraryTab.Playlists -> items(library.playlists, key = { it.id }) { playlist ->
                    DeezerPlaylistRow(playlist = playlist, onClick = { onPlaylist(playlist.id) })
                }

                DeezerLibraryTab.Tracks -> items(library.tracks, key = { it.id }) { track ->
                    DeezerTrackRow(
                        track = track,
                        downloadState = downloadStates[track.id],
                        onClick = { onPreview(track) },
                        onDownload = { onDownload(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeezerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp)
    )
}

@Composable
private fun DeezerTrackCard(track: DeezerTrackItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DeezerCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(156.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                MediaImage(
                    model = track.imageUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.deezer_preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DeezerAlbumCard(album: DeezerAlbumItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DeezerCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(156.dp)
    ) {
        Column {
            MediaImage(
                model = album.imageUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeezerPlaylistCard(playlist: DeezerPlaylistItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DeezerCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(156.dp)
    ) {
        Column {
            MediaImage(
                model = playlist.imageUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.creatorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeezerArtistCard(artist: DeezerArtistItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(118.dp)
            .clip(DeezerCardShape)
            .padding(4.dp)
            .background(Color.Transparent)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(110.dp)
        ) {
            MediaImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun DeezerSearchContent(
    state: DeezerUiState,
    downloadStates: Map<Long, DeezerDownloadState>,
    onTypeChange: (DeezerSearchType) -> Unit,
    onRetry: () -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                DeezerSearchType.entries.forEach { type ->
                    FilterChip(
                        selected = state.searchType == type,
                        onClick = { onTypeChange(type) },
                        label = {
                            Text(
                                text = stringResource(type.labelRes),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }
        }

        when {
            state.searchLoading -> item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                ) {
                    CircularProgressIndicator()
                }
            }

            state.searchError -> item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.deezer_error),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.deezer_retry))
                    }
                }
            }

            state.searchType == DeezerSearchType.Tracks && state.tracks.isEmpty() ||
                    state.searchType == DeezerSearchType.Albums && state.albums.isEmpty() ||
                    state.searchType == DeezerSearchType.Artists && state.artists.isEmpty() ||
                    state.searchType == DeezerSearchType.Playlists && state.playlists.isEmpty() -> item {
                EmptyView(
                    icon = painterResource(R.drawable.ic_search_24dp),
                    title = stringResource(R.string.no_results),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                )
            }

            state.searchType == DeezerSearchType.Tracks -> items(state.tracks, key = { it.id }) { track ->
                DeezerTrackRow(
                    track = track,
                    downloadState = downloadStates[track.id],
                    onClick = { onPreview(track) },
                    onDownload = { onDownload(track) }
                )
            }

            state.searchType == DeezerSearchType.Albums -> items(state.albums, key = { it.id }) { album ->
                DeezerAlbumRow(album = album, onClick = { onAlbum(album.id) })
            }

            state.searchType == DeezerSearchType.Artists -> items(state.artists, key = { it.id }) { artist ->
                DeezerArtistRow(artist = artist, onClick = { onArtist(artist.id) })
            }

            state.searchType == DeezerSearchType.Playlists -> items(state.playlists, key = { it.id }) { playlist ->
                DeezerPlaylistRow(playlist = playlist, onClick = { onPlaylist(playlist.id) })
            }
        }
    }
}

private val DeezerSearchType.labelRes: Int
    get() = when (this) {
        DeezerSearchType.Tracks -> R.string.deezer_tracks_tab
        DeezerSearchType.Albums -> R.string.deezer_albums_tab
        DeezerSearchType.Artists -> R.string.deezer_artists_tab
        DeezerSearchType.Playlists -> R.string.deezer_playlists_tab
    }

@Composable
private fun DeezerTrackRow(
    track: DeezerTrackItem,
    downloadState: DeezerDownloadState?,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = DeezerRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            MediaImage(
                model = track.imageUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = (track.duration * 1000L).asReadableDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_play_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            when (downloadState) {
                is DeezerDownloadState.Done -> Icon(
                    painter = painterResource(R.drawable.ic_check_24dp),
                    contentDescription = stringResource(R.string.deezer_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                is DeezerDownloadState.Downloading -> CircularProgressIndicator(
                    progress = { downloadState.progress },
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )

                is DeezerDownloadState.Queued,
                is DeezerDownloadState.Tagging -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )

                is DeezerDownloadState.Failed -> IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error_24dp),
                        contentDescription = stringResource(R.string.deezer_download_failed),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                null -> IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download_24dp),
                        contentDescription = stringResource(R.string.deezer_download),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeezerAlbumRow(album: DeezerAlbumItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DeezerRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            MediaImage(
                model = album.imageUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (album.nbTracks > 0) {
                Text(
                    text = pluralStringResource(R.plurals.x_songs, album.nbTracks, album.nbTracks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeezerArtistRow(artist: DeezerArtistItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DeezerRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            MediaImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.nbAlbum > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.x_albums,
                            artist.nbAlbum,
                            artist.nbAlbum
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DeezerPlaylistRow(playlist: DeezerPlaylistItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DeezerRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            MediaImage(
                model = playlist.imageUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.creatorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (playlist.nbTracks > 0) {
                Text(
                    text = pluralStringResource(R.plurals.x_songs, playlist.nbTracks, playlist.nbTracks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeezerDetailContent(
    detail: DeezerDetail,
    library: DeezerLibrary?,
    downloadStates: Map<Long, DeezerDownloadState>,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit,
    onToggleAlbumSaved: (Long, Boolean) -> Unit,
    onToggleArtistSaved: (Long, Boolean) -> Unit
) {
    when (detail) {
        is DeezerDetail.Album -> DeezerAlbumDetailContent(
            album = detail.album,
            saved = library?.albums?.any { it.id == detail.album.id } == true,
            onToggleSaved = { onToggleAlbumSaved(detail.album.id, it) },
            downloadStates = downloadStates,
            onPreview = onPreview,
            onDownload = onDownload,
            onDownloadAll = onDownloadAll
        )

        is DeezerDetail.Artist -> DeezerArtistDetailContent(
            detail = detail,
            saved = library?.artists?.any { it.id == detail.artist.id } == true,
            onToggleSaved = { onToggleArtistSaved(detail.artist.id, it) },
            downloadStates = downloadStates,
            onPreview = onPreview,
            onDownload = onDownload,
            onDownloadAll = onDownloadAll,
            onAlbum = onAlbum
        )

        is DeezerDetail.Playlist -> DeezerPlaylistDetailContent(
            playlist = detail.playlist,
            downloadStates = downloadStates,
            onPreview = onPreview,
            onDownload = onDownload,
            onDownloadAll = onDownloadAll
        )
    }
}

@Composable
private fun DeezerAlbumDetailContent(
    album: DeezerAlbumDetail,
    saved: Boolean,
    onToggleSaved: (Boolean) -> Unit,
    downloadStates: Map<Long, DeezerDownloadState>,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit
) {
    val context = LocalContext.current
    val firstPreview = album.tracks.data.firstOrNull { it.isPreviewAvailable }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DeezerDetailHeader(
                imageUrl = album.imageUrl,
                eyebrow = stringResource(R.string.deezer_album),
                title = album.title,
                subtitle = buildString {
                    if (album.artistName.isNotBlank()) {
                        append(album.artistName)
                    }
                    album.releaseDate?.take(4)?.let {
                        if (isNotEmpty()) append(" • ")
                        append(it)
                    }
                    if (isNotEmpty()) append(" • ")
                    append(pluralStringResource(R.plurals.x_songs, album.nbTracks, album.nbTracks))
                }
            )
        }
        item {
            DeezerDetailActions(
                previewEnabled = firstPreview != null,
                onPreview = { firstPreview?.let(onPreview) },
                onOpenDeezer = { openDeezerLink(context, album.link) },
                onDownload = { onDownloadAll(album.tracks.data) },
                saved = saved,
                onToggleSaved = onToggleSaved
            )
        }
        item {
            Text(
                text = stringResource(R.string.songs_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        items(album.tracks.data, key = { it.id }) { track ->
            DeezerTrackRow(
                track = track,
                downloadState = downloadStates[track.id],
                onClick = { onPreview(track) },
                onDownload = { onDownload(track) }
            )
        }
    }
}

@Composable
private fun DeezerArtistDetailContent(
    detail: DeezerDetail.Artist,
    saved: Boolean,
    onToggleSaved: (Boolean) -> Unit,
    downloadStates: Map<Long, DeezerDownloadState>,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit,
    onAlbum: (Long) -> Unit
) {
    val context = LocalContext.current
    val firstPreview = detail.topTracks.firstOrNull { it.isPreviewAvailable }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DeezerDetailHeader(
                imageUrl = detail.artist.imageUrl,
                eyebrow = stringResource(R.string.deezer_artist),
                title = detail.artist.name,
                subtitle = pluralStringResource(
                    R.plurals.x_albums,
                    detail.artist.nbAlbum,
                    detail.artist.nbAlbum
                ),
                circularImage = true
            )
        }
        item {
            DeezerDetailActions(
                previewEnabled = firstPreview != null,
                onPreview = { firstPreview?.let(onPreview) },
                onOpenDeezer = { openDeezerLink(context, detail.artist.link) },
                onDownload = { onDownloadAll(detail.topTracks) },
                saved = saved,
                onToggleSaved = onToggleSaved
            )
        }
        item {
            Text(
                text = stringResource(R.string.top_tracks_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        items(detail.topTracks, key = { it.id }) { track ->
            DeezerTrackRow(
                track = track,
                downloadState = downloadStates[track.id],
                onClick = { onPreview(track) },
                onDownload = { onDownload(track) }
            )
        }
        if (detail.albums.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.albums_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(detail.albums, key = { it.id }) { album ->
                        DeezerAlbumCard(album = album, onClick = { onAlbum(album.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeezerPlaylistDetailContent(
    playlist: DeezerPlaylistDetail,
    downloadStates: Map<Long, DeezerDownloadState>,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit
) {
    val context = LocalContext.current
    val firstPreview = playlist.tracks.data.firstOrNull { it.isPreviewAvailable }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DeezerDetailHeader(
                imageUrl = playlist.imageUrl,
                eyebrow = stringResource(R.string.deezer_playlist),
                title = playlist.title,
                subtitle = buildString {
                    if (playlist.creatorName.isNotBlank()) {
                        append(playlist.creatorName)
                        append(" • ")
                    }
                    append(
                        pluralStringResource(
                            R.plurals.x_songs,
                            playlist.nbTracks,
                            playlist.nbTracks
                        )
                    )
                }
            )
        }
        item {
            DeezerDetailActions(
                previewEnabled = firstPreview != null,
                onPreview = { firstPreview?.let(onPreview) },
                onOpenDeezer = { openDeezerLink(context, playlist.link) },
                onDownload = { onDownloadAll(playlist.tracks.data) }
            )
        }
        item {
            Text(
                text = stringResource(R.string.songs_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        items(playlist.tracks.data, key = { it.id }) { track ->
            DeezerTrackRow(
                track = track,
                downloadState = downloadStates[track.id],
                onClick = { onPreview(track) },
                onDownload = { onDownload(track) }
            )
        }
    }
}

@Composable
private fun DeezerDetailHeader(
    imageUrl: String?,
    eyebrow: String,
    title: String,
    subtitle: String,
    circularImage: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            MediaImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(128.dp)
                    .clip(if (circularImage) CircleShape else RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DeezerDetailActions(
    previewEnabled: Boolean,
    onPreview: () -> Unit,
    onOpenDeezer: () -> Unit,
    onDownload: () -> Unit,
    saved: Boolean? = null,
    onToggleSaved: ((Boolean) -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Button(
            onClick = onPreview,
            enabled = previewEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.deezer_preview_short))
        }
        OutlinedButton(
            onClick = onOpenDeezer,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_open_in_new_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.deezer_open_short))
        }
        if (saved != null && onToggleSaved != null) {
            FilledTonalIconButton(
                onClick = { onToggleSaved(!saved) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (saved) R.drawable.ic_favorite_24dp
                        else R.drawable.ic_favorite_outline_24dp
                    ),
                    contentDescription = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    tint = if (saved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        FilledTonalIconButton(
            onClick = onDownload,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download_24dp),
                contentDescription = stringResource(R.string.deezer_download_all),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeezerDownloadAction(
    state: DeezerDownloadState?,
    onDownload: () -> Unit
) {
    when (state) {
        null -> OutlinedButton(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.deezer_download))
        }

        is DeezerDownloadState.Queued -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.deezer_download_queued),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is DeezerDownloadState.Downloading -> Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${stringResource(R.string.deezer_downloading)} ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is DeezerDownloadState.Tagging -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.deezer_tagging),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is DeezerDownloadState.Done -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.deezer_downloaded),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        is DeezerDownloadState.Failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDownload) {
                Text(text = stringResource(R.string.deezer_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeezerPreviewSheet(
    track: DeezerTrackItem,
    downloadState: DeezerDownloadState?,
    isSaved: Boolean?,
    onToggleSaved: ((Long, Boolean) -> Unit)?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewUrl = track.preview
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(30_000L) }
    var playbackError by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackError = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(track.id) {
        playbackError = false
        if (!previewUrl.isNullOrBlank()) {
            player.setMediaItem(MediaItem.fromUri(previewUrl))
            player.prepare()
            player.playWhenReady = true
        }
    }
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 30_000L
            delay(250)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaImage(
                    model = track.imageUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.deezer_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (isSaved != null && onToggleSaved != null) {
                    IconButton(onClick = { onToggleSaved(track.id, !isSaved) }) {
                        Icon(
                            painter = painterResource(
                                if (isSaved) R.drawable.ic_favorite_24dp
                                else R.drawable.ic_favorite_outline_24dp
                            ),
                            contentDescription = stringResource(
                                if (isSaved) R.string.deezer_remove_favorite
                                else R.string.deezer_add_favorite
                            ),
                            tint = if (isSaved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            DeezerDownloadAction(
                state = downloadState,
                onDownload = onDownload
            )
            Spacer(Modifier.height(12.dp))
            if (!previewUrl.isNullOrBlank() && !playbackError) {
                Slider(
                    value = position.coerceIn(0L, duration).toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat()
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = position.asReadableDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = duration.asReadableDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledIconButton(
                        onClick = {
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0)
                                }
                                player.play()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
                            ),
                            contentDescription = null
                        )
                    }
                    TextButton(
                        onClick = { openDeezerLink(context, track.link) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_in_new_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.deezer_open_in_deezer))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.deezer_preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { openDeezerLink(context, track.link) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_new_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.deezer_open_in_deezer))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun openDeezerLink(context: Context, link: String?) {
    val uri = link?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
