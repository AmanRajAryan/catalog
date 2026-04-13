package aman.catalog.audio

import aman.catalog.audio.internal.ModelMapper
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.taglib.TagLibHelper
import aman.catalog.audio.models.*
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.ContextualSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


/**
 * Manages all core library browsing and navigation data flows. Accessible via [Catalog.library].
 */
class CatalogLibrary internal constructor(private val databaseProvider: () -> CatalogDatabase) {
  private val database: CatalogDatabase
    get() = databaseProvider()

  // ----------------------------------------
  // CORE TRACK LISTS
  // ----------------------------------------

  /**
   * Live stream of all tracks, sorted by [sort]. Re-emits automatically whenever the underlying
   * data changes.
   */
  fun getTracks(sort: SortOption.Track = SortOption.Track.TITLE_ASC): Flow<List<Track>> {
    return database.trackDao()
            .getTracksSorted(sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of the most recently added tracks. */
  fun getRecentlyAddedTracks(limit: Int = 50): Flow<List<Track>> {
    return database.trackDao()
            .getRecentlyAdded(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of the most recently added albums. */
  fun getRecentlyAddedAlbums(limit: Int = 50): Flow<List<Album>> {
    return database.albumDao()
            .getRecentlyAdded(limit)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  // ----------------------------------------
  // CATEGORIES
  // ----------------------------------------

  /** Live stream of all artists with their track counts, sorted by [sort]. */
  fun getArtists(sort: SortOption.Artist = SortOption.Artist.NAME_ASC): Flow<List<Artist>> {
    return database.artistDao()
            .getArtistsSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all albums with their details, sorted by [sort]. */
  fun getAlbums(sort: SortOption.Album = SortOption.Album.TITLE_ASC): Flow<List<Album>> {
    return database.albumDao()
            .getAlbumsSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all album artists with their counts, sorted by [sort]. */
  fun getAlbumArtists(sort: SortOption.Artist = SortOption.Artist.NAME_ASC): Flow<List<Artist>> {
    return database.artistDao()
            .getAlbumArtistsSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres with track counts, sorted by [sort]. */
  fun getGenres(sort: SortOption.Genre = SortOption.Genre.NAME_ASC): Flow<List<Genre>> {
    return database.genreDao()
            .getGenresSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all composers with track counts, sorted by [sort]. */
  fun getComposers(sort: SortOption.Composer = SortOption.Composer.NAME_ASC): Flow<List<Composer>> {
    return database.composerDao()
            .getComposersSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toComposer(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all lyricists with track counts, sorted by [sort]. */
  fun getLyricists(sort: SortOption.Lyricist = SortOption.Lyricist.NAME_ASC): Flow<List<Lyricist>> {
    return database.lyricistDao()
            .getLyricistsSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toLyricist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all release years present in the library, with track counts. */
  fun getYears(sort: SortOption.Year = SortOption.Year.YEAR_DESC): Flow<List<Year>> {
    return database.yearDao()
            .getYearsSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toYear(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all folders that contain at least one track, with track counts. */
  fun getFolders(sort: SortOption.Folder = SortOption.Folder.NAME_ASC): Flow<List<Folder>> {
    return database.folderDao()
            .getFoldersSorted(sort)
            .distinctUntilChanged()
            .map { entities -> entities.map { ModelMapper.toFolder(it) } }
            .flowOn(Dispatchers.Default)
  }

  // ----------------------------------------
  // NAVIGATION API
  // ----------------------------------------

  /** Live stream of all tracks associated with a given artist. */
  fun getTracksForArtist(
          artistId: Long,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForArtist(artistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks associated with a given album artist. */
  fun getTracksForAlbumArtist(
          artistId: Long,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForAlbumArtist(artistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks belonging to a given album. */
  fun getTracksForAlbum(
          albumId: Long,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TRACK_NUMBER_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForAlbum(albumId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks tagged with a given genre. */
  fun getTracksForGenre(
          genreId: Long,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForGenre(genreId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks attributed to a given composer. */
  fun getTracksForComposer(
          composerId: Long,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForComposer(composerId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks attributed to a given lyricist. */
  fun getTracksForLyricist(
          lyricistId: Long,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForLyricist(lyricistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks from a given release year. */
  fun getTracksForYear(
          year: Int,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForYear(year, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all tracks located directly inside the given folder path. */
  fun getTracksForFolder(
          folderPath: String,
          sort: ContextualSortOption.Track = ContextualSortOption.Track.TRACK_NUMBER_ASC
  ): Flow<List<Track>> {
    return database.trackDao()
            .getTracksForFolder(folderPath, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of albums strictly owned by this artist (Main Releases). */
  fun getAlbumsForAlbumArtist(
          artistId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<Album>> {
    return database.albumDao()
            .getAlbumsForAlbumArtist(artistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /**
   * Live stream of albums where this artist is featured on a track, but does not own the album
   * (Appears On).
   */
  fun getAppearsOnAlbumsForTrackArtist(
          artistId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbum>> {
    return database.albumDao()
            .getAppearsOnAlbumsForTrackArtist(artistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of albums tagged with a given genre, including match context. */
  fun getAlbumsForGenre(
          genreId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbum>> {
    return database.albumDao()
            .getAlbumsForGenre(genreId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of albums featuring tracks by a given composer. */
  fun getAlbumsForComposer(
          composerId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbum>> {
    return database.albumDao()
            .getAlbumsForComposer(composerId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of albums featuring tracks by a given lyricist. */
  fun getAlbumsForLyricist(
          lyricistId: Long,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbum>> {
    return database.albumDao()
            .getAlbumsForLyricist(lyricistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of albums that have tracks located in the given folder. */
  fun getAlbumsForFolder(
          folderPath: String,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbum>> {
    return database.albumDao()
            .getAlbumsForFolder(folderPath, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of albums that have tracks released in the given year. */
  fun getAlbumsForYear(
          year: Int,
          sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC
  ): Flow<List<MatchedAlbum>> {
    return database.albumDao()
            .getAlbumsForYear(year, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given album. */
  fun getGenresForAlbum(
          albumId: Long,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForAlbum(albumId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given track artist. */
  fun getGenresForTrackArtist(
          artistId: Long,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForTrackArtist(artistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given album artist. */
  fun getGenresForAlbumArtist(
          artistId: Long,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForAlbumArtist(artistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given composer. */
  fun getGenresForComposer(
          composerId: Long,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForComposer(composerId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given lyricist. */
  fun getGenresForLyricist(
          lyricistId: Long,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForLyricist(lyricistId, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given folder path. */
  fun getGenresForFolder(
          folderPath: String,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForFolder(folderPath, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all genres associated with a given release year. */
  fun getGenresForYear(
          year: Int,
          sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC
  ): Flow<List<MatchedGenre>> {
    return database.genreDao()
            .getGenresForYear(year, sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toMatchedGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  // ----------------------------------------
  // GET BY ID / NATURAL KEY
  // ----------------------------------------

  /** Live stream of a single track artist by their id. Emits null if not found. */
  fun getTrackArtistById(artistId: Long): Flow<Artist?> {
    return database.artistDao()
            .getTrackArtistById(artistId)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single album artist by their id. Emits null if not found. */
  fun getAlbumArtistById(artistId: Long): Flow<Artist?> {
    return database.artistDao()
            .getAlbumArtistById(artistId)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single album by its id. Emits null if not found. */
  fun getAlbumById(albumId: Long): Flow<Album?> {
    return database.albumDao()
            .getAlbumById(albumId)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toAlbum(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of all participating track artists for an album. */
  fun getArtistsForAlbum(albumId: Long): Flow<List<Artist>> {
    return database.albumDao()
            .getArtistsForAlbum(albumId)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toArtist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single genre by its id. Emits null if not found. */
  fun getGenreById(genreId: Long): Flow<Genre?> {
    return database.genreDao()
            .getGenreById(genreId)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toGenre(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single composer by their id. Emits null if not found. */
  fun getComposerById(composerId: Long): Flow<Composer?> {
    return database.composerDao()
            .getComposerById(composerId)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toComposer(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single lyricist by their id. Emits null if not found. */
  fun getLyricistById(lyricistId: Long): Flow<Lyricist?> {
    return database.lyricistDao()
            .getLyricistById(lyricistId)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toLyricist(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single year entry by its year value. Emits null if not found. */
  fun getYearByValue(year: Int): Flow<Year?> {
    return database.yearDao()
            .getYearByValue(year)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toYear(it) } }
            .flowOn(Dispatchers.Default)
  }

  /** Live stream of a single folder entry by its path. Emits null if not found. */
  fun getFolderByPath(folderPath: String): Flow<Folder?> {
    return database.folderDao()
            .getFolderByPath(folderPath)
            .distinctUntilChanged()
            .map { it?.let { ModelMapper.toFolder(it) } }
            .flowOn(Dispatchers.Default)
  }

  // ----------------------------------------
  // MOSAIC API (lazy, on-demand)
  // ----------------------------------------

  /**
   * Returns up to [limit] distinct cover art paths for the given track artist.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForTrackArtist(artistId: Long, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.artistDao().getMosaicPathsForTrackArtist(artistId, limit)
      }

  /**
   * Returns up to [limit] distinct cover art paths for the given album artist.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForAlbumArtist(artistId: Long, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.artistDao().getMosaicPathsForAlbumArtist(artistId, limit)
      }

  /**
   * Returns up to [limit] distinct cover art paths for the given genre.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForGenre(genreId: Long, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.genreDao().getMosaicPathsForGenre(genreId, limit)
      }

  /**
   * Returns up to [limit] distinct cover art paths for the given composer.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForComposer(composerId: Long, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.composerDao().getMosaicPathsForComposer(composerId, limit)
      }

  /**
   * Returns up to [limit] distinct cover art paths for the given lyricist.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForLyricist(lyricistId: Long, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.lyricistDao().getMosaicPathsForLyricist(lyricistId, limit)
      }

  /**
   * Returns up to [limit] distinct cover art paths for the given release year.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForYear(year: Int, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.yearDao().getMosaicPathsForYear(year, limit)
      }

  /**
   * Returns up to [limit] distinct cover art paths for the given folder.
   * Intended for use by Coil or any other image loader to draw a mosaic.
   */
  suspend fun getMosaicForFolder(folderPath: String, limit: Int): List<ArtPath> =
      withContext(Dispatchers.IO) {
          database.folderDao().getMosaicPathsForFolder(folderPath, limit)
      }

  // ----------------------------------------
  // MOSAIC API (live, for detail screens)
  // ----------------------------------------

  /** Live stream of up to [limit] distinct cover art paths for the given track artist. */
  fun getMosaicForTrackArtistFlow(artistId: Long, limit: Int): Flow<List<ArtPath>> =
      database.artistDao().getMosaicPathsForTrackArtistFlow(artistId, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  /** Live stream of up to [limit] distinct cover art paths for the given album artist. */
  fun getMosaicForAlbumArtistFlow(artistId: Long, limit: Int): Flow<List<ArtPath>> =
      database.artistDao().getMosaicPathsForAlbumArtistFlow(artistId, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  /** Live stream of up to [limit] distinct cover art paths for the given genre. */
  fun getMosaicForGenreFlow(genreId: Long, limit: Int): Flow<List<ArtPath>> =
      database.genreDao().getMosaicPathsForGenreFlow(genreId, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  /** Live stream of up to [limit] distinct cover art paths for the given composer. */
  fun getMosaicForComposerFlow(composerId: Long, limit: Int): Flow<List<ArtPath>> =
      database.composerDao().getMosaicPathsForComposerFlow(composerId, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  /** Live stream of up to [limit] distinct cover art paths for the given lyricist. */
  fun getMosaicForLyricistFlow(lyricistId: Long, limit: Int): Flow<List<ArtPath>> =
      database.lyricistDao().getMosaicPathsForLyricistFlow(lyricistId, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  /** Live stream of up to [limit] distinct cover art paths for the given release year. */
  fun getMosaicForYearFlow(year: Int, limit: Int): Flow<List<ArtPath>> =
      database.yearDao().getMosaicPathsForYearFlow(year, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  /** Live stream of up to [limit] distinct cover art paths for the given folder. */
  fun getMosaicForFolderFlow(folderPath: String, limit: Int): Flow<List<ArtPath>> =
      database.folderDao().getMosaicPathsForFolderFlow(folderPath, limit)
          .distinctUntilChanged()
          .flowOn(Dispatchers.IO)

  // ----------------------------------------
  // ON-DEMAND METADATA EXTRACTION
  // ----------------------------------------

  /**
   * Extracts embedded lyrics directly from the audio file on-demand. Thread-safe against native
   * crashes during tag edits.
   * @return The lyrics string, or null if no lyrics are found.
   */
  suspend fun getLyricsForTrack(track: Track): String? =
          withContext(Dispatchers.IO) {
            Catalog.ioMutex.withLock {
              val lyrics = TagLibHelper.extractLyrics(track.path)
              return@withContext lyrics.takeIf { it.isNotBlank() }
            }
          }

  /**
   * Extracts embedded artwork directly from the audio file on-demand. Thread-safe against native
   * crashes during tag edits.
   * @return A list of [TrackPicture] objects.
   */
  suspend fun getPicturesForTrack(track: Track): List<TrackPicture> =
          withContext(Dispatchers.IO) {
            Catalog.ioMutex.withLock {
              return@withContext TagLibHelper.extractPictures(track.path)
            }
          }

  /**
   * Extracts embedded artwork directly from the audio file path on-demand.
   * Thread-safe against native crashes during tag edits.
   * @param path The absolute file path to the audio file.
   * @return A list of [TrackPicture] objects.
   */
  suspend fun getPicturesForPath(path: String): List<TrackPicture> =
          withContext(Dispatchers.IO) {
            Catalog.ioMutex.withLock {
              return@withContext TagLibHelper.extractPictures(path)
            }
          }
}
