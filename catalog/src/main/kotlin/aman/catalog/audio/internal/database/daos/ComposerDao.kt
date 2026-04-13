package aman.catalog.audio.internal.database.daos

import androidx.room.*
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.SortOption
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class ComposerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(composer: ComposerEntity): Long

    @Query("SELECT id FROM composers WHERE name = :name")
    protected abstract suspend fun getComposerId(name: String): Long?

    // ------------------------------------
    //  DYNAMIC SORTING
    // ------------------------------------

    @Transaction
    @RawQuery(
        observedEntities = [
            ComposerEntity::class,
            TrackComposerRef::class,
            TrackEntity::class,
            AlbumEntity::class
        ]
    )
    abstract fun getComposersSortedRaw(query: SupportSQLiteQuery): Flow<List<ComposerWithCount>>

    fun getComposersSorted(sort: SortOption.Composer = SortOption.Composer.NAME_ASC): Flow<List<ComposerWithCount>> {
        val sql = """
            SELECT 
                composers.id, composers.name, COUNT(track_composers.trackId) as trackCount, COUNT(DISTINCT tracks.albumId) as albumCount, 
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                composers.playCount, composers.lastPlayed, composers.totalPlayTimeMs 
            FROM composers 
            LEFT JOIN track_composers ON composers.id = track_composers.composerId 
            LEFT JOIN tracks ON track_composers.trackId = tracks.id
            GROUP BY composers.id 
            HAVING trackCount > 0
            ORDER BY ${sort.sqlString}
        """
        return getComposersSortedRaw(SimpleSQLiteQuery(sql))
    }

    data class ComposerWithCount(
        val id: Long,
        val name: String,
        val trackCount: Int,
        val albumCount: Int,
        val totalDuration: Long,
        val coverArtPath: String?,
        val coverArtDateModified: Long,
        val playCount: Int,
        val lastPlayed: Long,
        val totalPlayTimeMs: Long
    )

    fun getComposerById(id: Long): Flow<ComposerWithCount?> {
        val sql = """
            SELECT 
                composers.id, composers.name,
                COUNT(track_composers.trackId) as trackCount,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                composers.playCount, composers.lastPlayed, composers.totalPlayTimeMs
            FROM composers
            LEFT JOIN track_composers ON composers.id = track_composers.composerId
            LEFT JOIN tracks ON track_composers.trackId = tracks.id
            WHERE composers.id = ?
            GROUP BY composers.id
        """
        return getComposersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(id)))
            .map { it.firstOrNull() }
    }

    fun searchComposers(query: String): Flow<List<ComposerWithCount>> {
        val sql = """
            SELECT
                composers.id, composers.name,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COUNT(track_composers.trackId) as trackCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                composers.playCount, composers.lastPlayed, composers.totalPlayTimeMs
            FROM composers
            LEFT JOIN track_composers ON composers.id = track_composers.composerId
            LEFT JOIN tracks ON track_composers.trackId = tracks.id
            WHERE composers.name LIKE '%' || ? || '%'
            GROUP BY composers.id
            HAVING trackCount > 0
            ORDER BY composers.name ASC
        """
        return getComposersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
    }

    fun getRecentlyPlayedComposers(limit: Int): Flow<List<ComposerWithCount>> {
        val sql = """
            SELECT
                composers.id, composers.name,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COUNT(track_composers.trackId) as trackCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                composers.playCount, composers.lastPlayed, composers.totalPlayTimeMs
            FROM composers
            LEFT JOIN track_composers ON composers.id = track_composers.composerId
            LEFT JOIN tracks ON track_composers.trackId = tracks.id
            WHERE composers.lastPlayed > 0
            GROUP BY composers.id
            HAVING trackCount > 0
            ORDER BY composers.lastPlayed DESC LIMIT ?
        """
        return getComposersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    fun getMostPlayedComposers(limit: Int): Flow<List<ComposerWithCount>> {
        val sql = """
            SELECT
                composers.id, composers.name,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COUNT(track_composers.trackId) as trackCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                composers.playCount, composers.lastPlayed, composers.totalPlayTimeMs
            FROM composers
            LEFT JOIN track_composers ON composers.id = track_composers.composerId
            LEFT JOIN tracks ON track_composers.trackId = tracks.id
            WHERE composers.playCount > 0
            GROUP BY composers.id
            HAVING trackCount > 0
            ORDER BY composers.playCount DESC LIMIT ?
        """
        return getComposersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    @Transaction
    open suspend fun insertOrGetId(name: String): Long {
        val existingId = getComposerId(name)
        if (existingId != null) return existingId
        val newId = insert(ComposerEntity(name = name))
        return if (newId == -1L) getComposerId(name)!! else newId
    }

    @Query("DELETE FROM composers WHERE id NOT IN (SELECT composerId FROM track_composers)")
    abstract suspend fun deleteEmptyComposers()

    // ------------------------------------------------------------------------
    // PLAYBACK STATS ENGINE
    // ------------------------------------------------------------------------

    @Query("UPDATE composers SET playCount = playCount + 1, lastPlayed = :timestamp, totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id")
    abstract suspend fun incrementPlayStats(id: Long, timestamp: Long, durationMs: Long)

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (lazy, on-demand)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN track_composers tc ON t.id = tc.trackId
        WHERE tc.composerId = :composerId AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract suspend fun getMosaicPathsForComposer(composerId: Long, limit: Int): List<ArtPath>

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (live, for detail screens)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN track_composers tc ON t.id = tc.trackId
        WHERE tc.composerId = :composerId AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract fun getMosaicPathsForComposerFlow(composerId: Long, limit: Int): Flow<List<ArtPath>>
}
