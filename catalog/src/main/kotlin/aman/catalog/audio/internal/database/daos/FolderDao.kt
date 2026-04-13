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
abstract class FolderDao {

    data class FolderCount(
        val name: String,
        val path: String,
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
    abstract fun getFoldersSortedRaw(query: SupportSQLiteQuery): Flow<List<FolderCount>>

    fun getFoldersSorted(sort: SortOption.Folder = SortOption.Folder.NAME_ASC): Flow<List<FolderCount>> {
        val sql = """
            SELECT 
                folderName as name, folderPath as path,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            GROUP BY tracks.folderPath, tracks.folderName
            ORDER BY ${sort.sqlString}
        """
        return getFoldersSortedRaw(SimpleSQLiteQuery(sql))
    }

    fun searchFolders(query: String): Flow<List<FolderCount>> {
        val sql = """
            SELECT 
                folderName as name, folderPath as path,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            WHERE tracks.folderName LIKE '%' || ? || '%'
            GROUP BY tracks.folderPath, tracks.folderName
            ORDER BY name ASC
        """
        return getFoldersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
    }

    fun getRecentlyPlayedFolders(limit: Int): Flow<List<FolderCount>> {
        val sql = """
            SELECT 
                folderName as name, folderPath as path,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            GROUP BY tracks.folderPath, tracks.folderName
            HAVING MAX(tracks.lastPlayed) > 0
            ORDER BY lastPlayed DESC LIMIT ?
        """
        return getFoldersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    fun getMostPlayedFolders(limit: Int): Flow<List<FolderCount>> {
        val sql = """
            SELECT 
                folderName as name, folderPath as path,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            GROUP BY tracks.folderPath, tracks.folderName
            HAVING SUM(tracks.playCount) > 0
            ORDER BY playCount DESC LIMIT ?
        """
        return getFoldersSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    @RawQuery(observedEntities = [TrackEntity::class])
    abstract fun getFolderByPathRaw(query: SupportSQLiteQuery): Flow<List<FolderCount>>

    fun getFolderByPath(folderPath: String): Flow<FolderCount?> {
        val sql = """
            SELECT 
                folderName as name, folderPath as path,
                COUNT(tracks.id) as count,
                COUNT(DISTINCT tracks.albumId) as albumCount,
                COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                SUM(tracks.playCount) as playCount,
                MAX(tracks.lastPlayed) as lastPlayed,
                SUM(tracks.totalPlayTimeMs) as totalPlayTimeMs
            FROM tracks
            WHERE tracks.folderPath = ?
            GROUP BY tracks.folderPath, tracks.folderName
        """
        val raw: Flow<List<FolderCount>> = getFolderByPathRaw(SimpleSQLiteQuery(sql, arrayOf(folderPath)))
        return raw.map { list -> list.firstOrNull() }
    }

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (lazy, on-demand)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        WHERE t.folderPath = :folderPath AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract suspend fun getMosaicPathsForFolder(folderPath: String, limit: Int): List<ArtPath>

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (live, for detail screens)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        WHERE t.folderPath = :folderPath AND t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract fun getMosaicPathsForFolderFlow(folderPath: String, limit: Int): Flow<List<ArtPath>>
}
