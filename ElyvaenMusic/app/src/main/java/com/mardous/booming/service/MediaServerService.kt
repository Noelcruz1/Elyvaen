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

package com.mardous.booming.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.mardous.booming.MainActivity
import com.mardous.booming.R
import com.mardous.booming.data.local.MediaServerController
import com.mardous.booming.data.remote.mediaserver.DlnaServer
import com.mardous.booming.data.remote.mediaserver.MediaHttpServer
import com.mardous.booming.data.remote.mediaserver.MediaLibraryIndex
import com.mardous.booming.data.remote.mediaserver.PlaybackBridge
import com.mardous.booming.data.remote.wifi.WifiNetwork
import com.mardous.booming.data.repository.Repository
import fi.iki.elonen.NanoHTTPD
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.security.SecureRandom

/**
 * Foreground service that exposes the DAP library to the local network:
 * UPnP/DLNA for TVs, read-only WebDAV and a small browser remote control.
 */
class MediaServerService : Service(), KoinComponent {

    private val controller: MediaServerController by inject()
    private val repository: Repository by inject()

    private var server: MediaHttpServer? = null
    private var dlna: DlnaServer? = null
    private var bridge: PlaybackBridge? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startServer()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer() {
        if (running) return
        running = true
        startForegroundCompat(buildNotification(null))

        val ip = WifiNetwork.localIpv4()
        if (ip == null) {
            controller.onError("No se encontró una dirección Wi-Fi")
            stopSelf()
            return
        }
        val token = randomToken()
        val uuid = getOrCreateUuid()
        val friendlyName = "Elyvaen (${Build.MODEL})"
        val libraryIndex = MediaLibraryIndex(repository)
        val playbackBridge = PlaybackBridge(this)
        bridge = playbackBridge

        var started: Pair<MediaHttpServer, Int>? = null
        var lastError: Throwable? = null
        for (port in MediaHttpServer.DEFAULT_PORT..(MediaHttpServer.DEFAULT_PORT + 5)) {
            val candidate = MediaHttpServer(
                context = this,
                index = libraryIndex,
                bridge = playbackBridge,
                token = token,
                uuid = uuid,
                port = port,
                friendlyName = friendlyName
            ).apply { ipAddress = ip }
            try {
                candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                started = candidate to port
                break
            } catch (t: IOException) {
                lastError = t
                runCatching { candidate.stop() }
            }
        }

        val (activeServer, port) = started ?: run {
            controller.onError(lastError?.message ?: "No se pudo abrir el servidor")
            stopSelf()
            return
        }
        server = activeServer

        val ssdp = DlnaServer(ip, port, uuid, friendlyName)
        runCatching { ssdp.start() }
            .onFailure { Log.w(TAG, "SSDP unavailable", it) }
        dlna = ssdp

        acquireLocks()
        val webUrl = "http://$ip:$port/web/$token/"
        val davUrl = "http://$ip:$port/dav/$token/"
        controller.onStarted(ip, port, friendlyName, webUrl, davUrl)
        updateNotification("$friendlyName · $ip:$port")
    }

    private fun acquireLocks() {
        runCatching {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            multicastLock = wifi?.createMulticastLock("Elyvaen:MediaServer")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        runCatching {
            val power = getSystemService(PowerManager::class.java) ?: return@runCatching
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Elyvaen:MediaServer"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun buildNotification(detail: String?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopServer = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phonelink_ring_24dp)
            .setContentTitle(getString(R.string.media_server_notification_title))
            .setContentText(detail ?: getString(R.string.media_server_notification_preparing))
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_close_24dp),
                    getString(R.string.media_server_stop),
                    stopServer
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(detail: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(detail))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_server_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.media_server_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun getOrCreateUuid(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.getString(KEY_UUID, null)?.let { return it }
        val uuid = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_UUID, uuid).apply()
        return uuid
    }

    private fun randomToken(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        return buildString(TOKEN_LENGTH) {
            repeat(TOKEN_LENGTH) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    override fun onDestroy() {
        runCatching { dlna?.stop() }
        dlna = null
        runCatching { server?.stop() }
        server = null
        bridge?.close()
        bridge = null
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        controller.onStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.elyvaen.booming.action.MEDIA_SERVER_START"
        const val ACTION_STOP = "com.elyvaen.booming.action.MEDIA_SERVER_STOP"

        private const val TAG = "ElyvaenMediaServer"
        private const val CHANNEL_ID = "elyvaen_media_server"
        private const val NOTIFICATION_ID = 238
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L
        private const val TOKEN_LENGTH = 12
        private const val PREFS_NAME = "elyvaen_media_server"
        private const val KEY_UUID = "uuid"
    }
}
