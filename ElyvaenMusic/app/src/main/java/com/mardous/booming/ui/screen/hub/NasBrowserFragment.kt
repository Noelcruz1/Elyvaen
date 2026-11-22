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

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.mardous.booming.data.local.AudioImportManager
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.remote.nas.NasDownloadProgress
import com.mardous.booming.data.remote.nas.NasDownloadResult
import com.mardous.booming.data.remote.nas.NasEntry
import com.mardous.booming.data.remote.nas.NasSyncResult
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NasBrowserFragment : Fragment() {

    private val viewModel: NasBrowserViewModel by viewModel()

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
                    NasBrowserScreen(
                        state = state,
                        onBack = {
                            if (!viewModel.navigateUp()) {
                                findNavController().navigateUp()
                            }
                        },
                        onOpenSettings = {
                            findNavController().navigate(R.id.nav_nas_settings)
                        },
                        onRefresh = viewModel::refresh,
                        onOpenEntry = viewModel::open,
                        onDownloadFolder = viewModel::downloadCurrentFolder,
                        onSyncFolder = viewModel::syncCurrentFolder,
                        onBackupToNas = viewModel::backupToNas,
                        onCancelDownload = viewModel::cancelDownload,
                        onNavigateTo = viewModel::load
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
private fun NasBrowserScreen(
    state: NasBrowserUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onOpenEntry: (NasEntry) -> Unit,
    onDownloadFolder: () -> Unit,
    onSyncFolder: () -> Unit,
    onBackupToNas: () -> Unit,
    onCancelDownload: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    BackHandler(onBack = onBack)

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
                                text = state.settings.host.ifBlank {
                                    stringResource(R.string.nas_browser_title)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.nas_browser_subtitle),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (state.configured) {
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_update_24dp),
                                    contentDescription = stringResource(R.string.nas_browser_refresh)
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_24dp),
                                    contentDescription = stringResource(R.string.nas_browser_settings)
                                )
                            }
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            if (!state.configured) {
                NotConfigured(onOpenSettings)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        FolderHeader(
                            state = state,
                            onDownloadFolder = onDownloadFolder,
                            onSyncFolder = onSyncFolder,
                            onBackupToNas = onBackupToNas,
                            onCancelDownload = onCancelDownload
                        )
                    }
                    item {
                        Breadcrumbs(
                            path = state.currentPath,
                            onNavigateTo = onNavigateTo
                        )
                    }
                    state.error?.let { error ->
                        item {
                            Text(
                                text = stringResource(R.string.nas_browser_error, error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    state.result?.let { result ->
                        item { DownloadResult(result) }
                    }
                    state.syncResult?.let { result ->
                        item { SyncResult(result) }
                    }
                    state.backupResult?.let { result ->
                        item { BackupResult(result) }
                    }
                    if (state.loading) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp)
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (state.entries.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.nas_browser_empty_folder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(state.entries, key = { it.path }) { entry ->
                            NasEntryRow(
                                entry = entry,
                                enabled = !state.downloading && !state.backingUp,
                                onClick = { onOpenEntry(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotConfigured(onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Icon(
            painter = painterResource(R.drawable.ic_cloud_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Text(
            text = stringResource(R.string.nas_browser_unconfigured),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.nas_browser_configure))
        }
    }
}

@Composable
private fun FolderHeader(
    state: NasBrowserUiState,
    onDownloadFolder: () -> Unit,
    onSyncFolder: () -> Unit,
    onBackupToNas: () -> Unit,
    onCancelDownload: () -> Unit
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
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.settings.protocol) {
                            NasProtocol.SMB -> "SMB"
                            NasProtocol.WEBDAV -> "WebDAV"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.currentPath.isBlank()) {
                            "/${state.settings.path.trim('/')}"
                        } else {
                            "/${state.currentPath}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(R.string.nas_browser_folder),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.nas_browser_download_folder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.downloading) {
                DownloadProgress(state.progress)
                TextButton(onClick = onCancelDownload) {
                    Text(stringResource(R.string.nas_browser_cancel))
                }
            } else if (state.backingUp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.nas_browser_backing_up),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSyncFolder, modifier = Modifier.weight(1f)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_update_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nas_browser_sync_folder))
                    }
                    OutlinedButton(onClick = onDownloadFolder, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nas_browser_download_all))
                    }
                }
                OutlinedButton(onClick = onBackupToNas, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.nas_browser_backup))
                }
                Text(
                    text = stringResource(R.string.nas_browser_backup_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(progress: NasDownloadProgress?) {
    val total = progress?.totalFiles ?: 0
    val completed = progress?.completedFiles ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (progress?.currentName != null) {
                stringResource(
                    R.string.nas_browser_downloading,
                    completed + 1,
                    total
                ) + " · " + progress.currentName
            } else {
                stringResource(R.string.nas_browser_downloading, completed, total)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { if (total > 0) completed.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = Formatter.formatFileSize(LocalContext.current, progress?.bytes ?: 0L),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadResult(result: NasDownloadResult) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = if (result.totalFiles == 0) {
                    stringResource(R.string.nas_browser_empty)
                } else {
                    stringResource(
                        R.string.nas_browser_result,
                        result.downloadedFiles,
                        result.failedFiles,
                        Formatter.formatFileSize(context, result.bytes)
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (result.skippedFiles > 0) {
                Text(
                    text = stringResource(
                        R.string.nas_browser_skipped,
                        result.skippedFiles
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncResult(result: NasSyncResult) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = if (result.totalFiles == 0) {
                    stringResource(R.string.nas_browser_empty)
                } else {
                    stringResource(
                        R.string.nas_browser_sync_result,
                        result.downloadedFiles,
                        result.unchangedFiles,
                        result.conflictFiles,
                        result.failedFiles,
                        Formatter.formatFileSize(context, result.bytes)
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (result.conflictFiles > 0) {
                Text(
                    text = stringResource(R.string.nas_browser_conflict_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.skippedFiles > 0) {
                Text(
                    text = stringResource(R.string.nas_browser_skipped, result.skippedFiles),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackupResult(result: NasBackupResult) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                R.string.nas_browser_backup_result,
                result.name,
                Formatter.formatFileSize(context, result.bytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun Breadcrumbs(path: String, onNavigateTo: (String) -> Unit) {
    val segments = path.trim('/').split('/').filter { it.isNotBlank() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        TextButton(onClick = { onNavigateTo("") }) {
            Text(stringResource(R.string.nas_browser_root))
        }
        var current = ""
        segments.forEach { segment ->
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            current = if (current.isBlank()) segment else "$current/$segment"
            val target = current
            TextButton(onClick = { onNavigateTo(target) }) {
                Text(
                    text = segment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NasEntryRow(entry: NasEntry, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val isAudio = !entry.isDirectory &&
            AudioImportManager.SUPPORTED_EXTENSIONS.contains(
                entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled && (entry.isDirectory || isAudio), onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            entry.isDirectory -> R.drawable.ic_folder_24dp
                            isAudio -> R.drawable.ic_audio_file_24dp
                            else -> R.drawable.ic_description_24dp
                        }
                    ),
                    contentDescription = null,
                    tint = if (isAudio || entry.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.isDirectory) {
                    val modified = formatDate(entry.modified)
                    Text(
                        text = buildString {
                            append(Formatter.formatFileSize(context, entry.size))
                            if (modified.isNotBlank()) {
                                append(" · ")
                                append(modified)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isAudio) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_24dp),
                    contentDescription = stringResource(R.string.nas_browser_download_file),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else if (!entry.isDirectory) {
                Text(
                    text = stringResource(R.string.nas_browser_not_audio),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
    }.getOrDefault("")
}
