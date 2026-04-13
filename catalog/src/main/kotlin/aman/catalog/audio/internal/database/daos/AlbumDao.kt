package aman.catalog.audio.internal.database.daos

import androidx.room.*
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.ContextualSortOption
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


@Dao
abstract class AlbumDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insert(album: AlbumEntity): Long

  @Query(
          """
        SELECT id FROM albums 
        WHERE title = :title 
        AND (
            (:artistId IS NOT NULL AND albumArtistId = :artistId) 
            OR (:artistId IS NULL AND albumArtistId IS NULL)
        )
        AND folderGroup = :folderGroup
    """
  )
  protected abstract suspend fun getAlbumId(
          title: String,
          artistId: Long?,
          folderGroup: String
  ): Long?

  // ------------------------------------
  //  DYNAMIC SORTING
  // ------------------------------------

    @Transaction
    @RawQuery(
        observedEntities = [
            AlbumEntity::class, 
            ArtistEntity::class, 
            TrackEntity::class
        ]
    )
    abstract fun getAlbumsSortedRaw(query: SupportSQLiteQuery): Flow<List<AlbumDetails>>

    fun getAlbumsSorted(sort: SortOption.Album = SortOption.Album.TITLE_ASC): Flow<List<AlbumDetails>> {
        val sql = """
            SELECT 
                albums.id, albums.title, albums.albumArtistId,
                artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
                COUNT(tracks.id) as trackCount, COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
                MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
                MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
                MIN(tracks.path) as coverArtPath,
                MAX(tracks.dateModified) as coverArtDateModified,
                albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
            FROM albums 
            LEFT JOIN artists ON albums.albumArtistId = artists.id 
            LEFT JOIN tracks ON albums.id = tracks.albumId 
            GROUP BY albums.id 
            HAVING trackCount > 0
            ORDER BY ${sort.sqlString}
        """
        return getAlbumsSortedRaw(SimpleSQLiteQuery(sql))
    }
    
  
  
  
  @Query(
          """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            COUNT(tracks.id) as trackCount, COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
            MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        LEFT JOIN tracks ON albums.id = tracks.albumId 
        GROUP BY albums.id 
        HAVING trackCount > 0
        ORDER BY MAX(tracks.dateAdded) DESC LIMIT :limit
    """
  )
  abstract fun getRecentlyAdded(limit: Int): Flow<List<AlbumDetails>>

  data class AlbumDetails(
          val id: Long,
          val title: String,
          val albumArtistId: Long?,
          val albumArtistName: String?,
          val albumArtistCoverArtPath: String?,
          val albumArtistCoverArtDateModified: Long?,
          val trackCount: Int,
          val totalDuration: Long,
          val minYear: Int?,
          val maxYear: Int?,
          val coverArtPath: String?,
          val coverArtDateModified: Long,
          val playCount: Int,
          val lastPlayed: Long,
          val totalPlayTimeMs: Long
  )

  @Query(
          """
        SELECT 
            albums.id, 
            albums.title,
            albums.albumArtistId,
            artists.name as albumArtistName,
            (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            COUNT(tracks.id) as trackCount,
            COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
            MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified,
            albums.playCount,
            albums.lastPlayed,
            albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        LEFT JOIN tracks ON albums.id = tracks.albumId
        WHERE albums.title LIKE '%' || :query || '%'
        GROUP BY albums.id
        HAVING trackCount > 0
        ORDER BY albums.title ASC
    """
  )
  abstract fun searchAlbums(query: String): Flow<List<AlbumDetails>>

  fun getAlbumsForAlbumArtist(
          artistId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<AlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, 
            albums.title,
            albums.albumArtistId,
            artists.name as albumArtistName,
            (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            COUNT(tracks.id) as trackCount,
            COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
            MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified,
            albums.playCount,
            albums.lastPlayed,
            albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        LEFT JOIN tracks ON albums.id = tracks.albumId
        WHERE albums.albumArtistId = ?
        GROUP BY albums.id
        HAVING trackCount > 0
        ORDER BY ${sort.sqlString}
      """
      return getAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(artistId)))
  }

  fun getAlbumById(id: Long): Flow<AlbumDetails?> {
      val sql = """
          SELECT 
              albums.id, albums.title, albums.albumArtistId,
              artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
              COUNT(tracks.id) as trackCount, COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
              MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
          FROM albums
          LEFT JOIN artists ON albums.albumArtistId = artists.id
          LEFT JOIN tracks ON albums.id = tracks.albumId
          WHERE albums.id = ?
          GROUP BY albums.id
      """
      return getAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(id)))
          .map { it.firstOrNull() }
  }

  data class MatchedAlbumDetails(
          @Embedded val albumDetails: AlbumDetails,
          val matchedTrackCount: Int,
          val matchedDurationMs: Long
  )

  @Transaction
  @RawQuery(
      observedEntities = [
          AlbumEntity::class, 
          ArtistEntity::class, 
          TrackEntity::class,
          TrackArtistRef::class,
          TrackGenreRef::class,
          TrackComposerRef::class,
          TrackLyricistRef::class
      ]
  )
  abstract fun getMatchedAlbumsSortedRaw(query: SupportSQLiteQuery): Flow<List<MatchedAlbumDetails>>

  fun getAppearsOnAlbumsForTrackArtist(
          artistId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            (SELECT COUNT(*) FROM tracks WHERE albumId = albums.id) as trackCount,
            (SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE albumId = albums.id) as totalDuration,
            (SELECT MIN(year) FROM tracks WHERE albumId = albums.id AND year > 0) as minYear,
            (SELECT MAX(year) FROM tracks WHERE albumId = albums.id AND year > 0) as maxYear,
            (SELECT MIN(path) FROM tracks WHERE albumId = albums.id) as coverArtPath,
            (SELECT MAX(dateModified) FROM tracks WHERE albumId = albums.id) as coverArtDateModified,
            COUNT(DISTINCT tracks.id) as matchedTrackCount,
            COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        INNER JOIN tracks ON albums.id = tracks.albumId
        INNER JOIN track_artists ON tracks.id = track_artists.trackId
        WHERE track_artists.artistId = ? 
        AND (albums.albumArtistId IS NULL OR albums.albumArtistId != ?)
        GROUP BY albums.id
        ORDER BY ${sort.sqlString}
      """
      // artistId is bound twice: once for the track join, once to exclude the album artist.
      return getMatchedAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(artistId, artistId)))
  }

  fun getAlbumsForGenre(
          genreId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            (SELECT COUNT(*) FROM tracks WHERE albumId = albums.id) as trackCount,
            (SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE albumId = albums.id) as totalDuration,
            (SELECT MIN(year) FROM tracks WHERE albumId = albums.id AND year > 0) as minYear,
            (SELECT MAX(year) FROM tracks WHERE albumId = albums.id AND year > 0) as maxYear,
            (SELECT MIN(path) FROM tracks WHERE albumId = albums.id) as coverArtPath,
            (SELECT MAX(dateModified) FROM tracks WHERE albumId = albums.id) as coverArtDateModified,
            COUNT(DISTINCT tracks.id) as matchedTrackCount,
            COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        INNER JOIN tracks ON albums.id = tracks.albumId
        INNER JOIN track_genres ON tracks.id = track_genres.trackId
        WHERE track_genres.genreId = ?
        GROUP BY albums.id
        ORDER BY ${sort.sqlString}
      """
      return getMatchedAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(genreId)))
  }

  fun getAlbumsForComposer(
          composerId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            (SELECT COUNT(*) FROM tracks WHERE albumId = albums.id) as trackCount,
            (SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE albumId = albums.id) as totalDuration,
            (SELECT MIN(year) FROM tracks WHERE albumId = albums.id AND year > 0) as minYear,
            (SELECT MAX(year) FROM tracks WHERE albumId = albums.id AND year > 0) as maxYear,
            (SELECT MIN(path) FROM tracks WHERE albumId = albums.id) as coverArtPath,
            (SELECT MAX(dateModified) FROM tracks WHERE albumId = albums.id) as coverArtDateModified,
            COUNT(DISTINCT tracks.id) as matchedTrackCount,
            COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        INNER JOIN tracks ON albums.id = tracks.albumId
        INNER JOIN track_composers ON tracks.id = track_composers.trackId
        WHERE track_composers.composerId = ?
        GROUP BY albums.id
        ORDER BY ${sort.sqlString}
      """
      return getMatchedAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(composerId)))
  }

  fun getAlbumsForLyricist(
          lyricistId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            (SELECT COUNT(*) FROM tracks WHERE albumId = albums.id) as trackCount,
            (SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE albumId = albums.id) as totalDuration,
            (SELECT MIN(year) FROM tracks WHERE albumId = albums.id AND year > 0) as minYear,
            (SELECT MAX(year) FROM tracks WHERE albumId = albums.id AND year > 0) as maxYear,
            (SELECT MIN(path) FROM tracks WHERE albumId = albums.id) as coverArtPath,
            (SELECT MAX(dateModified) FROM tracks WHERE albumId = albums.id) as coverArtDateModified,
            COUNT(DISTINCT tracks.id) as matchedTrackCount,
            COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        INNER JOIN tracks ON albums.id = tracks.albumId
        INNER JOIN track_lyricists ON tracks.id = track_lyricists.trackId
        WHERE track_lyricists.lyricistId = ?
        GROUP BY albums.id
        ORDER BY ${sort.sqlString}
      """
      return getMatchedAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(lyricistId)))
  }

  fun getAlbumsForFolder(
          folderPath: String,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            (SELECT COUNT(*) FROM tracks WHERE albumId = albums.id) as trackCount,
            (SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE albumId = albums.id) as totalDuration,
            (SELECT MIN(year) FROM tracks WHERE albumId = albums.id AND year > 0) as minYear,
            (SELECT MAX(year) FROM tracks WHERE albumId = albums.id AND year > 0) as maxYear,
            (SELECT MIN(path) FROM tracks WHERE albumId = albums.id) as coverArtPath,
            (SELECT MAX(dateModified) FROM tracks WHERE albumId = albums.id) as coverArtDateModified,
            COUNT(DISTINCT tracks.id) as matchedTrackCount,
            COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        INNER JOIN tracks ON albums.id = tracks.albumId
        WHERE tracks.folderPath = ?
        GROUP BY albums.id
        ORDER BY ${sort.sqlString}
      """
      return getMatchedAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(folderPath)))
  }

  fun getAlbumsForYear(
          year: Int,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbumDetails>> {
      val sql = """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            (SELECT COUNT(*) FROM tracks WHERE albumId = albums.id) as trackCount,
            (SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE albumId = albums.id) as totalDuration,
            (SELECT MIN(year) FROM tracks WHERE albumId = albums.id AND year > 0) as minYear,
            (SELECT MAX(year) FROM tracks WHERE albumId = albums.id AND year > 0) as maxYear,
            (SELECT MIN(path) FROM tracks WHERE albumId = albums.id) as coverArtPath,
            (SELECT MAX(dateModified) FROM tracks WHERE albumId = albums.id) as coverArtDateModified,
            COUNT(DISTINCT tracks.id) as matchedTrackCount,
            COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        INNER JOIN tracks ON albums.id = tracks.albumId
        WHERE tracks.year = ?
        GROUP BY albums.id
        ORDER BY ${sort.sqlString}
      """
      return getMatchedAlbumsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(year)))
  }


  @Query("""
      SELECT 
          artists.id, artists.name,
          COUNT(DISTINCT track_artists.trackId) as trackCount,
          COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
          (SELECT COUNT(*) FROM albums WHERE albumArtistId = artists.id) as albumCount,
          MIN(tracks.path) as coverArtPath,
          MAX(tracks.dateModified) as coverArtDateModified,
          artists.playCount, artists.lastPlayed, artists.totalPlayTimeMs
      FROM artists
      INNER JOIN track_artists ON artists.id = track_artists.artistId
      INNER JOIN tracks ON track_artists.trackId = tracks.id
      WHERE tracks.albumId = :albumId
      GROUP BY artists.id
      ORDER BY artists.name ASC
  """)
  abstract fun getArtistsForAlbum(albumId: Long): Flow<List<ArtistDao.ArtistWithCount>>

  @Query("SELECT COUNT(*) FROM tracks WHERE albumId = :albumId")
  abstract suspend fun getTrackCountForAlbum(albumId: Long): Int

  @Query("""
      UPDATE albums SET
          playCount       = playCount       + (SELECT playCount       FROM albums WHERE id = :fromId),
          totalPlayTimeMs = totalPlayTimeMs + (SELECT totalPlayTimeMs FROM albums WHERE id = :fromId),
          lastPlayed      = MAX(lastPlayed,   (SELECT lastPlayed      FROM albums WHERE id = :fromId))
      WHERE id = :toId
  """)
  abstract suspend fun mergePlayStats(fromId: Long, toId: Long)

  @Transaction
  open suspend fun insertOrGetId(title: String, artistId: Long?, folderGroup: String): Long {
    val existingId = getAlbumId(title, artistId, folderGroup)
    if (existingId != null) return existingId
    val newId =
            insert(AlbumEntity(title = title, albumArtistId = artistId, folderGroup = folderGroup))
    return if (newId == -1L) getAlbumId(title, artistId, folderGroup)!! else newId
  }

  @Query(
          "DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM tracks WHERE albumId IS NOT NULL)"
  )
  abstract suspend fun deleteEmptyAlbums()

  // ----------------------------------------
  // PLAYBACK STATS
  // ----------------------------------------

  @Query(
          "UPDATE albums SET playCount = playCount + 1, lastPlayed = :timestamp, totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id"
  )
  abstract suspend fun incrementPlayStats(id: Long, timestamp: Long, durationMs: Long)

  @Query(
          """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            COUNT(tracks.id) as trackCount, COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
            MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        LEFT JOIN tracks ON albums.id = tracks.albumId 
        WHERE albums.lastPlayed > 0
        GROUP BY albums.id 
        HAVING trackCount > 0
        ORDER BY albums.lastPlayed DESC LIMIT :limit
    """
  )
  abstract fun getRecentlyPlayedAlbums(limit: Int): Flow<List<AlbumDetails>>

  @Query(
          """
        SELECT 
            albums.id, albums.title, albums.albumArtistId,
            artists.name as albumArtistName, (SELECT MIN(t.path) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtPath,
                (SELECT MAX(t.dateModified) FROM tracks t INNER JOIN track_artists ta ON t.id = ta.trackId WHERE ta.artistId = albums.albumArtistId) as albumArtistCoverArtDateModified,
            COUNT(tracks.id) as trackCount, COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
            MIN(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as minYear,
            MAX(CASE WHEN tracks.year > 0 THEN tracks.year ELSE NULL END) as maxYear,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified,
            albums.playCount, albums.lastPlayed, albums.totalPlayTimeMs
        FROM albums 
        LEFT JOIN artists ON albums.albumArtistId = artists.id 
        LEFT JOIN tracks ON albums.id = tracks.albumId 
        WHERE albums.playCount > 0
        GROUP BY albums.id 
        HAVING trackCount > 0
        ORDER BY albums.playCount DESC LIMIT :limit
    """
  )
  abstract fun getMostPlayedAlbums(limit: Int): Flow<List<AlbumDetails>>
}
