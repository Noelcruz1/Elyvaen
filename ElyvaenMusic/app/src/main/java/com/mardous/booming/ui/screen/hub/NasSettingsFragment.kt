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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.mardous.booming.R
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.remote.nas.NasTestResult
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class NasSettingsFragment : Fragment() {

    private val viewModel: NasSettingsViewModel by viewModel()

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
                    NasSettingsScreen(
                        state = state,
                        onBack = { findNavController().navigateUp() },
                        onProtocol = viewModel::setProtocol,
                        onHost = viewModel::setHost,
                        onPort = viewModel::setPort,
                        onShare = viewModel::setShare,
                        onPath = viewModel::setPath,
                        onUsername = viewModel::setUsername,
                        onPassword = viewModel::setPassword,
                        onDomain = viewModel::setDomain,
                        onUseTls = viewModel::setUseTls,
                        onSave = viewModel::save,
                        onTest = viewModel::test,
                        onOpenTailscale = viewModel::openTailscale
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTailscale()
    }
}

@Composable
private fun NasSettingsScreen(
    state: NasSettingsUiState,
    onBack: () -> Unit,
    onProtocol: (NasProtocol) -> Unit,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onShare: (String) -> Unit,
    onPath: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onDomain: (String) -> Unit,
    onUseTls: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onOpenTailscale: () -> Unit
) {
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
                                text = "ELYVAEN HUB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.nas_title),
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    TailscaleCard(
                        installed = state.tailscaleInstalled,
                        vpnActive = state.vpnActive,
                        onOpen = onOpenTailscale
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.nas_protocol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.protocol == NasProtocol.SMB,
                            onClick = { onProtocol(NasProtocol.SMB) },
                            label = { Text(stringResource(R.string.nas_smb)) }
                        )
                        FilterChip(
                            selected = state.protocol == NasProtocol.WEBDAV,
                            onClick = { onProtocol(NasProtocol.WEBDAV) },
                            label = { Text(stringResource(R.string.nas_webdav)) }
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.host,
                        onValueChange = onHost,
                        label = { Text(stringResource(R.string.nas_host)) },
                        placeholder = { Text("192.168.1.10 o nas.tailnet.ts.net") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.port,
                            onValueChange = onPort,
                            label = { Text(stringResource(R.string.nas_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        if (state.protocol == NasProtocol.WEBDAV) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.nas_use_tls),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Switch(checked = state.useTls, onCheckedChange = onUseTls)
                            }
                        }
                    }
                }
                if (state.protocol == NasProtocol.SMB) {
                    item {
                        OutlinedTextField(
                            value = state.share,
                            onValueChange = onShare,
                            label = { Text(stringResource(R.string.nas_share)) },
                            placeholder = { Text("Music") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = state.domain,
                            onValueChange = onDomain,
                            label = { Text(stringResource(R.string.nas_domain)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.path,
                        onValueChange = onPath,
                        label = { Text(stringResource(R.string.nas_path)) },
                        placeholder = { Text("Music o /music") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUsername,
                        label = { Text(stringResource(R.string.nas_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPassword,
                        label = { Text(stringResource(R.string.nas_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.nas_credentials_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.storageAvailable) {
                    item {
                        Text(
                            text = "El almacenamiento cifrado no está disponible en este dispositivo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                state.validationError?.let { error ->
                    item {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSave,
                            enabled = !state.testing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.nas_save))
                        }
                        OutlinedButton(
                            onClick = onTest,
                            enabled = !state.testing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.testing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(text = stringResource(R.string.nas_test))
                            }
                        }
                    }
                }
                if (state.saved) {
                    item {
                        Text(
                            text = stringResource(R.string.nas_saved),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                when (val result = state.testResult) {
                    is NasTestResult.Success -> item {
                        Text(
                            text = stringResource(R.string.nas_test_success),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is NasTestResult.Failure -> item {
                        Text(
                            text = stringResource(R.string.nas_test_failure, result.message),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun TailscaleCard(
    installed: Boolean,
    vpnActive: Boolean,
    onOpen: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nas_tailscale_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = when {
                    !installed -> stringResource(R.string.nas_tailscale_not_installed)
                    vpnActive -> stringResource(R.string.nas_tailscale_vpn_active)
                    else -> stringResource(R.string.nas_tailscale_vpn_inactive)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.nas_tailscale_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        if (installed) R.string.nas_tailscale_open
                        else R.string.nas_tailscale_install
                    )
                )
            }
        }
    }
}
