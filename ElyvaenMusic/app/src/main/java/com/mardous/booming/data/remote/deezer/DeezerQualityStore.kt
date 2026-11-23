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

package com.mardous.booming.data.remote.deezer

import android.content.Context

enum class DeezerAudioQuality(val storageValue: String) {
    FLAC("flac"),
    MP3_320("mp3_320"),
    MP3_128("mp3_128");

    companion object {
        fun fromStorage(value: String?): DeezerAudioQuality {
            return entries.firstOrNull { it.storageValue == value } ?: FLAC
        }
    }
}

/**
 * Plain preference for the preferred Deezer stream quality. The access token
 * lives separately in [DeezerTokenStore]; this value is not sensitive.
 */
class DeezerQualityStore(context: Context) {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var quality: DeezerAudioQuality
        get() = DeezerAudioQuality.fromStorage(preferences.getString(KEY_QUALITY, null))
        set(value) {
            preferences.edit().putString(KEY_QUALITY, value.storageValue).apply()
        }

    companion object {
        private const val FILE_NAME = "elyvaen_deezer_stream_quality"
        private const val KEY_QUALITY = "quality"
    }
}
