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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DeezerNotConnectedException : IllegalStateException("Deezer is not connected")

class DeezerApiException(message: String) : IllegalStateException(message)

data class DeezerAccount(
    val userId: String,
    val userName: String,
    val plan: String
)

data class DeezerStream(
    val url: String,
    val format: String,
    val effectiveId: String,
    val key: ByteArray,
    val durationSeconds: Int,
    val mimeType: String
)

/**
 * Experimental personal Deezer session.
 *
 * It follows the same web-player handshake used by Sonora (GPL-3.0):
 * `deezer.ping` -> `deezer.getUserData` -> `song.getData` -> media `get_url`
 * and BF_CBC_STRIPE decryption. It is not an official API and can break when
 * Deezer changes its web player.
 */
class DeezerPrivateClient(
    baseClient: OkHttpClient,
    private val store: DeezerAccountStore
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cookieJar = DeezerCookieJar()
    private val client = baseClient.newBuilder()
        .cookieJar(cookieJar)
        .build()

    private var apiToken: String = ""
    private var licenseToken: String = ""
    private var account: DeezerAccount? = null

    val isConnected: Boolean
        get() = account != null

    val currentAccount: DeezerAccount?
        get() = account

    suspend fun restore(): DeezerAccount? = withContext(Dispatchers.IO) {
        val arl = store.arl?.takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching { connectInternal(arl, persist = false) }.getOrNull()
    }

    suspend fun connect(arl: String): DeezerAccount = withContext(Dispatchers.IO) {
        connectInternal(arl.trim(), persist = true)
    }

    fun disconnect() {
        cookieJar.reset()
        apiToken = ""
        licenseToken = ""
        account = null
        store.clear()
    }

    private fun connectInternal(arl: String, persist: Boolean): DeezerAccount {
        if (arl.length < 32 || arl.any { !it.isLetterOrDigit() }) {
            throw DeezerApiException("The ARL does not look valid")
        }
        cookieJar.reset()
        cookieJar.add("arl", arl)
        ping()
        val result = fetchAccount()
        apiToken = result.checkForm
        licenseToken = result.licenseToken
        account = result.account
        if (persist) {
            store.arl = arl
            store.userId = result.account.userId
            store.userName = result.account.userName
            store.plan = result.account.plan
        }
        return result.account
    }

    private fun ping() {
        val url = "$GATEWAY?method=deezer.ping&input=3&api_version=1.0&api_token="
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val root = executeJson(request)
        val session = (root["results"] as? JsonObject)
            ?.get("SESSION")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
        if (session.isNullOrBlank()) {
            throw DeezerApiException("Deezer refused the ARL")
        }
        cookieJar.add("sid", session)
    }

    private fun fetchAccount(): AccountResult {
        val results = gwRaw("deezer.getUserData", "", buildJsonObject {})
        val checkForm = (results["checkForm"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val user = results["USER"] as? JsonObject ?: throw DeezerApiException("No Deezer user")
        val options = user["OPTIONS"] as? JsonObject
        val userId = (user["USER_ID"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (userId.isBlank() || userId == "0" || checkForm.isBlank()) {
            throw DeezerApiException("The Deezer session is not valid")
        }
        val userName = (user["BLOG_NAME"] as? JsonPrimitive)?.contentOrNull
            ?: (user["USER_NAME"] as? JsonPrimitive)?.contentOrNull
            ?: "Deezer"
        val license = (options?.get("license_token") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val lossless = options?.flag("web_lossless") == true ||
                options?.flag("mobile_lossless") == true
        val hq = options?.flag("web_hq") == true || options?.flag("mobile_hq") == true
        val plan = when {
            lossless -> "HiFi"
            hq -> "Premium"
            else -> "Free"
        }
        return AccountResult(
            account = DeezerAccount(userId, userName, plan),
            checkForm = checkForm,
            licenseToken = license
        )
    }

    /**
     * Tries the best formats first. A plan that does not cover a format answers
     * with `errors`; the next one is tried.
     */
    fun openStream(trackId: String, format: String? = null): DeezerStream {
        val data = trackData(trackId)
        val effectiveId = (data["SNG_ID"] as? JsonPrimitive)?.contentOrNull
            ?: throw DeezerApiException("Deezer track without id")
        val trackToken = (data["TRACK_TOKEN"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw DeezerApiException("Deezer track is not playable")
        val duration = (data["DURATION"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val formats = when {
            !format.isNullOrBlank() -> listOf(format)
            else -> preferredFormats()
        }
        var lastError: Throwable? = null
        for (candidate in formats) {
            try {
                val url = requestStreamUrl(trackToken, candidate)
                if (!url.isNullOrBlank()) {
                    return DeezerStream(
                        url = url,
                        format = candidate,
                        effectiveId = effectiveId,
                        key = deriveKey(effectiveId),
                        durationSeconds = duration,
                        mimeType = if (candidate.startsWith("FLAC")) "audio/flac" else "audio/mpeg"
                    )
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw DeezerApiException(
            lastError?.message ?: "Deezer did not offer a playable format"
        )
    }

    fun download(stream: DeezerStream, output: File, onProgress: (Float) -> Unit = {}) {
        val request = Request.Builder()
            .url(stream.url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DeezerApiException("Deezer CDN HTTP ${response.code}")
            }
            val body = response.body ?: throw DeezerApiException("Empty Deezer stream")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            body.byteStream().use { input ->
                FileOutputStream(output).use { fileOutput ->
                    decryptStream(
                        input = input,
                        output = fileOutput,
                        key = stream.key,
                        total = total,
                        onProgress = onProgress
                    )
                }
            }
        }
    }

    fun downloadBytes(url: String): ByteArray? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.bytes()
        }
    }.getOrNull()

    private fun trackData(trackId: String): JsonObject {
        val results = gw(
            "song.getData",
            buildJsonObject { put("SNG_ID", trackId) }
        )
        val fallback = results["FALLBACK"] as? JsonObject
        return fallback ?: results
    }

    private fun requestStreamUrl(trackToken: String, format: String): String? {
        if (licenseToken.isBlank()) {
            throw DeezerApiException("The Deezer plan has no stream license")
        }
        val payload = buildJsonObject {
            put("license_token", licenseToken)
            put(
                "media",
                buildJsonArray {
                    add(buildJsonObject {
                        put("type", "FULL")
                        put(
                            "formats",
                            buildJsonArray {
                                add(buildJsonObject {
                                    put("cipher", "BF_CBC_STRIPE")
                                    put("format", format)
                                })
                            }
                        )
                    })
                }
            )
            put(
                "track_tokens",
                buildJsonArray { add(JsonPrimitive(trackToken)) }
            )
        }
        val response = post(MEDIA, payload.toString(), "application/json")
        val root = json.parseToJsonElement(response) as? JsonObject ?: return null
        val entry = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val errors = entry["errors"] as? JsonArray
        if (errors != null && errors.isNotEmpty()) return null
        return (entry["media"] as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("sources")
            ?.let { it as? JsonArray }
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("url")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
    }

    private fun preferredFormats(): List<String> = listOf(
        "FLAC_24",
        "FLAC",
        "MP3_320",
        "MP3_128"
    )

    private fun gw(method: String, body: JsonObject): JsonObject {
        return gwRaw(method, apiToken, body)
    }

    private fun gwRaw(method: String, token: String, body: JsonObject): JsonObject {
        val cid = System.nanoTime()
        val encodedMethod = URLEncoder.encode(method, Charsets.UTF_8.name())
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        val url = "$GATEWAY?method=$encodedMethod&input=3&api_version=1.0&api_token=$encodedToken&cid=$cid"
        val root = executeJson(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .post(body.toString().toRequestBody(TEXT_PLAIN))
                .build()
        )
        val error = root["error"]
        val hasError = when (error) {
            null, is JsonNull -> false
            is JsonObject -> error.isNotEmpty()
            is JsonArray -> error.isNotEmpty()
            is JsonPrimitive -> error.booleanOrNull != false && error.contentOrNull?.isNotBlank() == true
            else -> true
        }
        if (hasError) {
            throw DeezerApiException("Deezer $method refused the call")
        }
        return root["results"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun executeJson(request: Request): JsonObject {
        val response = post(request)
        return json.parseToJsonElement(response) as? JsonObject
            ?: throw DeezerApiException("Deezer answered no JSON object")
    }

    private fun post(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DeezerApiException("Deezer HTTP ${response.code}")
            }
            return response.body?.string()
                ?: throw DeezerApiException("Empty Deezer response")
        }
    }

    private fun post(url: String, body: String, contentType: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody(contentType.toMediaType()))
            .build()
        return post(request)
    }

    private fun decryptStream(
        input: InputStream,
        output: FileOutputStream,
        key: ByteArray,
        total: Long,
        onProgress: (Float) -> Unit
    ) {
        val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "Blowfish")
        val iv = IvParameterSpec(IV)
        val buffer = ByteArray(BLOCK_SIZE)
        var blockIndex = 0L
        var readTotal = 0L
        var lastReported = -1f
        val buffered = BufferedInputStream(input, BLOCK_SIZE)
        while (true) {
            var read = 0
            while (read < BLOCK_SIZE) {
                val count = buffered.read(buffer, read, BLOCK_SIZE - read)
                if (count < 0) break
                read += count
            }
            if (read == 0) break
            if (read == BLOCK_SIZE && blockIndex % 3L == 0L) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)
                output.write(cipher.doFinal(buffer, 0, read))
            } else {
                output.write(buffer, 0, read)
            }
            readTotal += read
            blockIndex++
            if (total > 0) {
                val progress = (readTotal.toFloat() / total).coerceIn(0f, 1f)
                if (progress - lastReported >= 0.01f || progress >= 1f) {
                    lastReported = progress
                    onProgress(progress)
                }
            }
            if (read < BLOCK_SIZE) break
        }
        output.flush()
        if (lastReported < 1f) onProgress(1f)
    }

    private fun deriveKey(trackId: String): ByteArray {
        val md5 = MessageDigest.getInstance("MD5").digest(trackId.toByteArray(Charsets.UTF_8))
        val hex = buildString(32) {
            md5.forEach { append("%02x".format(it)) }
        }
        val secret = FALLBACK_SECRET.toByteArray(Charsets.US_ASCII)
        return ByteArray(16) { index ->
            (hex[index].code xor hex[index + 16].code xor secret[index].toInt()).toByte()
        }
    }

    private fun JsonObject.flag(key: String): Boolean {
        val value = this[key] as? JsonPrimitive ?: return false
        return value.booleanOrNull ?: (value.contentOrNull == "1")
    }

    private data class AccountResult(
        val account: DeezerAccount,
        val checkForm: String,
        val licenseToken: String
    )

    private class DeezerCookieJar : CookieJar {
        private val cookies = LinkedHashMap<String, Cookie>()

        fun reset() = cookies.clear()

        fun add(name: String, value: String) {
            cookies[name] = Cookie.Builder()
                .domain("deezer.com")
                .path("/")
                .name(name)
                .value(value)
                .build()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[it.name] = it }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.values.filter { it.matches(url) }
    }

    private companion object {
        const val GATEWAY = "https://www.deezer.com/ajax/gw-light.php"
        const val MEDIA = "https://media.deezer.com/v1/get_url"
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0"
        const val FALLBACK_SECRET = "g4el58wc0zvf9na1"
        const val BLOCK_SIZE = 2048
        val IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val TEXT_PLAIN = "text/plain;charset=UTF-8".toMediaType()
    }
}
