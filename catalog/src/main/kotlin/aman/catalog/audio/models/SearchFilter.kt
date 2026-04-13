package aman.catalog.audio.models

/**
 * Defines the categories available for global search.
 * Pass a Set of these to Catalog.search() to restrict the query.
 */
enum class SearchFilter {
    TRACKS,
    ARTISTS,
    ALBUM_ARTISTS,
    ALBUMS,
    GENRES,
    COMPOSERS,
    LYRICISTS,
    PLAYLISTS,
    FOLDERS,
    YEARS
}



