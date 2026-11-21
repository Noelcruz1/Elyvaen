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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mardous.booming.R
import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.ContentType
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.model.Suggestion
import com.mardous.booming.extensions.media.asReadableDuration
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.ui.component.compose.MediaImage
import com.mardous.booming.ui.screen.dap.DapStatusStrip

private val SectionShape = RoundedCornerShape(22.dp)
private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun ElyvaenHomeTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            ) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ELYVAEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.6.sp
                    )
                    Text(
                        text = "Cypfq DAP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onStatsClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trending_up_24dp),
                        contentDescription = stringResource(R.string.stats_title)
                    )
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.search_label)
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_24dp),
                        contentDescription = stringResource(R.string.settings_title)
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_equalizer_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ElyvaenStatusCard(
    onDashboardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = SectionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            DapStatusStrip(modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDashboardClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_equalizer_24dp),
                    contentDescription = stringResource(R.string.dap_dashboard),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ElyvaenSourceRow(
    onDeezerClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        ElyvaenSourceChip(
            text = stringResource(R.string.deezer_local_source),
            icon = R.drawable.ic_library_music_24dp,
            selected = true,
            onClick = {},
            modifier = Modifier.weight(1f)
        )
        ElyvaenSourceChip(
            text = stringResource(R.string.deezer_source),
            icon = R.drawable.ic_cloud_24dp,
            selected = false,
            onClick = onDeezerClick,
            modifier = Modifier.weight(1f)
        )
        ElyvaenSourceChip(
            text = stringResource(R.string.deezer_downloads_source),
            icon = R.drawable.ic_download_24dp,
            selected = false,
            onClick = onDownloadsClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ElyvaenSourceChip(
    text: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier.height(54.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ElyvaenHomeIntro(
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.elyvaen_your_music),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.elyvaen_home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPlayAllClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.elyvaen_play_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledTonalButton(
                onClick = onShuffleClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.shuffle_action),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ElyvaenHomeTopActions(
    onActionClick: (ContentType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        ElyvaenQuickAction(
            text = stringResource(R.string.top_tracks_label),
            icon = R.drawable.ic_trending_up_24dp,
            tint = MaterialTheme.colorScheme.primary,
            onClick = { onActionClick(ContentType.TopTracks) },
            modifier = Modifier.weight(1f)
        )
        ElyvaenQuickAction(
            text = stringResource(R.string.last_added_label),
            icon = R.drawable.ic_library_add_24dp,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = { onActionClick(ContentType.RecentSongs) },
            modifier = Modifier.weight(1f)
        )
        ElyvaenQuickAction(
            text = stringResource(R.string.history_label),
            icon = R.drawable.ic_history_24dp,
            tint = MaterialTheme.colorScheme.tertiary,
            onClick = { onActionClick(ContentType.History) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ElyvaenQuickAction(
    text: String,
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.height(96.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.16f))
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ElyvaenSuggestionSection(
    suggestion: Suggestion,
    onSongItemClick: (List<Song>, Int) -> Unit,
    onItemClick: (Any) -> Unit,
    onPlayAlbumClick: (Album) -> Unit,
    onShuffleClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs = suggestion.items.filterIsInstance<Song>()
    val albums = suggestion.items.filterIsInstance<Album>()
    val artists = suggestion.items.filterIsInstance<Artist>()
    val canOpen = suggestion.type != ContentType.Favorites &&
            suggestion.type != ContentType.TopTracks

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(suggestion.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(suggestion.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (songs.isNotEmpty() && !canOpen) {
                IconButton(onClick = onShuffleClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle_24dp),
                        contentDescription = stringResource(R.string.action_shuffle_play),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (canOpen) {
                TextButton(
                    onClick = onOpenClick,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.elyvaen_view_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        when {
            suggestion.type == ContentType.NotRecentlyPlayed -> ElyvaenForgottenTracks(
                songs = songs,
                onSongItemClick = onSongItemClick
            )

            songs.isNotEmpty() -> ElyvaenSongSpotlight(
                songs = songs,
                onSongItemClick = onSongItemClick,
                onShuffleClick = onShuffleClick
            )

            albums.isNotEmpty() -> ElyvaenAlbumGrid(
                albums = albums,
                onAlbumClick = onItemClick,
                onPlayAlbumClick = onPlayAlbumClick
            )

            artists.isNotEmpty() -> ElyvaenArtistRow(
                artists = artists,
                onArtistClick = onItemClick
            )
        }
    }
}

@Composable
private fun ElyvaenSongSpotlight(
    songs: List<Song>,
    onSongItemClick: (List<Song>, Int) -> Unit,
    onShuffleClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "elyvaen-shuffle") {
            ElyvaenMixTile(onClick = onShuffleClick)
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            ElyvaenSongCard(
                song = song,
                index = index,
                onClick = { onSongItemClick(songs, index) }
            )
        }
    }
}

@Composable
private fun ElyvaenMixTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .width(148.dp)
            .height(188.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.elyvaen_mix),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.shuffle_all_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ElyvaenSongCard(
    song: Song,
    index: Int,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(148.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
        ) {
            MediaImage(
                model = song,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.62f)
                            ),
                            startY = 150f
                        )
                    )
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
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
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.displayArtistName(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ElyvaenAlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Any) -> Unit,
    onPlayAlbumClick: (Album) -> Unit
) {
    if (albums.size == 1) {
        ElyvaenFeaturedAlbum(
            album = albums.first(),
            onClick = { onAlbumClick(albums.first()) },
            onPlayClick = { onPlayAlbumClick(albums.first()) },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        return
    }

    val columns = if (albums.size == 2) 2 else 3
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        albums.take(9).chunked(columns).forEach { rowAlbums ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowAlbums.forEach { album ->
                    ElyvaenAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) },
                        onPlayClick = { onPlayAlbumClick(album) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowAlbums.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ElyvaenFeaturedAlbum(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = SectionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            MediaImage(
                model = album,
                contentDescription = album.name,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.albums_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                Text(
                    text = pluralStringResource(
                        R.plurals.x_songs,
                        album.songCount,
                        album.songCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24dp),
                        contentDescription = stringResource(R.string.action_play_x, album.name),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ElyvaenAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CardShape)
                .clickable(onClick = onClick)
        ) {
            MediaImage(
                model = album,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24dp),
                        contentDescription = stringResource(R.string.action_play_x, album.name),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = album.name,
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

@Composable
private fun ElyvaenArtistRow(
    artists: List<Artist>,
    onArtistClick: (Any) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            ElyvaenArtistCard(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
private fun ElyvaenArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(126.dp)
            .clip(CardShape)
            .clickable(onClick = onClick)
    ) {
        MediaImage(
            model = artist,
            contentDescription = artist.name,
            modifier = Modifier
                .size(126.dp)
                .clip(CardShape)
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pluralStringResource(
                R.plurals.x_albums,
                artist.albumCount,
                artist.albumCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun ElyvaenForgottenTracks(
    songs: List<Song>,
    onSongItemClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    Surface(
        shape = SectionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            songs.take(5).forEachIndexed { index, song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongItemClick(songs, index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    MediaImage(
                        model = song,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(46.dp)
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
                            text = song.displayArtistName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = song.duration.asReadableDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (index != songs.take(5).lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
