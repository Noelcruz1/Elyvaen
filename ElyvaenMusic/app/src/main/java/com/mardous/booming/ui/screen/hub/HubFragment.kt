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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.mardous.booming.data.local.HubStorageSummary
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.local.StorageVolumeInfo
import com.mardous.booming.data.local.WifiUploadState
import com.mardous.booming.data.remote.tailscale.TailscaleInfo
import com.mardous.booming.data.remote.wifi.WifiUploadEvent
import com.mardous.booming.ui.theme.BoomingMusicTheme
import com.mardous.booming.util.QrCodes
import org.koin.androidx.viewmodel.ext.android.viewModel

class HubFragment : Fragment() {

    private val viewModel: HubViewModel by viewModel()

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
                    val wifi by viewModel.wifi.collectAsStateWithLifecycle()
                    HubScreen(
                        state = state,
                        wifi = wifi,
                        onBack = { findNavController().navigateUp() },
                        onOpenNas = { findNavController().navigate(R.id.nav_nas_settings) },
                        onBrowseNas = { findNavController().navigate(R.id.nav_nas_browser) },
                        onOpenTailscale = viewModel::openTailscale,
                        onMediaServer = { findNavController().navigate(R.id.nav_media_server) },
                        onAudioEngine = { findNavController().navigate(R.id.nav_audio_engine) },
                        onSala = { findNavController().navigate(R.id.nav_sala) },
                        onDapMode = { findNavController().navigate(R.id.nav_dap_mode) },
                        onImportTree = { uri -> importTree(uri) },
                        onCancelImport = viewModel::cancelImport,
                        onStartWifi = viewModel::startWifi,
                        onStopWifi = viewModel::stopWifi,
                        onClearWifiHistory = viewModel::clearWifiHistory,
                        onClearCache = viewModel::clearCache
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun importTree(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.importTree(uri)
    }
}

@Composable
private fun HubScreen(
    state: HubUiState,
    wifi: WifiUploadState,
    onBack: () -> Unit,
    onOpenNas: () -> Unit,
    onBrowseNas: () -> Unit,
    onOpenTailscale: () -> Unit,
    onMediaServer: () -> Unit,
    onAudioEngine: () -> Unit,
    onSala: () -> Unit,
    onDapMode: () -> Unit,
    onImportTree: (Uri) -> Unit,
    onCancelImport: () -> Unit,
    onStartWifi: () -> Unit,
    onStopWifi: () -> Unit,
    onClearWifiHistory: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
        if (uri != null) onImportTree(uri)
    }
    val notificationsPermission = rememberLauncherForActivityResult(RequestPermission()) {
        onStartWifi()
    }
    val requestWifiStart = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStartWifi()
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
                                text = stringResource(R.string.hub_eyebrow),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.hub_title),
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    HubHeroCard(
                        storage = state.storage,
                        loading = state.loading
                    )
                }
                item {
                    UsbImportCard(
                        state = state,
                        onChooseFolder = { folderPicker.launch(null) },
                        onCancel = onCancelImport
                    )
                }
                item {
                    WifiUploadCard(
                        wifi = wifi,
                        onStart = requestWifiStart,
                        onStop = onStopWifi,
                        onClearHistory = onClearWifiHistory
                    )
                }
                item {
                    NasCard(
                        state = state,
                        onOpenSettings = onOpenNas,
                        onBrowse = onBrowseNas,
                        onOpenTailscale = onOpenTailscale
                    )
                }
                item {
                    MediaServerCard(onOpen = onMediaServer)
                    AudioEngineCard(onOpen = onAudioEngine)
                    SalaCard(onOpen = onSala)
                    DapModeCard(onOpen = onDapMode)
                    StorageCard(
                        state = state,
                        onClearCache = onClearCache
                    )
                }
            }
        }
    }
}

@Composable
private fun HubHeroCard(storage: HubStorageSummary, loading: Boolean) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phonelink_ring_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hub_hero_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.hub_hero_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubStat(
                            value = storage.libraryTracks.toString(),
                            label = stringResource(R.string.hub_stat_tracks),
                            modifier = Modifier.weight(1f)
                        )
                        HubStat(
                            value = Formatter.formatFileSize(context, storage.libraryBytes),
                            label = stringResource(R.string.hub_stat_library),
                            modifier = Modifier.weight(1f)
                        )
                        HubStat(
                            value = Formatter.formatFileSize(
                                context,
                                storage.internalVolume?.freeBytes ?: 0L
                            ),
                            label = stringResource(R.string.hub_stat_free),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UsbImportCard(
    state: HubUiState,
    onChooseFolder: () -> Unit,
    onCancel: () -> Unit
) {
    HubCard(
        icon = R.drawable.ic_usb_24dp,
        title = stringResource(R.string.hub_usb_title),
        subtitle = stringResource(R.string.hub_usb_hint)
    ) {
        val volumes = state.storage.removableVolumes
        Text(
            text = if (volumes.isEmpty()) {
                stringResource(R.string.hub_usb_none)
            } else {
                stringResource(
                    R.string.hub_usb_connected,
                    volumes.joinToString(", ") { it.description }
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (volumes.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        val progress = state.progress
        if (state.importing) {
            Spacer(Modifier.height(10.dp))
            val total = progress?.total ?: 0
            val current = progress?.copied ?: 0
            Text(
                text = progress?.currentName?.let { name ->
                    stringResource(R.string.hub_usb_importing, current, total) + " · " + name
                } ?: stringResource(R.string.hub_usb_preparing),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (total > 0) current.toFloat() / total else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.hub_usb_cancel))
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onChooseFolder) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hub_usb_choose))
                }
            }
        }
        state.result?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (result.total == 0) {
                    stringResource(R.string.hub_usb_empty)
                } else {
                    stringResource(
                        R.string.hub_usb_result,
                        result.copied,
                        result.failed
                    )
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (result.failed == 0 && result.total > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        state.importError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hub_usb_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun WifiUploadCard(
    wifi: WifiUploadState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearHistory: () -> Unit
) {
    HubCard(
        icon = R.drawable.ic_network_wifi_24dp,
        title = stringResource(R.string.hub_wifi_title),
        subtitle = stringResource(R.string.hub_wifi_hint)
    ) {
        val qr = remember(wifi.url) {
            wifi.url?.let { QrCodes.create(it) }
        }
        if (wifi.running && wifi.url != null) {
            Text(
                text = stringResource(R.string.hub_wifi_scan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (qr != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = stringResource(R.string.hub_wifi_qr_description),
                        modifier = Modifier
                            .padding(12.dp)
                            .size(210.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = stringResource(R.string.hub_wifi_url),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    text = wifi.url ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onStop) {
                    Text(stringResource(R.string.hub_wifi_stop))
                }
                if (wifi.totalBytes > 0) {
                    Text(
                        text = stringResource(
                            R.string.hub_wifi_received,
                            Formatter.formatFileSize(LocalContext.current, wifi.totalBytes)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (wifi.uploads.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.hub_wifi_history),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(stringResource(R.string.hub_wifi_clear))
                    }
                }
                wifi.uploads.take(8).forEach { event ->
                    WifiEventRow(event)
                }
            }
        } else {
            Text(
                text = stringResource(R.string.hub_wifi_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                enabled = !wifi.starting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (wifi.starting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.hub_wifi_starting))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_network_wifi_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hub_wifi_start))
                }
            }
        }
        wifi.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun WifiEventRow(event: WifiUploadEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(
                if (event.success) R.drawable.ic_check_24dp else R.drawable.ic_close_24dp
            ),
            contentDescription = null,
            tint = if (event.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = event.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (event.success) {
                Formatter.formatFileSize(LocalContext.current, event.bytes)
            } else {
                event.message ?: stringResource(R.string.hub_wifi_failed_short)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (event.success) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NasCard(
    state: HubUiState,
    onOpenSettings: () -> Unit,
    onBrowse: () -> Unit,
    onOpenTailscale: () -> Unit
) {
    HubCard(
        icon = R.drawable.ic_cloud_24dp,
        title = stringResource(R.string.hub_nas_title),
        subtitle = if (state.nas.host.isBlank()) {
            stringResource(R.string.hub_nas_empty)
        } else {
            val protocol = when (state.nas.protocol) {
                NasProtocol.SMB -> "SMB"
                NasProtocol.WEBDAV -> "WebDAV"
                NasProtocol.SFTP -> "SFTP"
            }
            "$protocol · ${state.nas.host}${state.nas.path.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
        }
    ) {
        val tailscale = state.tailscale
        val tailscaleText = tailscaleLabel(tailscale)
        val tailscaleColor = when {
            tailscale.connected -> MaterialTheme.colorScheme.primary
            state.nasRequiresTailscale -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tailscaleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = tailscaleColor
                )
                if (state.nasRequiresTailscale && !tailscale.vpnActive) {
                    Text(
                        text = stringResource(R.string.hub_nas_tailscale_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = onOpenTailscale) {
                Text(
                    stringResource(
                        if (tailscale.installed) R.string.nas_tailscale_open
                        else R.string.nas_tailscale_install
                    )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (state.nas.host.isBlank()) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hub_nas_configure))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBrowse, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.hub_nas_browse))
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nas_browser_settings))
                }
            }
        }
    }
}

@Composable
private fun tailscaleLabel(info: TailscaleInfo): String = when {
    !info.installed -> stringResource(R.string.nas_tailscale_not_installed)
    !info.vpnActive -> stringResource(R.string.nas_tailscale_vpn_inactive)
    info.tailnetAddress != null -> stringResource(
        R.string.nas_tailscale_connected,
        info.tailnetAddress
    )
    else -> stringResource(R.string.nas_tailscale_vpn_active)
}

@Composable
private fun DapModeCard(onOpen: () -> Unit) {
    HubCard(
        icon = R.drawable.ic_phone_android_24dp,
        title = stringResource(R.string.hub_dap_mode_title),
        subtitle = stringResource(R.string.hub_dap_mode_subtitle)
    ) {
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hub_dap_mode_open))
        }
    }
}

@Composable
private fun SalaCard(onOpen: () -> Unit) {
    HubCard(
        icon = R.drawable.ic_album_24dp,
        title = stringResource(R.string.hub_sala_title),
        subtitle = stringResource(R.string.hub_sala_subtitle)
    ) {
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hub_sala_open))
        }
    }
}

@Composable
private fun AudioEngineCard(onOpen: () -> Unit) {
    HubCard(
        icon = R.drawable.ic_headphones_24dp,
        title = stringResource(R.string.hub_audio_engine_title),
        subtitle = stringResource(R.string.hub_audio_engine_subtitle)
    ) {
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hub_audio_engine_open))
        }
    }
}

@Composable
private fun MediaServerCard(onOpen: () -> Unit) {
    HubCard(
        icon = R.drawable.ic_phonelink_ring_24dp,
        title = stringResource(R.string.hub_media_server_title),
        subtitle = stringResource(R.string.hub_media_server_subtitle)
    ) {
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hub_media_server_open))
        }
    }
}

@Composable
private fun StorageCard(state: HubUiState, onClearCache: () -> Unit) {
    val context = LocalContext.current
    HubCard(
        icon = R.drawable.ic_phone_android_24dp,
        title = stringResource(R.string.hub_storage_title),
        subtitle = stringResource(R.string.hub_storage_hint)
    ) {
        state.storage.volumes.filter { it.isMounted }.forEach { volume ->
            StorageVolumeRow(volume)
            Spacer(Modifier.height(10.dp))
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(10.dp))
        StorageStat(
            label = stringResource(R.string.hub_storage_library),
            value = stringResource(
                R.string.hub_storage_library_value,
                state.storage.libraryTracks,
                Formatter.formatFileSize(context, state.storage.libraryBytes)
            )
        )
        StorageStat(
            label = stringResource(R.string.hub_storage_deezer),
            value = Formatter.formatFileSize(context, state.storage.deezerBytes)
        )
        StorageStat(
            label = stringResource(R.string.hub_storage_import),
            value = Formatter.formatFileSize(context, state.storage.importBytes)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hub_storage_cache),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = Formatter.formatFileSize(context, state.storage.cacheBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearCache, enabled = !state.clearingCache) {
                if (state.clearingCache) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(stringResource(R.string.hub_storage_cache_clear))
                }
            }
        }
        state.cacheCleared?.let { freed ->
            Text(
                text = stringResource(
                    R.string.hub_storage_cache_cleared,
                    Formatter.formatFileSize(context, freed)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.hub_storage_manage))
        }
    }
}

@Composable
private fun StorageVolumeRow(volume: StorageVolumeInfo) {
    val context = LocalContext.current
    val used = (volume.totalBytes - volume.freeBytes).coerceAtLeast(0L)
    val fraction = if (volume.totalBytes > 0) {
        used.toFloat() / volume.totalBytes
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = volume.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (volume.isRemovable) {
                Text(
                    text = stringResource(R.string.hub_storage_removable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = stringResource(
                R.string.hub_storage_free,
                Formatter.formatFileSize(context, volume.freeBytes),
                Formatter.formatFileSize(context, volume.totalBytes)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun StorageStat(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HubCard(
    icon: Int,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
