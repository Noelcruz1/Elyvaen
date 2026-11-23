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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the optional Deezer SmartLogin access token used for
 * full streaming. The ARL session in [DeezerAccountStore] remains responsible
 * for favorites and downloads.
 */
class DeezerTokenStore(context: Context) {

    private val preferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private var memoryAccessToken: String? = null
    private var memoryUserId: Long? = null

    val isAvailable: Boolean
        get() = preferences != null

    var accessToken: String?
        get() = preferences?.getString(KEY_ACCESS_TOKEN, null) ?: memoryAccessToken
        set(value) {
            memoryAccessToken = value
            preferences?.edit()?.putString(KEY_ACCESS_TOKEN, value)?.apply()
        }

    var userId: Long?
        get() = preferences?.getString(KEY_USER_ID, null)?.toLongOrNull() ?: memoryUserId
        set(value) {
            memoryUserId = value
            preferences?.edit()?.putString(KEY_USER_ID, value?.toString())?.apply()
        }

    val isActive: Boolean
        get() = !accessToken.isNullOrBlank()

    fun clear() {
        memoryAccessToken = null
        memoryUserId = null
        preferences?.edit()?.clear()?.apply()
    }

    companion object {
        private const val FILE_NAME = "elyvaen_deezer_stream"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
    }
}
