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

package com.mardous.booming.data.remote.discord

import android.util.Log
import com.mardous.booming.data.model.Song
import com.mardous.booming.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * Sends a single “now playing” embed to a Discord channel webhook.
 *
 * This is deliberately not Rich Presence: Discord on Android does not expose
 * third-party RPC. A webhook is the portable fallback and can be disabled or
 * left without a URL.
 */
class DiscordWebhookService(private val client: OkHttpClient) {

    private val lastSongId = AtomicLong(-1L)
    private val lastSentAt = AtomicLong(0L)

    suspend fun sendNowPlaying(song: Song) = withContext(Dispatchers.IO) {
        if (!Preferences.discordWebhookEnabled) return@withContext
        val url = Preferences.discordWebhookUrl.trim()
        if (!isWebhookUrl(url)) return@withContext

        val now = System.currentTimeMillis()
        if (song.id == lastSongId.get() && now - lastSentAt.get() < REPEAT_GUARD_MS) {
            return@withContext
        }

        val description = buildString {
            append("**").append(song.artistName.ifBlank { "Artista desconocido" }).append("**")
            val album = song.albumName.takeIf { it.isNotBlank() }
            if (album != null) append("\n").append(album)
            if (song.year > 0) append(" · ").append(song.year)
        }
        val payload = buildString {
            append("{\"username\":\"Elyvaen DAP\",\"embeds\":[{")
            append("\"title\":").append(JsonPrimitive(song.title.ifBlank { "Reproduciendo" }))
            append(",\"description\":").append(JsonPrimitive(description))
            append(",\"color\":2999487")
            append(",\"footer\":{\"text\":\"Elyvaen DAP\"}")
            append("}]}")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Elyvaen/1.4.2")
            .post(payload.toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Discord webhook HTTP ${response.code}")
                }
            }
        }.onFailure {
            Log.w("DiscordWebhook", "Could not send the now-playing embed")
        }
        lastSongId.set(song.id)
        lastSentAt.set(now)
    }

    private fun isWebhookUrl(url: String): Boolean {
        return url.startsWith("https://discord.com/api/webhooks/") ||
                url.startsWith("https://discordapp.com/api/webhooks/") ||
                url.startsWith("https://ptb.discord.com/api/webhooks/") ||
                url.startsWith("https://canary.discord.com/api/webhooks/")
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val REPEAT_GUARD_MS = 60_000L
    }
}
