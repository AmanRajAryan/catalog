package aman.catalog.audio.internal.database.daos

import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.internal.database.relations.TrackWithRelations
import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.ContextualSortOption

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(track: TrackEntity): Long

  // --- JUNCTION INSERTS ---
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertArtistRefs(refs: List<TrackArtistRef>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertGenreRefs(refs: List<TrackGenreRef>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertComposerRefs(refs: List<TrackComposerRef>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertLyricistRefs(refs: List<TrackLyricistRef>)

  // --- JUNCTION DELETES ---
  @Query("DELETE FROM track_artists WHERE trackId = :trackId")
  suspend fun deleteArtistRefs(trackId: Long)

  @Query("DELETE FROM track_genres WHERE trackId = :trackId")
  suspend fun deleteGenreRefs(trackId: Long)

  @Query("DELETE FROM track_composers WHERE trackId = :trackId")
  suspend fun deleteComposerRefs(trackId: Long)

  @Query("DELETE FROM track_lyricists WHERE trackId = :trackId")
  suspend fun deleteLyricistRefs(trackId: Long)

  // --- CORE DELETES ---
  @Query("DELETE FROM tracks WHERE id = :id") suspend fun delete(id: Long)

  @Query("DELETE FROM tracks WHERE path IN (:paths)") suspend fun deleteByPaths(paths: List<String>)

  @Query("DELETE FROM tracks") suspend fun clearAll()

  @Query("DELETE FROM tracks WHERE path LIKE :pathPrefix || '/%' OR path = :pathPrefix")
  suspend fun deleteTracksByPathPrefix(pathPrefix: String)

  // --- RETRIEVAL ---

  @Transaction
  @Query("SELECT * FROM tracks WHERE id = :id")
  suspend fun getTrackById(id: Long): TrackWithRelations?

  @Query("SELECT albumId FROM tracks WHERE id = :id")
  suspend fun getAlbumIdForTrack(id: Long): Long?

  @Query("SELECT path FROM tracks") fun getAllPaths(): List<String>

  @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
  suspend fun getTrackByPath(path: String): TrackEntity?

  data class TrackSnapshot(
          val id: Long,
          val path: String,
          val dateModified: Long,
          val mediaStoreId: Long
  )
  @Query("SELECT id, path, dateModified, mediaStoreId FROM tracks")
  suspend fun getTrackSnapshots(): List<TrackSnapshot>

  // --- COMPILATION UTILS ---

  @Query("SELECT DISTINCT artistDisplay FROM tracks WHERE folderPath = :folderPath")
  suspend fun getDistinctArtistsInFolder(folderPath: String): List<String>

  @Query(
          "UPDATE tracks SET albumArtistDisplay = 'Various Artists' WHERE folderPath = :folderPath AND rawAlbumArtistString = ''"
  )
  suspend fun markFolderAsCompilation(folderPath: String)

  @Query(
          """
        UPDATE tracks 
        SET albumArtistDisplay = rawAlbumArtistString
        WHERE folderPath = :folderPath
    """
  )
  suspend fun unmarkFolderAsCompilation(folderPath: String)

  @Query("SELECT DISTINCT albumDisplay FROM tracks WHERE folderPath = :folderPath")
  suspend fun getDistinctAlbumsInFolder(folderPath: String): List<String>

  /**
   * Bulk update album IDs for a specific album in a folder. SAFEGUARD: Only touches tracks that DO
   * NOT have a specific Album Artist tag.
   */
  @Query(
          """
        UPDATE tracks 
        SET albumId = :newAlbumId 
        WHERE folderPath = :folderPath 
          AND albumDisplay = :albumName 
          AND (rawAlbumArtistString IS NULL OR rawAlbumArtistString = '')
    """
  )
  suspend fun updateAlbumIdForFolder(folderPath: String, albumName: String, newAlbumId: Long)

  data class TrackRestoreInfo(
          val id: Long,
          val albumDisplay: String,
          val artistDisplay: String,
          val rawAlbumArtistString: String
  )
  @Query(
          "SELECT id, albumDisplay, artistDisplay, rawAlbumArtistString FROM tracks WHERE folderPath = :folderPath"
  )
  suspend fun getTracksForRestore(folderPath: String): List<TrackRestoreInfo>

  @Query("UPDATE tracks SET albumId = :newAlbumId WHERE id = :trackId")
  suspend fun updateAlbumIdOnly(trackId: Long, newAlbumId: Long)

  // --- RAW DATA UTILS (Optimized) ---

  /** Lightweight data class for config updates. */
  data class RawTrackData(
          val id: Long,
          val rawArtistString: String,
          val rawGenreString: String,
          val rawComposerString: String,
          val rawLyricistString: String,
          val rawAlbumArtistString: String,
          val albumDisplay: String,
          val albumArtistDisplay: String,
          val folderPath: String
  )

  @Query(
          """
        SELECT 
          id, 
          rawArtistString, 
          rawGenreString, 
          rawComposerString, 
          rawLyricistString, 
          rawAlbumArtistString, 
          albumDisplay, 
          albumArtistDisplay,
          folderPath 
        FROM tracks
    """
  )
  suspend fun getAllTracksRaw(): List<RawTrackData>

  // --- CONFIG UPDATES ---
  @Query(
          "UPDATE tracks SET albumId = :newAlbumId, albumArtistDisplay = :newDisplay WHERE id = :trackId"
  )
  suspend fun updateAlbumLink(trackId: Long, newAlbumId: Long, newDisplay: String)

  @Query("UPDATE tracks SET albumArtistDisplay = :newDisplay WHERE id = :id")
  suspend fun updateAlbumArtist(id: Long, newDisplay: String)

  // ------------------------------------
  //  SORTING LOGIC
  // ------------------------------------

  @Transaction
  @RawQuery(
          observedEntities =
                  [
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
  fun getTracksSortedRaw(query: SupportSQLiteQuery): Flow<List<TrackWithRelations>>

  fun getTracksSorted(sort: SortOption.Track = SortOption.Track.TITLE_ASC): Flow<List<TrackWithRelations>> {
      val sql = "SELECT * FROM tracks ORDER BY ${sort.sqlString}"
      return getTracksSortedRaw(SimpleSQLiteQuery(sql))
  }

  // --- PRE-CANNED LISTS ---

  /**
   * Returns the most recently added tracks.
   * @param limit The maximum number of tracks to return. Pass -1 to return all tracks.
   */
  @Transaction
  @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :limit")
  fun getRecentlyAdded(limit: Int): Flow<List<TrackWithRelations>>

  /**
   * Returns tracks ordered by highest play count.
   * @param limit The maximum number of tracks to return. Pass -1 to return all tracks.
   */
  @Transaction
  @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
  fun getMostPlayed(limit: Int): Flow<List<TrackWithRelations>>

  /**
   * Returns tracks ordered by the most recent playback timestamp.
   * @param limit The maximum number of tracks to return. Pass -1 to return all tracks.
   */
  @Transaction
  @Query("SELECT * FROM tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
  fun getRecentlyPlayed(limit: Int): Flow<List<TrackWithRelations>>

  // --- NAVIGATION QUERIES ---

  fun getTracksForArtist(
      artistId: Long,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT tracks.* FROM tracks 
          INNER JOIN track_artists ON tracks.id = track_artists.trackId 
          WHERE track_artists.artistId = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(artistId)))
  }

  fun getTracksForAlbumArtist(
      artistId: Long,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT tracks.* FROM tracks 
          INNER JOIN albums ON tracks.albumId = albums.id 
          WHERE albums.albumArtistId = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(artistId)))
  }

  // ------------------------------------
  // CONTEXTUAL SORTING: ALBUM TRACKS
  // ------------------------------------

  @Transaction
  @RawQuery(
      observedEntities = [
          TrackEntity::class,
          TrackArtistRef::class,
          TrackGenreRef::class,
          TrackComposerRef::class,
          TrackLyricistRef::class,
          AlbumEntity::class,
          FavoritesEntity::class
      ]
  )
  abstract fun getTracksForAlbumRaw(query: SupportSQLiteQuery): Flow<List<TrackWithRelations>>

  fun getTracksForAlbum(
      albumId: Long,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TRACK_NUMBER_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT tracks.* FROM tracks 
          WHERE tracks.albumId = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksForAlbumRaw(SimpleSQLiteQuery(sql, arrayOf(albumId)))
  }

  fun getTracksForGenre(
      genreId: Long,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT tracks.* FROM tracks 
          INNER JOIN track_genres ON tracks.id = track_genres.trackId 
          WHERE track_genres.genreId = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(genreId)))
  }

  fun getTracksForComposer(
      composerId: Long,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT tracks.* FROM tracks 
          INNER JOIN track_composers ON tracks.id = track_composers.trackId 
          WHERE track_composers.composerId = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(composerId)))
  }

  fun getTracksForLyricist(
      lyricistId: Long,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT tracks.* FROM tracks 
          INNER JOIN track_lyricists ON tracks.id = track_lyricists.trackId 
          WHERE track_lyricists.lyricistId = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(lyricistId)))
  }

  fun getTracksForYear(
      year: Int,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT * FROM tracks 
          WHERE year = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(year)))
  }

  fun getTracksForFolder(
      folderPath: String,
      sort: ContextualSortOption.Track = ContextualSortOption.Track.TRACK_NUMBER_ASC
  ): Flow<List<TrackWithRelations>> {
      val sql = """
          SELECT * FROM tracks 
          WHERE folderPath = ? 
          ORDER BY ${sort.sqlString}
      """
      return getTracksSortedRaw(SimpleSQLiteQuery(sql, arrayOf(folderPath)))
  }

  // --- SEARCH ---

  @Transaction
  @Query(
          "SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artistDisplay LIKE '%' || :query || '%' ORDER BY title ASC"
  )
  fun searchTracks(query: String): Flow<List<TrackWithRelations>>

  @Transaction
  @RawQuery(
          observedEntities =
                  [
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
                          FavoritesEntity::class]
  )
  fun searchTracksRaw(query: SupportSQLiteQuery): Flow<List<TrackWithRelations>>

  // --- UPDATES ---
  @Query(
          "UPDATE tracks SET playCount = playCount + 1, lastPlayed = :timestamp, totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE id = :id"
  )
  suspend fun incrementPlayCount(id: Long, timestamp: Long, durationMs: Long)

  @Query("UPDATE tracks SET skipCount = skipCount + 1 WHERE id = :id")
  suspend fun incrementSkipCount(id: Long)

  @Query(
          """
        UPDATE tracks SET 
            mediaStoreId = :mediaStoreId,
            albumId = :albumId,
            
            path = :path, 
            folderName = :folderName, 
            folderPath = :folderPath, 
            
            title = :title,
            sizeBytes = :size,
            dateModified = :dateModified,
            mimeType = :mimeType,
            durationMs = :duration,
            artistDisplay = :artist,
            albumDisplay = :album,
            albumArtistDisplay = :albumArtist,
            genreDisplay = :genre,
            composerDisplay = :composer,
            lyricistDisplay = :lyricist,
            rawArtistString = :rawArtist,
            rawGenreString = :rawGenre,
            rawComposerString = :rawComposer,
            rawLyricistString = :rawLyricist,
            rawAlbumArtistString = :rawAlbumArtist,
            rawAlbumString = :rawAlbum,
            year = :year,
            releaseDate = :releaseDate,
            trackNumber = :trackNum,
            discNumber = :discNum,
            contentRating = :rating,
            bitrate = :bitrate,
            sampleRate = :sampleRate,
            channels = :channels,
            codec = :codec,
            bitsPerSample = :bitsPerSample,
            replayGainTrackGain = :replayGainTrackGain,
            replayGainTrackPeak = :replayGainTrackPeak,
            replayGainAlbumGain = :replayGainAlbumGain,
            replayGainAlbumPeak = :replayGainAlbumPeak
        WHERE id = :id
    """
  )
  suspend fun updateTrackMetadata(
          id: Long,
          mediaStoreId: Long,
          albumId: Long?,
          path: String,
          folderName: String,
          folderPath: String,
          title: String,
          size: Long,
          dateModified: Long,
          mimeType: String,
          duration: Long,
          artist: String,
          album: String,
          albumArtist: String,
          genre: String,
          composer: String,
          lyricist: String,
          rawArtist: String,
          rawGenre: String,
          rawComposer: String,
          rawLyricist: String,
          rawAlbumArtist: String,
          rawAlbum: String,
          year: Int,
          releaseDate: String,
          trackNum: Int,
          discNum: Int,
          rating: Int,
          bitrate: Int,
          sampleRate: Int,
          channels: Int,
          codec: String,
          bitsPerSample: Int,
          replayGainTrackGain: Double,
          replayGainTrackPeak: Double,
          replayGainAlbumGain: Double,
          replayGainAlbumPeak: Double
  )

  data class PathIdPair(val path: String, val id: Long)
  @Query("SELECT path, id FROM tracks WHERE path IN (:paths)")
  suspend fun getTrackIdsByPaths(paths: List<String>): List<PathIdPair>
}
