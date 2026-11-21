package com.mardous.booming.ui.screen.deezer

import androidx.lifecycle.ViewModel
import com.mardous.booming.data.remote.deezer.DeezerDownloadManager
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem

class DownloadsViewModel(
    private val downloadManager: DeezerDownloadManager
) : ViewModel() {

    val entries = downloadManager.entries

    fun retry(track: DeezerTrackItem) {
        downloadManager.downloadTrack(track)
    }

    fun clearFinished() {
        downloadManager.clearFinished()
    }
}
