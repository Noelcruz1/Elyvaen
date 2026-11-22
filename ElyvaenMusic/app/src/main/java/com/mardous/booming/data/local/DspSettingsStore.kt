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

package com.mardous.booming.data.local

import android.content.Context
import com.mardous.booming.core.model.dsp.DspSettings
import kotlinx.serialization.json.Json

class DspSettingsStore(context: Context) {

    private val preferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): DspSettings {
        val raw = preferences.getString(KEY_SETTINGS, null) ?: return DspSettings()
        return runCatching {
            json.decodeFromString<DspSettings>(raw).normalized()
        }.getOrDefault(DspSettings())
    }

    fun save(settings: DspSettings) {
        preferences.edit()
            .putString(KEY_SETTINGS, json.encodeToString(settings.normalized()))
            .apply()
    }

    companion object {
        private const val FILE_NAME = "elyvaen_dsp"
        private const val KEY_SETTINGS = "settings"
    }
}
