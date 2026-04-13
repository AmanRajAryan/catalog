package aman.catalog.audio

import aman.catalog.audio.internal.ModelMapper
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.models.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages playback tracking and historical/statistical data flows (Recently Played, Most Played).
 * Accessible via [Catalog.stats].
 */
class CatalogStats internal constructor(
    private val databaseProvider: () -> CatalogDatabase
) {
    private val database: CatalogDatabase
        get() = databaseProvider()

    // ----------------------------------------
    // PLAYBACK TRACKING
    // ----------------------------------------

    /**
     * Records a playback event for a track, incrementing its play count and updating
     * its last-played timestamp. Also recursively updates the stats for its Album,
     * Artists, Genres, Composers, and Lyricists in a single atomic transaction.
     *
     * @param trackId The ID of the track that was played.
     * @param durationMs How long the track was played for, in milliseconds.
     * @return `true` if the track was found and the count was incremented, `false` otherwise.
     */
    suspend fun logPlayback(trackId: Long, durationMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val container = database.trackDao().getTrackById(trackId)
            if (container != null) {
                val timestamp = System.currentTimeMillis()

                database.withTransaction {
                    // 1. Update the Track
                    database.trackDao().incrementPlayCount(trackId, timestamp, durationMs)

                    // Track which artist IDs we've updated to prevent double-counting 
                    // if someone is BOTH the Album Artist and a Track Artist.
                    val updatedArtistIds = mutableSetOf<Long>()

                    // 2. Update the Album & Album Artist
                    container.album?.let { album ->
                        database.albumDao().incrementPlayStats(album.id, timestamp, durationMs)

                        if (album.albumArtistId != null) {
                            database.artistDao().incrementPlayStats(album.albumArtistId, timestamp, durationMs)
                            updatedArtistIds.add(album.albumArtistId)
                        }
                    }

                    // 3. Update Track Artists (Safely)
                    container.artists.forEach { artist ->
                        if (!updatedArtistIds.contains(artist.id)) {
                            database.artistDao().incrementPlayStats(artist.id, timestamp, durationMs)
                            updatedArtistIds.add(artist.id)
                        }
                    }

                    // 4. Update the rest
                    container.genres.forEach { genre ->
                        database.genreDao().incrementPlayStats(genre.id, timestamp, durationMs)
                    }
                    container.composers.forEach { composer ->
                        database.composerDao().incrementPlayStats(composer.id, timestamp, durationMs)
                    }
                    container.lyricists.forEach { lyricist ->
                        database.lyricistDao().incrementPlayStats(lyricist.id, timestamp, durationMs)
                    }
                }
                true
            } else {
                false
            }
        }
    }

    /**
     * Records a skip event for a track, incrementing its skip count.
     *
     * @param trackId The ID of the track that was skipped.
     * @return `true` if the track was found and the count was incremented, `false` otherwise.
     */
    suspend fun logSkip(trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val exists = database.trackDao().getTrackById(trackId) != null
            if (exists) {
                database.trackDao().incrementSkipCount(trackId)
                true
            } else {
                false
            }
        }
    }

    // ----------------------------------------
    // RECENTLY PLAYED API
    // ----------------------------------------

    /** Live stream of tracks ordered by most recently played. */
    fun getRecentlyPlayedTracks(limit: Int = 50): Flow<List<Track>> {
        return database.trackDao().getRecentlyPlayed(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
    }

    /** Live stream of albums ordered by most recently played. */
    fun getRecentlyPlayedAlbums(limit: Int = 20): Flow<List<Album>> =
        database.albumDao().getRecentlyPlayedAlbums(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toAlbum(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of track artists ordered by most recently played. */
    fun getRecentlyPlayedArtists(limit: Int = 20): Flow<List<Artist>> =
        database.artistDao().getRecentlyPlayedArtists(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of album artists ordered by most recently played. */
    fun getRecentlyPlayedAlbumArtists(limit: Int = 20): Flow<List<Artist>> =
        database.artistDao().getRecentlyPlayedAlbumArtists(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of genres ordered by most recently played. */
    fun getRecentlyPlayedGenres(limit: Int = 20): Flow<List<Genre>> =
        database.genreDao().getRecentlyPlayedGenres(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toGenre(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of composers ordered by most recently played. */
    fun getRecentlyPlayedComposers(limit: Int = 20): Flow<List<Composer>> =
        database.composerDao().getRecentlyPlayedComposers(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toComposer(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of lyricists ordered by most recently played. */
    fun getRecentlyPlayedLyricists(limit: Int = 20): Flow<List<Lyricist>> =
        database.lyricistDao().getRecentlyPlayedLyricists(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toLyricist(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of years ordered by most recently played. */
    fun getRecentlyPlayedYears(limit: Int = 20): Flow<List<Year>> =
        database.yearDao().getRecentlyPlayedYears(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toYear(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of folders ordered by most recently played. */
    fun getRecentlyPlayedFolders(limit: Int = 20): Flow<List<Folder>> =
        database.folderDao().getRecentlyPlayedFolders(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toFolder(it) } }
            .flowOn(Dispatchers.Default)

    // ----------------------------------------
    // MOST PLAYED API
    // ----------------------------------------

    /** Live stream of tracks ordered by highest play count. */
    fun getMostPlayedTracks(limit: Int = 50): Flow<List<Track>> {
        return database.trackDao().getMostPlayed(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
    }

    /** Live stream of albums ordered by highest play count. */
    fun getMostPlayedAlbums(limit: Int = 20): Flow<List<Album>> =
        database.albumDao().getMostPlayedAlbums(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toAlbum(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of track artists ordered by highest play count. */
    fun getMostPlayedArtists(limit: Int = 20): Flow<List<Artist>> =
        database.artistDao().getMostPlayedArtists(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of album artists ordered by highest play count. */
    fun getMostPlayedAlbumArtists(limit: Int = 20): Flow<List<Artist>> =
        database.artistDao().getMostPlayedAlbumArtists(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of genres ordered by highest play count. */
    fun getMostPlayedGenres(limit: Int = 20): Flow<List<Genre>> =
        database.genreDao().getMostPlayedGenres(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toGenre(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of composers ordered by highest play count. */
    fun getMostPlayedComposers(limit: Int = 20): Flow<List<Composer>> =
        database.composerDao().getMostPlayedComposers(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toComposer(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of lyricists ordered by highest play count. */
    fun getMostPlayedLyricists(limit: Int = 20): Flow<List<Lyricist>> =
        database.lyricistDao().getMostPlayedLyricists(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toLyricist(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of years ordered by highest play count. */
    fun getMostPlayedYears(limit: Int = 20): Flow<List<Year>> =
        database.yearDao().getMostPlayedYears(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toYear(it) } }
            .flowOn(Dispatchers.Default)

    /** Live stream of folders ordered by highest play count. */
    fun getMostPlayedFolders(limit: Int = 20): Flow<List<Folder>> =
        database.folderDao().getMostPlayedFolders(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toFolder(it) } }
            .flowOn(Dispatchers.Default)
}
