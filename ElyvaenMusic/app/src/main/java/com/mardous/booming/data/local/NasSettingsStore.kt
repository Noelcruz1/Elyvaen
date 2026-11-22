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
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class NasProtocol {
    SMB,
    WEBDAV
}

data class NasSettings(
    val protocol: NasProtocol = NasProtocol.WEBDAV,
    val host: String = "",
    val port: Int = 0,
    val share: String = "",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val useTls: Boolean = true
)

/**
 * NAS settings including the password are kept in
 * EncryptedSharedPreferences backed by the Android Keystore. The public
 * Elyvaen build contains no credentials or default server.
 */
class NasSettingsStore(context: Context) {

    private val preferences: SharedPreferences? = runCatching {
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

    val isAvailable: Boolean
        get() = preferences != null

    fun load(): NasSettings {
        val prefs = preferences ?: return NasSettings()
        return NasSettings(
            protocol = runCatching {
                NasProtocol.valueOf(prefs.getString(KEY_PROTOCOL, null) ?: NasProtocol.WEBDAV.name)
            }.getOrDefault(NasProtocol.WEBDAV),
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_PORT, 0),
            share = prefs.getString(KEY_SHARE, "").orEmpty(),
            path = prefs.getString(KEY_PATH, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            domain = prefs.getString(KEY_DOMAIN, "").orEmpty(),
            useTls = prefs.getBoolean(KEY_USE_TLS, true)
        )
    }

    fun save(settings: NasSettings) {
        preferences?.edit()
            ?.putString(KEY_PROTOCOL, settings.protocol.name)
            ?.putString(KEY_HOST, settings.host.trim())
            ?.putInt(KEY_PORT, settings.port)
            ?.putString(KEY_SHARE, settings.share.trim())
            ?.putString(KEY_PATH, settings.path.trim())
            ?.putString(KEY_USERNAME, settings.username)
            ?.putString(KEY_PASSWORD, settings.password)
            ?.putString(KEY_DOMAIN, settings.domain.trim())
            ?.putBoolean(KEY_USE_TLS, settings.useTls)
            ?.apply()
    }

    fun clear() {
        preferences?.edit()?.clear()?.apply()
    }

    companion object {
        private const val FILE_NAME = "elyvaen_nas"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_SHARE = "share"
        private const val KEY_PATH = "path"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_USE_TLS = "use_tls"
    }
}
