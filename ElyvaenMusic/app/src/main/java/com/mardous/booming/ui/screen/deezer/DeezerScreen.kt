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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mardous.booming.R
import com.mardous.booming.data.remote.deezer.DeezerAccount
import com.mardous.booming.data.remote.deezer.DeezerDownloadEntry
import com.mardous.booming.data.remote.deezer.DeezerDownloadState
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumDetail
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumItem
import com.mardous.booming.data.remote.deezer.model.DeezerArtistItem
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistDetail
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistItem
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem
import com.mardous.booming.extensions.media.asReadableDuration
import com.mardous.booming.playback.library.MediaIDs
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.component.compose.MediaImage
import com.mardous.booming.ui.screen.player.PlayerViewModel
import com.mardous.booming.util.QrCodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel


private enum class DeezerTab(val labelRes: Int) {
    Home(R.string.deezer_tab_home),
    Search(R.string.deezer_tab_search),
    Library(R.string.deezer_tab_library),
    Downloads(R.string.deezer_tab_downloads)
}

private fun DeezerTrackItem.downloadMatchKey(): String {
    return "${title.trim().lowercase()}|${artistName.trim().lowercase()}|${albumName.trim().lowercase()}"
}

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
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadedByKey = remember(downloads) {
        downloads.values
            .filter { it.state is DeezerDownloadState.Done }
            .associateBy { it.track.downloadMatchKey() }
    }
    val downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState? = { track ->
        downloadStates[track.id] ?: downloadedByKey[track.downloadMatchKey()]?.state
    }
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
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(DeezerTab.Home) }
    var libraryTab by remember { mutableStateOf(DeezerLibraryTab.Artists) }
    var previousDownloads by remember {
        mutableStateOf<Map<Long, DeezerDownloadState>>(emptyMap())
    }
    var showStreamingDialog by remember { mutableStateOf(false) }
    var streamLoading by remember { mutableStateOf(false) }
    var pendingPreviewTrack by remember { mutableStateOf<DeezerTrackItem?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.account) {
        if (state.account != null) {
            showConnectDialog = false
        }
    }
    LaunchedEffect(state.streamingActive) {
        if (state.streamingActive) {
            showStreamingDialog = false
            pendingPreviewTrack = null
            Toast.makeText(
                context,
                context.getString(R.string.deezer_streaming_active),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == DeezerTab.Search) {
            delay(160)
            runCatching { searchFocusRequester.requestFocus() }
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
    val openTrack: (DeezerTrackItem) -> Unit = { track ->
        val songId = (downloadStateFor(track) as? DeezerDownloadState.Done)
            ?.uri?.lastPathSegment?.toLongOrNull()
        if (songId != null) {
            playerViewModel.playMediaId(MediaIDs.getPathId(MediaIDs.SONGS, songId))
        } else if (state.streamingActive) {
            streamLoading = true
            scope.launch {
                viewModel.resolveStreamUrl(track.id)
                    .onSuccess { url ->
                        playerViewModel.playMediaItem(streamingMediaItem(track, url))
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: context.getString(R.string.deezer_streaming_error),
                            Toast.LENGTH_SHORT
                        ).show()
                        openPreview(track)
                    }
                streamLoading = false
            }
        } else {
            pendingPreviewTrack = track
            showStreamingDialog = true
        }
    }
    val openDownloaded: (DeezerDownloadEntry) -> Unit = { entry ->
        val songId = (entry.state as? DeezerDownloadState.Done)
            ?.uri?.lastPathSegment?.toLongOrNull()
        if (songId != null) {
            playerViewModel.playMediaId(MediaIDs.getPathId(MediaIDs.SONGS, songId))
        }
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
            selectedTab != DeezerTab.Home -> selectedTab = DeezerTab.Home
            else -> onBack()
        }
    }

    BackHandler(enabled = state.detail != null || selectedTab != DeezerTab.Home) {
        when {
            state.detail != null -> viewModel.closeDetail()
            selectedTab != DeezerTab.Home -> selectedTab = DeezerTab.Home
        }
    }

    Scaffold(
        topBar = {
            DeezerTopBar(
                query = state.query,
                selectedTab = selectedTab,
                isSearching = state.searchLoading,
                account = state.account,
                searchFocusRequester = searchFocusRequester,
                onQueryChange = viewModel::onQueryChange,
                onClearSearch = viewModel::clearSearch,
                onTabChange = { tab ->
                    viewModel.closeDetail()
                    selectedTab = tab
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
            if (streamLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
            when {
                state.detailLoading -> DeezerLoading()
                state.detailError -> DeezerDetailError(onBack = viewModel::closeDetail)
                state.detail != null -> DeezerDetailContent(
                    detail = state.detail!!,
                    library = state.library,
                    downloadStateFor = downloadStateFor,
                    onPlay = openTrack,
                    onPreview = openPreview,
                    onDownload = openDownload,
                    onDownloadAll = openDownloadAll,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist,
                    onToggleAlbumSaved = viewModel::setAlbumSaved,
                    onToggleArtistSaved = viewModel::setArtistSaved,
                    onToggleTrackSaved = viewModel::setTrackSaved
                )

                selectedTab == DeezerTab.Search -> {
                    if (state.query.isBlank()) {
                        DeezerSearchLanding()
                    } else {
                        DeezerSearchContent(
                            state = state,
                            downloadStateFor = downloadStateFor,
                            onTypeChange = viewModel::onSearchTypeChange,
                            onRetry = { viewModel.onSearchTypeChange(state.searchType) },
                            onPlay = openTrack,
                            onDownload = openDownload,
                            onAlbum = viewModel::openAlbum,
                            onArtist = viewModel::openArtist,
                            onPlaylist = viewModel::openPlaylist,
                            onToggleTrackSaved = viewModel::setTrackSaved,
                            onToggleAlbumSaved = viewModel::setAlbumSaved,
                            onToggleArtistSaved = viewModel::setArtistSaved
                        )
                    }
                }

                selectedTab == DeezerTab.Library -> DeezerLibraryContent(
                    state = state,
                    downloadStateFor = downloadStateFor,
                    tab = libraryTab,
                    onTabChange = { libraryTab = it },
                    onRefresh = viewModel::loadLibrary,
                    onConnect = { showConnectDialog = true },
                    onPlay = openTrack,
                    onDownload = openDownload,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist,
                    onToggleTrackSaved = viewModel::setTrackSaved,
                    onToggleAlbumSaved = viewModel::setAlbumSaved,
                    onToggleArtistSaved = viewModel::setArtistSaved
                )

                selectedTab == DeezerTab.Downloads -> DeezerDownloadsContent(
                    downloads = downloads,
                    account = state.account,
                    onPlay = openDownloaded,
                    onPreview = openPreview,
                    onRetry = viewModel::downloadTrack,
                    onClearHistory = { showClearDownloadsDialog = true },
                    onExplore = { selectedTab = DeezerTab.Home },
                    onConnect = { showConnectDialog = true }
                )

                state.chartLoading -> DeezerLoading()
                state.chartError -> DeezerError(onRetry = viewModel::loadChart)
                else -> DeezerHomeContent(
                    state = state,
                    downloadStateFor = downloadStateFor,
                    downloads = downloads,
                    streamingActive = state.streamingActive,
                    onPlay = openTrack,
                    onDownload = openDownload,
                    onAlbum = viewModel::openAlbum,
                    onArtist = viewModel::openArtist,
                    onPlaylist = viewModel::openPlaylist,
                    onTab = { selectedTab = it },
                    onConnect = { showConnectDialog = true },
                    onActivateStreaming = { showStreamingDialog = true },
                    onToggleTrackSaved = viewModel::setTrackSaved,
                    onToggleAlbumSaved = viewModel::setAlbumSaved,
                    onToggleArtistSaved = viewModel::setArtistSaved
                )
            }
        }
    }

    previewTrack?.let { track ->
        DeezerPreviewSheet(
            track = track,
            downloadState = downloadStateFor(track),
            isSaved = state.library?.tracks?.any { it.id == track.id },
            onToggleSaved = viewModel::setTrackSaved,
            onDownload = { openDownload(track) },
            onDismiss = { previewTrack = null }
        )
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text(stringResource(R.string.deezer_downloads_clear_title)) },
            text = { Text(stringResource(R.string.deezer_downloads_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearDownloadsHistory()
                        showClearDownloadsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.deezer_downloads_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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
            streamingActive = state.streamingActive,
            onActivateStreaming = {
                showAccountDialog = false
                showStreamingDialog = true
            },
            onDisconnectStreaming = viewModel::disconnectStreaming,
            onDismiss = { showAccountDialog = false },
            onDisconnect = {
                viewModel.disconnect()
                showAccountDialog = false
            }
        )
    }
    if (showStreamingDialog) {
        DeezerStreamingDialog(
            state = state,
            canUsePreview = pendingPreviewTrack != null,
            onDismiss = {
                showStreamingDialog = false
                pendingPreviewTrack = null
                viewModel.cancelSmartLogin()
            },
            onGenerate = viewModel::startSmartLogin,
            onOpenLink = { url -> openStreamingLink(context, url) },
            onUsePreview = {
                showStreamingDialog = false
                pendingPreviewTrack?.let(openPreview)
                pendingPreviewTrack = null
                viewModel.cancelSmartLogin()
            }
        )
    }
}

@Composable
private fun DeezerTopBar(
    query: String,
    selectedTab: DeezerTab,
    isSearching: Boolean,
    account: DeezerAccount?,
    searchFocusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onTabChange: (DeezerTab) -> Unit,
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
                    .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 2.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back_24dp),
                        contentDescription = stringResource(R.string.back_action)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ELYVAEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.6.sp
                    )
                    Text(
                        text = stringResource(R.string.deezer_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selectedTab != DeezerTab.Search) {
                    IconButton(onClick = { onTabChange(DeezerTab.Search) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = stringResource(R.string.deezer_tab_search)
                        )
                    }
                }
                if (account != null) {
                    val initial = account.userName
                        .trim()
                        .firstOrNull()
                        ?.uppercaseChar()
                        ?.toString()
                        .orEmpty()
                    Surface(
                        onClick = onAccountClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (initial.isNotEmpty()) {
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_person_24dp),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                DeezerTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { onTabChange(tab) },
                        label = {
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            if (selectedTab == DeezerTab.Search) {
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
                        .focusRequester(searchFocusRequester)
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
    streamingActive: Boolean,
    onActivateStreaming: () -> Unit,
    onDisconnectStreaming: () -> Unit,
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
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                Text(
                    text = stringResource(
                        if (streamingActive) R.string.deezer_streaming_active
                        else R.string.deezer_streaming_title
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (streamingActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = stringResource(
                        if (streamingActive) R.string.deezer_streaming_active_hint
                        else R.string.deezer_streaming_inactive
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = if (streamingActive) onDisconnectStreaming else onActivateStreaming
                ) {
                    Text(
                        stringResource(
                            if (streamingActive) R.string.deezer_streaming_deactivate
                            else R.string.deezer_streaming_activate
                        )
                    )
                }
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
private fun DeezerStreamingDialog(
    state: DeezerUiState,
    canUsePreview: Boolean,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
    onOpenLink: (String) -> Unit,
    onUsePreview: () -> Unit
) {
    val url = state.smartLoginUrl
    val qrCode = remember(url) {
        url?.let { QrCodes.create(it, 512)?.asImageBitmap() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deezer_streaming_dialog_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.deezer_streaming_dialog_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.deezer_streaming_identity),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (url != null) {
                    qrCode?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.deezer_streaming_scan),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    state.smartLoginCode?.let { code ->
                        Text(
                            text = stringResource(R.string.deezer_streaming_code, code),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.deezer_streaming_waiting),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    if (state.smartLoginLoading) {
                        CircularProgressIndicator()
                    } else if (state.smartLoginError) {
                        Text(
                            text = stringResource(R.string.deezer_streaming_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.deezer_streaming_preview_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canUsePreview) {
                    TextButton(onClick = onUsePreview) {
                        Text(stringResource(R.string.deezer_preview_short))
                    }
                }
            }
        },
        confirmButton = {
            if (url != null) {
                TextButton(onClick = { onOpenLink(url) }) {
                    Text(stringResource(R.string.deezer_streaming_open_link))
                }
            } else {
                TextButton(
                    onClick = onGenerate,
                    enabled = !state.smartLoginLoading && !state.smartLoginPolling
                ) {
                    Text(stringResource(R.string.deezer_streaming_generate))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_action))
            }
        }
    )
}

private fun openStreamingLink(context: Context, url: String) {
    runCatching {
        val intent = Intent(context, DeezerLoginActivity::class.java)
            .putExtra(DeezerLoginActivity.EXTRA_START_URL, url)
            .putExtra(DeezerLoginActivity.EXTRA_SMART_LOGIN, true)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun streamingMediaItem(track: DeezerTrackItem, url: String): MediaItem {
    return MediaItem.Builder()
        .setUri(url)
        .setMediaId("deezer_stream_${track.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artistName)
                .setAlbumTitle(track.albumName)
                .setArtworkUri(track.imageUrl?.let(Uri::parse))
                .build()
        )
        .build()
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
private fun DeezerHomeContent(
    state: DeezerUiState,
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    downloads: Map<Long, DeezerDownloadEntry>,
    streamingActive: Boolean,
    onPlay: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit,
    onTab: (DeezerTab) -> Unit,
    onConnect: () -> Unit,
    onActivateStreaming: () -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit,
    onToggleAlbumSaved: (Long, Boolean) -> Unit,
    onToggleArtistSaved: (Long, Boolean) -> Unit
) {
    val chart = state.chart
    val library = state.library
    val heroAlbum = library?.albums?.firstOrNull() ?: chart.albums.firstOrNull()
    val heroPlaylist = library?.playlists?.firstOrNull() ?: chart.playlists.firstOrNull()
    val completedDownloads = downloads.values
        .filter { it.state is DeezerDownloadState.Done }
        .sortedBy { it.track.title.lowercase() }
    val savedTracks = library?.tracks.orEmpty()
    val savedAlbums = library?.albums.orEmpty()
    val savedArtists = library?.artists.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            heroAlbum != null -> item {
                DeezerHeroCard(
                    imageUrl = heroAlbum.imageUrl,
                    eyebrow = stringResource(R.string.deezer_album),
                    title = heroAlbum.title,
                    subtitle = listOfNotNull(
                        heroAlbum.artistName.takeIf { it.isNotBlank() },
                        heroAlbum.releaseDate?.take(4)
                    ).joinToString(" · "),
                    onOpen = { onAlbum(heroAlbum.id) }
                )
            }

            heroPlaylist != null -> item {
                DeezerHeroCard(
                    imageUrl = heroPlaylist.imageUrl,
                    eyebrow = stringResource(R.string.deezer_playlist),
                    title = heroPlaylist.title,
                    subtitle = heroPlaylist.creatorName,
                    onOpen = { onPlaylist(heroPlaylist.id) }
                )
            }
        }

        if (state.account == null) {
            item { DeezerConnectBanner(onConnect = onConnect) }
        }
        if (state.account != null && !streamingActive) {
            item { DeezerStreamingBanner(onActivate = onActivateStreaming) }
        }

        item {
            DeezerQuickAccess(
                favoritesCount = savedTracks.size,
                downloadsCount = completedDownloads.size,
                onFavorites = { onTab(DeezerTab.Library) },
                onDownloads = { onTab(DeezerTab.Downloads) }
            )
        }

        if (savedAlbums.isNotEmpty()) {
            item {
                DeezerSectionHeader(
                    title = stringResource(R.string.deezer_your_library),
                    actionLabel = stringResource(R.string.deezer_view_all),
                    onAction = { onTab(DeezerTab.Library) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedAlbums.take(10), key = { it.id }) { album ->
                        DeezerAlbumCard(
                            album = album,
                            saved = true,
                            canFavorite = state.account != null,
                            onOpen = { onAlbum(album.id) },
                            onToggleSaved = { onToggleAlbumSaved(album.id, it) }
                        )
                    }
                }
            }
        }

        if (completedDownloads.isNotEmpty()) {
            item {
                DeezerSectionHeader(
                    title = stringResource(R.string.deezer_downloads_short),
                    actionLabel = stringResource(R.string.deezer_view_all),
                    onAction = { onTab(DeezerTab.Downloads) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(completedDownloads.take(10), key = { it.track.id }) { entry ->
                        DeezerTrackCard(
                            track = entry.track,
                            downloadState = entry.state,
                            saved = savedTracks.any { it.id == entry.track.id },
                            canFavorite = state.account != null,
                            showPreviewBadge = false,
                            onPlay = { onPlay(entry.track) },
                            onDownload = { onDownload(entry.track) },
                            onToggleSaved = { onToggleTrackSaved(entry.track.id, it) }
                        )
                    }
                }
            }
        }

        if (chart.tracks.isNotEmpty()) {
            item { DeezerSectionHeader(stringResource(R.string.deezer_trending_tracks)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.tracks, key = { it.id }) { track ->
                        DeezerTrackCard(
                            track = track,
                            downloadState = downloadStateFor(track),
                            saved = savedTracks.any { it.id == track.id },
                            canFavorite = state.account != null,
                            showPreviewBadge = !streamingActive,
                            onPlay = { onPlay(track) },
                            onDownload = { onDownload(track) },
                            onToggleSaved = { onToggleTrackSaved(track.id, it) }
                        )
                    }
                }
            }
        }

        if (chart.albums.isNotEmpty()) {
            item { DeezerSectionHeader(stringResource(R.string.deezer_featured_albums)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.albums, key = { it.id }) { album ->
                        DeezerAlbumCard(
                            album = album,
                            saved = savedAlbums.any { it.id == album.id },
                            canFavorite = state.account != null,
                            onOpen = { onAlbum(album.id) },
                            onToggleSaved = { onToggleAlbumSaved(album.id, it) }
                        )
                    }
                }
            }
        }

        if (chart.playlists.isNotEmpty()) {
            item { DeezerSectionHeader(stringResource(R.string.deezer_editorial_playlists)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.playlists, key = { it.id }) { playlist ->
                        DeezerPlaylistCard(
                            playlist = playlist,
                            onOpen = { onPlaylist(playlist.id) }
                        )
                    }
                }
            }
        }

        if (chart.artists.isNotEmpty()) {
            item { DeezerSectionHeader(stringResource(R.string.deezer_popular_artists)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chart.artists, key = { it.id }) { artist ->
                        DeezerArtistCard(
                            artist = artist,
                            saved = savedArtists.any { it.id == artist.id },
                            canFavorite = state.account != null,
                            onOpen = { onArtist(artist.id) },
                            onToggleSaved = { onToggleArtistSaved(artist.id, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeezerHeroCard(
    imageUrl: String?,
    eyebrow: String,
    title: String,
    subtitle: String,
    onOpen: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(214.dp)
    ) {
        Box {
            MediaImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward_24dp),
                        contentDescription = stringResource(R.string.deezer_open_short),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeezerConnectBanner(onConnect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.deezer_connect_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.deezer_connect_banner_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onConnect) {
                Text(stringResource(R.string.deezer_connect))
            }
        }
    }
}

@Composable
private fun DeezerStreamingBanner(onActivate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_circle_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.deezer_streaming_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.deezer_streaming_inactive),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onActivate) {
                Text(stringResource(R.string.deezer_streaming_activate))
            }
        }
    }
}

@Composable
private fun DeezerQuickAccess(
    favoritesCount: Int,
    downloadsCount: Int,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        DeezerQuickCard(
            iconRes = R.drawable.ic_favorite_24dp,
            title = stringResource(R.string.deezer_favorites),
            subtitle = pluralStringResource(R.plurals.x_songs, favoritesCount, favoritesCount),
            onClick = onFavorites,
            modifier = Modifier.weight(1f)
        )
        DeezerQuickCard(
            iconRes = R.drawable.ic_download_24dp,
            title = stringResource(R.string.deezer_downloads_short),
            subtitle = pluralStringResource(R.plurals.x_songs, downloadsCount, downloadsCount),
            onClick = onDownloads,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeezerQuickCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeezerSearchLanding() {
    EmptyView(
        icon = painterResource(R.drawable.ic_search_24dp),
        title = stringResource(R.string.deezer_search_landing),
        subtitle = stringResource(R.string.deezer_search_landing_hint),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    )
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
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    tab: DeezerLibraryTab,
    onTabChange: (DeezerLibraryTab) -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onPlay: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit,
    onToggleAlbumSaved: (Long, Boolean) -> Unit,
    onToggleArtistSaved: (Long, Boolean) -> Unit
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

        if (state.account == null) {
            item {
                DeezerConnectBanner(onConnect = onConnect)
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
                    DeezerArtistRow(
                        artist = artist,
                        saved = true,
                        canFavorite = state.account != null,
                        onClick = { onArtist(artist.id) },
                        onToggleSaved = { onToggleArtistSaved(artist.id, it) }
                    )
                }

                DeezerLibraryTab.Albums -> items(library.albums, key = { it.id }) { album ->
                    DeezerAlbumRow(
                        album = album,
                        saved = true,
                        canFavorite = state.account != null,
                        onClick = { onAlbum(album.id) },
                        onToggleSaved = { onToggleAlbumSaved(album.id, it) }
                    )
                }

                DeezerLibraryTab.Playlists -> items(library.playlists, key = { it.id }) { playlist ->
                    DeezerPlaylistRow(playlist = playlist, onClick = { onPlaylist(playlist.id) })
                }

                DeezerLibraryTab.Tracks -> items(library.tracks, key = { it.id }) { track ->
                    DeezerTrackRow(
                        track = track,
                        downloadState = downloadStateFor(track),
                        saved = true,
                        canFavorite = state.account != null,
                        onClick = { onPlay(track) },
                        onDownload = { onDownload(track) },
                        onToggleSaved = { onToggleTrackSaved(track.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeezerDownloadsContent(
    downloads: Map<Long, DeezerDownloadEntry>,
    account: DeezerAccount?,
    onPlay: (DeezerDownloadEntry) -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onRetry: (DeezerTrackItem) -> Unit,
    onClearHistory: () -> Unit,
    onExplore: () -> Unit,
    onConnect: () -> Unit
) {
    val active = downloads.values
        .filter {
            it.state is DeezerDownloadState.Queued ||
                    it.state is DeezerDownloadState.Downloading ||
                    it.state is DeezerDownloadState.Tagging
        }
        .sortedBy { it.track.title.lowercase() }
    val completed = downloads.values
        .filter { it.state is DeezerDownloadState.Done }
        .sortedBy { it.track.title.lowercase() }
    val failed = downloads.values
        .filter { it.state is DeezerDownloadState.Failed }
        .sortedBy { it.track.title.lowercase() }

    if (active.isEmpty() && completed.isEmpty() && failed.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            if (account == null) {
                DeezerConnectBanner(onConnect = onConnect)
            }
            EmptyView(
                icon = painterResource(R.drawable.ic_download_24dp),
                title = stringResource(R.string.deezer_downloads_empty_title),
                subtitle = stringResource(R.string.deezer_downloads_empty_text),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onExplore,
                modifier = Modifier.padding(bottom = 120.dp)
            ) {
                Text(stringResource(R.string.downloads_open_deezer))
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.deezer_downloads_short),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.x_songs,
                            completed.size,
                            completed.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (completed.isNotEmpty() || failed.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text(stringResource(R.string.deezer_downloads_clear))
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_downloads_active))
            }
            items(active, key = { it.track.id }) { entry ->
                DeezerDownloadProgressRow(entry)
            }
        }
        if (completed.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_downloads_finished))
            }
            items(completed, key = { it.track.id }) { entry ->
                DeezerTrackRow(
                    track = entry.track,
                    downloadState = entry.state,
                    saved = true,
                    canFavorite = false,
                    onClick = { onPlay(entry) },
                    onDownload = {},
                    onToggleSaved = {}
                )
            }
        }
        if (failed.isNotEmpty()) {
            item {
                DeezerSectionHeader(stringResource(R.string.deezer_downloads_failed))
            }
            items(failed, key = { it.track.id }) { entry ->
                DeezerTrackRow(
                    track = entry.track,
                    downloadState = entry.state,
                    saved = false,
                    canFavorite = false,
                    onClick = { onPreview(entry.track) },
                    onDownload = { onRetry(entry.track) },
                    onToggleSaved = {}
                )
            }
        }
    }
}

@Composable
private fun DeezerDownloadProgressRow(entry: DeezerDownloadEntry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        MediaImage(
            model = entry.track.imageUrl,
            contentDescription = entry.track.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (val state = entry.state) {
                is DeezerDownloadState.Downloading -> {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is DeezerDownloadState.Queued -> Text(
                    text = stringResource(R.string.deezer_download_queued),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is DeezerDownloadState.Tagging -> Text(
                    text = stringResource(R.string.deezer_tagging),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun DeezerSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

private data class DeezerMenuAction(
    val label: String,
    val iconRes: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun DeezerOverflowMenu(
    actions: List<DeezerMenuAction>,
    modifier: Modifier = Modifier,
    onArtwork: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        if (onArtwork) {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(R.string.deezer_more_options),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert_24dp),
                    contentDescription = stringResource(R.string.deezer_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun DeezerTrackCard(
    track: DeezerTrackItem,
    downloadState: DeezerDownloadState?,
    saved: Boolean,
    canFavorite: Boolean,
    showPreviewBadge: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleSaved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val downloadLabel = when (downloadState) {
        is DeezerDownloadState.Done -> stringResource(R.string.deezer_downloaded)
        is DeezerDownloadState.Downloading -> stringResource(R.string.deezer_downloading)
        is DeezerDownloadState.Queued -> stringResource(R.string.deezer_download_queued)
        is DeezerDownloadState.Tagging -> stringResource(R.string.deezer_tagging)
        is DeezerDownloadState.Failed -> stringResource(R.string.deezer_retry)
        null -> stringResource(R.string.deezer_download)
    }
    val downloadIcon = when (downloadState) {
        is DeezerDownloadState.Done -> R.drawable.ic_check_24dp
        is DeezerDownloadState.Failed -> R.drawable.ic_error_24dp
        else -> R.drawable.ic_download_24dp
    }
    val downloadEnabled = downloadState !is DeezerDownloadState.Done &&
            downloadState !is DeezerDownloadState.Downloading &&
            downloadState !is DeezerDownloadState.Queued &&
            downloadState !is DeezerDownloadState.Tagging
    val actions = buildList {
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_play),
                iconRes = R.drawable.ic_play_24dp,
                onClick = onPlay
            )
        )
        add(
            DeezerMenuAction(
                label = downloadLabel,
                iconRes = downloadIcon,
                enabled = downloadEnabled,
                onClick = onDownload
            )
        )
        if (canFavorite) {
            add(
                DeezerMenuAction(
                    label = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    iconRes = if (saved) R.drawable.ic_favorite_24dp
                    else R.drawable.ic_favorite_outline_24dp,
                    onClick = { onToggleSaved(!saved) }
                )
            )
        }
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_in_deezer),
                iconRes = R.drawable.ic_open_in_new_24dp,
                onClick = { openDeezerLink(context, track.link) }
            )
        )
    }

    Column(modifier = Modifier.width(152.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onPlay)
        ) {
            MediaImage(
                model = track.imageUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize()
            )
            if (downloadState is DeezerDownloadState.Done) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_24dp),
                            contentDescription = stringResource(R.string.deezer_downloaded),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } else if (showPreviewBadge) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.deezer_preview_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            DeezerOverflowMenu(
                actions = actions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                onArtwork = true
            )
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24dp),
                        contentDescription = stringResource(R.string.deezer_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onPlay)
        ) {
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
    }
}

@Composable
private fun DeezerAlbumCard(
    album: DeezerAlbumItem,
    saved: Boolean,
    canFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val actions = buildList {
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_album),
                iconRes = R.drawable.ic_album_24dp,
                onClick = onOpen
            )
        )
        if (canFavorite) {
            add(
                DeezerMenuAction(
                    label = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    iconRes = if (saved) R.drawable.ic_favorite_24dp
                    else R.drawable.ic_favorite_outline_24dp,
                    onClick = { onToggleSaved(!saved) }
                )
            )
        }
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_in_deezer),
                iconRes = R.drawable.ic_open_in_new_24dp,
                onClick = { openDeezerLink(context, album.link) }
            )
        )
    }

    Column(modifier = Modifier.width(152.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
        ) {
            MediaImage(
                model = album.imageUrl,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize()
            )
            DeezerOverflowMenu(
                actions = actions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                onArtwork = true
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onOpen)
        ) {
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

@Composable
private fun DeezerPlaylistCard(
    playlist: DeezerPlaylistItem,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val actions = listOf(
        DeezerMenuAction(
            label = stringResource(R.string.deezer_open_playlist),
            iconRes = R.drawable.ic_queue_music_24dp,
            onClick = onOpen
        ),
        DeezerMenuAction(
            label = stringResource(R.string.deezer_open_in_deezer),
            iconRes = R.drawable.ic_open_in_new_24dp,
            onClick = { openDeezerLink(context, playlist.link) }
        )
    )

    Column(modifier = Modifier.width(152.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
        ) {
            MediaImage(
                model = playlist.imageUrl,
                contentDescription = playlist.title,
                modifier = Modifier.fillMaxSize()
            )
            DeezerOverflowMenu(
                actions = actions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                onArtwork = true
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onOpen)
        ) {
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

@Composable
private fun DeezerArtistCard(
    artist: DeezerArtistItem,
    saved: Boolean,
    canFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val actions = buildList {
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_artist),
                iconRes = R.drawable.ic_artist_24dp,
                onClick = onOpen
            )
        )
        if (canFavorite) {
            add(
                DeezerMenuAction(
                    label = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    iconRes = if (saved) R.drawable.ic_favorite_24dp
                    else R.drawable.ic_favorite_outline_24dp,
                    onClick = { onToggleSaved(!saved) }
                )
            )
        }
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_in_deezer),
                iconRes = R.drawable.ic_open_in_new_24dp,
                onClick = { openDeezerLink(context, artist.link) }
            )
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(122.dp)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpen)
        ) {
            MediaImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize()
            )
            DeezerOverflowMenu(
                actions = actions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                onArtwork = true
            )
        }
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 7.dp)
                .clickable(onClick = onOpen)
        )
    }
}

@Composable
private fun DeezerSearchContent(
    state: DeezerUiState,
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    onTypeChange: (DeezerSearchType) -> Unit,
    onRetry: () -> Unit,
    onPlay: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit,
    onToggleAlbumSaved: (Long, Boolean) -> Unit,
    onToggleArtistSaved: (Long, Boolean) -> Unit
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
                    downloadState = downloadStateFor(track),
                    saved = state.library?.tracks?.any { it.id == track.id } == true,
                    canFavorite = state.account != null,
                    onClick = { onPlay(track) },
                    onDownload = { onDownload(track) },
                    onToggleSaved = { onToggleTrackSaved(track.id, it) }
                )
            }

            state.searchType == DeezerSearchType.Albums -> items(state.albums, key = { it.id }) { album ->
                DeezerAlbumRow(
                    album = album,
                    saved = state.library?.albums?.any { it.id == album.id } == true,
                    canFavorite = state.account != null,
                    onClick = { onAlbum(album.id) },
                    onToggleSaved = { onToggleAlbumSaved(album.id, it) }
                )
            }

            state.searchType == DeezerSearchType.Artists -> items(state.artists, key = { it.id }) { artist ->
                DeezerArtistRow(
                    artist = artist,
                    saved = state.library?.artists?.any { it.id == artist.id } == true,
                    canFavorite = state.account != null,
                    onClick = { onArtist(artist.id) },
                    onToggleSaved = { onToggleArtistSaved(artist.id, it) }
                )
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
    saved: Boolean,
    canFavorite: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onToggleSaved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val downloadLabel = when (downloadState) {
        is DeezerDownloadState.Done -> stringResource(R.string.deezer_downloaded)
        is DeezerDownloadState.Downloading -> stringResource(R.string.deezer_downloading)
        is DeezerDownloadState.Queued -> stringResource(R.string.deezer_download_queued)
        is DeezerDownloadState.Tagging -> stringResource(R.string.deezer_tagging)
        is DeezerDownloadState.Failed -> stringResource(R.string.deezer_retry)
        null -> stringResource(R.string.deezer_download)
    }
    val downloadIcon = when (downloadState) {
        is DeezerDownloadState.Done -> R.drawable.ic_check_24dp
        is DeezerDownloadState.Failed -> R.drawable.ic_error_24dp
        else -> R.drawable.ic_download_24dp
    }
    val downloadEnabled = downloadState !is DeezerDownloadState.Done &&
            downloadState !is DeezerDownloadState.Downloading &&
            downloadState !is DeezerDownloadState.Queued &&
            downloadState !is DeezerDownloadState.Tagging
    val actions = buildList {
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_play),
                iconRes = R.drawable.ic_play_24dp,
                onClick = onClick
            )
        )
        add(
            DeezerMenuAction(
                label = downloadLabel,
                iconRes = downloadIcon,
                enabled = downloadEnabled,
                onClick = onDownload
            )
        )
        if (canFavorite) {
            add(
                DeezerMenuAction(
                    label = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    iconRes = if (saved) R.drawable.ic_favorite_24dp
                    else R.drawable.ic_favorite_outline_24dp,
                    onClick = { onToggleSaved(!saved) }
                )
            )
        }
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_in_deezer),
                iconRes = R.drawable.ic_open_in_new_24dp,
                onClick = { openDeezerLink(context, track.link) }
            )
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            MediaImage(
                model = track.imageUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
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
            if (track.duration > 0) {
                Text(
                    text = (track.duration * 1000L).asReadableDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            DeezerOverflowMenu(actions = actions)
        }
    }
}

@Composable
private fun DeezerAlbumRow(
    album: DeezerAlbumItem,
    saved: Boolean,
    canFavorite: Boolean,
    onClick: () -> Unit,
    onToggleSaved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val actions = buildList {
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_album),
                iconRes = R.drawable.ic_album_24dp,
                onClick = onClick
            )
        )
        if (canFavorite) {
            add(
                DeezerMenuAction(
                    label = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    iconRes = if (saved) R.drawable.ic_favorite_24dp
                    else R.drawable.ic_favorite_outline_24dp,
                    onClick = { onToggleSaved(!saved) }
                )
            )
        }
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_in_deezer),
                iconRes = R.drawable.ic_open_in_new_24dp,
                onClick = { openDeezerLink(context, album.link) }
            )
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            MediaImage(
                model = album.imageUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
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
            DeezerOverflowMenu(actions = actions)
        }
    }
}

@Composable
private fun DeezerArtistRow(
    artist: DeezerArtistItem,
    saved: Boolean,
    canFavorite: Boolean,
    onClick: () -> Unit,
    onToggleSaved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val actions = buildList {
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_artist),
                iconRes = R.drawable.ic_artist_24dp,
                onClick = onClick
            )
        )
        if (canFavorite) {
            add(
                DeezerMenuAction(
                    label = stringResource(
                        if (saved) R.string.deezer_remove_favorite
                        else R.string.deezer_add_favorite
                    ),
                    iconRes = if (saved) R.drawable.ic_favorite_24dp
                    else R.drawable.ic_favorite_outline_24dp,
                    onClick = { onToggleSaved(!saved) }
                )
            )
        }
        add(
            DeezerMenuAction(
                label = stringResource(R.string.deezer_open_in_deezer),
                iconRes = R.drawable.ic_open_in_new_24dp,
                onClick = { openDeezerLink(context, artist.link) }
            )
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
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
            DeezerOverflowMenu(actions = actions)
        }
    }
}

@Composable
private fun DeezerPlaylistRow(
    playlist: DeezerPlaylistItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val actions = listOf(
        DeezerMenuAction(
            label = stringResource(R.string.deezer_open_playlist),
            iconRes = R.drawable.ic_queue_music_24dp,
            onClick = onClick
        ),
        DeezerMenuAction(
            label = stringResource(R.string.deezer_open_in_deezer),
            iconRes = R.drawable.ic_open_in_new_24dp,
            onClick = { openDeezerLink(context, playlist.link) }
        )
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            MediaImage(
                model = playlist.imageUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
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
            DeezerOverflowMenu(actions = actions)
        }
    }
}

@Composable
private fun DeezerDetailContent(
    detail: DeezerDetail,
    library: DeezerLibrary?,
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    onPlay: (DeezerTrackItem) -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit,
    onToggleAlbumSaved: (Long, Boolean) -> Unit,
    onToggleArtistSaved: (Long, Boolean) -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit
) {
    val savedTracks = library?.tracks.orEmpty()
    val canFavorite = library != null
    when (detail) {
        is DeezerDetail.Album -> DeezerAlbumDetailContent(
            album = detail.album,
            saved = library?.albums?.any { it.id == detail.album.id } == true,
            onToggleSaved = { onToggleAlbumSaved(detail.album.id, it) },
            downloadStateFor = downloadStateFor,
            savedTracks = savedTracks,
            canFavorite = canFavorite,
            onPlay = onPlay,
            onPreview = onPreview,
            onDownload = onDownload,
            onDownloadAll = onDownloadAll,
            onToggleTrackSaved = onToggleTrackSaved
        )

        is DeezerDetail.Artist -> DeezerArtistDetailContent(
            detail = detail,
            saved = library?.artists?.any { it.id == detail.artist.id } == true,
            onToggleSaved = { onToggleArtistSaved(detail.artist.id, it) },
            downloadStateFor = downloadStateFor,
            savedTracks = savedTracks,
            canFavorite = canFavorite,
            onPlay = onPlay,
            onPreview = onPreview,
            onDownload = onDownload,
            onDownloadAll = onDownloadAll,
            onAlbum = onAlbum,
            onToggleTrackSaved = onToggleTrackSaved
        )

        is DeezerDetail.Playlist -> DeezerPlaylistDetailContent(
            playlist = detail.playlist,
            downloadStateFor = downloadStateFor,
            savedTracks = savedTracks,
            canFavorite = canFavorite,
            onPlay = onPlay,
            onPreview = onPreview,
            onDownload = onDownload,
            onDownloadAll = onDownloadAll,
            onToggleTrackSaved = onToggleTrackSaved
        )
    }
}

@Composable
private fun DeezerAlbumDetailContent(
    album: DeezerAlbumDetail,
    saved: Boolean,
    onToggleSaved: (Boolean) -> Unit,
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    savedTracks: List<DeezerTrackItem>,
    canFavorite: Boolean,
    onPlay: (DeezerTrackItem) -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val firstPlayable = album.tracks.data.firstOrNull {
        downloadStateFor(it) is DeezerDownloadState.Done || it.isPreviewAvailable
    }
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
                previewEnabled = firstPlayable != null,
                onPreview = { firstPlayable?.let(onPlay) },
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
                downloadState = downloadStateFor(track),
                saved = savedTracks.any { it.id == track.id },
                canFavorite = canFavorite,
                onClick = { onPlay(track) },
                onDownload = { onDownload(track) },
                onToggleSaved = { onToggleTrackSaved(track.id, it) }
            )
        }
    }
}

@Composable
private fun DeezerArtistDetailContent(
    detail: DeezerDetail.Artist,
    saved: Boolean,
    onToggleSaved: (Boolean) -> Unit,
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    savedTracks: List<DeezerTrackItem>,
    canFavorite: Boolean,
    onPlay: (DeezerTrackItem) -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit,
    onAlbum: (Long) -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val firstPlayable = detail.topTracks.firstOrNull {
        downloadStateFor(it) is DeezerDownloadState.Done || it.isPreviewAvailable
    }
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
                previewEnabled = firstPlayable != null,
                onPreview = { firstPlayable?.let(onPlay) },
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
                downloadState = downloadStateFor(track),
                saved = savedTracks.any { it.id == track.id },
                canFavorite = canFavorite,
                onClick = { onPlay(track) },
                onDownload = { onDownload(track) },
                onToggleSaved = { onToggleTrackSaved(track.id, it) }
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
                        DeezerAlbumCard(
                            album = album,
                            saved = false,
                            canFavorite = false,
                            onOpen = { onAlbum(album.id) },
                            onToggleSaved = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeezerPlaylistDetailContent(
    playlist: DeezerPlaylistDetail,
    downloadStateFor: (DeezerTrackItem) -> DeezerDownloadState?,
    savedTracks: List<DeezerTrackItem>,
    canFavorite: Boolean,
    onPlay: (DeezerTrackItem) -> Unit,
    onPreview: (DeezerTrackItem) -> Unit,
    onDownload: (DeezerTrackItem) -> Unit,
    onDownloadAll: (List<DeezerTrackItem>) -> Unit,
    onToggleTrackSaved: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val firstPlayable = playlist.tracks.data.firstOrNull {
        downloadStateFor(it) is DeezerDownloadState.Done || it.isPreviewAvailable
    }
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
                previewEnabled = firstPlayable != null,
                onPreview = { firstPlayable?.let(onPlay) },
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
                downloadState = downloadStateFor(track),
                saved = savedTracks.any { it.id == track.id },
                canFavorite = canFavorite,
                onClick = { onPlay(track) },
                onDownload = { onDownload(track) },
                onToggleSaved = { onToggleTrackSaved(track.id, it) }
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
            Text(
                text = stringResource(R.string.deezer_play),
                maxLines = 1
            )
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
