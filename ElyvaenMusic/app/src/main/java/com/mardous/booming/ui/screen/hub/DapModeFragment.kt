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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mardous.booming.MainActivity
import com.mardous.booming.R
import com.mardous.booming.ui.theme.BoomingMusicTheme
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale
import kotlin.math.roundToInt

class DapModeFragment : Fragment() {

    private val viewModel: DapModeViewModel by viewModel()

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
                    DapModeScreen(
                        state = state,
                        onBack = { findNavController().navigateUp() },
                        onRefresh = viewModel::refresh,
                        onKiosk = { enabled ->
                            val activity = activity as? MainActivity
                            if (enabled) activity?.enterKioskMode() else activity?.exitKioskMode()
                            viewModel.refresh()
                        },
                        onKeepScreenOn = { enabled ->
                            viewModel.setKeepScreenOn(enabled)
                            (activity as? MainActivity)?.setDapKeepScreenOn(enabled)
                        },
                        onRefreshRate = { rate ->
                            viewModel.setRefreshRate(rate)
                            (activity as? MainActivity)?.setDapRefreshRate(rate)
                        },
                        onAssistantAction = viewModel::setAssistantAction
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
private fun DapModeScreen(
    state: DapModeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onKiosk: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onRefreshRate: (Int) -> Unit,
    onAssistantAction: (DapAssistantAction) -> Unit
) {
    val context = LocalContext.current
    val display = remember {
        @Suppress("DEPRECATION")
        context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
    }
    val supportedRates = remember(display) {
        @Suppress("DEPRECATION")
        display?.supportedModes
            ?.map { it.refreshRate.roundToInt() }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(60, 90)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(3_000)
                onRefresh()
            }
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
                                text = "ELYVAEN HUB · H6",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.dap_mode_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                painter = painterResource(R.drawable.ic_update_24dp),
                                contentDescription = stringResource(R.string.dap_mode_refresh)
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
                item { BatteryCard(state.battery) }
                item { ThermalCard(state.thermal) }
                item {
                    DedicatedCard(
                        state = state,
                        supportedRates = supportedRates,
                        onKiosk = onKiosk,
                        onKeepScreenOn = onKeepScreenOn,
                        onRefreshRate = onRefreshRate
                    )
                }
                item {
                    ButtonCard(
                        action = state.assistantAction,
                        onAction = onAssistantAction
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(battery: DapBatteryInfo) {
    ModeCard(title = stringResource(R.string.dap_mode_battery)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${battery.level}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = battery.statusLabel + if (battery.plugged) " · conectado" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", battery.temperatureC)} °C · " +
                            "${battery.currentMa} mA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { battery.level / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        ModeStat("Salud", battery.healthLabel)
        ModeStat("Voltaje", "${battery.voltageMv} mV")
        ModeStat("Límite de carga", battery.chargeLimit)
    }
}

@Composable
private fun ThermalCard(thermal: DapThermalInfo) {
    ModeCard(title = stringResource(R.string.dap_mode_thermal)) {
        ModeStat("Estado térmico", thermal.statusLabel)
        ModeStat(
            "Zona más caliente",
            if (thermal.maxZoneC > 0f) {
                String.format(Locale.US, "%.1f °C", thermal.maxZoneC)
            } else "—"
        )
        ModeStat("Governor CPU", thermal.governor)
        Text(
            text = "Los perfiles de batería y térmica se ajustan con el módulo Magisk del DAP.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DedicatedCard(
    state: DapModeUiState,
    supportedRates: List<Int>,
    onKiosk: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onRefreshRate: (Int) -> Unit
) {
    ModeCard(title = stringResource(R.string.dap_mode_dedicated)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dap_mode_kiosk),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (state.kioskActive) "Activo · app fijada" else "Inactivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { onKiosk(!state.kioskActive) }) {
                Text(
                    stringResource(
                        if (state.kioskActive) R.string.dap_mode_exit_kiosk
                        else R.string.dap_mode_enter_kiosk
                    )
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dap_mode_keep_screen),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.dap_mode_keep_screen_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.keepScreenOn,
                onCheckedChange = onKeepScreenOn
            )
        }
        Text(
            text = stringResource(R.string.dap_mode_refresh_rate),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            supportedRates.distinct().forEach { rate ->
                FilterChip(
                    selected = state.refreshRate == rate,
                    onClick = { onRefreshRate(rate) },
                    label = { Text("$rate Hz") }
                )
            }
        }
        Text(
            text = stringResource(R.string.dap_mode_refresh_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ButtonCard(
    action: DapAssistantAction,
    onAction: (DapAssistantAction) -> Unit
) {
    ModeCard(title = stringResource(R.string.dap_mode_buttons)) {
        Text(
            text = stringResource(R.string.dap_mode_assistant_action),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DapAssistantAction.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { candidate ->
                        FilterChip(
                            selected = action == candidate,
                            onClick = { onAction(candidate) },
                            label = { Text(assistantLabel(candidate)) }
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.dap_mode_button_gestures),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun assistantLabel(action: DapAssistantAction): String = when (action) {
    DapAssistantAction.PLAY_PAUSE -> "Play/Pausa"
    DapAssistantAction.NEXT -> "Siguiente"
    DapAssistantAction.PREVIOUS -> "Anterior"
    DapAssistantAction.OPEN_PLAYER -> "Abrir player"
    DapAssistantAction.TOGGLE_KIOSK -> "Kiosco"
    DapAssistantAction.NONE -> "Nada"
}

@Composable
private fun ModeStat(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
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
private fun ModeCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}
