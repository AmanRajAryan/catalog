package aman.catalog.audio.models

/**
 * A unified container holding the results of a global search query.
 * Lists will be empty if their corresponding SearchFilter was not requested,
 * or if no matches were found in the database.
 */
data class SearchResult(
    val query: String,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albumArtists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val composers: List<Composer> = emptyList(),
    val lyricists: List<Lyricist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val years: List<Year> = emptyList()
) {
    /**
     * Helper property for UI developers to easily check if they should show an "Empty State" screen.
     */
    val isEmpty: Boolean
        get() = tracks.isEmpty() && artists.isEmpty() && albumArtists.isEmpty() &&
                albums.isEmpty() && genres.isEmpty() && composers.isEmpty() &&
                lyricists.isEmpty() && playlists.isEmpty() && folders.isEmpty() && years.isEmpty()
}

