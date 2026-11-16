package com.mardous.booming.ui.screen.dap

import android.app.ActivityManager
import android.os.PowerManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mardous.booming.MainActivity
import com.mardous.booming.R
import com.mardous.booming.core.model.audiodevice.AudioDeviceType
import com.mardous.booming.ui.component.compose.AnimatedEqBars
import com.mardous.booming.ui.component.compose.BottomSheetDialogSurface
import com.mardous.booming.ui.component.compose.ShapedText
import com.mardous.booming.ui.component.compose.TitledCard
import com.mardous.booming.ui.screen.equalizer.EqualizerViewModel
import com.mardous.booming.ui.screen.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

@Composable
fun DapDashboardSheet(
    equalizerViewModel: EqualizerViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = koinActivityViewModel()
) {
    val context = LocalContext.current
    val currentSong by playerViewModel.currentSongFlow.collectAsState()
    val isPlaying by playerViewModel.isPlayingFlow.collectAsState()
    val extraInfo by playerViewModel.extraInfoFlow.collectAsState()
    val outputDevice by equalizerViewModel.audioDevice.collectAsState()
    val bitPerfectState by equalizerViewModel.bitPerfectState.collectAsState()
    val equalizerSettings by equalizerViewModel.equalizerSettings.collectAsState()
    val battery = rememberBatteryInfo()
    val storage = rememberStorageInfo()
    val hasSong = currentSong.id != -1L
    var uac2Enabled by remember { mutableStateOf(Uac2Control.isEnabled(context)) }
    var uac2Status by remember { mutableStateOf(Uac2Control.refresh(context)) }
    var uac2AutoStart by remember { mutableStateOf(Uac2Control.isAutoStart(context)) }
    var uac2Busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            val status = withContext(Dispatchers.IO) { Uac2Control.refresh(context) }
            if (!uac2Busy) {
                uac2Enabled = status.enabled
                uac2Status = status
            }
            delay(2000)
        }
    }
    var pendingPowerAction by remember { mutableStateOf<PowerAction?>(null) }
    var kioskActive by remember {
        mutableStateOf(
            context.getSystemService(ActivityManager::class.java)?.isInLockTaskMode == true
        )
    }

    BottomSheetDialogSurface(
        title = { Text(text = stringResource(R.string.dap_dashboard)) }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            item {
                DapStatusStrip(modifier = Modifier.fillMaxWidth())
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_now_playing),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (hasSong) currentSong.title else stringResource(R.string.dap_no_track),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasSong) {
                                AnimatedEqBars(
                                    color = MaterialTheme.colorScheme.primary,
                                    isPlaying = isPlaying,
                                    barCount = 4,
                                    barWidth = 3.dp,
                                    gap = 3.dp,
                                    modifier = Modifier.size(34.dp, 24.dp)
                                )
                            }
                        }
                        if (hasSong) {
                            Text(
                                text = currentSong.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        extraInfo?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_audio_output),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Text(
                            text = outputDevice.getDeviceName(context),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (bitPerfectState.isActive) {
                            Text(
                                text = "${bitPerfectState.encodingLabel} · ${bitPerfectState.sampleRateLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (outputDevice.type == AudioDeviceType.Headset) {
                                ShapedText(
                                    text = stringResource(R.string.dap_lohifi),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dap_pure_audio_title),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.dap_pure_audio_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = !equalizerSettings.eqState.enabled,
                                onCheckedChange = { pure ->
                                    equalizerViewModel.setEqualizerState(!pure)
                                }
                            )
                        }
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_uac2),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        if (uac2Enabled) R.string.dap_uac2_on
                                        else R.string.dap_uac2_off
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.dap_uac2_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uac2Enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && isPlaying) {
                                        playerViewModel.togglePlayPause()
                                    }
                                    uac2Enabled = enabled
                                    scope.launch {
                                        uac2Busy = true
                                        val changed = withContext(Dispatchers.IO) {
                                            Uac2Control.setEnabled(context, enabled)
                                        }
                                        if (!changed) {
                                            uac2Enabled = !enabled
                                            Toast.makeText(
                                                context,
                                                R.string.dap_uac2_error,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            val status = withContext(Dispatchers.IO) {
                                                Uac2Control.refresh(context)
                                            }
                                            uac2Enabled = status.enabled
                                            uac2Status = status
                                        }
                                        uac2Busy = false
                                    }
                                }
                            )
                        }
                        if (uac2Enabled) {
                            val rateLabel = if (uac2Status.rate > 0) {
                                String.format(
                                    Locale.US,
                                    "%.1f kHz",
                                    uac2Status.rate / 1000.0
                                )
                            } else {
                                ""
                            }
                            val modeLabel = stringResource(
                                if (uac2Status.native) R.string.dap_uac2_native_short
                                else R.string.dap_uac2_compat_short
                            )
                            Text(
                                text = when {
                                    uac2Status.streaming && rateLabel.isNotEmpty() ->
                                        stringResource(
                                            R.string.dap_uac2_linked,
                                            rateLabel,
                                            modeLabel
                                        )
                                    rateLabel.isNotEmpty() ->
                                        stringResource(
                                            R.string.dap_uac2_waiting_rate,
                                            rateLabel
                                        )
                                    else -> stringResource(R.string.dap_uac2_starting)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uac2Status.streaming) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = if (uac2Status.hostMute) {
                                    stringResource(R.string.dap_uac2_host_muted)
                                } else {
                                    stringResource(
                                        R.string.dap_uac2_host,
                                        String.format(
                                            Locale.US,
                                            "%.1f dB",
                                            uac2Status.hostVolume / 256.0
                                        )
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.dap_uac2_quality),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uac2Status.native,
                                    onClick = {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                Uac2Control.setQuality(context, true)
                                            }
                                            if (ok) {
                                                uac2Status = uac2Status.copy(native = true)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    R.string.dap_uac2_quality_error,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    label = {
                                        Text(text = stringResource(R.string.dap_uac2_native))
                                    }
                                )
                                FilterChip(
                                    selected = !uac2Status.native,
                                    onClick = {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                Uac2Control.setQuality(context, false)
                                            }
                                            if (ok) {
                                                uac2Status = uac2Status.copy(native = false)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    R.string.dap_uac2_quality_error,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    label = {
                                        Text(text = stringResource(R.string.dap_uac2_compat))
                                    }
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.dap_uac2_autostart),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.dap_uac2_autostart_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = uac2AutoStart,
                                    onCheckedChange = { checked ->
                                        uac2AutoStart = checked
                                        Uac2Control.setAutoStart(context, checked)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_battery),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${battery.level}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    if (battery.charging) R.string.dap_charging else R.string.dap_discharging
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { battery.level / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.dap_battery_temp,
                                    String.format(Locale.US, "%.1f", battery.temperatureC)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ShapedText(
                                text = stringResource(R.string.dap_battery_care),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_storage),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.dap_storage_free,
                                Formatter.formatFileSize(context, storage.free),
                                Formatter.formatFileSize(context, storage.total)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (storage.total > 0) {
                                    (storage.total - storage.free).toFloat() / storage.total
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_quick_access),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickAccessButton(
                                label = stringResource(R.string.dap_wifi),
                                action = Settings.ACTION_WIFI_SETTINGS,
                                modifier = Modifier.weight(1f)
                            )
                            QuickAccessButton(
                                label = stringResource(R.string.dap_bluetooth),
                                action = Settings.ACTION_BLUETOOTH_SETTINGS,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickAccessButton(
                                label = stringResource(R.string.dap_sound),
                                action = Settings.ACTION_SOUND_SETTINGS,
                                modifier = Modifier.weight(1f)
                            )
                            QuickAccessButton(
                                label = stringResource(R.string.dap_display),
                                action = Settings.ACTION_DISPLAY_SETTINGS,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(
                            onClick = { captureScreenshot() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.dap_screenshot))
                        }
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_power),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.Shutdown },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.dap_power_off))
                        }
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.Reboot },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.dap_reboot))
                        }
                    }
                }
            }

            item {
                TitledCard(
                    title = stringResource(R.string.dap_kiosk),
                    modifier = Modifier.fillMaxWidth()
                ) { contentPadding ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        Text(
                            text = stringResource(
                                if (kioskActive) R.string.dap_kiosk_active else R.string.dap_kiosk_inactive
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (kioskActive) {
                            OutlinedButton(
                                onClick = {
                                    context.findMainActivity()?.exitKioskMode()
                                    kioskActive = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.dap_exit_kiosk))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingPowerAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingPowerAction = null },
            title = {
                Text(
                    text = stringResource(
                        if (action == PowerAction.Shutdown) {
                            R.string.dap_power_off_confirm
                        } else {
                            R.string.dap_reboot_confirm
                        }
                    )
                )
            },
            text = { Text(text = stringResource(R.string.dap_power)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        executePowerAction(context, action)
                        pendingPowerAction = null
                    }
                ) {
                    Text(text = stringResource(R.string.dap_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPowerAction = null }) {
                    Text(text = stringResource(R.string.dap_cancel))
                }
            }
        )
    }
}

@Composable
private fun QuickAccessButton(label: String, action: String, modifier: Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        modifier = modifier
    ) {
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class StorageInfo(val free: Long, val total: Long)

@Composable
private fun rememberStorageInfo(): StorageInfo = remember {
    runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        StorageInfo(stat.availableBytes, stat.totalBytes)
    }.getOrDefault(StorageInfo(0L, 0L))
}

private fun captureScreenshot() {
    Thread {
        val filename = "cypfq_${System.currentTimeMillis()}.png"
        val command = "mkdir -p /sdcard/Pictures/Screenshots && " +
                "screencap -p /sdcard/Pictures/Screenshots/$filename"
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", command)).waitFor()
        }
    }.start()
}

private enum class PowerAction { Shutdown, Reboot }

private fun executePowerAction(context: Context, action: PowerAction) {
    val accepted = runCatching {
        if (action == PowerAction.Reboot) {
            context.getSystemService(PowerManager::class.java)?.reboot(null)
            true
        } else {
            context.startActivity(
                Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
                    .putExtra("android.intent.extra.KEY_CONFIRM", false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
    }.getOrDefault(false)
    if (!accepted) {
        val command = if (action == PowerAction.Shutdown) "reboot -p" else "reboot"
        Thread {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command)).waitFor()
            }
        }.start()
    }
}

private fun Context.findMainActivity(): MainActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is MainActivity) {
            return current
        }
        current = current.baseContext
    }
    return current as? MainActivity
}
