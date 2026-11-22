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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mardous.booming.R
import com.mardous.booming.core.model.shuffle.OpenShuffleMode
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.remote.odesli.ResolvedLink
import com.mardous.booming.ui.component.compose.BottomSheetDialogSurface
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.component.compose.MediaImage
import com.mardous.booming.ui.screen.player.PlayerViewModel
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Receives a Deezer/Spotify/YouTube/Tidal/Apple Music link shared with
 * Elyvaen and resolves it through song.link (the service used by Redomi).
 * The result can be opened on the equivalent platform or played from the
 * local library when the song exists there.
 */
class LinkResolverFragment : BottomSheetDialogFragment() {

    private val viewModel: LinkResolverViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()

    private val link: String?
        get() = arguments?.getString(ARG_LINK)

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
                    LaunchedEffect(link) {
                        viewModel.resolve(link)
                    }
                    LinkResolverSheet(
                        state = state,
                        onPlay = { songs, index ->
                            playerViewModel.openQueue(
                                songs,
                                index,
                                shuffleMode = OpenShuffleMode.Off
                            )
                            dismiss()
                        },
                        onOpenUrl = ::openUrl,
                        onRetry = { viewModel.resolve(link) },
                        onClose = { dismiss() }
                    )
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.link_resolver_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_LINK = "link"

        fun newInstance(link: String): LinkResolverFragment {
            return LinkResolverFragment().apply {
                arguments = Bundle().apply { putString(ARG_LINK, link) }
            }
        }
    }
}

@Composable
private fun LinkResolverSheet(
    state: LinkResolverState,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    BottomSheetDialogSurface(
        title = { Text(text = stringResource(R.string.link_resolver_title)) }
    ) {
        when (state) {
            is LinkResolverState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.link_resolver_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is LinkResolverState.Error -> {
                EmptyView(
                    icon = painterResource(R.drawable.ic_open_in_new_24dp),
                    title = stringResource(R.string.link_resolver_error),
                    subtitle = state.message,
                    modifier = Modifier.fillMaxWidth(),
                    button = {
                        TextButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.deezer_retry))
                        }
                    }
                )
            }

            is LinkResolverState.Success -> {
                LinkResolverSuccess(
                    resolved = state.resolved,
                    localSongs = state.localSongs,
                    onPlay = onPlay,
                    onOpenUrl = onOpenUrl,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun LinkResolverSuccess(
    resolved: ResolvedLink,
    localSongs: List<Song>,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaImage(
                    model = resolved.artworkUrl,
                    contentDescription = resolved.title,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolved.title ?: stringResource(R.string.link_resolver_unknown_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!resolved.artist.isNullOrBlank()) {
                        Text(
                            text = resolved.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!resolved.provider.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.link_resolver_source, resolved.provider),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (resolved.deezerUrl != null) {
            item {
                Button(
                    onClick = { onOpenUrl(resolved.deezerUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.deezer_open_in_deezer))
                }
            }
        }

        val otherLinks = resolved.links
            .filterKeys { it != "deezer" }
            .entries
            .take(8)
        if (otherLinks.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    otherLinks.forEach { (platform, url) ->
                        OutlinedButton(onClick = { onOpenUrl(url) }) {
                            Text(text = platformLabel(platform))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.link_resolver_in_library),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (localSongs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.link_resolver_not_in_library),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(localSongs) { index, song ->
                LocalSongRow(song = song, onClick = { onPlay(localSongs, index) })
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(R.string.close_action))
                }
            }
        }
    }
}

@Composable
private fun LocalSongRow(song: Song, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            MediaImage(
                model = song,
                contentDescription = song.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(song.artistName, song.albumName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.action_play),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun platformLabel(platform: String): String {
    val spaced = platform.replace(Regex("([a-z])([A-Z])"), "$1 $2")
    return spaced.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
