package aman.catalog.audio.internal.database.daos

import androidx.room.*
import aman.catalog.audio.internal.database.entities.PlaylistEntity
import aman.catalog.audio.internal.database.entities.PlaylistEntryEntity
import aman.catalog.audio.internal.database.entities.TrackEntity
import aman.catalog.audio.internal.database.entities.TrackArtistRef
import aman.catalog.audio.internal.database.entities.ArtistEntity
import aman.catalog.audio.internal.database.entities.TrackGenreRef
import aman.catalog.audio.internal.database.entities.GenreEntity
import aman.catalog.audio.internal.database.entities.TrackComposerRef
import aman.catalog.audio.internal.database.entities.ComposerEntity
import aman.catalog.audio.internal.database.entities.TrackLyricistRef
import aman.catalog.audio.internal.database.entities.LyricistEntity
import aman.catalog.audio.internal.database.entities.AlbumEntity
import aman.catalog.audio.internal.database.entities.FavoritesEntity
import aman.catalog.audio.internal.database.relations.TrackWithRelations
import kotlinx.coroutines.flow.Flow

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.ContextualSortOption

@Dao
abstract class PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :newName, dateModified = :timestamp WHERE id = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Long, newName: String, timestamp: Long)

    // ------------------------------------
    //  DYNAMIC SORTING: PLAYLISTS
    // ------------------------------------

    @Transaction
    @RawQuery(
        observedEntities = [
            PlaylistEntity::class,
            PlaylistEntryEntity::class,
            TrackEntity::class
        ]
    )
    abstract fun getPlaylistsSortedRaw(query: SupportSQLiteQuery): Flow<List<PlaylistWithCount>>

    fun getPlaylistsSorted(sort: SortOption.Playlist = SortOption.Playlist.NAME_ASC): Flow<List<PlaylistWithCount>> {
        val sql = """
            SELECT 
                playlists.*, 
                COUNT(playlist_entries.id) as trackCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified
            FROM playlists 
            LEFT JOIN playlist_entries ON playlists.id = playlist_entries.playlistId 
            LEFT JOIN tracks ON playlist_entries.trackId = tracks.id
            GROUP BY playlists.id 
            ORDER BY ${sort.sqlString}
        """
        return getPlaylistsSortedRaw(SimpleSQLiteQuery(sql))
    }

    data class PlaylistWithCount(
        @Embedded val playlist: PlaylistEntity,
        val trackCount: Int,
        val totalDuration: Long,
        val coverArtPath: String?,
        val coverArtDateModified: Long
    )

    @Query("""
        SELECT 
            playlists.*, 
            COUNT(playlist_entries.id) as trackCount,
            COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified
        FROM playlists 
        LEFT JOIN playlist_entries ON playlists.id = playlist_entries.playlistId 
        LEFT JOIN tracks ON playlist_entries.trackId = tracks.id
        WHERE playlists.name LIKE '%' || :query || '%'
        GROUP BY playlists.id 
        ORDER BY playlists.name ASC
    """)
    abstract fun searchPlaylists(query: String): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEntry(entry: PlaylistEntryEntity)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun removeSongFromPlaylist(playlistId: Long, trackId: Long)

    @Query("SELECT playlistId FROM playlist_entries WHERE id = :entryId")
    abstract suspend fun getPlaylistIdForEntry(entryId: Long): Long?


    @Query("SELECT * FROM playlist_entries WHERE id = :entryId")
    protected abstract suspend fun getEntryById(entryId: Long): PlaylistEntryEntity?

    @Query("DELETE FROM playlist_entries WHERE id = :entryId")
    protected abstract suspend fun deleteEntryRaw(entryId: Long)

    @Query("UPDATE playlist_entries SET sortOrder = sortOrder - 1 WHERE playlistId = :playlistId AND sortOrder > :deletedOrder")
    protected abstract suspend fun shiftSortOrders(playlistId: Long, deletedOrder: Int)

    @Transaction
    open suspend fun removeEntryById(entryId: Long) {
        val entry = getEntryById(entryId) ?: return
        deleteEntryRaw(entryId)
        shiftSortOrders(entry.playlistId, entry.sortOrder)
        updateModifiedDate(entry.playlistId, System.currentTimeMillis())
    }

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_entries WHERE playlistId = :playlistId")
    abstract suspend fun getNextSortOrder(playlistId: Long): Int

    @Transaction
    open suspend fun addSongToPlaylist(playlistId: Long, trackId: Long) {
        val order = getNextSortOrder(playlistId)
        insertEntry(PlaylistEntryEntity(playlistId = playlistId, trackId = trackId, sortOrder = order))
        updateModifiedDate(playlistId, System.currentTimeMillis())
    }

    @Query("UPDATE playlist_entries SET sortOrder = :newOrder WHERE id = :entryId")
    abstract suspend fun updateSortOrder(entryId: Long, newOrder: Int)

    @Transaction
    open suspend fun reorderPlaylist(playlistId: Long, updates: Map<Long, Int>) {
        updates.forEach { (entryId, newOrder) ->
            updateSortOrder(entryId, newOrder)
        }
        updateModifiedDate(playlistId, System.currentTimeMillis())
    }


    @Query("UPDATE playlist_entries SET sortOrder = sortOrder - 1 WHERE playlistId = :playlistId AND sortOrder > :fromPosition AND sortOrder <= :toPosition")
    protected abstract suspend fun shiftUpForMove(playlistId: Long, fromPosition: Int, toPosition: Int)

    @Query("UPDATE playlist_entries SET sortOrder = sortOrder + 1 WHERE playlistId = :playlistId AND sortOrder >= :toPosition AND sortOrder < :fromPosition")
    protected abstract suspend fun shiftDownForMove(playlistId: Long, fromPosition: Int, toPosition: Int)

    @Transaction
    open suspend fun movePlaylistEntry(playlistId: Long, entryId: Long, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        updateSortOrder(entryId, -1)
        if (fromPosition < toPosition) {
            shiftUpForMove(playlistId, fromPosition, toPosition)
        } else {
            shiftDownForMove(playlistId, fromPosition, toPosition)
        }
        updateSortOrder(entryId, toPosition)
        updateModifiedDate(playlistId, System.currentTimeMillis())
    }

    @Query("UPDATE playlists SET dateModified = :timestamp WHERE id = :playlistId")
    abstract suspend fun updateModifiedDate(playlistId: Long, timestamp: Long)

    data class PlaylistEntryWithRichTrack(
        @Embedded val entry: PlaylistEntryEntity,
        @Relation(
            entity = TrackEntity::class,
            parentColumn = "trackId",
            entityColumn = "id"
        )
        val track: TrackWithRelations
    )

    @Transaction
    @RawQuery(
        observedEntities = [
            PlaylistEntryEntity::class,
            TrackEntity::class,
            TrackArtistRef::class,
            ArtistEntity::class,
            TrackGenreRef::class,
            GenreEntity::class,
            TrackComposerRef::class,
            ComposerEntity::class,
            TrackLyricistRef::class,
            LyricistEntity::class,
            AlbumEntity::class,
            FavoritesEntity::class
        ]
    )
    protected abstract fun getTracksForPlaylistRaw(query: SupportSQLiteQuery): Flow<List<PlaylistEntryWithRichTrack>>

    fun getTracksForPlaylist(
        playlistId: Long,
        sort: ContextualSortOption.PlaylistTrack = ContextualSortOption.PlaylistTrack.USER_DEFINED
    ): Flow<List<PlaylistEntryWithRichTrack>> {
        val sql = """
            SELECT playlist_entries.* FROM playlist_entries
            INNER JOIN tracks ON playlist_entries.trackId = tracks.id
            WHERE playlist_entries.playlistId = $playlistId
            ORDER BY ${sort.sqlString}
        """
        return getTracksForPlaylistRaw(SimpleSQLiteQuery(sql))
    }

    @Query("""
        SELECT tracks.path 
        FROM playlist_entries 
        INNER JOIN tracks ON playlist_entries.trackId = tracks.id 
        WHERE playlistId = :playlistId 
        ORDER BY sortOrder ASC
    """)
    abstract suspend fun getPlaylistPaths(playlistId: Long): List<String>

    @Query("""
        SELECT 
            playlists.*,
            COUNT(playlist_entries.id) as trackCount,
            COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified
        FROM playlists
        LEFT JOIN playlist_entries ON playlists.id = playlist_entries.playlistId
        LEFT JOIN tracks ON playlist_entries.trackId = tracks.id
        WHERE playlists.id = :playlistId
        GROUP BY playlists.id
    """)
    abstract fun observePlaylistById(playlistId: Long): Flow<PlaylistWithCount?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    abstract suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (lazy, on-demand)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN playlist_entries pe ON t.id = pe.trackId
        WHERE pe.playlistId = :playlistId AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract suspend fun getMosaicPathsForPlaylist(playlistId: Long, limit: Int): List<ArtPath>

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (live, for detail screens)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN playlist_entries pe ON t.id = pe.trackId
        WHERE pe.playlistId = :playlistId AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract fun getMosaicPathsForPlaylistFlow(playlistId: Long, limit: Int): Flow<List<ArtPath>>
}
