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

package com.mardous.booming.data.remote.nas

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.mardous.booming.data.local.NasProtocol
import com.mardous.booming.data.local.NasSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

sealed interface NasTestResult {
    data object Success : NasTestResult
    data class Failure(val message: String) : NasTestResult
}

/**
 * Tests a NAS using the credentials entered by the user. No default host,
 * share or credentials are baked into the app.
 */
class NasConnectionTester(private val client: OkHttpClient) {

    suspend fun test(settings: NasSettings): NasTestResult = withContext(Dispatchers.IO) {
        when (settings.protocol) {
            NasProtocol.WEBDAV -> testWebDav(settings)
            NasProtocol.SMB -> testSmb(settings)
            NasProtocol.SFTP -> testSftp(settings)
        }
    }

    suspend fun detectSftpHostKey(host: String, port: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val verifier = CapturingHostKeyVerifier()
                newSshClient().use { client ->
                    client.addHostKeyVerifier(verifier)
                    client.connect(host.trim(), port.takeIf { it > 0 } ?: 22)
                }
                verifier.fingerprint
                    ?: error("El servidor no presentó una clave SSH")
            }
        }

    private fun testWebDav(settings: NasSettings): NasTestResult {
        val scheme = if (settings.useTls) "https" else "http"
        val port = if (settings.port > 0) ":${settings.port}" else ""
        val path = settings.path.trim().trimStart('/')
        val url = "$scheme://${settings.host.trim()}$port/${if (path.isBlank()) "" else path}"
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "0")
            .apply {
                if (settings.username.isNotBlank()) {
                    header("Authorization", Credentials.basic(settings.username, settings.password))
                }
            }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 207 -> NasTestResult.Success
                    401, 403 -> NasTestResult.Failure("Credenciales rechazadas")
                    404 -> NasTestResult.Failure("Ruta no encontrada")
                    else -> NasTestResult.Failure("HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            failure(e)
        }
    }

    private fun testSmb(settings: NasSettings): NasTestResult {
        if (settings.share.isBlank()) {
            return NasTestResult.Failure("Falta el recurso compartido")
        }
        val port = if (settings.port > 0) settings.port else 445
        return try {
            SMBClient().connect(settings.host.trim(), port).use { connection ->
                val auth = AuthenticationContext(
                    settings.username,
                    settings.password.toCharArray(),
                    settings.domain.ifBlank { null }
                )
                val session = connection.authenticate(auth)
                val share = session.connectShare(settings.share.trim()) as? DiskShare
                    ?: return NasTestResult.Failure("El recurso no es una carpeta SMB")
                try {
                    share.list(settings.path.trim().trim('/'))
                } finally {
                    share.close()
                }
                NasTestResult.Success
            }
        } catch (e: Exception) {
            failure(e)
        }
    }

    private fun testSftp(settings: NasSettings): NasTestResult {
        if (settings.hostKey.isBlank()) {
            return NasTestResult.Failure("Falta la huella SSH del servidor")
        }
        if (settings.username.isBlank()) {
            return NasTestResult.Failure("Escribe el usuario SFTP")
        }
        return try {
            newSshClient().use { client ->
                client.addHostKeyVerifier(
                    FingerprintVerifier.getInstance(settings.hostKey.trim())
                )
                client.connect(
                    settings.host.trim(),
                    settings.port.takeIf { it > 0 } ?: 22
                )
                client.authPassword(settings.username, settings.password)
                client.newSFTPClient().use { sftp ->
                    sftp.ls(settings.path.trim().ifBlank { "." })
                }
            }
            NasTestResult.Success
        } catch (e: Exception) {
            failure(e)
        }
    }

    private fun failure(e: Exception): NasTestResult.Failure {
        val message = e.message.orEmpty()
        return when {
            message.contains("fingerprint", true) ||
                    message.contains("host key", true) ->
                NasTestResult.Failure("La huella SSH no coincide con el servidor")
            message.contains("UnknownHost", true) ||
                    message.contains("unable to resolve", true) ||
                    message.contains("nodename nor servname", true) ->
                NasTestResult.Failure(
                    "No se pudo resolver el host. Activa Tailscale si usas MagicDNS."
                )
            message.isBlank() -> NasTestResult.Failure("Error de red")
            else -> NasTestResult.Failure(message)
        }
    }

    private inner class CapturingHostKeyVerifier : HostKeyVerifier {
        @Volatile
        var fingerprint: String? = null

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            fingerprint = sshFingerprint(key)
            return true
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
            emptyList()
    }

    private fun sshFingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Buffer.PlainBuffer().putPublicKey(key).compactData)
        return "SHA256:" + Base64.getEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }
}
