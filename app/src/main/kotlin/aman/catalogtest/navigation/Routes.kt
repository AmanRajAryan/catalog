package aman.catalogtest.navigation

import android.net.Uri

// Single source of truth for all navigation routes.
// Always use these constants/functions — never raw strings.
object Routes {
    const val HOME      = "home"
    const val FAVORITES = "favorites"

    const val ALBUM_DETAIL        = "album/{id}"
    const val ARTIST_DETAIL       = "artist/{id}"
    const val ALBUM_ARTIST_DETAIL = "album_artist/{id}"
    const val PLAYLIST_DETAIL     = "playlist/{id}"

    // Shared detail route for simpler categories (genre, composer, lyricist, year, folder).
    // title and id are URL-encoded before passing.
    const val CATEGORY_DETAIL = "category/{type}/{id}/{title}"

    fun albumDetail(id: Long)       = "album/$id"
    fun artistDetail(id: Long)      = "artist/$id"
    fun albumArtistDetail(id: Long) = "album_artist/$id"
    fun playlistDetail(id: Long)    = "playlist/$id"

    fun categoryDetail(type: String, id: String, title: String): String {
        // Navigation args can't be empty strings — fallback to "Unknown" to avoid NavGraph crash.
        val safeTitle = title.ifBlank { "Unknown" }
        return "category/$type/${Uri.encode(id)}/${Uri.encode(safeTitle)}"
    }

    const val TRACK_EDITOR = "track_editor/{trackId}"
    fun trackEditor(trackId: Long) = "track_editor/$trackId"
}



