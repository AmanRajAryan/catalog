package aman.catalog.audio.internal

import android.content.ContentUris
import android.provider.MediaStore
import aman.catalog.audio.internal.database.daos.*
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.internal.database.relations.TrackWithRelations
import aman.catalog.audio.models.*
import aman.catalog.audio.models.ArtPath


internal object ModelMapper {

    // --- TRACK MAPPING ---

    fun toTrack(container: TrackWithRelations): Track {
        val entity = container.track
        
        val albumEntity = container.album

        val contentUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            entity.mediaStoreId
        )

        return Track(
            id = entity.id,
            uri = contentUri, 
            path = entity.path,
            albumId = entity.albumId,
            albumArtistId = albumEntity?.albumArtistId,
            
            // Chips
            artists = container.artists.map { IdName(it.id, it.name) },
            genres = container.genres.map { IdName(it.id, it.name) },
            composers = container.composers.map { IdName(it.id, it.name) },
            lyricists = container.lyricists.map { IdName(it.id, it.name) },
            
            // Strings
            title = entity.title,
            artist = entity.artistDisplay,
            album = albumEntity?.title ?: entity.albumDisplay,
            albumArtist = entity.albumArtistDisplay,
            genre = entity.genreDisplay,
            composer = entity.composerDisplay,
            lyricist = entity.lyricistDisplay,
            
            // Metadata
            folderName = entity.folderName,
            folderPath = entity.folderPath,
            durationMs = entity.durationMs,
            trackNumber = entity.trackNumber,
            discNumber = entity.discNumber,
            year = entity.year,
            releaseDate = entity.releaseDate,
            dateAdded = entity.dateAdded,
            dateModified = entity.dateModified,
            sizeBytes = entity.sizeBytes,
            mimeType = entity.mimeType,
            
            // Stats
            playCount = entity.playCount,
            lastPlayed = entity.lastPlayed,
            totalPlayTimeMs = entity.totalPlayTimeMs,
            skipCount = entity.skipCount,
            
            dateFavorited = container.favorite?.dateMarked ?: 0,
            
            // Technical
            metadata = ExtendedMetadata(
                contentRating = entity.contentRating,
                bitrate = entity.bitrate,
                sampleRate = entity.sampleRate,
                channels = entity.channels,
                codec = entity.codec.ifBlank { entity.mimeType },
                bitsPerSample = entity.bitsPerSample,
                replayGainTrackGain = entity.replayGainTrackGain,
                replayGainTrackPeak = entity.replayGainTrackPeak,
                replayGainAlbumGain = entity.replayGainAlbumGain,
                replayGainAlbumPeak = entity.replayGainAlbumPeak,
                foundComposer = entity.rawComposerString,
                foundAlbumArtist = entity.rawAlbumArtistString,
                foundYear = entity.year,
                foundReleaseDate = entity.releaseDate,
                foundLyricist = entity.rawLyricistString,
                foundTrackNumber = entity.trackNumber,
                foundDiscNumber = entity.discNumber,
                foundTitle = entity.title,
                foundArtist = entity.rawArtistString,
                foundAlbum = entity.rawAlbumString,
                foundGenre = entity.rawGenreString
            )
        )
    }

    // --- CATEGORY MAPPING ---

    // Artist (Standard)
    fun toArtist(data: ArtistDao.ArtistWithCount): Artist {
        return Artist(data.id, data.name, data.trackCount, data.albumCount, data.totalDuration, data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) }, data.playCount, data.lastPlayed, data.totalPlayTimeMs)
    }

    // Artist (Album Artist View)
    fun toArtist(data: ArtistDao.AlbumArtistWithCounts): Artist {
        return Artist(data.id, data.name, data.trackCount, data.albumCount, data.totalDuration, data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) }, data.playCount, data.lastPlayed, data.totalPlayTimeMs)
    }

    // Album
    fun toAlbum(data: AlbumDao.AlbumDetails): Album {
        return Album(
            id = data.id,
            title = data.title,
            albumArtistId = data.albumArtistId,
            albumArtist = data.albumArtistName ?: "Unknown Artist",
            albumArtistCoverArtPath = data.albumArtistCoverArtPath?.let { ArtPath(it, data.albumArtistCoverArtDateModified ?: 0L) },
            trackCount = data.trackCount,
            totalDurationMs = data.totalDuration,
            year = formatYearRange(data.minYear, data.maxYear),
            coverArtPath = data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) },
            playCount = data.playCount,
            lastPlayed = data.lastPlayed,
            totalPlayTimeMs = data.totalPlayTimeMs
        )
    }

    // Genre
    fun toGenre(data: GenreDao.GenreWithCount): Genre {
        return Genre(
            data.id,
            data.name,
            data.trackCount,
            data.albumCount,
            data.totalDuration,
            data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) },
            data.playCount,
            data.lastPlayed,
            data.totalPlayTimeMs
        )
    }

    // MatchedAlbum Wrapper
    fun toMatchedAlbum(data: AlbumDao.MatchedAlbumDetails): MatchedAlbum {
        return MatchedAlbum(
            album = toAlbum(data.albumDetails),
            matchedTrackCount = data.matchedTrackCount,
            matchedDurationMs = data.matchedDurationMs
        )
    }

    // MatchedGenre Wrapper
    fun toMatchedGenre(data: GenreDao.MatchedGenreDetails): MatchedGenre {
        return MatchedGenre(
            genre = toGenre(data.genreWithCount),
            matchedTrackCount = data.matchedTrackCount,
            matchedDurationMs = data.matchedDurationMs
        )
    }

    // Composer
    fun toComposer(data: ComposerDao.ComposerWithCount): Composer {
        return Composer(data.id, data.name, data.trackCount, data.albumCount, data.totalDuration, data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) }, data.playCount, data.lastPlayed, data.totalPlayTimeMs)
    }

    // Lyricist
    fun toLyricist(data: LyricistDao.LyricistWithCount): Lyricist {
        return Lyricist(data.id, data.name, data.trackCount, data.albumCount, data.totalDuration, data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) }, data.playCount, data.lastPlayed, data.totalPlayTimeMs)
    }

    // Playlist
    fun toPlaylist(entity: PlaylistEntity, trackCount: Int, totalDuration: Long, coverArtPath: ArtPath?): Playlist {
        return Playlist(
            id = entity.id,
            name = entity.name,
            dateCreated = entity.dateCreated,
            dateModified = entity.dateModified,
            trackCount = trackCount,
            totalDurationMs = totalDuration,
            coverArtPath = coverArtPath
        )
    }

    // Folder
    fun toFolder(data: FolderDao.FolderCount): Folder {
        return Folder(
            data.name,
            data.path,
            data.count,
            data.albumCount,
            data.totalDuration,
            data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) },
            data.playCount,
            data.lastPlayed,
            data.totalPlayTimeMs
        )
    }

    // Year
    fun toYear(data: YearDao.YearCount): Year {
        return Year(
            data.year,
            data.count,
            data.albumCount,
            data.totalDuration,
            data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) },
            data.playCount,
            data.lastPlayed,
            data.totalPlayTimeMs
        )
    }

    // FavoritesInfo
    fun toFavoritesInfo(data: FavoritesDao.FavoritesInfoResult): FavoritesInfo {
        return FavoritesInfo(
            trackCount = data.trackCount,
            totalDurationMs = data.totalDurationMs,
            coverArtPath = data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) }
        )
    }

    // --- UTILS ---
    fun formatYearRange(min: Int?, max: Int?): String {
        if (min == null || max == null || min == 0) return ""
        return if (min == max) "$min" else "$min-$max"
    }
}