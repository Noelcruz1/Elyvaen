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

import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mardous.booming.R
import com.mardous.booming.data.remote.deezer.DeezerDownloadEntry
import com.mardous.booming.data.remote.deezer.DeezerDownloadState
import com.mardous.booming.playback.library.MediaIDs
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.component.compose.MediaImage
import com.mardous.booming.ui.screen.player.PlayerViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDeezer: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = koinActivityViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val playDownloaded: (android.net.Uri) -> Unit = { uri ->
        uri.lastPathSegment?.toLongOrNull()?.let { songId ->
            playerViewModel.playMediaId(MediaIDs.getPathId(MediaIDs.SONGS, songId))
        }
    }
    val active = entries.values.filter {
        it.state is DeezerDownloadState.Queued ||
                it.state is DeezerDownloadState.Downloading ||
                it.state is DeezerDownloadState.Tagging
    }
    val finished = entries.values.filter {
        it.state is DeezerDownloadState.Done || it.state is DeezerDownloadState.Failed
    }
    val storage = remember {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        Formatter.formatFileSize(context, stat.availableBytes) to
                Formatter.formatFileSize(context, stat.totalBytes)
    }

    Scaffold(
        topBar = {
            DownloadsTopBar(
                canClear = finished.isNotEmpty(),
                onClear = viewModel::clearFinished,
                onBack = onBack
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            if (entries.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    EmptyView(
                        icon = painterResource(R.drawable.ic_download_24dp),
                        title = stringResource(R.string.downloads_empty_title),
                        subtitle = stringResource(R.string.downloads_empty_subtitle),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onOpenDeezer,
                        modifier = Modifier.padding(bottom = 120.dp)
                    ) {
                        Text(text = stringResource(R.string.downloads_open_deezer))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StorageCard(
                            free = storage.first,
                            total = storage.second
                        )
                    }
                    if (active.isNotEmpty()) {
                        item {
                            DownloadsSectionHeader(stringResource(R.string.downloads_active))
                        }
                        items(active, key = { it.track.id }) { entry ->
                            DownloadRow(
                                entry = entry,
                                onRetry = { viewModel.retry(entry.track) },
                                onPlay = null
                            )
                        }
                    }
                    if (finished.isNotEmpty()) {
                        item {
                            DownloadsSectionHeader(stringResource(R.string.downloads_finished))
                        }
                        items(finished, key = { it.track.id }) { entry ->
                            DownloadRow(
                                entry = entry,
                                onRetry = { viewModel.retry(entry.track) },
                                onPlay = (entry.state as? DeezerDownloadState.Done)
                                    ?.let { { playDownloaded(it.uri) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsTopBar(
    canClear: Boolean,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
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
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (canClear) {
                    IconButton(onClick = onClear) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear_all_24dp),
                            contentDescription = stringResource(R.string.downloads_clear_finished)
                        )
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun StorageCard(free: String, total: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.downloads_storage).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
            Text(
                text = stringResource(R.string.downloads_storage_summary, free, total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.downloads_folder_label,
                    stringResource(R.string.downloads_folder)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun DownloadRow(
    entry: DeezerDownloadEntry,
    onRetry: () -> Unit,
    onPlay: (() -> Unit)?
) {
    Surface(
        onClick = { onPlay?.invoke() },
        enabled = onPlay != null,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            MediaImage(
                model = entry.track.imageUrl,
                contentDescription = entry.track.title,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
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
            }
            when (val state = entry.state) {
                is DeezerDownloadState.Done -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.downloads_in_library),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is DeezerDownloadState.Failed -> Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.deezer_retry))
                    }
                }

                is DeezerDownloadState.Downloading -> Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.width(80.dp)
                    )
                }

                is DeezerDownloadState.Queued -> Text(
                    text = stringResource(R.string.deezer_download_queued),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is DeezerDownloadState.Tagging -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
