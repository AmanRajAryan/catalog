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
abstract class LyricistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(lyricist: LyricistEntity): Long

    @Query("SELECT id FROM lyricists WHERE name = :name")
    protected abstract suspend fun getLyricistId(name: String): Long?

    // ------------------------------------
    //  DYNAMIC SORTING
    // ------------------------------------

    @Transaction
    @RawQuery(
        observedEntities = [
            LyricistEntity::class,
            TrackLyricistRef::class,
            TrackEntity::class,
            AlbumEntity::class
        ]
    )
    abstract fun getLyricistsSortedRaw(query: SupportSQLiteQuery): Flow<List<LyricistWithCount>>

    fun getLyricistsSorted(sort: SortOption.Lyricist = SortOption.Lyricist.NAME_ASC): Flow<List<LyricistWithCount>> {
        val sql = """
            SELECT 
                lyricists.id, lyricists.name, COUNT(track_lyricists.trackId) as trackCount, COUNT(DISTINCT tracks.albumId) as albumCount, 
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                lyricists.playCount, lyricists.lastPlayed, lyricists.totalPlayTimeMs 
            FROM lyricists 
            LEFT JOIN track_lyricists ON lyricists.id = track_lyricists.lyricistId 
            LEFT JOIN tracks ON track_lyricists.trackId = tracks.id
            GROUP BY lyricists.id 
            HAVING trackCount > 0
            ORDER BY ${sort.sqlString}
        """
        return getLyricistsSortedRaw(SimpleSQLiteQuery(sql))
    }

    data class LyricistWithCount(
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

    fun getLyricistById(id: Long): Flow<LyricistWithCount?> {
        val sql = """
            SELECT 
                lyricists.id, lyricists.name,
                COUNT(track_lyricists.trackId) as trackCount,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                lyricists.playCount, lyricists.lastPlayed, lyricists.totalPlayTimeMs
            FROM lyricists
            LEFT JOIN track_lyricists ON lyricists.id = track_lyricists.lyricistId
            LEFT JOIN tracks ON track_lyricists.trackId = tracks.id
            WHERE lyricists.id = ?
            GROUP BY lyricists.id
        """
        return getLyricistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(id)))
            .map { it.firstOrNull() }
    }

    fun searchLyricists(query: String): Flow<List<LyricistWithCount>> {
        val sql = """
            SELECT
                lyricists.id, lyricists.name,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COUNT(track_lyricists.trackId) as trackCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                lyricists.playCount, lyricists.lastPlayed, lyricists.totalPlayTimeMs
            FROM lyricists
            LEFT JOIN track_lyricists ON lyricists.id = track_lyricists.lyricistId
            LEFT JOIN tracks ON track_lyricists.trackId = tracks.id
            WHERE lyricists.name LIKE '%' || ? || '%'
            GROUP BY lyricists.id
            HAVING trackCount > 0
            ORDER BY lyricists.name ASC
        """
        return getLyricistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
    }

    fun getRecentlyPlayedLyricists(limit: Int): Flow<List<LyricistWithCount>> {
        val sql = """
            SELECT
                lyricists.id, lyricists.name,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COUNT(track_lyricists.trackId) as trackCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                lyricists.playCount, lyricists.lastPlayed, lyricists.totalPlayTimeMs
            FROM lyricists
            LEFT JOIN track_lyricists ON lyricists.id = track_lyricists.lyricistId
            LEFT JOIN tracks ON track_lyricists.trackId = tracks.id
            WHERE lyricists.lastPlayed > 0
            GROUP BY lyricists.id
            HAVING trackCount > 0
            ORDER BY lyricists.lastPlayed DESC LIMIT ?
        """
        return getLyricistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    fun getMostPlayedLyricists(limit: Int): Flow<List<LyricistWithCount>> {
        val sql = """
            SELECT
                lyricists.id, lyricists.name,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COUNT(track_lyricists.trackId) as trackCount,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                lyricists.playCount, lyricists.lastPlayed, lyricists.totalPlayTimeMs
            FROM lyricists
            LEFT JOIN track_lyricists ON lyricists.id = track_lyricists.lyricistId
            LEFT JOIN tracks ON track_lyricists.trackId = tracks.id
            WHERE lyricists.playCount > 0
            GROUP BY lyricists.id
            HAVING trackCount > 0
            ORDER BY lyricists.playCount DESC LIMIT ?
        """
        return getLyricistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    @Transaction
    open suspend fun insertOrGetId(name: String): Long {
        val existingId = getLyricistId(name)
        if (existingId != null) return existingId
        val newId = insert(LyricistEntity(name = name))
        return if (newId == -1L) getLyricistId(name)!! else newId
    }

    @Query("DELETE FROM lyricists WHERE id NOT IN (SELECT lyricistId FROM track_lyricists)")
    abstract suspend fun deleteEmptyLyricists()

    // ------------------------------------------------------------------------
    // PLAYBACK STATS ENGINE
    // ------------------------------------------------------------------------

    @Query("UPDATE lyricists SET playCount = playCount + 1, lastPlayed = :timestamp, totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id")
    abstract suspend fun incrementPlayStats(id: Long, timestamp: Long, durationMs: Long)

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (lazy, on-demand)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN track_lyricists tl ON t.id = tl.trackId
        WHERE tl.lyricistId = :lyricistId AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract suspend fun getMosaicPathsForLyricist(lyricistId: Long, limit: Int): List<ArtPath>

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (live, for detail screens)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN track_lyricists tl ON t.id = tl.trackId
        WHERE tl.lyricistId = :lyricistId AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract fun getMosaicPathsForLyricistFlow(lyricistId: Long, limit: Int): Flow<List<ArtPath>>
}
