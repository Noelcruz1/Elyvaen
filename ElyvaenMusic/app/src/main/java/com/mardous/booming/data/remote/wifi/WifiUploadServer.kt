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

package com.mardous.booming.data.remote.wifi

import android.content.Context
import com.mardous.booming.data.local.AudioImportManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class WifiUploadEvent(
    val name: String,
    val bytes: Long,
    val success: Boolean,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tiny HTTP server for uploading music from any browser on the same Wi-Fi.
 * The URL contains a random token; uploads are copied to
 * Music/Elyvaen/Import/Wi-Fi and indexed by MediaStore.
 */
class WifiUploadServer(
    private val context: Context,
    private val token: String,
    private val importer: AudioImportManager,
    port: Int,
    private val onEvent: (WifiUploadEvent) -> Unit
) : NanoHTTPD(port) {

    private val uploadPath = "/u/$token"

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != uploadPath) {
            return text(Response.Status.NOT_FOUND, "No encontrado")
        }
        return when (session.method) {
            Method.GET -> text(Response.Status.OK, uploadPage(), MIME_HTML)
            Method.POST -> receiveUpload(session)
            else -> text(Response.Status.METHOD_NOT_ALLOWED, "Método no permitido")
        }
    }

    private fun receiveUpload(session: IHTTPSession): Response {
        val requestedName = fileNameParameter(session)
            ?: return text(Response.Status.BAD_REQUEST, "Falta el nombre del archivo")
        val fileName = requestedName.trim()

        if (fileName.isBlank() || !importer.isSupportedFile(fileName)) {
            return text(Response.Status.BAD_REQUEST, "Formato de audio no compatible")
        }
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: -1L
        if (contentLength <= 0L) {
            return text(Response.Status.BAD_REQUEST, "Tamaño de archivo no válido")
        }
        if (contentLength > MAX_UPLOAD_BYTES) {
            return text(
                Response.Status.PAYLOAD_TOO_LARGE,
                "El archivo supera el límite de 4 GB"
            )
        }

        val temp = File.createTempFile("wifi-upload-", ".part", context.cacheDir)
        return try {
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = contentLength
                while (remaining > 0) {
                    val read = session.inputStream.read(
                        buffer,
                        0,
                        minOf(buffer.size.toLong(), remaining).toInt()
                    )
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                if (remaining != 0L) {
                    throw IllegalStateException("La subida se interrumpió")
                }
            }
            val mime = importer.resolveMimeType(fileName, session.headers["content-type"])
            temp.inputStream().use { input ->
                importer.publishAudio(
                    input = input,
                    displayName = fileName,
                    mimeType = mime,
                    folder = "Import/Wi-Fi"
                )
            }
            onEvent(WifiUploadEvent(fileName, contentLength, true))
            text(
                Response.Status.OK,
                "{\"ok\":true,\"name\":${jsonString(fileName)},\"bytes\":$contentLength}",
                MIME_JSON
            )
        } catch (t: Throwable) {
            onEvent(
                WifiUploadEvent(
                    name = fileName,
                    bytes = contentLength,
                    success = false,
                    message = t.message ?: "Error de subida"
                )
            )
            text(
                Response.Status.INTERNAL_ERROR,
                "{\"ok\":false,\"error\":${jsonString(t.message ?: "Error de subida")}}",
                MIME_JSON
            )
        } finally {
            temp.delete()
        }
    }

    private fun text(
        status: Response.Status,
        body: String,
        mime: String = NanoHTTPD.MIME_PLAINTEXT
    ): Response {
        return newFixedLengthResponse(status, mime, body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Connection", "close")
        }
    }

    private fun fileNameParameter(session: IHTTPSession): String? {
        session.parms["name"]?.let { return it }
        session.headers["x-filename"]?.let { return it }
        val query = session.queryParameterString ?: return null
        val encoded = query.split('&')
            .firstOrNull { it.substringBefore('=') == "name" }
            ?.substringAfter('=', "")
            ?: return null
        return runCatching {
            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        }.getOrDefault(encoded)
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }

    private fun uploadPage(): String = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Elyvaen · Subida Wi-Fi</title>
<style>
:root { color-scheme: dark; }
* { box-sizing: border-box; }
body {
  margin: 0; min-height: 100vh; font-family: system-ui, sans-serif;
  background: radial-gradient(circle at 18% 0%, #163b3c 0, #080b0d 46%, #050607 100%);
  color: #edf7f5; display: flex; align-items: center; justify-content: center;
  padding: 22px;
}
main { width: min(680px, 100%); }
.eyebrow { color: #6fe0c8; font-weight: 700; letter-spacing: .18em; font-size: 12px; }
h1 { font-size: clamp(28px, 6vw, 40px); margin: 6px 0 8px; }
p { color: #a9bcb8; line-height: 1.5; }
.card {
  margin-top: 20px; background: rgba(17, 25, 28, .88); border: 1px solid #2c4a48;
  border-radius: 24px; padding: 22px; box-shadow: 0 20px 60px rgba(0,0,0,.35);
}
.drop {
  border: 1.5px dashed #3c7c73; border-radius: 18px; padding: 28px 18px;
  text-align: center; transition: .18s; background: rgba(30, 77, 70, .14);
}
.drop.over { border-color: #79f0d1; background: rgba(58, 176, 151, .18); }
.drop strong { display: block; margin-bottom: 6px; color: #d9fff6; }
button, label.pick {
  display: inline-block; margin-top: 14px; cursor: pointer;
  background: #6fe0c8; color: #07211d; border: 0; border-radius: 999px;
  padding: 12px 20px; font-weight: 800; font-size: 15px;
}
input[type=file] { display: none; }
.bar { height: 9px; background: #1d302e; border-radius: 999px; overflow: hidden; margin-top: 18px; }
.bar > i { display: block; width: 0; height: 100%; background: linear-gradient(90deg, #37b99f, #9af7df); transition: width .18s; }
#status { margin-top: 10px; color: #b8cfca; font-size: 14px; min-height: 21px; }
ul { list-style: none; padding: 0; margin: 16px 0 0; }
li { display: flex; gap: 10px; justify-content: space-between; padding: 9px 0; border-top: 1px solid #203533; font-size: 14px; }
li span:last-child { color: #8fa7a2; white-space: nowrap; }
.ok { color: #7ef0c0; } .bad { color: #ff9b9b; }
footer { margin-top: 20px; color: #6f8580; font-size: 12px; line-height: 1.5; }
</style>
</head>
<body>
<main>
  <div class="eyebrow">ELYVAEN · WI-FI</div>
  <h1>Deja tu música aquí</h1>
  <p>Estás conectado al DAP. Los archivos se copian a
  <b>Music/Elyvaen/Import/Wi-Fi</b> y aparecen en la biblioteca sin usar el PC.</p>
  <section class="card">
    <div class="drop" id="drop">
      <strong>Arrastra archivos de audio</strong>
      <span>FLAC, MP3, M4A, AAC, OGG, OPUS, WAV, AIFF, WMA, APE, DSF, DFF</span>
      <div><label class="pick" for="files">Elegir archivos</label></div>
      <input id="files" type="file" multiple accept="audio/*,.flac,.mp3,.m4a,.aac,.ogg,.opus,.wav,.aiff,.wma,.ape,.dsf,.dff">
    </div>
    <div class="bar"><i id="bar"></i></div>
    <div id="status">Esperando archivos…</div>
    <ul id="list"></ul>
  </section>
  <footer>El enlace contiene un token temporal y solo funciona en la red Wi-Fi local.
  Cierra la subida desde Elyvaen cuando termines.</footer>
</main>
<script>
const TOKEN = '$token';
const PATH = '/u/' + TOKEN;
const drop = document.getElementById('drop');
const input = document.getElementById('files');
const bar = document.getElementById('bar');
const status = document.getElementById('status');
const list = document.getElementById('list');

function bytes(n) {
  if (n < 1024) return n + ' B';
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
  if (n < 1073741824) return (n / 1048576).toFixed(1) + ' MB';
  return (n / 1073741824).toFixed(2) + ' GB';
}
function row(name, state, cls) {
  const li = document.createElement('li');
  const left = document.createElement('span');
  const right = document.createElement('span');
  left.textContent = name;
  right.textContent = state;
  if (cls) right.className = cls;
  li.appendChild(left); li.appendChild(right); list.prepend(li);
}
function upload(file, index, total) {
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', PATH + '?name=' + encodeURIComponent(file.name));
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) return;
      const filePart = event.loaded / event.total;
      const overall = ((index + filePart) / total) * 100;
      bar.style.width = overall.toFixed(1) + '%';
      status.textContent = 'Subiendo ' + (index + 1) + ' de ' + total + ' · ' +
        file.name + ' · ' + Math.round(filePart * 100) + '%';
    };
    xhr.onload = () => {
      let ok = false;
      try { ok = JSON.parse(xhr.responseText).ok === true; } catch (e) {}
      if (ok) row(file.name, 'Añadido', 'ok');
      else row(file.name, 'Error', 'bad');
      resolve();
    };
    xhr.onerror = () => { row(file.name, 'Sin conexión', 'bad'); resolve(); };
    xhr.send(file);
  });
}
async function uploadAll(files) {
  if (!files || !files.length) return;
  const total = files.length;
  status.textContent = 'Preparando ' + total + ' archivo(s)…';
  for (let i = 0; i < total; i++) {
    bar.style.width = ((i / total) * 100).toFixed(1) + '%';
    await upload(files[i], i, total);
    bar.style.width = (((i + 1) / total) * 100).toFixed(1) + '%';
  }
  status.textContent = 'Terminado. Elyvaen está indexando los archivos.';
}
input.addEventListener('change', () => uploadAll(input.files));
['dragenter', 'dragover'].forEach((name) => drop.addEventListener(name, (e) => {
  e.preventDefault(); drop.classList.add('over');
}));
['dragleave', 'drop'].forEach((name) => drop.addEventListener(name, (e) => {
  e.preventDefault(); drop.classList.remove('over');
}));
drop.addEventListener('drop', (e) => uploadAll(e.dataTransfer.files));
</script>
</body>
</html>
""".trimIndent()

    companion object {
        const val DEFAULT_PORT = 8099
        const val MAX_UPLOAD_BYTES = 4L * 1024L * 1024L * 1024L
        private const val MIME_HTML = "text/html; charset=utf-8"
        private const val MIME_JSON = "application/json; charset=utf-8"
    }
}
