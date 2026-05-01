package aman.catalog.audio.internal.scanner

import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.models.ExtendedMetadata

internal class TrackIngestor(
    private val db: CatalogDatabase,
    var splitter: SmartSplitter
) {

    suspend fun ingestNewTrack(
        skeleton: SkeletonTrack,
        extendedMeta: ExtendedMetadata
    ) {
        val (processed, finalAlbumArtist, rawAlbumArtist) =
            processCommonFields(skeleton, extendedMeta)

        // Always call insertOrGetId() even for a blank Album Artist — the DAO handles it
        // gracefully, ensuring every track belongs to a valid queryable artist entry.
        val albumArtistId = db.artistDao().insertOrGetId(finalAlbumArtist)

        // A real Album Artist tag ("Pink Floyd") means multiple folders may belong to the same
        // release (e.g. multi-disc albums split across Disc 1/ and Disc 2/), so we use global
        // grouping ("") to collapse them into one album entry.
        // A blank Album Artist means the title may be generic ("Greatest Hits"), so we scope
        // grouping to the folder path to prevent unrelated albums from merging.
        val folderGroup = if (finalAlbumArtist.isNotBlank()) "" else processed.folderPath

        // Prefer TagLib's album title over MediaStore's — MediaStore can hold stale or garbage data.
        val finalAlbumId = db.albumDao().insertOrGetId(processed.validAlbum, albumArtistId, folderGroup)

        val trackEntity = TrackEntity(
            mediaStoreId = skeleton.mediaStoreId,
            path = skeleton.path,
            albumId = finalAlbumId,
            folderName = processed.folderName,
            folderPath = processed.folderPath,
            title = processed.validTitle,
            sizeBytes = skeleton.size,
            dateAdded = skeleton.dateAdded,
            dateModified = skeleton.dateModified,
            mimeType = skeleton.mimeType,
            durationMs = skeleton.duration,
            artistDisplay = processed.validArtist,
            albumDisplay = processed.validAlbum,
            albumArtistDisplay = finalAlbumArtist,
            genreDisplay = processed.validGenre,
            composerDisplay = processed.rawComposer,
            lyricistDisplay = extendedMeta.foundLyricist,
            rawArtistString = processed.validArtist,
            rawGenreString = processed.validGenre,
            rawComposerString = processed.rawComposer,
            rawLyricistString = extendedMeta.foundLyricist,
            rawAlbumArtistString = rawAlbumArtist,
            rawAlbumString = processed.rawAlbum,
            year = processed.finalYear,
            releaseDate = processed.finalReleaseDate,
            trackNumber = processed.finalTrackNumber,
            discNumber = extendedMeta.foundDiscNumber,
            contentRating = extendedMeta.contentRating,
            bitrate = extendedMeta.bitrate,
            sampleRate = extendedMeta.sampleRate,
            channels = extendedMeta.channels,
            codec = extendedMeta.codec,
            bitsPerSample = extendedMeta.bitsPerSample,
            replayGainTrackGain = extendedMeta.replayGainTrackGain,
            replayGainTrackPeak = extendedMeta.replayGainTrackPeak,
            replayGainAlbumGain = extendedMeta.replayGainAlbumGain,
            replayGainAlbumPeak = extendedMeta.replayGainAlbumPeak
        )

        val trackId = db.trackDao().insert(trackEntity)
        insertJunctions(trackId, processed)
    }

    suspend fun ingestUpdatedTrack(
        id: Long,
        skeleton: SkeletonTrack,
        extendedMeta: ExtendedMetadata,
        trustTagLibFirst: Boolean = false
    ) {
        val (processed, finalAlbumArtist, rawAlbumArtist) =
            processCommonFields(skeleton, extendedMeta, trustTagLibFirst)

        // Always call insertOrGetId() even for a blank Album Artist — same reasoning as ingestNewTrack.
        val albumArtistId = db.artistDao().insertOrGetId(finalAlbumArtist)

        // Real Album Artist → global grouping so multi-disc albums merge across folders.
        // Blank Album Artist → folder-scoped grouping to prevent unrelated albums from merging.
        val folderGroup = if (finalAlbumArtist.isNotBlank()) "" else processed.folderPath

        // Prefer TagLib's album title — MediaStore may not have caught up after a rename.
        val finalAlbumId = db.albumDao().insertOrGetId(processed.validAlbum, albumArtistId, folderGroup)

        // If the track is moving to a different album and the old album only had this one track,
        // it's about to be GC'd — transfer its play stats so history isn't lost on a rename.
        val oldAlbumId = db.trackDao().getAlbumIdForTrack(id)
        if (oldAlbumId != null && oldAlbumId != finalAlbumId) {
            val remainingTracks = db.albumDao().getTrackCountForAlbum(oldAlbumId)
            if (remainingTracks == 1) {
                db.albumDao().mergePlayStats(fromId = oldAlbumId, toId = finalAlbumId)
            }
        }

        db.trackDao().updateTrackMetadata(
            id = id,
            mediaStoreId = skeleton.mediaStoreId,
            albumId = finalAlbumId,
            path = skeleton.path,
            folderName = processed.folderName,
            folderPath = processed.folderPath,
            title = processed.validTitle,
            size = skeleton.size,
            dateModified = skeleton.dateModified,
            mimeType = skeleton.mimeType,
            duration = skeleton.duration,
            artist = processed.validArtist,
            album = processed.validAlbum,
            albumArtist = finalAlbumArtist,
            genre = processed.validGenre,
            composer = processed.rawComposer,
            lyricist = extendedMeta.foundLyricist,
            rawArtist = processed.validArtist,
            rawGenre = processed.validGenre,
            rawComposer = processed.rawComposer,
            rawLyricist = extendedMeta.foundLyricist,
            rawAlbumArtist = rawAlbumArtist,
            rawAlbum = processed.rawAlbum,
            year = processed.finalYear,
            releaseDate = processed.finalReleaseDate,
            trackNum = processed.finalTrackNumber,
            discNum = extendedMeta.foundDiscNumber,
            rating = extendedMeta.contentRating,
            bitrate = extendedMeta.bitrate,
            sampleRate = extendedMeta.sampleRate,
            channels = extendedMeta.channels,
            codec = extendedMeta.codec,
            bitsPerSample = extendedMeta.bitsPerSample,
            replayGainTrackGain = extendedMeta.replayGainTrackGain,
            replayGainTrackPeak = extendedMeta.replayGainTrackPeak,
            replayGainAlbumGain = extendedMeta.replayGainAlbumGain,
            replayGainAlbumPeak = extendedMeta.replayGainAlbumPeak
        )

        // Clear old junction refs and insert fresh ones based on the updated metadata
        db.trackDao().deleteArtistRefs(id)
        db.trackDao().deleteGenreRefs(id)
        db.trackDao().deleteComposerRefs(id)
        db.trackDao().deleteLyricistRefs(id)
        insertJunctions(id, processed)
    }

    /**
     * Used by Config Changes to regenerate junctions without touching the main track data.
     */
    suspend fun regenerateJunctions(
        trackId: Long,
        rawArtist: String,
        rawComposer: String,
        rawLyricist: String,
        rawGenre: String
    ) {
        val newArtists = splitter.split(rawArtist)
        db.trackDao().deleteArtistRefs(trackId)
        val artistRefs = newArtists.map { name ->
            val id = db.artistDao().insertOrGetId(name)
            TrackArtistRef(trackId, id)
        }
        if (artistRefs.isNotEmpty()) db.trackDao().insertArtistRefs(artistRefs)

        val newComposers = splitter.split(rawComposer)
        db.trackDao().deleteComposerRefs(trackId)
        val composerRefs = newComposers.map { name ->
            val id = db.composerDao().insertOrGetId(name)
            TrackComposerRef(trackId, id)
        }
        if (composerRefs.isNotEmpty()) db.trackDao().insertComposerRefs(composerRefs)

        val newLyricists = splitter.split(rawLyricist)
        db.trackDao().deleteLyricistRefs(trackId)
        val lyricistRefs = newLyricists.map { name ->
            val id = db.lyricistDao().insertOrGetId(name)
            TrackLyricistRef(trackId, id)
        }
        if (lyricistRefs.isNotEmpty()) db.trackDao().insertLyricistRefs(lyricistRefs)

        val newGenres = splitter.split(rawGenre)
        db.trackDao().deleteGenreRefs(trackId)
        val genreRefs = newGenres.map { name ->
            val id = db.genreDao().insertOrGetId(name)
            TrackGenreRef(trackId, id)
        }
        if (genreRefs.isNotEmpty()) db.trackDao().insertGenreRefs(genreRefs)
    }

    suspend fun performGarbageCollection() {
        db.albumDao().deleteEmptyAlbums()
        db.artistDao().deleteEmptyArtists()
        db.genreDao().deleteEmptyGenres()
        db.composerDao().deleteEmptyComposers()
        db.lyricistDao().deleteEmptyLyricists()
    }

    // --- INTERNALS ---

    private suspend fun insertJunctions(trackId: Long, data: ProcessedData) {
        val artistRefs = data.trackArtists.map { name ->
            val artistId = db.artistDao().insertOrGetId(name)
            TrackArtistRef(trackId, artistId)
        }
        if (artistRefs.isNotEmpty()) db.trackDao().insertArtistRefs(artistRefs)

        val composerRefs = data.trackComposers.map { name ->
            val composerId = db.composerDao().insertOrGetId(name)
            TrackComposerRef(trackId, composerId)
        }
        if (composerRefs.isNotEmpty()) db.trackDao().insertComposerRefs(composerRefs)

        val lyricistRefs = data.trackLyricists.map { name ->
            val lyricistId = db.lyricistDao().insertOrGetId(name)
            TrackLyricistRef(trackId, lyricistId)
        }
        if (lyricistRefs.isNotEmpty()) db.trackDao().insertLyricistRefs(lyricistRefs)

        val genreRefs = data.trackGenres.map { name ->
            val genreId = db.genreDao().insertOrGetId(name)
            TrackGenreRef(trackId, genreId)
        }
        if (genreRefs.isNotEmpty()) db.trackDao().insertGenreRefs(genreRefs)
    }

    private data class ProcessedData(
        val folderName: String,
        val folderPath: String,
        val rawComposer: String,
        val rawAlbum: String,
        val finalYear: Int,
        val finalReleaseDate: String,
        val finalTrackNumber: Int,
        val trackArtists: List<String>,
        val trackComposers: List<String>,
        val trackGenres: List<String>,
        val trackLyricists: List<String>,
        val validTitle: String,
        val validArtist: String,
        val validAlbum: String,
        val validGenre: String
    )

    private suspend fun processCommonFields(
        skeleton: SkeletonTrack,
        extendedMeta: ExtendedMetadata,
        trustTagLibFirst: Boolean = false
    ): Triple<ProcessedData, String, String> {

        // TagLib is the source of truth. MediaStore values are used as fallback only when
        // trustTagLibFirst is false (normal scan). When true (post-edit rescan), we let fields
        // be genuinely empty if the user stripped them.
        val validTitle = if (extendedMeta.foundTitle.isNotBlank()) {
            extendedMeta.foundTitle
        } else if (trustTagLibFirst) {
            try {
                java.io.File(skeleton.path).nameWithoutExtension
            } catch (e: Exception) {
                "Unknown"
            }
        } else {
            skeleton.title
        }

        val validArtist = if (extendedMeta.foundArtist.isNotBlank()) {
            extendedMeta.foundArtist
        } else if (trustTagLibFirst) {
            ""
        } else {
            skeleton.artist
        }

        val validAlbum = if (extendedMeta.foundAlbum.isNotBlank()) {
            extendedMeta.foundAlbum
        } else if (trustTagLibFirst) {
            ""
        } else {
            skeleton.album
        }

        val validGenre = if (extendedMeta.foundGenre.isNotBlank()) {
            extendedMeta.foundGenre
        } else if (trustTagLibFirst) {
            ""
        } else {
            skeleton.genre
        }

        val rawComposer = if (extendedMeta.foundComposer.isNotBlank()) extendedMeta.foundComposer else skeleton.composer
        val finalYear = if (extendedMeta.foundYear > 0) extendedMeta.foundYear else skeleton.year
        val finalReleaseDate = if (extendedMeta.foundReleaseDate.isNotBlank()) extendedMeta.foundReleaseDate else if (finalYear > 0) finalYear.toString() else ""
        val finalTrackNumber = if (extendedMeta.foundTrackNumber > 0) extendedMeta.foundTrackNumber else skeleton.trackNumber

        val trackArtists = splitter.split(validArtist)
        val trackComposers = splitter.split(rawComposer)
        val trackGenres = splitter.split(validGenre)
        val trackLyricists = splitter.split(extendedMeta.foundLyricist)

        val parentFile = java.io.File(skeleton.path).parentFile
        val folderName = parentFile?.name ?: "Unknown"
        val folderPath = parentFile?.absolutePath ?: ""

        val rawAlbumArtist = if (extendedMeta.foundAlbumArtist.isNotBlank()) {
            extendedMeta.foundAlbumArtist
        } else if (skeleton.albumArtist.isNotBlank()) {
            skeleton.albumArtist
        } else {
            ""
        }

        val finalAlbumArtistName = if (rawAlbumArtist.isNotBlank()) rawAlbumArtist else ""

        val rawAlbum = extendedMeta.foundAlbum

        return Triple(
            ProcessedData(
                folderName = folderName,
                folderPath = folderPath,
                rawComposer = rawComposer,
                rawAlbum = rawAlbum,
                finalYear = finalYear,
                finalReleaseDate = finalReleaseDate,
                finalTrackNumber = finalTrackNumber,
                trackArtists = trackArtists,
                trackComposers = trackComposers,
                trackGenres = trackGenres,
                trackLyricists = trackLyricists,
                validTitle = validTitle,
                validArtist = validArtist,
                validAlbum = validAlbum,
                validGenre = validGenre
            ),
            finalAlbumArtistName,
            rawAlbumArtist
        )
    }
}
