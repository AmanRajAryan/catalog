package aman.catalog.audio.internal.database.daos

import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.SortOption
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class ArtistDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insert(artist: ArtistEntity): Long

  @Query("SELECT id FROM artists WHERE name = :name")
  protected abstract suspend fun getArtistId(name: String): Long?

  // ------------------------------------
  //  DYNAMIC SORTING: TRACK ARTISTS
  // ------------------------------------

  @Transaction
  @RawQuery(
      observedEntities = [
          ArtistEntity::class,
          TrackArtistRef::class,
          TrackEntity::class,
          AlbumEntity::class
      ]
  )
  abstract fun getArtistsSortedRaw(query: SupportSQLiteQuery): Flow<List<ArtistWithCount>>

  fun getArtistsSorted(sort: SortOption.Artist = SortOption.Artist.NAME_ASC): Flow<List<ArtistWithCount>> {
      val sql = """
          SELECT 
              artists.id, 
              artists.name, 
              COUNT(track_artists.trackId) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              (SELECT COUNT(*) FROM albums WHERE albumArtistId = artists.id) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              artists.playCount,      
              artists.lastPlayed,      
              artists.totalPlayTimeMs 
          FROM artists 
          LEFT JOIN track_artists ON artists.id = track_artists.artistId 
          LEFT JOIN tracks ON track_artists.trackId = tracks.id
          GROUP BY artists.id 
          HAVING trackCount > 0
          ORDER BY ${sort.trackArtistSqlString}
      """
      return getArtistsSortedRaw(SimpleSQLiteQuery(sql))
  }

  data class ArtistWithCount(
          val id: Long,
          val name: String,
          val trackCount: Int,
          val totalDuration: Long,
          val albumCount: Int,
          val coverArtPath: String?,
          val coverArtDateModified: Long,
          val playCount: Int,
          val lastPlayed: Long,
          val totalPlayTimeMs: Long
  )

  // ------------------------------------
  //  DYNAMIC SORTING: ALBUM ARTISTS
  // ------------------------------------

  @Transaction
  @RawQuery(
      observedEntities = [
          ArtistEntity::class,
          AlbumEntity::class,
          TrackEntity::class
      ]
  )
  abstract fun getAlbumArtistsSortedRaw(query: SupportSQLiteQuery): Flow<List<AlbumArtistWithCounts>>

  fun getAlbumArtistsSorted(sort: SortOption.Artist = SortOption.Artist.NAME_ASC): Flow<List<AlbumArtistWithCounts>> {
      val sql = """
          SELECT 
              artists.id, 
              artists.name, 
              COUNT(DISTINCT albums.id) as albumCount,
              COUNT(tracks.id) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              (SELECT COALESCE(SUM(playCount), 0) FROM albums WHERE albumArtistId = artists.id) as playCount,
              (SELECT COALESCE(MAX(lastPlayed), 0) FROM albums WHERE albumArtistId = artists.id) as lastPlayed,
              (SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM albums WHERE albumArtistId = artists.id) as totalPlayTimeMs
          FROM artists 
          INNER JOIN albums ON artists.id = albums.albumArtistId
          LEFT JOIN tracks ON albums.id = tracks.albumId
          GROUP BY artists.id
          ORDER BY ${sort.albumArtistSqlString}
      """
      return getAlbumArtistsSortedRaw(SimpleSQLiteQuery(sql))
  }

  data class AlbumArtistWithCounts(
          val id: Long,
          val name: String,
          val albumCount: Int,
          val trackCount: Int,
          val totalDuration: Long,
          val coverArtPath: String?,
          val coverArtDateModified: Long,
          val playCount: Int,
          val lastPlayed: Long,
          val totalPlayTimeMs: Long
  )

  fun getTrackArtistById(id: Long): Flow<ArtistWithCount?> {
      val sql = """
          SELECT
              artists.id, artists.name,
              COUNT(track_artists.trackId) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              (SELECT COUNT(*) FROM albums WHERE albumArtistId = artists.id) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              artists.playCount, artists.lastPlayed, artists.totalPlayTimeMs
          FROM artists
          LEFT JOIN track_artists ON artists.id = track_artists.artistId
          LEFT JOIN tracks ON track_artists.trackId = tracks.id
          WHERE artists.id = ?
          GROUP BY artists.id
      """
      val raw: Flow<List<ArtistWithCount>> = getArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(id)))
      return raw.map { it.firstOrNull() }
  }

  fun getAlbumArtistById(id: Long): Flow<AlbumArtistWithCounts?> {
      val sql = """
          SELECT
              artists.id, artists.name,
              COUNT(DISTINCT albums.id) as albumCount,
              COUNT(tracks.id) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              (SELECT COALESCE(SUM(playCount), 0) FROM albums WHERE albumArtistId = artists.id) as playCount,
              (SELECT COALESCE(MAX(lastPlayed), 0) FROM albums WHERE albumArtistId = artists.id) as lastPlayed,
              (SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM albums WHERE albumArtistId = artists.id) as totalPlayTimeMs
          FROM artists
          INNER JOIN albums ON artists.id = albums.albumArtistId
          LEFT JOIN tracks ON albums.id = tracks.albumId
          WHERE artists.id = ?
          GROUP BY artists.id
      """
      val raw: Flow<List<AlbumArtistWithCounts>> = getAlbumArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(id)))
      return raw.map { it.firstOrNull() }
  }

  fun searchArtists(query: String): Flow<List<ArtistWithCount>> {
      val sql = """
          SELECT 
              artists.id, artists.name,
              COUNT(track_artists.trackId) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              (SELECT COUNT(*) FROM albums WHERE albumArtistId = artists.id) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              artists.playCount, artists.lastPlayed, artists.totalPlayTimeMs
          FROM artists
          LEFT JOIN track_artists ON artists.id = track_artists.artistId
          LEFT JOIN tracks ON track_artists.trackId = tracks.id
          WHERE artists.name LIKE '%' || ? || '%'
          GROUP BY artists.id
          HAVING trackCount > 0
          ORDER BY artists.name ASC
      """
      return getArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
  }

  fun searchAlbumArtists(query: String): Flow<List<AlbumArtistWithCounts>> {
      val sql = """
          SELECT
              artists.id, artists.name,
              COUNT(DISTINCT albums.id) as albumCount,
              COUNT(tracks.id) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              (SELECT COALESCE(SUM(playCount), 0) FROM albums WHERE albumArtistId = artists.id) as playCount,
              (SELECT COALESCE(MAX(lastPlayed), 0) FROM albums WHERE albumArtistId = artists.id) as lastPlayed,
              (SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM albums WHERE albumArtistId = artists.id) as totalPlayTimeMs
          FROM artists
          INNER JOIN albums ON artists.id = albums.albumArtistId
          LEFT JOIN tracks ON albums.id = tracks.albumId
          WHERE artists.name LIKE '%' || ? || '%'
          GROUP BY artists.id
          ORDER BY artists.name ASC
      """
      return getAlbumArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
  }

  @Transaction
  open suspend fun insertOrGetId(name: String): Long {
    val existingId = getArtistId(name)
    if (existingId != null) return existingId
    val newId = insert(ArtistEntity(name = name))
    return if (newId == -1L) getArtistId(name)!! else newId
  }

  @Query(
          """
        DELETE FROM artists 
        WHERE id NOT IN (SELECT artistId FROM track_artists) 
        AND id NOT IN (SELECT albumArtistId FROM albums WHERE albumArtistId IS NOT NULL)
    """
  )
  abstract suspend fun deleteEmptyArtists()

  // ------------------------------------------------------------------------
  // PLAYBACK STATS ENGINE
  // ------------------------------------------------------------------------

  @Query(
          "UPDATE artists SET playCount = playCount + 1, lastPlayed = :timestamp, totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id"
  )
  abstract suspend fun incrementPlayStats(id: Long, timestamp: Long, durationMs: Long)

  fun getRecentlyPlayedArtists(limit: Int): Flow<List<ArtistWithCount>> {
      val sql = """
          SELECT
              artists.id, artists.name, COUNT(track_artists.trackId) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              (SELECT COUNT(*) FROM albums WHERE albumArtistId = artists.id) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              artists.playCount, artists.lastPlayed, artists.totalPlayTimeMs
          FROM artists
          LEFT JOIN track_artists ON artists.id = track_artists.artistId
          LEFT JOIN tracks ON track_artists.trackId = tracks.id
          WHERE artists.lastPlayed > 0
          GROUP BY artists.id
          HAVING trackCount > 0
          ORDER BY artists.lastPlayed DESC LIMIT ?
      """
      return getArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
  }

  fun getMostPlayedArtists(limit: Int): Flow<List<ArtistWithCount>> {
      val sql = """
          SELECT
              artists.id, artists.name, COUNT(track_artists.trackId) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              (SELECT COUNT(*) FROM albums WHERE albumArtistId = artists.id) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              artists.playCount, artists.lastPlayed, artists.totalPlayTimeMs
          FROM artists
          LEFT JOIN track_artists ON artists.id = track_artists.artistId
          LEFT JOIN tracks ON track_artists.trackId = tracks.id
          WHERE artists.playCount > 0
          GROUP BY artists.id
          HAVING trackCount > 0
          ORDER BY artists.playCount DESC LIMIT ?
      """
      return getArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
  }

  fun getRecentlyPlayedAlbumArtists(limit: Int): Flow<List<AlbumArtistWithCounts>> {
      val sql = """
          SELECT
              artists.id, artists.name,
              COUNT(DISTINCT albums.id) as albumCount,
              COUNT(tracks.id) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              (SELECT COALESCE(SUM(playCount), 0) FROM albums WHERE albumArtistId = artists.id) as playCount,
              (SELECT COALESCE(MAX(lastPlayed), 0) FROM albums WHERE albumArtistId = artists.id) as lastPlayed,
              (SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM albums WHERE albumArtistId = artists.id) as totalPlayTimeMs
          FROM artists
          INNER JOIN albums ON artists.id = albums.albumArtistId
          LEFT JOIN tracks ON albums.id = tracks.albumId
          WHERE artists.id IN (SELECT albumArtistId FROM albums WHERE lastPlayed > 0)
          GROUP BY artists.id
          ORDER BY lastPlayed DESC LIMIT ?
      """
      return getAlbumArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
  }

  fun getMostPlayedAlbumArtists(limit: Int): Flow<List<AlbumArtistWithCounts>> {
      val sql = """
          SELECT
              artists.id, artists.name,
              COUNT(DISTINCT albums.id) as albumCount,
              COUNT(tracks.id) as trackCount,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              (SELECT COALESCE(SUM(playCount), 0) FROM albums WHERE albumArtistId = artists.id) as playCount,
              (SELECT COALESCE(MAX(lastPlayed), 0) FROM albums WHERE albumArtistId = artists.id) as lastPlayed,
              (SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM albums WHERE albumArtistId = artists.id) as totalPlayTimeMs
          FROM artists
          INNER JOIN albums ON artists.id = albums.albumArtistId
          LEFT JOIN tracks ON albums.id = tracks.albumId
          WHERE artists.id IN (SELECT albumArtistId FROM albums WHERE playCount > 0)
          GROUP BY artists.id
          ORDER BY playCount DESC LIMIT ?
      """
      return getAlbumArtistsSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
  }

  // ------------------------------------------------------------------------
  // MOSAIC QUERIES (lazy, on-demand)
  // ------------------------------------------------------------------------

  @Query("""
      SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
      FROM tracks t
      INNER JOIN track_artists ta ON t.id = ta.trackId
      WHERE ta.artistId = :artistId AND t.albumId IS NOT NULL
      GROUP BY t.albumId
      ORDER BY t.albumId ASC
      LIMIT :limit
  """)
  abstract suspend fun getMosaicPathsForTrackArtist(artistId: Long, limit: Int): List<ArtPath>

  @Query("""
      SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
      FROM tracks t
      INNER JOIN albums a ON t.albumId = a.id
      WHERE a.albumArtistId = :artistId
      GROUP BY t.albumId
      ORDER BY t.albumId ASC
      LIMIT :limit
  """)
  abstract suspend fun getMosaicPathsForAlbumArtist(artistId: Long, limit: Int): List<ArtPath>

  // ------------------------------------------------------------------------
  // MOSAIC QUERIES (live, for detail screens)
  // ------------------------------------------------------------------------

  @Query("""
      SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
      FROM tracks t
      INNER JOIN track_artists ta ON t.id = ta.trackId
      WHERE ta.artistId = :artistId AND t.albumId IS NOT NULL
      GROUP BY t.albumId
      ORDER BY t.albumId ASC
      LIMIT :limit
  """)
  abstract fun getMosaicPathsForTrackArtistFlow(artistId: Long, limit: Int): Flow<List<ArtPath>>

  @Query("""
      SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
      FROM tracks t
      INNER JOIN albums a ON t.albumId = a.id
      WHERE a.albumArtistId = :artistId
      GROUP BY t.albumId
      ORDER BY t.albumId ASC
      LIMIT :limit
  """)
  abstract fun getMosaicPathsForAlbumArtistFlow(artistId: Long, limit: Int): Flow<List<ArtPath>>
}
