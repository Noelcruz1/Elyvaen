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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DapAssistantAction {
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    OPEN_PLAYER,
    TOGGLE_KIOSK,
    NONE
}

data class DapBatteryInfo(
    val level: Int = 0,
    val temperatureC: Float = 0f,
    val voltageMv: Int = 0,
    val currentMa: Int = 0,
    val healthLabel: String = "—",
    val statusLabel: String = "—",
    val plugged: Boolean = false,
    val chargeLimit: String = "—"
)

data class DapThermalInfo(
    val statusLabel: String = "—",
    val maxZoneC: Float = 0f,
    val governor: String = "—"
)

data class DapModeUiState(
    val battery: DapBatteryInfo = DapBatteryInfo(),
    val thermal: DapThermalInfo = DapThermalInfo(),
    val kioskActive: Boolean = false,
    val refreshRate: Int = 90,
    val keepScreenOn: Boolean = false,
    val assistantAction: DapAssistantAction = DapAssistantAction.PLAY_PAUSE
)

class DapModeViewModel(private val context: Context) : ViewModel() {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val _state = MutableStateFlow(
        DapModeUiState(
            refreshRate = preferences.getInt(KEY_REFRESH_RATE, 90),
            keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, false),
            assistantAction = runCatching {
                DapAssistantAction.valueOf(
                    preferences.getString(KEY_ASSISTANT_ACTION, DapAssistantAction.PLAY_PAUSE.name)
                        ?: DapAssistantAction.PLAY_PAUSE.name
                )
            }.getOrDefault(DapAssistantAction.PLAY_PAUSE)
        )
    )
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val battery = withContext(Dispatchers.IO) { readBattery() }
            val thermal = withContext(Dispatchers.IO) { readThermal() }
            _state.update {
                it.copy(
                    battery = battery,
                    thermal = thermal,
                    kioskActive = context.getSystemService(ActivityManager::class.java)
                        ?.isInLockTaskMode == true,
                    refreshRate = preferences.getInt(KEY_REFRESH_RATE, 90),
                    keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, false),
                    assistantAction = runCatching {
                        DapAssistantAction.valueOf(
                            preferences.getString(
                                KEY_ASSISTANT_ACTION,
                                DapAssistantAction.PLAY_PAUSE.name
                            ) ?: DapAssistantAction.PLAY_PAUSE.name
                        )
                    }.getOrDefault(DapAssistantAction.PLAY_PAUSE)
                )
            }
        }
    }

    fun setAssistantAction(action: DapAssistantAction) {
        preferences.edit { putString(KEY_ASSISTANT_ACTION, action.name) }
        _state.update { it.copy(assistantAction = action) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
        _state.update { it.copy(keepScreenOn = enabled) }
    }

    fun setRefreshRate(rate: Int) {
        preferences.edit { putInt(KEY_REFRESH_RATE, rate) }
        _state.update { it.copy(refreshRate = rate) }
    }

    private fun readBattery(): DapBatteryInfo {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = when {
            rawLevel >= 0 && scale > 0 -> rawLevel * 100 / scale
            else -> readSysfs("capacity")?.toIntOrNull() ?: 0
        }
        val rawTemperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperature = when {
            rawTemperature > 0 -> rawTemperature / 10f
            else -> (readSysfs("temp")?.toIntOrNull() ?: 0) / 10f
        }
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltage = when {
            rawVoltage > 0 -> rawVoltage
            else -> (readSysfs("voltage_now")?.toLongOrNull() ?: 0L).div(1000).toInt()
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val manager = context.getSystemService(BatteryManager::class.java)
        val current = runCatching {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()?.takeIf { it != 0 }
            ?: (readSysfs("current_now")?.toIntOrNull() ?: 0)
        return DapBatteryInfo(
            level = level,
            temperatureC = temperature,
            voltageMv = voltage,
            currentMa = current / 1000,
            healthLabel = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
                BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Agotada"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensión"
                else -> "Desconocida"
            },
            statusLabel = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
                BatteryManager.BATTERY_STATUS_FULL -> "Completa"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Sin carga"
                else -> "Desconocido"
            },
            plugged = plugged,
            chargeLimit = readSysfs("charge_control_limit")?.let {
                if (it == "0") "Carga completa" else "Límite 80% activo"
            } ?: "Controlado por Magisk"
        )
    }

    private fun readThermal(): DapThermalInfo {
        val power = context.getSystemService(PowerManager::class.java)
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (power?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Ligera"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderada"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severa"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Crítica"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado"
                else -> "Desconocido"
            }
        } else {
            "—"
        }
        val maxZone = runCatching {
            File("/sys/class/thermal").listFiles()
                ?.asSequence()
                ?.filter { it.name.startsWith("thermal_zone") }
                ?.mapNotNull { zone -> readFile("${zone.path}/temp")?.toFloatOrNull() }
                ?.filter { it > 0f }
                ?.maxOrNull()
                ?.div(1000f)
        }.getOrNull() ?: 0f
        val governor = readFile(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        ) ?: "—"
        return DapThermalInfo(status, maxZone, governor)
    }

    private fun readSysfs(name: String): String? = readFile(
        "/sys/class/power_supply/battery/$name"
    )

    private fun readFile(path: String): String? = runCatching {
        File(path).readText().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    companion object {
        const val KEY_REFRESH_RATE = "dap_refresh_rate"
        const val KEY_KEEP_SCREEN_ON = "dap_keep_screen_on"
        const val KEY_ASSISTANT_ACTION = "dap_assistant_action"
    }
}
