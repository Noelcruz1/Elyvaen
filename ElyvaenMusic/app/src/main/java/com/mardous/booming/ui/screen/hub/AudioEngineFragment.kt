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

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.mardous.booming.R
import com.mardous.booming.core.model.dsp.DspBand
import com.mardous.booming.core.model.dsp.DspBandType
import com.mardous.booming.ui.theme.BoomingMusicTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class AudioEngineFragment : Fragment() {

    private val viewModel: AudioEngineViewModel by viewModel()

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
                    AudioEngineScreen(
                        state = state,
                        onBack = { findNavController().navigateUp() },
                        onNative = viewModel::setNative,
                        onDspEnabled = viewModel::setDspEnabled,
                        onBandChange = viewModel::updateBand,
                        onCrossfeed = viewModel::setCrossfeed,
                        onConvolution = viewModel::setConvolution,
                        onImportImpulse = viewModel::importImpulse,
                        onClearImpulse = viewModel::clearImpulse,
                        onResetEq = viewModel::resetEqualizer,
                        onRefreshUac2 = viewModel::refreshUac2
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUac2()
    }
}

@Composable
private fun AudioEngineScreen(
    state: AudioEngineUiState,
    onBack: () -> Unit,
    onNative: (Boolean) -> Unit,
    onDspEnabled: (Boolean) -> Unit,
    onBandChange: (Int, (DspBand) -> DspBand) -> Unit,
    onCrossfeed: (Boolean, Float) -> Unit,
    onConvolution: (Boolean) -> Unit,
    onImportImpulse: (Uri) -> Unit,
    onClearImpulse: () -> Unit,
    onResetEq: () -> Unit,
    onRefreshUac2: () -> Unit
) {
    val impulsePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) onImportImpulse(uri)
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
                                text = "ELYVAEN HUB · H4",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.audio_engine_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = onRefreshUac2) {
                            Icon(
                                painter = painterResource(R.drawable.ic_update_24dp),
                                contentDescription = stringResource(R.string.audio_engine_refresh)
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
                item { Uac2Card(state, onNative) }
                item { DspMasterCard(state, onDspEnabled) }
                item { ParametricEqCard(state, onBandChange, onResetEq) }
                item { CrossfeedCard(state, onCrossfeed) }
                item {
                    ConvolutionCard(
                        state = state,
                        onEnabled = onConvolution,
                        onPick = { impulsePicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                        onClear = onClearImpulse
                    )
                }
                item { DopCard() }
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
private fun Uac2Card(state: AudioEngineUiState, onNative: (Boolean) -> Unit) {
    val uac2 = state.uac2
    EngineCard(title = stringResource(R.string.audio_engine_uac2)) {
        Text(
            text = when {
                !uac2.enabled -> stringResource(R.string.audio_engine_uac2_off)
                uac2.streaming && uac2.rate > 0 -> stringResource(
                    R.string.audio_engine_uac2_streaming,
                    uac2.rate / 1000.0,
                    if (uac2.modeNative) "nativo" else "compatibilidad"
                )
                uac2.rate > 0 -> stringResource(
                    R.string.audio_engine_uac2_rate,
                    uac2.rate / 1000.0
                )
                else -> stringResource(R.string.audio_engine_uac2_waiting)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audio_engine_native),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.audio_engine_native_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = uac2.modeNative,
                onCheckedChange = onNative,
                enabled = uac2.enabled
            )
        }
        Text(
            text = stringResource(R.string.audio_engine_exclusive),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DspMasterCard(state: AudioEngineUiState, onEnabled: (Boolean) -> Unit) {
    EngineCard(title = stringResource(R.string.audio_engine_dsp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audio_engine_dsp_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.enabled,
                onCheckedChange = onEnabled
            )
        }
    }
}

@Composable
private fun ParametricEqCard(
    state: AudioEngineUiState,
    onBandChange: (Int, (DspBand) -> DspBand) -> Unit,
    onReset: () -> Unit
) {
    EngineCard(title = stringResource(R.string.audio_engine_eq)) {
        Text(
            text = stringResource(R.string.audio_engine_eq_summary),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.settings.bands.forEachIndexed { index, band ->
            BandEditor(
                index = index,
                band = band,
                enabled = state.settings.enabled,
                onChange = { transform -> onBandChange(index, transform) }
            )
        }
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.audio_engine_reset_eq))
        }
    }
}

@Composable
private fun BandEditor(
    index: Int,
    band: DspBand,
    enabled: Boolean,
    onChange: ((DspBand) -> DspBand) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.audio_engine_band, index + 1) + " · " +
                        when (band.type) {
                            DspBandType.LOW_SHELF -> "Low shelf"
                            DspBandType.PEAK -> "Peak"
                            DspBandType.HIGH_SHELF -> "High shelf"
                        },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatHz(band.frequencyHz),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = frequencyToSlider(band.frequencyHz),
            onValueChange = { value ->
                onChange { it.copy(frequencyHz = sliderToFrequency(value)) }
            },
            enabled = enabled,
            valueRange = 0f..1f
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Gain ${formatDb(band.gainDb)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(100.dp)
            )
            Slider(
                value = band.gainDb,
                onValueChange = { value -> onChange { it.copy(gainDb = value) } },
                enabled = enabled,
                valueRange = -12f..12f,
                modifier = Modifier.weight(1f)
            )
        }
        if (band.type == DspBandType.PEAK) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Q ${String.format(Locale.US, "%.2f", band.q)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(100.dp)
                )
                Slider(
                    value = band.q,
                    onValueChange = { value -> onChange { it.copy(q = value) } },
                    enabled = enabled,
                    valueRange = 0.3f..6f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CrossfeedCard(state: AudioEngineUiState, onCrossfeed: (Boolean, Float) -> Unit) {
    EngineCard(title = stringResource(R.string.audio_engine_crossfeed)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audio_engine_crossfeed_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.crossfeedEnabled,
                onCheckedChange = { enabled ->
                    onCrossfeed(enabled, state.settings.crossfeedAmount)
                }
            )
        }
        Text(
            text = stringResource(
                R.string.audio_engine_crossfeed_amount,
                (state.settings.crossfeedAmount * 100).toInt()
            ),
            style = MaterialTheme.typography.labelSmall
        )
        Slider(
            value = state.settings.crossfeedAmount,
            onValueChange = { amount ->
                onCrossfeed(state.settings.crossfeedEnabled, amount)
            },
            valueRange = 0f..1f,
            enabled = state.settings.crossfeedEnabled
        )
    }
}

@Composable
private fun ConvolutionCard(
    state: AudioEngineUiState,
    onEnabled: (Boolean) -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    EngineCard(title = stringResource(R.string.audio_engine_convolution)) {
        Text(
            text = stringResource(R.string.audio_engine_convolution_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.settings.impulseName
                    ?: stringResource(R.string.audio_engine_no_impulse),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.settings.impulseName != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.settings.convolutionEnabled,
                onCheckedChange = onEnabled,
                enabled = state.settings.impulseName != null
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPick,
                enabled = !state.importing,
                modifier = Modifier.weight(1f)
            ) {
                if (state.importing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(stringResource(R.string.audio_engine_choose_impulse))
                }
            }
            if (state.settings.impulseName != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.audio_engine_clear_impulse))
                }
            }
        }
        Text(
            text = stringResource(R.string.audio_engine_convolution_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DopCard() {
    EngineCard(title = stringResource(R.string.audio_engine_dop)) {
        Text(
            text = stringResource(R.string.audio_engine_dop_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EngineCard(title: String, content: @Composable () -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

private fun frequencyToSlider(frequency: Float): Float {
    val clamped = frequency.coerceIn(20f, 20000f)
    return (log10((clamped / 20f).toDouble()) / log10(1000.0)).toFloat().coerceIn(0f, 1f)
}

private fun sliderToFrequency(value: Float): Float {
    return (20.0 * 10.0.pow(value.coerceIn(0f, 1f) * 3.0)).toFloat()
        .coerceIn(20f, 20000f)
}

private fun formatHz(frequency: Float): String {
    return if (frequency >= 1000f) {
        String.format(Locale.US, "%.1f kHz", frequency / 1000f)
    } else {
        String.format(Locale.US, "%.0f Hz", frequency)
    }
}

private fun formatDb(gain: Float): String = String.format(Locale.US, "%+.1f dB", gain)
