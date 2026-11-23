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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class DeezerSmartLogin(
    val code: String,
    val url: String,
    val pollingIntervalSeconds: Int = 2,
    val ttlSeconds: Int = 900
)

data class DeezerStreamUrls(
    val flac: String? = null,
    val mp3_320: String? = null,
    val mp3_128: String? = null
) {
    val bestUrl: String?
        get() = flac ?: mp3_320 ?: mp3_128

    /**
     * Picks the requested quality, falling back to the next available one.
     * The returned tier is the one actually used, not necessarily the requested one.
     */
    fun select(preferred: DeezerAudioQuality): DeezerStreamSelection? {
        return when (preferred) {
            DeezerAudioQuality.FLAC -> flac?.asSelection(DeezerAudioQuality.FLAC)
                ?: mp3_320?.asSelection(DeezerAudioQuality.MP3_320)
                ?: mp3_128?.asSelection(DeezerAudioQuality.MP3_128)

            DeezerAudioQuality.MP3_320 -> mp3_320?.asSelection(DeezerAudioQuality.MP3_320)
                ?: mp3_128?.asSelection(DeezerAudioQuality.MP3_128)
                ?: flac?.asSelection(DeezerAudioQuality.FLAC)

            DeezerAudioQuality.MP3_128 -> mp3_128?.asSelection(DeezerAudioQuality.MP3_128)
                ?: mp3_320?.asSelection(DeezerAudioQuality.MP3_320)
                ?: flac?.asSelection(DeezerAudioQuality.FLAC)
        }
    }

    private fun String.asSelection(tier: DeezerAudioQuality): DeezerStreamSelection? {
        return takeIf { it.isNotBlank() }?.let { DeezerStreamSelection(tier, it) }
    }
}

data class DeezerStreamSelection(
    val quality: DeezerAudioQuality,
    val url: String
)

class DeezerStreamingUnauthorizedException :
    IllegalStateException("La sesión de streaming de Deezer ha caducado")

class DeezerStreamingException(message: String) : IllegalStateException(message)

/**
 * Optional full-streaming bridge for Deezer.
 *
 * This is the same flow used by Pixer (MIT): SmartLogin returns an access
 * token, and `platform/gcast/.../streamUrls` returns direct playable URLs.
 * The endpoints are unofficial and can change without notice.
 */
class DeezerStreamingClient(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestSmartLogin(): Result<DeezerSmartLogin> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$CONNECT_BASE/smartlogin?app_id=$APP_ID")
                .post("".toRequestBody(FORM_MEDIA_TYPE))
                .let(::browserHeaders)
                .build()
            val (code, body) = execute(request)
            if (code !in 200..299) {
                throw DeezerStreamingException("Deezer SmartLogin HTTP $code")
            }
            val data = json.decodeFromString<SmartLoginResponse>(body).data
                ?: throw DeezerStreamingException("Deezer no devolvió un código de acceso")
            DeezerSmartLogin(
                code = data.smartLoginCode,
                url = data.url,
                pollingIntervalSeconds = data.pollingInterval.coerceAtLeast(2),
                ttlSeconds = data.ttl.coerceAtLeast(60)
            )
        }
    }

    /**
     * Returns null while the user has not authorized the code yet.
     */
    suspend fun pollSmartLogin(code: String): Result<DeezerSmartLoginToken?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$CONNECT_BASE/smartlogin/$code?app_id=$APP_ID")
                    .get()
                    .let(::browserHeaders)
                    .build()
                val (status, body) = execute(request)
                if (status !in 200..299) {
                    throw DeezerStreamingException("Deezer SmartLogin HTTP $status")
                }
                val data = json.decodeFromString<SmartLoginPollResponse>(body).data
                val token = data?.accessToken?.takeIf { it.isNotBlank() }
                val userId = data?.userId
                if (token == null || userId == null) {
                    null
                } else {
                    DeezerSmartLoginToken(accessToken = token, userId = userId)
                }
            }
        }

    suspend fun streamUrls(trackId: Long, accessToken: String): Result<DeezerStreamUrls> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$API_BASE/platform/gcast/track/$trackId/streamUrls")
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .let(::browserHeaders)
                    .build()
                val (status, body) = execute(request)
                if (status == 401) throw DeezerStreamingUnauthorizedException()
                if (status !in 200..299) {
                    throw DeezerStreamingException("Deezer stream HTTP $status")
                }
                val attributes = json.decodeFromString<StreamUrlsResponse>(body)
                    .data?.attributes
                    ?: throw DeezerStreamingException("Deezer no devolvió una URL de audio")
                val urls = DeezerStreamUrls(
                    flac = attributes.urlFlac,
                    mp3_320 = attributes.url320,
                    mp3_128 = attributes.url
                )
                Log.d(
                    TAG,
                    "streamUrls track=$trackId status=$status " +
                            "flac=${urls.flac != null} mp3_320=${urls.mp3_320 != null} " +
                            "mp3_128=${urls.mp3_128 != null}"
                )
                if (urls.bestUrl.isNullOrBlank()) {
                    throw DeezerStreamingException("Deezer no devolvió una URL reproducible")
                }
                urls
            }
        }

    private fun browserHeaders(builder: Request.Builder): Request.Builder {
        return builder
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://www.deezer.com")
            .header("Referer", "https://www.deezer.com/")
    }

    private fun execute(request: Request): Pair<Int, String> {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return response.code to body
        }
    }

    @Serializable
    private data class SmartLoginResponse(
        val data: SmartLoginData? = null
    )

    @Serializable
    private data class SmartLoginData(
        val smartLoginCode: String,
        val url: String,
        val pollingInterval: Int = 2,
        val ttl: Int = 900
    )

    @Serializable
    private data class SmartLoginPollResponse(
        val data: SmartLoginPollData? = null
    )

    @Serializable
    private data class SmartLoginPollData(
        @SerialName("accessToken") val accessToken: String? = null,
        @SerialName("userId") val userId: Long? = null
    )

    @Serializable
    private data class StreamUrlsResponse(
        val data: StreamUrlsData? = null
    )

    @Serializable
    private data class StreamUrlsData(
        val attributes: StreamUrlsAttributes? = null
    )

    @Serializable
    private data class StreamUrlsAttributes(
        val url: String? = null,
        @SerialName("url_320") val url320: String? = null,
        @SerialName("url_flac") val urlFlac: String? = null
    )

    companion object {
        private const val TAG = "ElyvaenStream"

        // Public Deezer application id used by the open-source Pixer client.
        private const val APP_ID = "447462"
        private const val CONNECT_BASE = "https://connect.deezer.com/2.0"
        private const val API_BASE = "https://api.deezer.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; moto g51 5G) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE =
            "application/x-www-form-urlencoded".toMediaType()
    }
}

data class DeezerSmartLoginToken(
    val accessToken: String,
    val userId: Long
)
