/*
 * Copyright (c) 2024 Christians Martínez Alvarado
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

import com.mardous.booming.data.remote.deezer.model.DeezerAlbum
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumDetail
import com.mardous.booming.data.remote.deezer.model.DeezerAlbumItem
import com.mardous.booming.data.remote.deezer.model.DeezerArtist
import com.mardous.booming.data.remote.deezer.model.DeezerArtistItem
import com.mardous.booming.data.remote.deezer.model.DeezerPage
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistDetail
import com.mardous.booming.data.remote.deezer.model.DeezerPlaylistItem
import com.mardous.booming.data.remote.deezer.model.DeezerTrack
import com.mardous.booming.data.remote.deezer.model.DeezerTrackItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLParameter

class DeezerService(private val client: HttpClient) {
    suspend fun track(artistName: String, title: String) =
        client.get("https://api.deezer.com/search?limit=1") {
            url {
                encodedParameters.append("q", "$artistName $title".encodeURLParameter())
            }
        }.body<DeezerTrack>()

    suspend fun album(artistName: String, name: String, limit: Int = 1) =
        client.get("https://api.deezer.com/search/album?limit=$limit") {
            url {
                encodedParameters.append("q", "$artistName $name".encodeURLParameter())
            }
        }.body<DeezerAlbum>()

    suspend fun artist(artistName: String, limit: Int = 1, index: Int = 0) =
        client.get("https://api.deezer.com/search/artist") {
            url {
                parameters.append("limit", limit.toString())
                parameters.append("index", index.toString())
                parameters.append("q", artistName)
            }
        }.body<DeezerArtist>()

    // ---- Read-only catalog (public API) -------------------------------------

    suspend fun chartTracks(limit: Int = 20): DeezerPage<DeezerTrackItem> =
        client.get("https://api.deezer.com/chart/0/tracks") {
            parameter("limit", limit)
        }.body()

    suspend fun chartAlbums(limit: Int = 12): DeezerPage<DeezerAlbumItem> =
        client.get("https://api.deezer.com/chart/0/albums") {
            parameter("limit", limit)
        }.body()

    suspend fun chartPlaylists(limit: Int = 12): DeezerPage<DeezerPlaylistItem> =
        client.get("https://api.deezer.com/chart/0/playlists") {
            parameter("limit", limit)
        }.body()

    suspend fun chartArtists(limit: Int = 12): DeezerPage<DeezerArtistItem> =
        client.get("https://api.deezer.com/chart/0/artists") {
            parameter("limit", limit)
        }.body()

    suspend fun searchTracks(query: String, limit: Int = 30): DeezerPage<DeezerTrackItem> =
        client.get("https://api.deezer.com/search/track") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun searchAlbums(query: String, limit: Int = 30): DeezerPage<DeezerAlbumItem> =
        client.get("https://api.deezer.com/search/album") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun searchArtists(query: String, limit: Int = 30): DeezerPage<DeezerArtistItem> =
        client.get("https://api.deezer.com/search/artist") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun searchPlaylists(query: String, limit: Int = 30): DeezerPage<DeezerPlaylistItem> =
        client.get("https://api.deezer.com/search/playlist") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun albumDetail(id: Long): DeezerAlbumDetail =
        client.get("https://api.deezer.com/album/$id").body()

    suspend fun artistDetail(id: Long): DeezerArtistItem =
        client.get("https://api.deezer.com/artist/$id").body()

    suspend fun artistTopTracks(id: Long, limit: Int = 20): DeezerPage<DeezerTrackItem> =
        client.get("https://api.deezer.com/artist/$id/top") {
            parameter("limit", limit)
        }.body()

    suspend fun artistAlbums(id: Long, limit: Int = 20): DeezerPage<DeezerAlbumItem> =
        client.get("https://api.deezer.com/artist/$id/albums") {
            parameter("limit", limit)
        }.body()

    suspend fun playlistDetail(id: Long): DeezerPlaylistDetail =
        client.get("https://api.deezer.com/playlist/$id").body()
}