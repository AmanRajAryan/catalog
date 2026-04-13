package aman.catalog.audio.internal.database.daos

import aman.catalog.audio.internal.database.entities.TrackEntity
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.SortOption
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class YearDao {

    data class YearCount(
        val year: Int,
        val count: Int,
        val albumCount: Int,
        val totalDuration: Long,
        val coverArtPath: String?,
        val coverArtDateModified: Long,
        val playCount: Int,
        val lastPlayed: Long,
        val totalPlayTimeMs: Long
    )

    // ------------------------------------
    //  DYNAMIC SORTING
    // ------------------------------------

    @RawQuery(observedEntities = [TrackEntity::class])
    abstract fun getYearsSortedRaw(query: SupportSQLiteQuery): Flow<List<YearCount>>

    fun getYearsSorted(sort: SortOption.Year = SortOption.Year.YEAR_DESC): Flow<List<YearCount>> {
        val sql = """
            SELECT 
                tracks.year,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            GROUP BY tracks.year
            ORDER BY ${sort.sqlString}
        """
        return getYearsSortedRaw(SimpleSQLiteQuery(sql))
    }

    fun searchYears(query: String): Flow<List<YearCount>> {
        val sql = """
            SELECT 
                tracks.year,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            WHERE CAST(tracks.year AS TEXT) LIKE '%' || ? || '%'
            GROUP BY tracks.year
            ORDER BY tracks.year DESC
        """
        return getYearsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
    }

    fun getRecentlyPlayedYears(limit: Int): Flow<List<YearCount>> {
        val sql = """
            SELECT 
                tracks.year,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            WHERE tracks.year > 0
            GROUP BY tracks.year
            HAVING MAX(tracks.lastPlayed) > 0
            ORDER BY lastPlayed DESC LIMIT ?
        """
        return getYearsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    fun getMostPlayedYears(limit: Int): Flow<List<YearCount>> {
        val sql = """
            SELECT 
                tracks.year,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            WHERE tracks.year > 0
            GROUP BY tracks.year
            HAVING SUM(tracks.playCount) > 0
            ORDER BY playCount DESC LIMIT ?
        """
        return getYearsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    @RawQuery(observedEntities = [TrackEntity::class])
    abstract fun getYearByValueRaw(query: SupportSQLiteQuery): Flow<List<YearCount>>

    fun getYearByValue(year: Int): Flow<YearCount?> {
        val sql = """
            SELECT 
                tracks.year,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            WHERE tracks.year = ?
            GROUP BY tracks.year
        """
        val raw: Flow<List<YearCount>> = getYearByValueRaw(SimpleSQLiteQuery(sql, arrayOf(year)))
        return raw.map { list -> list.firstOrNull() }
    }

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (lazy, on-demand)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        WHERE t.year = :year AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract suspend fun getMosaicPathsForYear(year: Int, limit: Int): List<ArtPath>

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (live, for detail screens)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        WHERE t.year = :year AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract fun getMosaicPathsForYearFlow(year: Int, limit: Int): Flow<List<ArtPath>>
}
