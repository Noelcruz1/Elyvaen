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

package com.mardous.booming.ui.screen.library.hidden

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mardous.booming.R
import com.mardous.booming.data.local.HiddenLibraryItem
import com.mardous.booming.data.local.HiddenLibraryStore
import com.mardous.booming.ui.component.compose.BottomSheetDialogSurface
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.screen.library.LibraryViewModel
import com.mardous.booming.ui.screen.library.ReloadType
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Lists the albums, artists and tracks archived from the library and lets the
 * user restore them. Nothing here deletes files.
 */
class HiddenContentFragment : BottomSheetDialogFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BoomingMusicTheme {
                    HiddenContentSheet(onChanged = ::reloadLibrary)
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        reloadLibrary()
    }

    private fun reloadLibrary() {
        libraryViewModel.forceReload(ReloadType.Songs)
        libraryViewModel.forceReload(ReloadType.Albums)
        libraryViewModel.forceReload(ReloadType.Artists)
        libraryViewModel.forceReload(ReloadType.Genres)
        libraryViewModel.forceReload(ReloadType.Years)
    }
}

private data class HiddenSection(
    val type: String,
    val labelRes: Int
)

@Composable
private fun HiddenContentSheet(onChanged: () -> Unit) {
    val items by HiddenLibraryStore.items.collectAsStateWithLifecycle()
    val sections = listOf(
        HiddenSection(HiddenLibraryStore.TYPE_ALBUM, R.string.albums_label),
        HiddenSection(HiddenLibraryStore.TYPE_ARTIST, R.string.artists_label),
        HiddenSection(HiddenLibraryStore.TYPE_TRACK, R.string.songs_label)
    )

    BottomSheetDialogSurface(
        title = { Text(text = stringResource(R.string.hidden_content_title)) }
    ) {
        if (items.isEmpty()) {
            EmptyView(
                icon = painterResource(R.drawable.ic_blacklist_24dp),
                title = stringResource(R.string.hidden_content_empty),
                subtitle = stringResource(R.string.hidden_content_summary),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.hidden_content_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                HiddenLibraryStore.restoreAll()
                                onChanged()
                            }
                        ) {
                            Text(text = stringResource(R.string.hidden_content_restore_all))
                        }
                    }
                }
                sections.forEach { section ->
                    val group = items.filter { it.type == section.type }
                    if (group.isNotEmpty()) {
                        item(key = "header-${section.type}") {
                            Text(
                                text = stringResource(section.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(
                            items = group,
                            key = { "${it.type}:${it.id}:${it.name}" }
                        ) { item ->
                            HiddenItemRow(
                                item = item,
                                onRestore = {
                                    HiddenLibraryStore.restore(item)
                                    onChanged()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenItemRow(item: HiddenLibraryItem, onRestore: () -> Unit) {
    val subtitle = when {
        item.type == HiddenLibraryStore.TYPE_ARTIST && item.count > 0 ->
            pluralStringResource(R.plurals.x_albums, item.count, item.count)
        else -> item.subtitle
    }
    Surface(
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
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = onRestore) {
                Text(text = stringResource(R.string.hidden_content_restore))
            }
        }
    }
}
