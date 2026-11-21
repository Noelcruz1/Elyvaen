package com.mardous.booming.data.remote.deezer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-only Deezer catalog models (public API).
 *
 * These are intentionally separate from the small cover-lookup models used by the
 * tag editor so the existing metadata flows keep working unchanged.
 */
@Serializable
data class DeezerPage<T>(
    val data: List<T> = emptyList(),
    val total: Int = 0,
    val next: String? = null
)

@Serializable
data class DeezerArtistRef(
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class DeezerAlbumRef(
    val id: Long = 0,
    val title: String = "",
    @SerialName("cover_small") val coverSmall: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null
) {
    val imageUrl: String?
        get() = coverBig ?: coverMedium ?: coverSmall
}

@Serializable
data class DeezerTrackItem(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val preview: String? = null,
    val link: String? = null,
    val artist: DeezerArtistRef? = null,
    val album: DeezerAlbumRef? = null
) {
    val artistName: String
        get() = artist?.name.orEmpty()
    val albumName: String
        get() = album?.title.orEmpty()
    val imageUrl: String?
        get() = album?.imageUrl
    val isPreviewAvailable: Boolean
        get() = !preview.isNullOrBlank()
}

@Serializable
data class DeezerAlbumItem(
    val id: Long = 0,
    val title: String = "",
    @SerialName("cover_small") val coverSmall: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    val artist: DeezerArtistRef? = null,
    @SerialName("nb_tracks") val nbTracks: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    val link: String? = null
) {
    val artistName: String
        get() = artist?.name.orEmpty()
    val imageUrl: String?
        get() = coverBig ?: coverMedium ?: coverSmall
}

@Serializable
data class DeezerArtistItem(
    val id: Long = 0,
    val name: String = "",
    @SerialName("picture_small") val pictureSmall: String? = null,
    @SerialName("picture_medium") val pictureMedium: String? = null,
    @SerialName("picture_big") val pictureBig: String? = null,
    @SerialName("nb_album") val nbAlbum: Int = 0,
    @SerialName("nb_fan") val nbFan: Long = 0,
    val link: String? = null
) {
    val imageUrl: String?
        get() = pictureBig ?: pictureMedium ?: pictureSmall
}

@Serializable
data class DeezerUser(
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class DeezerPlaylistItem(
    val id: Long = 0,
    val title: String = "",
    @SerialName("picture_small") val pictureSmall: String? = null,
    @SerialName("picture_medium") val pictureMedium: String? = null,
    @SerialName("picture_big") val pictureBig: String? = null,
    val user: DeezerUser? = null,
    @SerialName("nb_tracks") val nbTracks: Int = 0,
    val link: String? = null
) {
    val creatorName: String
        get() = user?.name.orEmpty()
    val imageUrl: String?
        get() = pictureBig ?: pictureMedium ?: pictureSmall
}

@Serializable
data class DeezerAlbumDetail(
    val id: Long = 0,
    val title: String = "",
    @SerialName("cover_small") val coverSmall: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    val artist: DeezerArtistRef? = null,
    @SerialName("nb_tracks") val nbTracks: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    val label: String? = null,
    val duration: Int = 0,
    val link: String? = null,
    val tracks: DeezerPage<DeezerTrackItem> = DeezerPage()
) {
    val artistName: String
        get() = artist?.name.orEmpty()
    val imageUrl: String?
        get() = coverBig ?: coverMedium ?: coverSmall
}

@Serializable
data class DeezerPlaylistDetail(
    val id: Long = 0,
    val title: String = "",
    @SerialName("picture_small") val pictureSmall: String? = null,
    @SerialName("picture_medium") val pictureMedium: String? = null,
    @SerialName("picture_big") val pictureBig: String? = null,
    val user: DeezerUser? = null,
    @SerialName("nb_tracks") val nbTracks: Int = 0,
    val duration: Int = 0,
    val link: String? = null,
    val tracks: DeezerPage<DeezerTrackItem> = DeezerPage()
) {
    val creatorName: String
        get() = user?.name.orEmpty()
    val imageUrl: String?
        get() = pictureBig ?: pictureMedium ?: pictureSmall
}
