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

package com.mardous.booming.ui.screen.health

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.mardous.booming.R
import com.mardous.booming.data.local.EditTarget
import com.mardous.booming.data.model.Song
import com.mardous.booming.ui.component.base.AbsTagEditorActivity
import com.mardous.booming.ui.component.compose.EmptyView
import com.mardous.booming.ui.component.compose.MediaImage
import com.mardous.booming.ui.screen.tageditor.SongTagEditorActivity
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MetadataHealthFragment : Fragment() {

    private val viewModel: MetadataHealthViewModel by viewModel()
    private var hasResumed = false

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
                    val autoFixState by viewModel.autoFixState.collectAsStateWithLifecycle()
                    MetadataHealthScreen(
                        state = state,
                        autoFixState = autoFixState,
                        onBack = { findNavController().navigateUp() },
                        onRefresh = viewModel::refresh,
                        onScanCovers = viewModel::scanCovers,
                        onAutoFix = viewModel::autoFixFields,
                        onClearAutoFix = viewModel::clearAutoFixState,
                        onEdit = ::openTagEditor
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed) {
            viewModel.refresh()
        } else {
            hasResumed = true
        }
    }

    private fun openTagEditor(song: Song) {
        startActivity(
            Intent(requireContext(), SongTagEditorActivity::class.java).apply {
                putExtra(AbsTagEditorActivity.EXTRA_TARGET, EditTarget.song(song))
            }
        )
    }
}

private sealed interface HealthFilter {
    data object All : HealthFilter
    data class Issue(val issue: MetadataIssue) : HealthFilter
    data object Duplicates : HealthFilter
}

@Composable
private fun MetadataHealthScreen(
    state: MetadataHealthState,
    autoFixState: AutoFixState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onScanCovers: () -> Unit,
    onAutoFix: () -> Unit,
    onClearAutoFix: () -> Unit,
    onEdit: (Song) -> Unit
) {
    var filter by remember { mutableStateOf<HealthFilter>(HealthFilter.All) }
    var showAutoFixDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MetadataHealthTopBar(
                loading = state.loading,
                onBack = onBack,
                onRefresh = onRefresh
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
            if (state.loading && state.total == 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.metadata_health_scanning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                }
            } else {
                HealthContent(
                    state = state,
                    filter = filter,
                    autoFixState = autoFixState,
                    onFilterChange = { filter = it },
                    onScanCovers = onScanCovers,
                    onAutoFixRequest = { showAutoFixDialog = true },
                    onEdit = onEdit
                )
            }
        }
    }

    if (showAutoFixDialog) {
        AlertDialog(
            onDismissRequest = { showAutoFixDialog = false },
            title = { Text(text = stringResource(R.string.metadata_health_autofix_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.metadata_health_autofix_message,
                        MetadataHealthViewModel.MAX_AUTO_FIX
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAutoFixDialog = false
                        onAutoFix()
                    }
                ) {
                    Text(text = stringResource(R.string.metadata_health_autofix_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoFixDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    when (autoFixState) {
        is AutoFixState.Finished -> {
            AlertDialog(
                onDismissRequest = onClearAutoFix,
                title = { Text(text = stringResource(R.string.metadata_health_autofix_title)) },
                text = {
                    Text(
                        text = if (autoFixState.fixed == 0 &&
                            autoFixState.skipped == 0 &&
                            autoFixState.failed == 0
                        ) {
                            stringResource(R.string.metadata_health_autofix_none)
                        } else {
                            stringResource(
                                R.string.metadata_health_autofix_done,
                                autoFixState.fixed,
                                autoFixState.skipped,
                                autoFixState.failed
                            )
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = onClearAutoFix) {
                        Text(text = stringResource(R.string.close_action))
                    }
                }
            )
        }

        is AutoFixState.Error -> {
            AlertDialog(
                onDismissRequest = onClearAutoFix,
                title = { Text(text = stringResource(R.string.metadata_health_autofix_error)) },
                text = { Text(text = autoFixState.message.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = onClearAutoFix) {
                        Text(text = stringResource(R.string.close_action))
                    }
                }
            )
        }

        else -> Unit
    }
}

@Composable
private fun MetadataHealthTopBar(
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
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
                        text = "ELYVAEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.metadata_health_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_update_24dp),
                            contentDescription = stringResource(R.string.metadata_health_refresh)
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
private fun HealthContent(
    state: MetadataHealthState,
    filter: HealthFilter,
    autoFixState: AutoFixState,
    onFilterChange: (HealthFilter) -> Unit,
    onScanCovers: () -> Unit,
    onAutoFixRequest: () -> Unit,
    onEdit: (Song) -> Unit
) {
    val filters = buildList {
        add(Triple(HealthFilter.All, R.string.metadata_health_all, state.unhealthy))
        MetadataIssue.entries.forEach { issue ->
            add(Triple(HealthFilter.Issue(issue), issue.labelRes, state.issues[issue]?.size ?: 0))
        }
        add(Triple(HealthFilter.Duplicates, R.string.metadata_health_duplicates, state.duplicates.size))
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            HealthSummaryCard(
                state = state,
                autoFixState = autoFixState,
                onScanCovers = onScanCovers,
                onAutoFixRequest = onAutoFixRequest
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                filters.forEach { (itemFilter, labelRes, count) ->
                    FilterChip(
                        selected = filter == itemFilter,
                        onClick = { onFilterChange(itemFilter) },
                        enabled = count > 0 || filter == itemFilter,
                        label = {
                            Text(
                                text = "${stringResource(labelRes)} ($count)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        when (val current = filter) {
            is HealthFilter.All -> {
                val songs = state.issues.values
                    .flatten()
                    .distinctBy { it.id }
                    .sortedBy { it.title.lowercase() }
                if (songs.isEmpty()) {
                    item { HealthEmpty(R.string.metadata_health_empty_all) }
                } else {
                    items(songs, key = { "all-${it.id}" }) { song ->
                        HealthSongRow(
                            song = song,
                            issues = state.issuesBySong[song.id].orEmpty(),
                            duplicate = false,
                            onEdit = { onEdit(song) }
                        )
                    }
                }
            }

            is HealthFilter.Issue -> {
                val songs = state.issues[current.issue].orEmpty()
                if (songs.isEmpty()) {
                    item { HealthEmpty(R.string.metadata_health_empty) }
                } else {
                    items(songs, key = { "issue-${current.issue.name}-${it.id}" }) { song ->
                        HealthSongRow(
                            song = song,
                            issues = state.issuesBySong[song.id].orEmpty(),
                            duplicate = false,
                            onEdit = { onEdit(song) }
                        )
                    }
                }
            }

            is HealthFilter.Duplicates -> {
                if (state.duplicates.isEmpty()) {
                    item { HealthEmpty(R.string.metadata_health_empty) }
                } else {
                    state.duplicates.forEach { group ->
                        item(key = "group-${group.title}-${group.artist}") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.metadata_health_duplicate_group,
                                        group.songs.size
                                    ) + " · " + group.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        items(group.songs, key = { "dup-${it.id}" }) { song ->
                            HealthSongRow(
                                song = song,
                                issues = state.issuesBySong[song.id].orEmpty(),
                                duplicate = true,
                                onEdit = { onEdit(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSummaryCard(
    state: MetadataHealthState,
    autoFixState: AutoFixState,
    onScanCovers: () -> Unit,
    onAutoFixRequest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${state.score}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        R.string.metadata_health_summary,
                        state.healthy,
                        state.total
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            LinearProgressIndicator(
                progress = { state.score / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            if (state.unhealthy > 0) {
                Text(
                    text = stringResource(R.string.metadata_health_unhealthy, state.unhealthy),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.metadata_health_empty_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAutoFixRequest,
                    enabled = !state.loading &&
                            autoFixState !is AutoFixState.Running &&
                            state.unhealthy > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.metadata_health_autofix))
                }
                OutlinedButton(
                    onClick = onScanCovers,
                    enabled = !state.scanningCovers && !state.loading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.scanningCovers) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(text = stringResource(R.string.metadata_health_scan_covers))
                    }
                }
            }

            if (autoFixState is AutoFixState.Running) {
                LinearProgressIndicator(
                    progress = {
                        if (autoFixState.total == 0) 0f
                        else autoFixState.done.toFloat() / autoFixState.total.toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = stringResource(
                        R.string.metadata_health_autofix_running,
                        autoFixState.done,
                        autoFixState.total
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HealthSongRow(
    song: Song,
    issues: List<MetadataIssue>,
    duplicate: Boolean,
    onEdit: () -> Unit
) {
    Surface(
        onClick = onEdit,
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
                val labels = buildList {
                    issues.forEach { add(stringResource(it.labelRes)) }
                    if (duplicate) add(stringResource(R.string.metadata_health_duplicate_badge))
                }
                if (labels.isNotEmpty()) {
                    Text(
                        text = labels.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_edit_24dp),
                contentDescription = stringResource(R.string.metadata_health_edit),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HealthEmpty(titleRes: Int) {
    EmptyView(
        icon = painterResource(R.drawable.ic_edit_note_24dp),
        title = stringResource(titleRes),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 24.dp)
    )
}
