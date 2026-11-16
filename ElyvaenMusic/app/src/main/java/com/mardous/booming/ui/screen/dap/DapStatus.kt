package com.mardous.booming.ui.screen.dap

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mardous.booming.R
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DapStatusStrip(modifier: Modifier = Modifier) {
    val time = rememberClock()
    val wifiConnected = rememberWifiConnected()
    val bluetoothEnabled = rememberBluetoothEnabled()
    val battery = rememberBatteryInfo()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusIcon(
                iconRes = R.drawable.ic_network_wifi_24dp,
                active = wifiConnected,
                contentDescription = stringResource(R.string.dap_wifi)
            )
            StatusIcon(
                iconRes = R.drawable.ic_media_bluetooth_on_24dp,
                active = bluetoothEnabled,
                contentDescription = stringResource(R.string.dap_bluetooth)
            )
            Icon(
                painter = painterResource(R.drawable.ic_battery_std_24dp),
                contentDescription = stringResource(R.string.dap_battery),
                tint = if (battery.charging) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${battery.level}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusIcon(
    @DrawableRes iconRes: Int,
    active: Boolean,
    contentDescription: String
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun rememberClock(): String {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(1_000)
        }
    }
    return time
}

private fun currentTime(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun rememberWifiConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(isWifiConnected(context)) }
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected = isWifiConnected(context)
            }

            override fun onLost(network: Network) {
                connected = isWifiConnected(context)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                connected = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }
        runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        }
    }
    return connected
}

private fun isWifiConnected(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

@Composable
private fun rememberBluetoothEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isBluetoothEnabled(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                enabled = when (state) {
                    BluetoothAdapter.STATE_ON -> true
                    BluetoothAdapter.STATE_OFF -> false
                    else -> isBluetoothEnabled(context)
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

private fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(BluetoothManager::class.java)
    val adapterEnabled = runCatching { manager?.adapter?.isEnabled }.getOrNull()
    if (adapterEnabled != null) {
        return adapterEnabled
    }
    return runCatching {
        Settings.Global.getInt(context.contentResolver, "bluetooth_on", 0) == 1
    }.getOrDefault(false)
}

internal data class BatteryInfo(
    val level: Int,
    val charging: Boolean,
    val temperatureC: Float
)

@Composable
internal fun rememberBatteryInfo(): BatteryInfo {
    val context = LocalContext.current
    var batteryInfo by remember {
        mutableStateOf(
            readBatteryInfo(
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            )
        )
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                batteryInfo = readBatteryInfo(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return batteryInfo
}

private fun readBatteryInfo(intent: Intent?): BatteryInfo {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0
    return BatteryInfo(
        level = percentage.coerceIn(0, 100),
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        temperatureC = temperature / 10f
    )
}
