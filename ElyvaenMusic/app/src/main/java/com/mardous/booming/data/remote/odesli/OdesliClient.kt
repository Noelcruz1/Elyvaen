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

package com.mardous.booming.data.remote.odesli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale

@Serializable
data class OdesliEntity(
    val id: String? = null,
    val type: String? = null,
    val title: String? = null,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val apiProvider: String? = null
)

@Serializable
data class OdesliLink(
    val url: String? = null,
    val entityUniqueId: String? = null
)

@Serializable
data class OdesliResponse(
    val entityUniqueId: String? = null,
    val entitiesByUniqueId: Map<String, OdesliEntity> = emptyMap(),
    val linksByPlatform: Map<String, OdesliLink> = emptyMap()
)

data class ResolvedLink(
    val title: String?,
    val artist: String?,
    val artworkUrl: String?,
    val provider: String?,
    val deezerUrl: String?,
    val links: Map<String, String>
) {
    val searchQuery: String
        get() = listOfNotNull(title, artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

/**
 * Small client for song.link / Odesli, the service behind Redomi.
 *
 * It only resolves a public music URL to the equivalent links on other
 * platforms. No account, API key or audio download is involved.
 */
class OdesliClient(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(url: String): ResolvedLink = withContext(Dispatchers.IO) {
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        val endpoint = buildString {
            append(API_URL)
            append("?url=").append(URLEncoder.encode(url, Charsets.UTF_8.name()))
            append("&key=").append(URLEncoder.encode(API_KEY, Charsets.UTF_8.name()))
            append("&userCountry=").append(country)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = client.newCall(request).execute().use { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "song.link HTTP ${response.code}${content.take(160).let { if (it.isBlank()) "" else ": $it" }}"
                )
            }
            content
        }
        val answer = json.decodeFromString<OdesliResponse>(body)
        val entity = answer.entitiesByUniqueId[answer.entityUniqueId]
            ?: answer.entitiesByUniqueId.values.firstOrNull { !it.title.isNullOrBlank() }
        val artwork = entity?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: answer.entitiesByUniqueId.values
                .firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                ?.thumbnailUrl
        val links = answer.linksByPlatform
            .mapNotNull { (platform, link) -> link.url?.let { platform to it } }
            .toMap()
        ResolvedLink(
            title = entity?.title?.trim()?.takeIf { it.isNotEmpty() },
            artist = entity?.artistName?.trim()?.takeIf { it.isNotEmpty() },
            artworkUrl = artwork?.replaceFirst("http://", "https://"),
            provider = entity?.apiProvider?.let(::prettyProvider),
            deezerUrl = links["deezer"],
            links = links
        )
    }

    private fun prettyProvider(value: String): String = value
        .replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    companion object {
        private const val API_URL = "https://api.song.link/v1-alpha.1/links"
        private const val USER_AGENT = "Elyvaen/1.4.2 (Android; song.link client)"

        /**
         * Public song.link client key used by Redomi (GPL-3.0). The upstream
         * project describes it as a non-secret client identifier, not a user
         * credential. If song.link rotates it, the resolver shows a clear
         * error and only this constant needs updating.
         */
        private const val API_KEY = "71d7be8a-3a76-459b-b21e-8f0350374984"
    }
}
