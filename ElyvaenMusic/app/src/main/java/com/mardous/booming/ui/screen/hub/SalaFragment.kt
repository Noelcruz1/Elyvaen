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

package com.mardous.booming.ui.screen.hub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.mardous.booming.R
import com.mardous.booming.data.local.LocalVideo
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.mediaserver.DlnaRenderer
import com.mardous.booming.ui.screen.video.VideoPlayerActivity
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SalaFragment : Fragment() {

    private val viewModel: SalaViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BoomingMusicTheme {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val song by viewModel.currentSong.collectAsStateWithLifecycle()
                    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
                    val mediaServer by viewModel.mediaServer.collectAsStateWithLifecycle()
                    SalaScreen(
                        state = state,
                        song = song,
                        waveform = waveform,
                        mediaServerRunning = mediaServer.running,
                        onBack = { findNavController().navigateUp() },
                        onScan = viewModel::scanRenderers,
                        onSend = viewModel::sendTo,
                        onOpenLyrics = { findNavController().navigate(R.id.nav_lyrics) },
                        onOpenMediaServer = { findNavController().navigate(R.id.nav_media_server) },
                        onRefreshVideos = viewModel::refreshVideos,
                        onPlayVideo = { video -> playVideo(video) }
                    )
                }
            }
        }
    }

    private fun playVideo(video: LocalVideo) {
        startActivity(
            Intent(requireContext(), VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerActivity.EXTRA_URI, video.uri.toString())
        )
    }
}

@Composable
private fun SalaScreen(
    state: SalaUiState,
    song: Song,
    waveform: FloatArray,
    mediaServerRunning: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSend: (DlnaRenderer) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenMediaServer: () -> Unit,
    onRefreshVideos: () -> Unit,
    onPlayVideo: (LocalVideo) -> Unit
) {
    val context = LocalContext.current
    val videoPermission = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        onRefreshVideos()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            videoPermission.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO))
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f)) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back_24dp),
                                contentDescription = stringResource(R.string.back_action)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ELYVAEN HUB · H5",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.sala_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
            }
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { NowPlayingCard(song, waveform, onOpenLyrics) }
                item {
                    CastCard(
                        state = state,
                        mediaServerRunning = mediaServerRunning,
                        onScan = onScan,
                        onSend = onSend,
                        onOpenMediaServer = onOpenMediaServer
                    )
                }
                item {
                    VideoCard(
                        videos = state.videos,
                        onPlay = onPlayVideo,
                        onRefresh = onRefreshVideos
                    )
                }
                state.message?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                state.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(song: Song, waveform: FloatArray, onOpenLyrics: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.sala_now_playing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = song.title.ifBlank { stringResource(R.string.sala_nothing) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(song.artistName, song.albumName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Waveform(waveform)
            OutlinedButton(onClick = onOpenLyrics, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_lyrics_outline_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sala_open_lyrics))
            }
        }
    }
}

@Composable
private fun Waveform(waveform: FloatArray) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
    ) {
        val bars = 48
        val gap = size.width / bars * 0.35f
        val barWidth = (size.width / bars) - gap
        for (index in 0 until bars) {
            val first = waveform.getOrElse(index * 2) { 0f }
            val second = waveform.getOrElse(index * 2 + 1) { 0f }
            val amplitude = max(abs(first), abs(second)).coerceIn(0f, 1f)
            val barHeight = (size.height * 0.08f) + amplitude * size.height * 0.84f
            val x = index * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(primary, tertiary)),
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun CastCard(
    state: SalaUiState,
    mediaServerRunning: Boolean,
    onScan: () -> Unit,
    onSend: (DlnaRenderer) -> Unit,
    onOpenMediaServer: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sala_cast_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.sala_cast_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!mediaServerRunning) {
                Text(
                    text = stringResource(R.string.sala_cast_server_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                TextButton(onClick = onOpenMediaServer) {
                    Text(stringResource(R.string.sala_cast_open_server))
                }
            }
            Button(
                onClick = onScan,
                enabled = !state.scanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.scanning) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sala_cast_scanning))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_phonelink_ring_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sala_cast_scan))
                }
            }
            state.renderers.forEach { renderer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phonelink_ring_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = renderer.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onSend(renderer) },
                        enabled = !state.sending
                    ) {
                        Text(stringResource(R.string.sala_cast_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    videos: List<LocalVideo>,
    onPlay: (LocalVideo) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sala_videos_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.sala_videos_refresh))
                }
            }
            if (videos.isEmpty()) {
                Text(
                    text = stringResource(R.string.sala_videos_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.sala_videos_count, videos.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                videos.take(12).forEach { video ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlay(video) }
                            .padding(vertical = 7.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDuration(video.duration)} · " +
                                        Formatter.formatFileSize(LocalContext.current, video.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000L).coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%d:%02d",
        seconds / 60,
        seconds % 60
    )
}
