package aman.catalog.audio

import aman.catalog.audio.internal.ModelMapper
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.database.daos.TrackDao
import aman.catalog.audio.internal.database.entities.PlaylistEntity
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.Playlist
import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.PlaylistItem
import aman.catalog.audio.models.ContextualSortOption
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages all Playlist-related operations for the Catalog library.
 * Accessible via [Catalog.playlists].
 */
class CatalogPlaylists internal constructor(
    private val databaseProvider: () -> CatalogDatabase
) {
    private val database: CatalogDatabase
        get() = databaseProvider()

    // ----------------------------------------
    // PLAYLIST API
    // ----------------------------------------

    /** Live stream of all playlists, sorted by [sort], each including their track count and total duration. */
    fun getPlaylists(sort: SortOption.Playlist = SortOption.Playlist.NAME_ASC): Flow<List<Playlist>> {
        return database.playlistDao().getPlaylistsSorted(sort)
            .distinctUntilChanged()
            .map { entities ->
                entities.map { ModelMapper.toPlaylist(it.playlist, it.trackCount, it.totalDuration, it.coverArtPath?.let { p -> ArtPath(p, it.coverArtDateModified) }) }
            }
            .flowOn(Dispatchers.Default)
    }

    /** Live stream of a single playlist by ID, including track count and cover art. */
    fun getPlaylistById(playlistId: Long): Flow<Playlist?> {
        return database.playlistDao().observePlaylistById(playlistId)
            .distinctUntilChanged()
            .map { it?.let { data -> ModelMapper.toPlaylist(data.playlist, data.trackCount, data.totalDuration, data.coverArtPath?.let { ArtPath(it, data.coverArtDateModified) }) } }
            .flowOn(Dispatchers.Default)
    }

    /**
     * Live stream of all tracks within a specific playlist, sorted by [sort].
     * Each item includes the [PlaylistItem.entryId] needed for reordering and removal.
     * Defaults to [ContextualSortOption.PlaylistTrack.USER_DEFINED] — the order the user dragged them into.
     */
    fun getPlaylistTracks(
        playlistId: Long,
        sort: ContextualSortOption.PlaylistTrack = ContextualSortOption.PlaylistTrack.USER_DEFINED
    ): Flow<List<PlaylistItem>> {
        return database.playlistDao().getTracksForPlaylist(playlistId, sort)
            .distinctUntilChanged()
            .map { entries ->
                entries.map { item ->
                    PlaylistItem(
                        entryId = item.entry.id,
                        sortOrder = item.entry.sortOrder,
                        track = ModelMapper.toTrack(item.track)
                    )
                }
            }
            .flowOn(Dispatchers.Default)
    }

    /** Creates a new empty playlist with the given name. */
    suspend fun createPlaylist(name: String): Long {
        require(name.isNotBlank()) { "Playlist name cannot be blank." }
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.playlistDao().createPlaylist(
                PlaylistEntity(name = name.trim(), dateCreated = now, dateModified = now)
            )
        }
    }

    /** Permanently deletes a playlist and all its entries. */
    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            database.playlistDao().deletePlaylist(playlistId)
        }
    }

    /** Renames an existing playlist. */
    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        require(newName.isNotBlank()) { "Playlist name cannot be blank." }
        withContext(Dispatchers.IO) {
            database.playlistDao().renamePlaylist(playlistId, newName.trim(), System.currentTimeMillis())
        }
    }

    /** Adds a track to a playlist. Silently does nothing if the track no longer exists. */
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val trackExists = database.trackDao().getTrackById(trackId) != null
            if (trackExists) {
                database.playlistDao().addSongToPlaylist(playlistId, trackId)
                true
            } else {
                false
            }
        }
    }

    /** Removes a single entry from a playlist by its entry ID. */
    suspend fun removeEntryFromPlaylist(entryId: Long) {
        withContext(Dispatchers.IO) {
            database.playlistDao().removeEntryById(entryId)
        }
    }

    /** Applies a new sort order to playlist entries in bulk. */
    suspend fun reorderPlaylist(playlistId: Long, updates: Map<Long, Int>) {
        withContext(Dispatchers.IO) { database.playlistDao().reorderPlaylist(playlistId, updates) }
    }

    /**
     * Moves a single entry within a playlist to a new position.
     * Highly optimized for UI drag-and-drop operations.
     */
    suspend fun moveEntryInPlaylist(playlistId: Long, entryId: Long, fromPosition: Int, toPosition: Int) {
        withContext(Dispatchers.IO) {
            database.playlistDao().movePlaylistEntry(playlistId, entryId, fromPosition, toPosition)
        }
    }

    /**
     * Returns up to [limit] distinct cover art paths for the given playlist.
     * Intended for use by Coil or any other image loader to draw a mosaic.
     */
    suspend fun getMosaicForPlaylist(playlistId: Long, limit: Int): List<ArtPath> {
        return withContext(Dispatchers.IO) {
            database.playlistDao().getMosaicPathsForPlaylist(playlistId, limit)
        }
    }

    /** Live stream of up to [limit] distinct cover art paths for the given playlist. */
    fun getMosaicForPlaylistFlow(playlistId: Long, limit: Int): Flow<List<ArtPath>> =
        database.playlistDao().getMosaicPathsForPlaylistFlow(playlistId, limit)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // --- EXPORT / IMPORT PLAYLIST ---

    /** Exports a playlist to an M3U file at the given path. */
    suspend fun exportPlaylist(playlistId: Long, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlist = database.playlistDao().getPlaylistById(playlistId) ?: return@withContext false
            val paths = database.playlistDao().getPlaylistPaths(playlistId)
            file.bufferedWriter().use { out ->
                out.write("#EXTM3U")
                out.newLine()
                out.write("#PLAYLIST:${playlist.name}")
                out.newLine()
                paths.forEach { path ->
                    out.write(path)
                    out.newLine()
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /** Imports an M3U playlist file, matching each path against tracks already in the database. */
    suspend fun importPlaylist(file: File): Long = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext -1L

        var parsedName: String? = null
        val pathsToImport = mutableListOf<String>()

        try {
            file.forEachLine { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#PLAYLIST:") ->
                        parsedName = trimmed.removePrefix("#PLAYLIST:").trim()
                    trimmed.isNotBlank() && !trimmed.startsWith("#") ->
                        pathsToImport.add(trimmed)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext -1L
        }

        val playlistName = parsedName?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension

        if (pathsToImport.isEmpty()) return@withContext -1L

        val matchedPairs = mutableListOf<TrackDao.PathIdPair>()
        pathsToImport.chunked(900).forEach { chunk ->
            matchedPairs.addAll(database.trackDao().getTrackIdsByPaths(chunk))
        }

        val pathToIdMap = matchedPairs.associate { it.path to it.id }

        if (matchedPairs.isEmpty()) return@withContext 0L

        var finalPlaylistId = -1L

        database.withTransaction {
            val now = System.currentTimeMillis()
            finalPlaylistId = database.playlistDao().createPlaylist(
                PlaylistEntity(name = playlistName, dateCreated = now, dateModified = now)
            )
            pathsToImport.forEach { path ->
                val id = pathToIdMap[path]
                if (id != null) {
                    database.playlistDao().addSongToPlaylist(finalPlaylistId, id)
                }
            }
        }

        return@withContext finalPlaylistId
    }
}
