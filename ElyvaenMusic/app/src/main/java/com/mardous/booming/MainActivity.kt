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

package com.mardous.booming

import android.app.ActivityManager
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.view.KeyEvent
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mardous.booming.core.MediaEventBus
import com.mardous.booming.core.model.CategoryInfo
import com.mardous.booming.core.model.MediaEvent
import com.mardous.booming.core.model.shuffle.OpenShuffleMode
import com.mardous.booming.data.model.ContentType
import com.mardous.booming.data.model.network.NetworkFeature
import com.mardous.booming.extensions.currentFragment
import com.mardous.booming.extensions.navigation.detailArgs
import com.mardous.booming.extensions.navigation.isValidCategory
import com.mardous.booming.extensions.showToast
import com.mardous.booming.ui.screen.dap.Uac2Control
import com.mardous.booming.extensions.utilities.toEnum
import com.mardous.booming.extensions.whichFragment
import com.mardous.booming.playback.Playback
import com.mardous.booming.playback.library.MediaIDs
import com.mardous.booming.playback.library.SearchQueryProvider
import com.mardous.booming.ui.IScrollHelper
import com.mardous.booming.ui.component.base.AbsSlidingMusicPanelActivity
import com.mardous.booming.ui.screen.library.search.SearchFragment
import com.mardous.booming.ui.screen.link.LinkResolverFragment
import com.mardous.booming.ui.screen.link.extractMusicUrl
import com.mardous.booming.ui.screen.hub.DapAssistantAction
import com.mardous.booming.ui.screen.update.UpdateDialog
import com.mardous.booming.ui.screen.update.UpdateSearchResult
import com.mardous.booming.ui.screen.update.UpdateViewModel
import com.mardous.booming.util.IGNORE_AUDIO_FOCUS
import com.mardous.booming.util.PAUSE_ON_DISCONNECT
import com.mardous.booming.util.Preferences
import com.mardous.booming.util.RESUME_ON_CONNECT
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * @author Christians M. A. (mardous)
 */
class MainActivity : AbsSlidingMusicPanelActivity(), MediaController.Listener {

    private val updateViewModel: UpdateViewModel by viewModel()
    private val mediaEventBus: MediaEventBus by inject()
    private var kioskPaused = false
    private var kioskLockTaskRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = null
        applyDapSignaturePreset()
        applyDapAuroraUx()
        applyDapNoAutoplay()
        applyDapAudioDefaults()
        Uac2Control.ensureService(this)
        applyPreferredDisplayMode()
        applyDapKeepScreenOn()

        updateTabs()
        setupNavigationController()

        val shortcutManager = getSystemService<ShortcutManager>()
        shortcutManager?.removeDynamicShortcuts(OLD_SHORTCUT_IDS)

        prepareUpdateViewModel()
    }

    override fun onConnected(controller: MediaController) {
        super.onConnected(controller)
        intent?.let { handlePlaybackIntent(it) }
    }

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        controller: MediaController,
        command: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val sessionResult = when (command.customAction) {
            Playback.EVENT_MEDIA_CONTENT_CHANGED -> {
                mediaEventBus.postEvent(MediaEvent.MediaContentChanged)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            Playback.EVENT_FAVORITE_CONTENT_CHANGED -> {
                mediaEventBus.postEvent(MediaEvent.FavoriteContentChanged)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            Playback.EVENT_PLAYBACK_STARTED -> {
                mediaEventBus.postEvent(MediaEvent.PlaybackStarted)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }


            else -> SessionResult(SessionError.ERROR_NOT_SUPPORTED)
        }
        return Futures.immediateFuture(sessionResult)
    }

    fun scanAllPaths() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scan_media)
            .setMessage(R.string.scan_media_message)
            .setPositiveButton(R.string.scan_media_positive) { _, _ ->
                libraryViewModel.scanAllPaths(this).observe(this) {
                    // TODO show detailed info about scanned songs
                    showToast(R.string.scan_finished)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupNavigationController() {
        val navController = whichFragment<NavHostFragment>(R.id.fragment_container).navController
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.graph_main)

        val categoryInfo: CategoryInfo = Preferences.libraryCategories.first { it.visible }
        if (categoryInfo.visible) {
            val lastPage = Preferences.lastPage
            if (!navGraph.isValidCategory(lastPage)) {
                Preferences.lastPage = categoryInfo.category.id
                navGraph.setStartDestination(categoryInfo.category.id)
            } else {
                val preferredStart = if (Preferences.isRememberLastPage) {
                    lastPage.let {
                        if (it == 0) categoryInfo.category.id else it
                    }
                } else {
                    categoryInfo.category.id
                }
                // Only resume a category that is actually in the bottom bar.
                // Otherwise the bar shows no selection and Home can become unreachable.
                navGraph.setStartDestination(
                    if (navigationView.menu.findItem(preferredStart) != null) {
                        preferredStart
                    } else {
                        R.id.nav_home
                    }
                )
            }
        }

        navController.graph = navGraph
        navigationView.setupWithNavController(navController)
        // Scroll Fragment to top, or recover when a category outside the
        // bottom bar left a stale selection behind.
        navigationView.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id != item.itemId) {
                navController.navigate(item.itemId)
            } else {
                currentFragment(R.id.fragment_container).apply {
                    if (this is IScrollHelper) {
                        scrollToTop()
                    }
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == navGraph.startDestinationId) {
                currentFragment(R.id.fragment_container)?.enterTransition = null
            }
            if (destination.navigatorName == "dialog") {
                return@addOnDestinationChangedListener
            }
            when (destination.id) {
                R.id.nav_home,
                R.id.nav_songs,
                R.id.nav_albums,
                R.id.nav_artists,
                R.id.nav_folders,
                R.id.nav_playlists,
                R.id.nav_genres,
                R.id.nav_years -> {
                    // Save the last tab
                    if (Preferences.isRememberLastPage) {
                        saveTab(destination.id)
                    }
                    // Show Bottom Navigation Bar
                    setBottomNavVisibility(visible = true, animate = true)
                }

                R.id.nav_queue,
                R.id.nav_lyrics_editor,
                R.id.nav_play_info,
                R.id.nav_about -> {
                    setBottomNavVisibility(visible = false, hideBottomSheet = true)
                }

                else -> setBottomNavVisibility(visible = false, animate = true) // Hide Bottom Navigation Bar
            }
        }
    }

    private fun saveTab(id: Int) {
        if (Preferences.libraryCategories.firstOrNull { it.category.id == id }?.visible == true) {
            Preferences.lastPage = id
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePlaybackIntent(intent)
    }

    /**
     * A Deezer/Spotify/YouTube/Tidal/Apple Music URL shared with or opened by
     * Elyvaen is handed to the link resolver sheet. Non-URL media intents keep
     * their normal playback/search handling.
     */
    private fun handleSharedMusicLink(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) {
            return false
        }
        val raw = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString
        val link = raw.extractMusicUrl() ?: return false
        if (supportFragmentManager.findFragmentByTag(LINK_RESOLVER_TAG) == null) {
            LinkResolverFragment.newInstance(link).show(supportFragmentManager, LINK_RESOLVER_TAG)
        }
        setIntent(Intent())
        return true
    }

    @Suppress("DEPRECATION")
    private fun handlePlaybackIntent(intent: Intent) {
        if (handleSharedMusicLink(intent)) {
            return
        }
        when (intent.action) {
            ACTION_SHOW_CONTENT -> {
                intent.getStringExtra(EXTRA_CONTENT_TYPE)?.toEnum<ContentType>()?.let { type ->
                    whichFragment<NavHostFragment>(R.id.fragment_container).navController
                        .navigate(R.id.nav_detail_list, detailArgs(type))
                }
                setIntent(Intent())
            }

            APP_SHORTCUT_LAST_ADDED -> {
                playerViewModel.playMediaId(MediaIDs.LAST_ADDED)
                ShortcutManagerCompat.reportShortcutUsed(this, "last_added")
                setIntent(Intent())
            }
            APP_SHORTCUT_TOP_TRACKS -> {
                playerViewModel.playMediaId(MediaIDs.TOP_TRACKS)
                ShortcutManagerCompat.reportShortcutUsed(this, "top_tracks")
                setIntent(Intent())
            }
            APP_SHORTCUT_SHUFFLE -> {
                playerViewModel.playMediaId(MediaIDs.SONGS, true)
                ShortcutManagerCompat.reportShortcutUsed(this, "shuffle_all")
                setIntent(Intent())
            }
            APP_SHORTCUT_FAVORITES -> {
                playerViewModel.playMediaId(MediaIDs.FAVORITES, true)
                ShortcutManagerCompat.reportShortcutUsed(this, "favorites")
                setIntent(Intent())
            }

            Intent.ACTION_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY)
                whichFragment<NavHostFragment>(R.id.fragment_container).navController
                    .navigate(
                        R.id.nav_search,
                        Bundle().apply { putString(SearchFragment.QUERY, query) }
                    )
                setIntent(Intent())
            }

            MediaStore.INTENT_ACTION_MEDIA_SEARCH,
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                SearchQueryProvider.handleSearchIntent(intent) { mainQuery, subQueries ->
                    if (mainQuery == null) {
                        showToast(R.string.invalid_search_params)
                    } else {
                        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                            playerViewModel.playMediaItem(
                                MediaItem.Builder()
                                    .setRequestMetadata(
                                        MediaItem.RequestMetadata.Builder()
                                            .setSearchQuery(mainQuery)
                                            .setExtras(subQueries)
                                            .build()
                                    )
                                    .build()
                            )
                        } else {
                            whichFragment<NavHostFragment>(R.id.fragment_container).navController
                                .navigate(
                                    R.id.nav_search,
                                    Bundle().apply { putString(SearchFragment.QUERY, mainQuery) }
                                )
                        }
                    }
                }
            }

            else -> {
                libraryViewModel.handleIntent(intent).observe(this) { result ->
                    if (result.handled) {
                        if (result.songs.isNotEmpty()) {
                            playerViewModel.openQueue(
                                queue = result.songs,
                                position = result.position,
                                shuffleMode = OpenShuffleMode.Off
                            )
                        }
                        setIntent(Intent())
                    }
                    if (result.failed) {
                        showToast(R.string.unplayable_file)
                    }
                }
            }
        }
    }

    private fun prepareUpdateViewModel() {
        updateViewModel.run {
            updateEventObservable.observe(this@MainActivity) { event ->
                event.getContentIfNotConsumed()?.let { result ->
                    when (result.state) {
                        UpdateSearchResult.State.Completed -> {
                            val release = result.data ?: return@let
                            if (result.wasFromUser || release.isDownloadable(this@MainActivity)) {
                                val existingDialog = supportFragmentManager.findFragmentByTag("UPDATE_FOUND")
                                if (existingDialog == null) {
                                    UpdateDialog().show(supportFragmentManager, "UPDATE_FOUND")
                                }
                            }
                        }
                        UpdateSearchResult.State.Failed -> {
                            if (result.wasFromUser) {
                                showToast(R.string.could_not_check_for_updates)
                            }
                        }
                        else -> {}
                    }
                }
            }
            updateEvent?.peekContent().let { updateState ->
                if (updateState == null || updateState.state == UpdateSearchResult.State.Idle) {
                    if (NetworkFeature.Updater.isAvailable) {
                        searchForUpdate(false)
                    }
                }
            }
        }
    }

    private fun applyDapSignaturePreset() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getBoolean("dap_signature_applied", false)) {
            return
        }
        preferences.edit()
            .putString("now_playing_screen", "Elyvaen")
            .putString("player_expressive_color_scheme", "Blur")
            .putBoolean("carousel_effect", true)
            .putBoolean("now_playing_small_image", false)
            .putInt("now_playing_corner_radius", 20)
            .putBoolean("display_extra_info", true)
            .putBoolean("display_album_title", true)
            .putBoolean("black_theme", true)
            .putBoolean("material_you", true)
            .putString("general_theme", "black")
            .putString("appbar_mode", "compact")
            .putBoolean("adaptive_controls", true)
            .putBoolean("squiggly_seek_bar", true)
            .putInt("player_blur_radius", 30)
            .putBoolean("ignore_audio_focus", true)
            .putBoolean("resume_on_connect", false)
            .putBoolean("pause_on_disconnect", true)
            .putBoolean("dap_kiosk_enabled", true)
            .putBoolean("dap_signature_applied", true)
            .apply()
    }

    private fun applyDapAuroraUx() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!preferences.getBoolean("dap_aurora_applied", false)) {
            preferences.edit()
                .putBoolean("aurora_theme", true)
                .putBoolean("material_you", false)
                .putBoolean("black_theme", true)
                .putString("general_theme", "black")
                .putBoolean("dap_aurora_applied", true)
                .apply()
        }
        if (!preferences.getBoolean("dap_aurora_player_applied", false)) {
            preferences.edit()
                .putString("player_expressive_color_scheme", "AppTheme")
                .putString("player_default_color_scheme", "AppTheme")
                .putBoolean("dap_aurora_player_applied", true)
                .apply()
        }
        if (!preferences.getBoolean("dap_elyvaen_player_applied", false)) {
            preferences.edit()
                .putString("now_playing_screen", "Elyvaen")
                .putString("player_elyvaen_color_scheme", "AppTheme")
                .putBoolean("dap_elyvaen_player_applied", true)
                .apply()
        }
    }

    private fun applyDapNoAutoplay() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getBoolean("dap_no_autoplay_applied", false)) {
            return
        }
        preferences.edit()
            .putBoolean("resume_on_connect", false)
            .putBoolean("resume_on_bluetooth_connect", false)
            .putString("play_on_startup_mode", "never")
            .putBoolean("dap_no_autoplay_applied", true)
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredDisplayMode() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val requestedRate = preferences.getInt("dap_refresh_rate", 90)
        val modes = windowManager.defaultDisplay.supportedModes
        val bestMode = modes.firstOrNull {
            kotlin.math.abs(it.refreshRate - requestedRate) < 1f
        } ?: modes.firstOrNull {
            kotlin.math.abs(it.refreshRate - 90f) < 1f
        } ?: modes.firstOrNull {
            kotlin.math.abs(it.refreshRate - 60f) < 1f
        } ?: modes.maxByOrNull { it.refreshRate }
            ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = bestMode.modeId
        }
    }

    fun setDapRefreshRate(rate: Int) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putInt("dap_refresh_rate", rate)
            .apply()
        applyPreferredDisplayMode()
    }

    fun setDapKeepScreenOn(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("dap_keep_screen_on", enabled)
            .apply()
        applyDapKeepScreenOn()
    }

    private fun applyDapKeepScreenOn() {
        val enabled = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("dap_keep_screen_on", false)
        if (enabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun handleAssistantButton(): Boolean {
        val action = runCatching {
            DapAssistantAction.valueOf(
                PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("dap_assistant_action", DapAssistantAction.PLAY_PAUSE.name)
                    ?: DapAssistantAction.PLAY_PAUSE.name
            )
        }.getOrDefault(DapAssistantAction.PLAY_PAUSE)
        return when (action) {
            DapAssistantAction.PLAY_PAUSE -> {
                playerViewModel.togglePlayPause()
                true
            }

            DapAssistantAction.NEXT -> {
                playerViewModel.seekToNext()
                true
            }

            DapAssistantAction.PREVIOUS -> {
                playerViewModel.seekToPrevious()
                true
            }

            DapAssistantAction.OPEN_PLAYER -> {
                expandPanel()
                true
            }

            DapAssistantAction.TOGGLE_KIOSK -> {
                if (getSystemService<ActivityManager>()?.isInLockTaskMode == true) {
                    exitKioskMode()
                } else {
                    enterKioskMode()
                }
                true
            }

            DapAssistantAction.NONE -> false
        }
    }

    private fun applyDapAudioDefaults() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!preferences.getBoolean("dap_audio_defaults_applied", false)) {
            preferences.edit()
                .putBoolean(IGNORE_AUDIO_FOCUS, true)
                .putBoolean(RESUME_ON_CONNECT, false)
                .putBoolean(PAUSE_ON_DISCONNECT, true)
                .putBoolean("dap_audio_defaults_applied", true)
                .apply()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!kioskPaused && PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("dap_kiosk_enabled", false)) {
            enterKioskFullscreen()
            requestKioskLockTask()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("dap_kiosk_enabled", false)) {
            enterKioskFullscreen()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (event.repeatCount == 0 && handleAssistantButton()) {
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.repeatCount >= 20) {
            exitKioskMode()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun enterKioskMode() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("dap_kiosk_enabled", true)
            .apply()
        kioskPaused = false
        kioskLockTaskRequested = false
        enterKioskFullscreen()
        requestKioskLockTask()
    }

    fun exitKioskMode() {
        kioskPaused = true
        kioskLockTaskRequested = false
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("dap_kiosk_enabled", false)
            .apply()
        if (getSystemService<ActivityManager>()?.isInLockTaskMode == true) {
            stopLockTask()
        }
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Asks the system to pin this task. Elyvaen is not a device owner, so Android
     * shows its own screen-pinning confirmation the first time. If the user
     * declines, the DAP stays in immersive kiosk mode without task pinning.
     */
    private fun requestKioskLockTask() {
        if (kioskLockTaskRequested) return
        val activityManager = getSystemService<ActivityManager>() ?: return
        if (activityManager.isInLockTaskMode) return
        kioskLockTaskRequested = true
        runCatching { startLockTask() }
    }

    private fun enterKioskFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val LINK_RESOLVER_TAG = "LINK_RESOLVER"
        const val ACTION_SHOW_CONTENT = "com.mardous.booming.action.SHOW_CONTENT"
        const val EXTRA_CONTENT_TYPE = "com.mardous.booming.extra.CONTENT_TYPE"

        private const val APP_SHORTCUT_LAST_ADDED = "com.mardous.booming.shortcut.LAST_ADDED"
        private const val APP_SHORTCUT_TOP_TRACKS = "com.mardous.booming.shortcut.TOP_TRACKS"
        private const val APP_SHORTCUT_SHUFFLE = "com.mardous.booming.shortcut.SHUFFLE"
        private const val APP_SHORTCUT_FAVORITES = "com.mardous.booming.shortcut.FAVORITES"

        private val OLD_SHORTCUT_IDS = listOf(
            "com.mardous.booming.appshortcuts.id.last_added",
            "com.mardous.booming.appshortcuts.id.top_tracks",
            "com.mardous.booming.appshortcuts.id.shuffle_all",
        )
    }
}