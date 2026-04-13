package aman.catalog.audio.models

object ContextualSortOption {

    // Used for: getTracksForArtist, getTracksForAlbumArtist, getTracksForAlbum, 
    // getTracksForGenre, getTracksForComposer, getTracksForLyricist, 
    // getTracksForYear, getTracksForFolder
    enum class Track {

        TRACK_NUMBER_ASC,
        TITLE_ASC, 
        TITLE_DESC,
        DATE_ADDED_DESC,
        DATE_ADDED_ASC,
        YEAR_DESC,
        YEAR_ASC,
        DURATION_DESC, 
        DURATION_ASC,
        PLAY_COUNT_DESC, 
        PLAY_COUNT_ASC,
        RECENTLY_PLAYED,
        DATE_MODIFIED_DESC,
        DATE_MODIFIED_ASC
    }

    // Used for: getAppearsOnAlbumsForTrackArtist, getAlbumsForGenre, 
    // getAlbumsForComposer, getAlbumsForLyricist, getAlbumsForFolder, getAlbumsForYear
    // (Note: getAlbumsForAlbumArtist returns standard Albums, not MatchedAlbums, 
    // but can still use this enum for consistency)
    enum class Album {
        TITLE_ASC, 
        TITLE_DESC,
        DATE_ADDED_DESC,
        DATE_ADDED_ASC,
        YEAR_DESC, 
        YEAR_ASC,
        TOTAL_TRACK_COUNT_DESC,   
        TOTAL_TRACK_COUNT_ASC,
        MATCHED_TRACK_COUNT_DESC, 
        MATCHED_TRACK_COUNT_ASC,
        DURATION_DESC,
        DURATION_ASC,
        PLAY_COUNT_DESC, 
        PLAY_COUNT_ASC,
        RECENTLY_PLAYED
    }

    // Used for: getGenresForAlbum, getGenresForTrackArtist, getGenresForAlbumArtist, 
    // getGenresForComposer, getGenresForLyricist, getGenresForFolder, getGenresForYear
    enum class Genre {
        NAME_ASC, 
        NAME_DESC, 
        TOTAL_TRACK_COUNT_DESC,
        TOTAL_TRACK_COUNT_ASC,
        MATCHED_TRACK_COUNT_DESC, 
        MATCHED_TRACK_COUNT_ASC, 
        DURATION_DESC,
        DURATION_ASC,
        PLAY_COUNT_DESC, 
        PLAY_COUNT_ASC, 
        RECENTLY_PLAYED
    }

    // Used for: getPlaylistTracks
    enum class PlaylistTrack {
        USER_DEFINED,
        TITLE_ASC,
        TITLE_DESC,
        DATE_ADDED_DESC,
        DATE_ADDED_ASC,
        YEAR_DESC,
        YEAR_ASC,
        DURATION_DESC,
        DURATION_ASC,
        PLAY_COUNT_DESC,
        PLAY_COUNT_ASC,
        RECENTLY_PLAYED,
        DATE_MODIFIED_DESC,
        DATE_MODIFIED_ASC
    }

    // Used for: getFavoriteTracks
    enum class FavoriteTrack {
        USER_DEFINED,
        DATE_FAVORITED_DESC,
        DATE_FAVORITED_ASC,
        TITLE_ASC,
        TITLE_DESC,
        DATE_ADDED_DESC,
        DATE_ADDED_ASC,
        YEAR_DESC,
        YEAR_ASC,
        DURATION_DESC,
        DURATION_ASC,
        PLAY_COUNT_DESC,
        PLAY_COUNT_ASC,
        RECENTLY_PLAYED,
        DATE_MODIFIED_DESC,
        DATE_MODIFIED_ASC
    }
}
