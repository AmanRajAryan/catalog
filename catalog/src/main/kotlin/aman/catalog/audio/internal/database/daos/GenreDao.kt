package aman.catalog.audio.internal.database.daos

import androidx.room.*
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.ContextualSortOption
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class GenreDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insert(genre: GenreEntity): Long

  @Query("SELECT id FROM genres WHERE name = :name")
  protected abstract suspend fun getGenreId(name: String): Long?

  @Transaction
  open suspend fun insertOrGetId(name: String): Long {
    val existingId = getGenreId(name)
    if (existingId != null) return existingId
    val newId = insert(GenreEntity(name = name))
    return if (newId == -1L) getGenreId(name)!! else newId
  }

  // ------------------------------------
  //  DYNAMIC SORTING
  // ------------------------------------

  @Transaction
  @RawQuery(
      observedEntities = [
          GenreEntity::class,
          TrackGenreRef::class,
          TrackEntity::class,
          AlbumEntity::class
      ]
  )
  abstract fun getGenresSortedRaw(query: SupportSQLiteQuery): Flow<List<GenreWithCount>>

  fun getGenresSorted(sort: SortOption.Genre = SortOption.Genre.NAME_ASC): Flow<List<GenreWithCount>> {
      val sql = """
          SELECT 
              genres.id, genres.name, COUNT(track_genres.trackId) as trackCount, COUNT(DISTINCT tracks.albumId) as albumCount, 
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs 
          FROM genres 
          LEFT JOIN track_genres ON genres.id = track_genres.genreId 
          LEFT JOIN tracks ON track_genres.trackId = tracks.id
          GROUP BY genres.id 
          HAVING trackCount > 0
          ORDER BY ${sort.sqlString}
      """
      return getGenresSortedRaw(SimpleSQLiteQuery(sql))
  }

  data class GenreWithCount(
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

  fun getGenreById(id: Long): Flow<GenreWithCount?> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              COUNT(track_genres.trackId) as trackCount,
              COUNT(DISTINCT tracks.albumId) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          LEFT JOIN track_genres ON genres.id = track_genres.genreId
          LEFT JOIN tracks ON track_genres.trackId = tracks.id
          WHERE genres.id = ?
          GROUP BY genres.id
      """
      return getGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(id)))
          .map { it.firstOrNull() }
  }

  fun searchGenres(query: String): Flow<List<GenreWithCount>> {
      val sql = """
          SELECT
              genres.id, genres.name, COUNT(track_genres.trackId) as trackCount,
              COUNT(DISTINCT tracks.albumId) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          LEFT JOIN track_genres ON genres.id = track_genres.genreId
          LEFT JOIN tracks ON track_genres.trackId = tracks.id
          WHERE genres.name LIKE '%' || ? || '%'
          GROUP BY genres.id
          HAVING trackCount > 0
          ORDER BY genres.name ASC
      """
      return getGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(query)))
  }

  fun getRecentlyPlayedGenres(limit: Int): Flow<List<GenreWithCount>> {
      val sql = """
          SELECT
              genres.id, genres.name, COUNT(track_genres.trackId) as trackCount,
              COUNT(DISTINCT tracks.albumId) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          LEFT JOIN track_genres ON genres.id = track_genres.genreId
          LEFT JOIN tracks ON track_genres.trackId = tracks.id
          WHERE genres.lastPlayed > 0
          GROUP BY genres.id
          HAVING trackCount > 0
          ORDER BY genres.lastPlayed DESC LIMIT ?
      """
      return getGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
  }

  fun getMostPlayedGenres(limit: Int): Flow<List<GenreWithCount>> {
      val sql = """
          SELECT
              genres.id, genres.name, COUNT(track_genres.trackId) as trackCount,
              COUNT(DISTINCT tracks.albumId) as albumCount,
              MIN(tracks.path) as coverArtPath,
              MAX(tracks.dateModified) as coverArtDateModified,
              COALESCE(SUM(tracks.durationMs), 0) as totalDuration,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          LEFT JOIN track_genres ON genres.id = track_genres.genreId
          LEFT JOIN tracks ON track_genres.trackId = tracks.id
          WHERE genres.playCount > 0
          GROUP BY genres.id
          HAVING trackCount > 0
          ORDER BY genres.playCount DESC LIMIT ?
      """
      return getGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(limit)))
  }

  @Query("DELETE FROM genres WHERE id NOT IN (SELECT genreId FROM track_genres)")
  abstract suspend fun deleteEmptyGenres()

  // ------------------------------------------------------------------------
  // PLAYBACK STATS ENGINE
  // ------------------------------------------------------------------------

  @Query(
          "UPDATE genres SET playCount = playCount + 1, lastPlayed = :timestamp, totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id"
  )
  abstract suspend fun incrementPlayStats(id: Long, timestamp: Long, durationMs: Long)

  // ------------------------------------
  //  CONTEXTUAL GENRE QUERIES
  // ------------------------------------

  // --- INTERNAL SHAPE FOR CONTEXTUAL MATCHED GENRES ---
  data class MatchedGenreDetails(
          @Embedded val genreWithCount: GenreWithCount,
          val matchedTrackCount: Int,
          val matchedDurationMs: Long
  )

  @Transaction
  @RawQuery(
      observedEntities = [
          GenreEntity::class, TrackGenreRef::class, TrackEntity::class, AlbumEntity::class,
          TrackArtistRef::class, TrackComposerRef::class, TrackLyricistRef::class
      ]
  )
  abstract fun getContextualGenresSortedRaw(query: SupportSQLiteQuery): Flow<List<MatchedGenreDetails>>

  fun getGenresForAlbum(
      albumId: Long,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          WHERE tracks.albumId = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(albumId)))
  }

  fun getGenresForTrackArtist(
      artistId: Long,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(DISTINCT tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          INNER JOIN track_artists ON tracks.id = track_artists.trackId
          WHERE track_artists.artistId = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(artistId)))
  }

  fun getGenresForAlbumArtist(
      artistId: Long,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(DISTINCT tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          INNER JOIN albums ON tracks.albumId = albums.id
          WHERE albums.albumArtistId = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(artistId)))
  }

  fun getGenresForComposer(
      composerId: Long,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(DISTINCT tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          INNER JOIN track_composers ON tracks.id = track_composers.trackId
          WHERE track_composers.composerId = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(composerId)))
  }

  fun getGenresForLyricist(
      lyricistId: Long,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(DISTINCT tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          INNER JOIN track_lyricists ON tracks.id = track_lyricists.trackId
          WHERE track_lyricists.lyricistId = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(lyricistId)))
  }

  fun getGenresForFolder(
      folderPath: String,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(DISTINCT tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          WHERE tracks.folderPath = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(folderPath)))
  }

  fun getGenresForYear(
      year: Int,
      sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenreDetails>> {
      val sql = """
          SELECT 
              genres.id, genres.name,
              (SELECT COUNT(*) FROM track_genres tg WHERE tg.genreId = genres.id) as trackCount,
              (SELECT COUNT(DISTINCT t.albumId) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as albumCount,
              (SELECT MIN(t.path) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtPath,
              (SELECT MAX(t.dateModified) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as coverArtDateModified,
              (SELECT COALESCE(SUM(t.durationMs), 0) FROM track_genres tg INNER JOIN tracks t ON tg.trackId = t.id WHERE tg.genreId = genres.id) as totalDuration,
              COUNT(DISTINCT tracks.id) as matchedTrackCount,
              COALESCE(SUM(tracks.durationMs), 0) as matchedDurationMs,
              genres.playCount, genres.lastPlayed, genres.totalPlayTimeMs
          FROM genres
          INNER JOIN track_genres ON genres.id = track_genres.genreId
          INNER JOIN tracks ON track_genres.trackId = tracks.id
          WHERE tracks.year = ?
          GROUP BY genres.id
          ORDER BY ${sort.sqlString}
      """
      return getContextualGenresSortedRaw(SimpleSQLiteQuery(sql, arrayOf(year)))
  }

  // ------------------------------------------------------------------------
  // MOSAIC QUERY (lazy, on-demand)
  // ------------------------------------------------------------------------

  @Query("""
      SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
      FROM tracks t
      INNER JOIN track_genres tg ON t.id = tg.trackId
      WHERE tg.genreId = :genreId AND t.albumId IS NOT NULL
      GROUP BY t.albumId
      ORDER BY t.albumId ASC
      LIMIT :limit
  """)
  abstract suspend fun getMosaicPathsForGenre(genreId: Long, limit: Int): List<ArtPath>

  // ------------------------------------------------------------------------
  // MOSAIC QUERY (live, for detail screens)
  // ------------------------------------------------------------------------

  @Query("""
      SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
      FROM tracks t
      INNER JOIN track_genres tg ON t.id = tg.trackId
      WHERE tg.genreId = :genreId AND t.albumId IS NOT NULL
      GROUP BY t.albumId
      ORDER BY t.albumId ASC
      LIMIT :limit
  """)
  abstract fun getMosaicPathsForGenreFlow(genreId: Long, limit: Int): Flow<List<ArtPath>>
}
