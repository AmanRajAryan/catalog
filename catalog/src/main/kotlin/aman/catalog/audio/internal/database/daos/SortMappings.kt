package aman.catalog.audio.internal.database.daos

import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.ContextualSortOption

// ============================================================================
// GLOBAL LIST MAPPINGS
// ============================================================================

internal val SortOption.Track.sqlString: String
    get() = when (this) {
        SortOption.Track.TITLE_ASC -> "CASE WHEN title = '' THEN 1 ELSE 0 END ASC, title COLLATE NOCASE ASC"
        SortOption.Track.TITLE_DESC -> "title COLLATE NOCASE DESC"
        SortOption.Track.ARTIST_ASC -> "CASE WHEN artistDisplay = '' THEN 1 ELSE 0 END ASC, artistDisplay COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        SortOption.Track.ARTIST_DESC -> "artistDisplay COLLATE NOCASE DESC, title COLLATE NOCASE ASC"
        SortOption.Track.ALBUM_ASC -> "CASE WHEN albumDisplay = '' THEN 1 ELSE 0 END ASC, albumDisplay COLLATE NOCASE ASC, trackNumber ASC, title COLLATE NOCASE ASC"
        SortOption.Track.ALBUM_DESC -> "albumDisplay COLLATE NOCASE DESC, trackNumber DESC, title COLLATE NOCASE ASC"
        SortOption.Track.ALBUM_ARTIST_ASC -> "CASE WHEN albumArtistDisplay IS NULL OR albumArtistDisplay = '' THEN 1 ELSE 0 END ASC, CASE WHEN albumArtistDisplay IS NULL OR albumArtistDisplay = '' THEN artistDisplay ELSE albumArtistDisplay END COLLATE NOCASE ASC, artistDisplay COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        SortOption.Track.ALBUM_ARTIST_DESC -> "CASE WHEN albumArtistDisplay IS NULL OR albumArtistDisplay = '' THEN 1 ELSE 0 END ASC, CASE WHEN albumArtistDisplay IS NULL OR albumArtistDisplay = '' THEN artistDisplay ELSE albumArtistDisplay END COLLATE NOCASE DESC, artistDisplay COLLATE NOCASE DESC, title COLLATE NOCASE ASC"
        SortOption.Track.DATE_ADDED_DESC -> "dateAdded DESC, title COLLATE NOCASE ASC"
        SortOption.Track.DATE_ADDED_ASC -> "dateAdded ASC, title COLLATE NOCASE ASC"
        SortOption.Track.YEAR_DESC -> "year DESC, title COLLATE NOCASE ASC"
        SortOption.Track.YEAR_ASC -> "CASE WHEN year <= 0 THEN 1 ELSE 0 END ASC, year ASC, title COLLATE NOCASE ASC"
        SortOption.Track.DURATION_DESC -> "durationMs DESC, title COLLATE NOCASE ASC"
        SortOption.Track.DURATION_ASC -> "durationMs ASC, title COLLATE NOCASE ASC"
        SortOption.Track.PLAY_COUNT_DESC -> "playCount DESC, title COLLATE NOCASE ASC"
        SortOption.Track.PLAY_COUNT_ASC -> "playCount ASC, title COLLATE NOCASE ASC"
        SortOption.Track.RECENTLY_PLAYED -> "lastPlayed DESC, title COLLATE NOCASE ASC"
        SortOption.Track.DATE_MODIFIED_DESC -> "dateModified DESC, title COLLATE NOCASE ASC"
        SortOption.Track.DATE_MODIFIED_ASC -> "dateModified ASC, title COLLATE NOCASE ASC"
    }

internal val SortOption.Album.sqlString: String
    get() = when (this) {
        SortOption.Album.TITLE_ASC -> "CASE WHEN albums.title = '' THEN 1 ELSE 0 END ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.TITLE_DESC -> "albums.title COLLATE NOCASE DESC"
        SortOption.Album.ARTIST_ASC -> "CASE WHEN artistName IS NULL OR artistName = '' THEN 1 ELSE 0 END ASC, artistName COLLATE NOCASE ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.ARTIST_DESC -> "artistName COLLATE NOCASE DESC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.DATE_ADDED_DESC -> "MAX(tracks.dateAdded) DESC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.DATE_ADDED_ASC -> "MAX(tracks.dateAdded) ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.YEAR_DESC -> "maxYear DESC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.YEAR_ASC -> "CASE WHEN minYear IS NULL OR minYear <= 0 THEN 1 ELSE 0 END ASC, minYear ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.DURATION_DESC -> "totalDuration DESC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.DURATION_ASC -> "totalDuration ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.TRACK_COUNT_DESC -> "trackCount DESC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.TRACK_COUNT_ASC -> "trackCount ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.PLAY_COUNT_DESC -> "albums.playCount DESC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.PLAY_COUNT_ASC -> "albums.playCount ASC, albums.title COLLATE NOCASE ASC"
        SortOption.Album.RECENTLY_PLAYED -> "albums.lastPlayed DESC, albums.title COLLATE NOCASE ASC"
    }

// Used by getArtistsSorted (Track Artists).
// Requires the `artists.` prefix on playCount/lastPlayed to avoid ambiguity with
// the joined `tracks` table, which also has columns of the same name.
internal val SortOption.Artist.trackArtistSqlString: String
    get() = when (this) {
        SortOption.Artist.NAME_ASC -> "CASE WHEN artists.name = '' THEN 1 ELSE 0 END ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.NAME_DESC -> "artists.name COLLATE NOCASE DESC"
        SortOption.Artist.TRACK_COUNT_DESC -> "trackCount DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.TRACK_COUNT_ASC -> "trackCount ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.ALBUM_COUNT_DESC -> "albumCount DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.ALBUM_COUNT_ASC -> "albumCount ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.DURATION_DESC -> "totalDuration DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.DURATION_ASC -> "totalDuration ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.PLAY_COUNT_DESC -> "artists.playCount DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.PLAY_COUNT_ASC -> "artists.playCount ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.RECENTLY_PLAYED -> "artists.lastPlayed DESC, artists.name COLLATE NOCASE ASC"
    }

// Used by getAlbumArtistsSorted (Album Artists).
// Must NOT use the `artists.` prefix on playCount/lastPlayed because those are
// subquery aliases computed in the SELECT, not columns on the artists table.
internal val SortOption.Artist.albumArtistSqlString: String
    get() = when (this) {
        SortOption.Artist.NAME_ASC -> "CASE WHEN artists.name = '' THEN 1 ELSE 0 END ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.NAME_DESC -> "artists.name COLLATE NOCASE DESC"
        SortOption.Artist.TRACK_COUNT_DESC -> "trackCount DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.TRACK_COUNT_ASC -> "trackCount ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.ALBUM_COUNT_DESC -> "albumCount DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.ALBUM_COUNT_ASC -> "albumCount ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.DURATION_DESC -> "totalDuration DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.DURATION_ASC -> "totalDuration ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.PLAY_COUNT_DESC -> "playCount DESC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.PLAY_COUNT_ASC -> "playCount ASC, artists.name COLLATE NOCASE ASC"
        SortOption.Artist.RECENTLY_PLAYED -> "lastPlayed DESC, artists.name COLLATE NOCASE ASC"
    }

internal val SortOption.Genre.sqlString: String
    get() = when (this) {
        SortOption.Genre.NAME_ASC -> "CASE WHEN genres.name = '' THEN 1 ELSE 0 END ASC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.NAME_DESC -> "genres.name COLLATE NOCASE DESC"
        SortOption.Genre.TRACK_COUNT_DESC -> "trackCount DESC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.TRACK_COUNT_ASC -> "trackCount ASC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.ALBUM_COUNT_DESC -> "albumCount DESC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.ALBUM_COUNT_ASC -> "albumCount ASC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.DURATION_DESC -> "totalDuration DESC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.DURATION_ASC -> "totalDuration ASC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.PLAY_COUNT_DESC -> "genres.playCount DESC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.PLAY_COUNT_ASC -> "genres.playCount ASC, genres.name COLLATE NOCASE ASC"
        SortOption.Genre.RECENTLY_PLAYED -> "genres.lastPlayed DESC, genres.name COLLATE NOCASE ASC"
    }

internal val SortOption.Composer.sqlString: String
    get() = when (this) {
        SortOption.Composer.NAME_ASC -> "CASE WHEN composers.name = '' THEN 1 ELSE 0 END ASC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.NAME_DESC -> "composers.name COLLATE NOCASE DESC"
        SortOption.Composer.TRACK_COUNT_DESC -> "trackCount DESC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.TRACK_COUNT_ASC -> "trackCount ASC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.ALBUM_COUNT_DESC -> "albumCount DESC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.ALBUM_COUNT_ASC -> "albumCount ASC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.DURATION_DESC -> "totalDuration DESC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.DURATION_ASC -> "totalDuration ASC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.PLAY_COUNT_DESC -> "composers.playCount DESC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.PLAY_COUNT_ASC -> "composers.playCount ASC, composers.name COLLATE NOCASE ASC"
        SortOption.Composer.RECENTLY_PLAYED -> "composers.lastPlayed DESC, composers.name COLLATE NOCASE ASC"
    }

internal val SortOption.Lyricist.sqlString: String
    get() = when (this) {
        SortOption.Lyricist.NAME_ASC -> "CASE WHEN lyricists.name = '' THEN 1 ELSE 0 END ASC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.NAME_DESC -> "lyricists.name COLLATE NOCASE DESC"
        SortOption.Lyricist.TRACK_COUNT_DESC -> "trackCount DESC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.TRACK_COUNT_ASC -> "trackCount ASC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.ALBUM_COUNT_DESC -> "albumCount DESC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.ALBUM_COUNT_ASC -> "albumCount ASC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.DURATION_DESC -> "totalDuration DESC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.DURATION_ASC -> "totalDuration ASC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.PLAY_COUNT_DESC -> "lyricists.playCount DESC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.PLAY_COUNT_ASC -> "lyricists.playCount ASC, lyricists.name COLLATE NOCASE ASC"
        SortOption.Lyricist.RECENTLY_PLAYED -> "lyricists.lastPlayed DESC, lyricists.name COLLATE NOCASE ASC"
    }

internal val SortOption.Year.sqlString: String
    get() = when (this) {
        SortOption.Year.YEAR_DESC -> "year DESC"
        SortOption.Year.YEAR_ASC -> "year ASC"
        SortOption.Year.TRACK_COUNT_DESC -> "count DESC, year DESC"
        SortOption.Year.TRACK_COUNT_ASC -> "count ASC, year DESC"
        SortOption.Year.ALBUM_COUNT_DESC -> "albumCount DESC, year DESC"
        SortOption.Year.ALBUM_COUNT_ASC -> "albumCount ASC, year DESC"
        SortOption.Year.DURATION_DESC -> "totalDuration DESC, year DESC"
        SortOption.Year.DURATION_ASC -> "totalDuration ASC, year DESC"
        SortOption.Year.PLAY_COUNT_DESC -> "playCount DESC, year DESC"
        SortOption.Year.PLAY_COUNT_ASC -> "playCount ASC, year DESC"
        SortOption.Year.RECENTLY_PLAYED -> "lastPlayed DESC, year DESC"
    }

internal val SortOption.Folder.sqlString: String
    get() = when (this) {
        SortOption.Folder.NAME_ASC -> "CASE WHEN name = '' THEN 1 ELSE 0 END ASC, name COLLATE NOCASE ASC"
        SortOption.Folder.NAME_DESC -> "name COLLATE NOCASE DESC"
        SortOption.Folder.PATH_ASC -> "path COLLATE NOCASE ASC"
        SortOption.Folder.PATH_DESC -> "path COLLATE NOCASE DESC"
        SortOption.Folder.TRACK_COUNT_DESC -> "count DESC, name COLLATE NOCASE ASC"
        SortOption.Folder.TRACK_COUNT_ASC -> "count ASC, name COLLATE NOCASE ASC"
        SortOption.Folder.ALBUM_COUNT_DESC -> "albumCount DESC, name COLLATE NOCASE ASC"
        SortOption.Folder.ALBUM_COUNT_ASC -> "albumCount ASC, name COLLATE NOCASE ASC"
        SortOption.Folder.DURATION_DESC -> "totalDuration DESC, name COLLATE NOCASE ASC"
        SortOption.Folder.DURATION_ASC -> "totalDuration ASC, name COLLATE NOCASE ASC"
        SortOption.Folder.PLAY_COUNT_DESC -> "playCount DESC, name COLLATE NOCASE ASC"
        SortOption.Folder.PLAY_COUNT_ASC -> "playCount ASC, name COLLATE NOCASE ASC"
        SortOption.Folder.RECENTLY_PLAYED -> "lastPlayed DESC, name COLLATE NOCASE ASC"
    }

// ============================================================================
// CONTEXTUAL LIST MAPPINGS
// ============================================================================

internal val SortOption.Playlist.sqlString: String
    get() = when (this) {
        SortOption.Playlist.NAME_ASC -> "CASE WHEN playlists.name = '' THEN 1 ELSE 0 END ASC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.NAME_DESC -> "playlists.name COLLATE NOCASE DESC"
        SortOption.Playlist.TRACK_COUNT_DESC -> "trackCount DESC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.TRACK_COUNT_ASC -> "trackCount ASC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.DURATION_DESC -> "totalDuration DESC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.DURATION_ASC -> "totalDuration ASC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.DATE_CREATED_DESC -> "playlists.dateCreated DESC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.DATE_CREATED_ASC -> "playlists.dateCreated ASC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.DATE_MODIFIED_DESC -> "playlists.dateModified DESC, playlists.name COLLATE NOCASE ASC"
        SortOption.Playlist.DATE_MODIFIED_ASC -> "playlists.dateModified ASC, playlists.name COLLATE NOCASE ASC"
    }

internal val ContextualSortOption.Track.sqlString: String
    get() = when (this) {
        ContextualSortOption.Track.TRACK_NUMBER_ASC -> "tracks.discNumber ASC, tracks.trackNumber ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.TITLE_ASC -> "CASE WHEN tracks.title = '' THEN 1 ELSE 0 END ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.TITLE_DESC -> "tracks.title COLLATE NOCASE DESC"
        ContextualSortOption.Track.DATE_ADDED_DESC -> "tracks.dateAdded DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.DATE_ADDED_ASC -> "tracks.dateAdded ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.YEAR_DESC -> "tracks.year DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.YEAR_ASC -> "CASE WHEN tracks.year <= 0 THEN 1 ELSE 0 END ASC, tracks.year ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.DURATION_DESC -> "tracks.durationMs DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.DURATION_ASC -> "tracks.durationMs ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.PLAY_COUNT_DESC -> "tracks.playCount DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.PLAY_COUNT_ASC -> "tracks.playCount ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.RECENTLY_PLAYED -> "tracks.lastPlayed DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.DATE_MODIFIED_DESC -> "tracks.dateModified DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.Track.DATE_MODIFIED_ASC -> "tracks.dateModified ASC, tracks.title COLLATE NOCASE ASC"
    }

internal val ContextualSortOption.Album.sqlString: String
    get() = when (this) {
        ContextualSortOption.Album.TITLE_ASC -> "CASE WHEN albums.title = '' THEN 1 ELSE 0 END ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.TITLE_DESC -> "albums.title COLLATE NOCASE DESC"
        ContextualSortOption.Album.DATE_ADDED_DESC -> "MAX(tracks.dateAdded) DESC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.DATE_ADDED_ASC -> "MAX(tracks.dateAdded) ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.YEAR_DESC -> "maxYear DESC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.YEAR_ASC -> "CASE WHEN maxYear IS NULL OR maxYear <= 0 THEN 1 ELSE 0 END ASC, maxYear ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.TOTAL_TRACK_COUNT_DESC -> "albums.trackCount DESC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.TOTAL_TRACK_COUNT_ASC -> "albums.trackCount ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.MATCHED_TRACK_COUNT_DESC -> "matchedTrackCount DESC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.MATCHED_TRACK_COUNT_ASC -> "matchedTrackCount ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.DURATION_DESC -> "totalDuration DESC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.DURATION_ASC -> "totalDuration ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.PLAY_COUNT_DESC -> "albums.playCount DESC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.PLAY_COUNT_ASC -> "albums.playCount ASC, albums.title COLLATE NOCASE ASC"
        ContextualSortOption.Album.RECENTLY_PLAYED -> "albums.lastPlayed DESC, albums.title COLLATE NOCASE ASC"
    }

internal val ContextualSortOption.Genre.sqlString: String
    get() = when (this) {
        ContextualSortOption.Genre.NAME_ASC -> "CASE WHEN genres.name = '' THEN 1 ELSE 0 END ASC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.NAME_DESC -> "genres.name COLLATE NOCASE DESC"
        ContextualSortOption.Genre.TOTAL_TRACK_COUNT_DESC -> "trackCount DESC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.TOTAL_TRACK_COUNT_ASC -> "trackCount ASC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.MATCHED_TRACK_COUNT_DESC -> "matchedTrackCount DESC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.MATCHED_TRACK_COUNT_ASC -> "matchedTrackCount ASC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.DURATION_DESC -> "totalDuration DESC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.DURATION_ASC -> "totalDuration ASC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.PLAY_COUNT_DESC -> "genres.playCount DESC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.PLAY_COUNT_ASC -> "genres.playCount ASC, genres.name COLLATE NOCASE ASC"
        ContextualSortOption.Genre.RECENTLY_PLAYED -> "genres.lastPlayed DESC, genres.name COLLATE NOCASE ASC"
    }

internal val ContextualSortOption.PlaylistTrack.sqlString: String
    get() = when (this) {
        ContextualSortOption.PlaylistTrack.USER_DEFINED    -> "playlist_entries.sortOrder ASC"
        ContextualSortOption.PlaylistTrack.TITLE_ASC       -> "CASE WHEN tracks.title = '' THEN 1 ELSE 0 END ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.TITLE_DESC      -> "tracks.title COLLATE NOCASE DESC"
        ContextualSortOption.PlaylistTrack.DATE_ADDED_DESC -> "tracks.dateAdded DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.DATE_ADDED_ASC  -> "tracks.dateAdded ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.YEAR_DESC       -> "tracks.year DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.YEAR_ASC        -> "CASE WHEN tracks.year <= 0 THEN 1 ELSE 0 END ASC, tracks.year ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.DURATION_DESC   -> "tracks.durationMs DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.DURATION_ASC    -> "tracks.durationMs ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.PLAY_COUNT_DESC -> "tracks.playCount DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.PLAY_COUNT_ASC  -> "tracks.playCount ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.RECENTLY_PLAYED -> "tracks.lastPlayed DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.DATE_MODIFIED_DESC -> "tracks.dateModified DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.PlaylistTrack.DATE_MODIFIED_ASC  -> "tracks.dateModified ASC, tracks.title COLLATE NOCASE ASC"
    }

internal val ContextualSortOption.FavoriteTrack.sqlString: String
    get() = when (this) {
        ContextualSortOption.FavoriteTrack.USER_DEFINED        -> "favorites.sortOrder ASC"
        ContextualSortOption.FavoriteTrack.DATE_FAVORITED_DESC -> "favorites.dateMarked DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.DATE_FAVORITED_ASC  -> "favorites.dateMarked ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.TITLE_ASC           -> "CASE WHEN tracks.title = '' THEN 1 ELSE 0 END ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.TITLE_DESC          -> "tracks.title COLLATE NOCASE DESC"
        ContextualSortOption.FavoriteTrack.DATE_ADDED_DESC     -> "tracks.dateAdded DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.DATE_ADDED_ASC      -> "tracks.dateAdded ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.YEAR_DESC           -> "tracks.year DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.YEAR_ASC            -> "CASE WHEN tracks.year <= 0 THEN 1 ELSE 0 END ASC, tracks.year ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.DURATION_DESC       -> "tracks.durationMs DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.DURATION_ASC        -> "tracks.durationMs ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.PLAY_COUNT_DESC     -> "tracks.playCount DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.PLAY_COUNT_ASC      -> "tracks.playCount ASC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.RECENTLY_PLAYED     -> "tracks.lastPlayed DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.DATE_MODIFIED_DESC  -> "tracks.dateModified DESC, tracks.title COLLATE NOCASE ASC"
        ContextualSortOption.FavoriteTrack.DATE_MODIFIED_ASC   -> "tracks.dateModified ASC, tracks.title COLLATE NOCASE ASC"
    }
