package aman.catalogtest

import android.content.Context
import aman.catalog.audio.Catalog
import aman.catalog.audio.CatalogConfig
import aman.catalog.audio.CatalogEditor
import aman.catalog.audio.ConfigChangeResult
import aman.catalog.audio.ScanResult
import aman.catalog.audio.models.Album
import aman.catalog.audio.models.Artist
import aman.catalog.audio.models.Composer
import aman.catalog.audio.models.Folder
import aman.catalog.audio.models.Genre
import aman.catalog.audio.models.Lyricist
import aman.catalog.audio.models.MatchedAlbum
import aman.catalog.audio.models.MatchedGenre
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.PlaylistItem
import aman.catalog.audio.models.SearchFilter
import aman.catalog.audio.models.SearchResult
import aman.catalog.audio.models.ContextualSortOption
import aman.catalog.audio.models.FavoritesInfo
import aman.catalog.audio.models.SortOption
import aman.catalog.audio.models.TrackPicture
import aman.catalog.audio.models.Track
import aman.catalog.audio.models.Year
import aman.catalog.audio.models.Playlist
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

// ─────────────────────────────────────────────
// Fallback display names for untagged entries
// ─────────────────────────────────────────────

private const val UNKNOWN_ARTIST   = "Unknown Artist"
private const val UNKNOWN_ALBUM    = "Unknown Album"
private const val UNKNOWN_GENRE    = "Unknown Genre"
private const val UNKNOWN_COMPOSER = "Unknown Composer"
private const val UNKNOWN_LYRICIST = "Unknown Lyricist"

private fun Artist.withFallbackName()   = if (name.isBlank()) copy(name = UNKNOWN_ARTIST)   else this
private fun Album.withFallbackTitle()   = if (title.isBlank()) copy(title = UNKNOWN_ALBUM)  else this
private fun Genre.withFallbackName()    = if (name.isBlank()) copy(name = UNKNOWN_GENRE)    else this
private fun Composer.withFallbackName() = if (name.isBlank()) copy(name = UNKNOWN_COMPOSER) else this
private fun Lyricist.withFallbackName() = if (name.isBlank()) copy(name = UNKNOWN_LYRICIST) else this

private const val MOSAIC_LIMIT = 12

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel : ViewModel() {

    // ─────────────────────────────────────────────
    // Sort state
    // ─────────────────────────────────────────────

    private val _trackSort       = MutableStateFlow(SortOption.Track.TITLE_ASC)
    private val _albumSort       = MutableStateFlow(SortOption.Album.TITLE_ASC)
    private val _artistSort      = MutableStateFlow(SortOption.Artist.NAME_ASC)
    private val _albumArtistSort = MutableStateFlow(SortOption.Artist.NAME_ASC)
    private val _genreSort       = MutableStateFlow(SortOption.Genre.NAME_ASC)
    private val _composerSort    = MutableStateFlow(SortOption.Composer.NAME_ASC)
    private val _lyricistSort    = MutableStateFlow(SortOption.Lyricist.NAME_ASC)
    private val _folderSort      = MutableStateFlow(SortOption.Folder.NAME_ASC)
    private val _yearSort        = MutableStateFlow(SortOption.Year.YEAR_DESC)
    private val _playlistSort      = MutableStateFlow(SortOption.Playlist.NAME_ASC)
    private val _playlistTrackSort = MutableStateFlow(ContextualSortOption.PlaylistTrack.USER_DEFINED)
    private val _favoritesSort     = MutableStateFlow(ContextualSortOption.FavoriteTrack.USER_DEFINED)

    val trackSort:       StateFlow<SortOption.Track>    = _trackSort.asStateFlow()
    val albumSort:       StateFlow<SortOption.Album>    = _albumSort.asStateFlow()
    val artistSort:      StateFlow<SortOption.Artist>   = _artistSort.asStateFlow()
    val albumArtistSort: StateFlow<SortOption.Artist>   = _albumArtistSort.asStateFlow()
    val genreSort:       StateFlow<SortOption.Genre>    = _genreSort.asStateFlow()
    val composerSort:    StateFlow<SortOption.Composer> = _composerSort.asStateFlow()
    val lyricistSort:    StateFlow<SortOption.Lyricist> = _lyricistSort.asStateFlow()
    val folderSort:      StateFlow<SortOption.Folder>   = _folderSort.asStateFlow()
    val yearSort:        StateFlow<SortOption.Year>     = _yearSort.asStateFlow()
    val playlistSort:      StateFlow<SortOption.Playlist>                        = _playlistSort.asStateFlow()
    val playlistTrackSort: StateFlow<ContextualSortOption.PlaylistTrack>         = _playlistTrackSort.asStateFlow()
    val favoritesSort:     StateFlow<ContextualSortOption.FavoriteTrack>         = _favoritesSort.asStateFlow()

    fun setTrackSort(sort: SortOption.Track)        { _trackSort.value       = sort }
    fun setAlbumSort(sort: SortOption.Album)        { _albumSort.value       = sort }
    fun setArtistSort(sort: SortOption.Artist)      { _artistSort.value      = sort }
    fun setAlbumArtistSort(sort: SortOption.Artist) { _albumArtistSort.value = sort }
    fun setGenreSort(sort: SortOption.Genre)        { _genreSort.value       = sort }
    fun setComposerSort(sort: SortOption.Composer)  { _composerSort.value    = sort }
    fun setLyricistSort(sort: SortOption.Lyricist)  { _lyricistSort.value    = sort }
    fun setFolderSort(sort: SortOption.Folder)      { _folderSort.value      = sort }
    fun setYearSort(sort: SortOption.Year)          { _yearSort.value        = sort }
    fun setPlaylistSort(sort: SortOption.Playlist)                              { _playlistSort.value      = sort }
    fun setPlaylistTrackSort(sort: ContextualSortOption.PlaylistTrack)          { _playlistTrackSort.value = sort }
    fun setFavoritesSort(sort: ContextualSortOption.FavoriteTrack)              { _favoritesSort.value     = sort }

    // ─────────────────────────────────────────────
    // Library data
    // ─────────────────────────────────────────────

    val tracks: StateFlow<List<Track>> = _trackSort
        .flatMapLatest { Catalog.library.getTracks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = _albumSort
        .flatMapLatest { Catalog.library.getAlbums(it) }
        .map { list -> list.map { it.withFallbackTitle() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = _artistSort
        .flatMapLatest { Catalog.library.getArtists(it) }
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumArtists: StateFlow<List<Artist>> = _albumArtistSort
        .flatMapLatest { Catalog.library.getAlbumArtists(it) }
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<Genre>> = _genreSort
        .flatMapLatest { Catalog.library.getGenres(it) }
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val composers: StateFlow<List<Composer>> = _composerSort
        .flatMapLatest { Catalog.library.getComposers(it) }
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lyricists: StateFlow<List<Lyricist>> = _lyricistSort
        .flatMapLatest { Catalog.library.getLyricists(it) }
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = _folderSort
        .flatMapLatest { Catalog.library.getFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val years: StateFlow<List<Year>> = _yearSort
        .flatMapLatest { Catalog.library.getYears(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = _playlistSort
        .flatMapLatest { Catalog.playlists.getPlaylists(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Track>> = _favoritesSort
        .flatMapLatest { Catalog.user.getFavoriteTracks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesInfo: StateFlow<FavoritesInfo> = Catalog.user.favoritesInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FavoritesInfo(trackCount = 0))

    val favoritesMosaicPaths: StateFlow<List<ArtPath>> = Catalog.user.getMosaicForFavoritesFlow(MOSAIC_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─────────────────────────────────────────────
    // Mosaic paths (on-demand, for list + detail screens)
    // ─────────────────────────────────────────────

    suspend fun getMosaicForTrackArtist(id: Long)  = Catalog.library.getMosaicForTrackArtist(id, MOSAIC_LIMIT)
    fun getMosaicForTrackArtistFlow(id: Long)      = Catalog.library.getMosaicForTrackArtistFlow(id, MOSAIC_LIMIT)
    suspend fun getMosaicForAlbumArtist(id: Long)  = Catalog.library.getMosaicForAlbumArtist(id, MOSAIC_LIMIT)
    fun getMosaicForAlbumArtistFlow(id: Long)      = Catalog.library.getMosaicForAlbumArtistFlow(id, MOSAIC_LIMIT)
    suspend fun getMosaicForGenre(id: Long)         = Catalog.library.getMosaicForGenre(id, MOSAIC_LIMIT)
    fun getMosaicForGenreFlow(id: Long)            = Catalog.library.getMosaicForGenreFlow(id, MOSAIC_LIMIT)
    suspend fun getMosaicForComposer(id: Long)      = Catalog.library.getMosaicForComposer(id, MOSAIC_LIMIT)
    fun getMosaicForComposerFlow(id: Long)         = Catalog.library.getMosaicForComposerFlow(id, MOSAIC_LIMIT)
    suspend fun getMosaicForLyricist(id: Long)      = Catalog.library.getMosaicForLyricist(id, MOSAIC_LIMIT)
    fun getMosaicForLyricistFlow(id: Long)         = Catalog.library.getMosaicForLyricistFlow(id, MOSAIC_LIMIT)
    suspend fun getMosaicForYear(year: Int)         = Catalog.library.getMosaicForYear(year, MOSAIC_LIMIT)
    fun getMosaicForYearFlow(year: Int)            = Catalog.library.getMosaicForYearFlow(year, MOSAIC_LIMIT)
    suspend fun getMosaicForFolder(path: String)    = Catalog.library.getMosaicForFolder(path, MOSAIC_LIMIT)
    fun getMosaicForFolderFlow(path: String)       = Catalog.library.getMosaicForFolderFlow(path, MOSAIC_LIMIT)
    suspend fun getMosaicForPlaylist(id: Long)      = Catalog.playlists.getMosaicForPlaylist(id, MOSAIC_LIMIT)
    fun getMosaicForPlaylistFlow(id: Long)         = Catalog.playlists.getMosaicForPlaylistFlow(id, MOSAIC_LIMIT)

    // ─────────────────────────────────────────────
    // Home screen — pre-canned track lists
    // ─────────────────────────────────────────────

    val recentlyAdded: StateFlow<List<Track>> = Catalog.library.getRecentlyAddedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedAlbums: StateFlow<List<Album>> = Catalog.library.getRecentlyAddedAlbums()
        .map { list -> list.map { it.withFallbackTitle() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Track>> = Catalog.stats.getMostPlayedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = Catalog.stats.getRecentlyPlayedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─────────────────────────────────────────────
    // Home screen — recently played by category
    // ─────────────────────────────────────────────

    val recentlyPlayedAlbums: StateFlow<List<Album>> = Catalog.stats.getRecentlyPlayedAlbums()
        .map { list -> list.map { it.withFallbackTitle() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedArtists: StateFlow<List<Artist>> = Catalog.stats.getRecentlyPlayedArtists()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedAlbumArtists: StateFlow<List<Artist>> = Catalog.stats.getRecentlyPlayedAlbumArtists()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedGenres: StateFlow<List<Genre>> = Catalog.stats.getRecentlyPlayedGenres()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedComposers: StateFlow<List<Composer>> = Catalog.stats.getRecentlyPlayedComposers()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedLyricists: StateFlow<List<Lyricist>> = Catalog.stats.getRecentlyPlayedLyricists()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedYears: StateFlow<List<Year>> = Catalog.stats.getRecentlyPlayedYears()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedFolders: StateFlow<List<Folder>> = Catalog.stats.getRecentlyPlayedFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─────────────────────────────────────────────
    // Home screen — most played by category
    // ─────────────────────────────────────────────

    val mostPlayedAlbums: StateFlow<List<Album>> = Catalog.stats.getMostPlayedAlbums()
        .map { list -> list.map { it.withFallbackTitle() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedArtists: StateFlow<List<Artist>> = Catalog.stats.getMostPlayedArtists()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedAlbumArtists: StateFlow<List<Artist>> = Catalog.stats.getMostPlayedAlbumArtists()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedGenres: StateFlow<List<Genre>> = Catalog.stats.getMostPlayedGenres()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedComposers: StateFlow<List<Composer>> = Catalog.stats.getMostPlayedComposers()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedLyricists: StateFlow<List<Lyricist>> = Catalog.stats.getMostPlayedLyricists()
        .map { list -> list.map { it.withFallbackName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedYears: StateFlow<List<Year>> = Catalog.stats.getMostPlayedYears()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedFolders: StateFlow<List<Folder>> = Catalog.stats.getMostPlayedFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─────────────────────────────────────────────
    // Config
    // ─────────────────────────────────────────────

    val config: StateFlow<CatalogConfig> = Catalog.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogConfig())

    val isScanning: StateFlow<Boolean> = Catalog.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastScanResult: StateFlow<ScanResult?> = Catalog.lastScanResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastConfigChangeResult: StateFlow<ConfigChangeResult?> = Catalog.lastConfigChangeResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ─────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<SearchResult> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(SearchResult(query = ""))
            else Catalog.search(query = query, filters = SearchFilter.entries.toSet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResult(query = ""))

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ─────────────────────────────────────────────
    // Category track / album feeds
    // ─────────────────────────────────────────────

    suspend fun readTags(track: Track): Map<String, String> =
        CatalogEditor.readTags(track)

    suspend fun getPicturesForTrack(track: Track): List<TrackPicture> =
        Catalog.library.getPicturesForTrack(track)

    // ─────────────────────────────────────────────
    // Single-item lookups (by ID / natural key)
    // ─────────────────────────────────────────────

    fun getTrackArtistById(artistId: Long): Flow<Artist?> =
        Catalog.library.getTrackArtistById(artistId)

    fun getAlbumArtistById(artistId: Long): Flow<Artist?> =
        Catalog.library.getAlbumArtistById(artistId)

    fun getAlbumById(albumId: Long): Flow<Album?> =
        Catalog.library.getAlbumById(albumId)

    fun getArtistsForAlbum(albumId: Long): Flow<List<Artist>> =
        Catalog.library.getArtistsForAlbum(albumId)

    fun getGenreById(genreId: Long): Flow<Genre?> =
        Catalog.library.getGenreById(genreId)

    fun getComposerById(composerId: Long): Flow<Composer?> =
        Catalog.library.getComposerById(composerId)

    fun getLyricistById(lyricistId: Long): Flow<Lyricist?> =
        Catalog.library.getLyricistById(lyricistId)

    fun getYearByValue(year: Int): Flow<Year?> =
        Catalog.library.getYearByValue(year)

    fun getFolderByPath(folderPath: String): Flow<Folder?> =
        Catalog.library.getFolderByPath(folderPath)

    fun getAlbumsForAlbumArtist(artistId: Long, sort: ContextualSortOption.Album = ContextualSortOption.Album.YEAR_DESC): Flow<List<Album>> =
        Catalog.library.getAlbumsForAlbumArtist(artistId, sort)

    fun getAppearsOnAlbums(artistId: Long, sort: ContextualSortOption.Album = ContextualSortOption.Album.YEAR_DESC): Flow<List<MatchedAlbum>> =
        Catalog.library.getAppearsOnAlbumsForTrackArtist(artistId, sort)

    fun getAlbumsForGenre(genreId: Long, sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC): Flow<List<MatchedAlbum>> =
        Catalog.library.getAlbumsForGenre(genreId, sort)

    fun getAlbumsForComposer(composerId: Long, sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC): Flow<List<MatchedAlbum>> =
        Catalog.library.getAlbumsForComposer(composerId, sort)

    fun getAlbumsForLyricist(lyricistId: Long, sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC): Flow<List<MatchedAlbum>> =
        Catalog.library.getAlbumsForLyricist(lyricistId, sort)

    fun getAlbumsForFolder(folderPath: String, sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC): Flow<List<MatchedAlbum>> =
        Catalog.library.getAlbumsForFolder(folderPath, sort)

    fun getAlbumsForYear(year: Int, sort: ContextualSortOption.Album = ContextualSortOption.Album.TITLE_ASC): Flow<List<MatchedAlbum>> =
        Catalog.library.getAlbumsForYear(year, sort)

    fun getGenresForAlbum(albumId: Long, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForAlbum(albumId, sort)

    fun getGenresForTrackArtist(artistId: Long, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForTrackArtist(artistId, sort)

    fun getGenresForAlbumArtist(artistId: Long, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForAlbumArtist(artistId, sort)

    fun getGenresForComposer(composerId: Long, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForComposer(composerId, sort)

    fun getGenresForLyricist(lyricistId: Long, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForLyricist(lyricistId, sort)

    fun getGenresForFolder(folderPath: String, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForFolder(folderPath, sort)

    fun getGenresForYear(year: Int, sort: ContextualSortOption.Genre = ContextualSortOption.Genre.NAME_ASC): Flow<List<MatchedGenre>> =
        Catalog.library.getGenresForYear(year, sort)

    fun getTracksForCategory(
        category: String,
        id: Long,
        stringId: String = "",
        sort: ContextualSortOption.Track = ContextualSortOption.Track.TITLE_ASC
    ): Flow<List<Track>> {
        return when (category) {
            "album"        -> Catalog.library.getTracksForAlbum(id, sort)
            "artist"       -> Catalog.library.getTracksForArtist(id, sort)
            "album_artist" -> Catalog.library.getTracksForAlbumArtist(id, sort)
            "genre"        -> Catalog.library.getTracksForGenre(id, sort)
            "composer"     -> Catalog.library.getTracksForComposer(id, sort)
            "lyricist"     -> Catalog.library.getTracksForLyricist(id, sort)
            "year"         -> Catalog.library.getTracksForYear(id.toInt(), sort)
            "folder"       -> Catalog.library.getTracksForFolder(stringId, sort)
            else           -> flowOf(emptyList())
        }
    }

    fun getPlaylistItems(
        playlistId: Long,
        sort: ContextualSortOption.PlaylistTrack = ContextualSortOption.PlaylistTrack.USER_DEFINED
    ): Flow<List<PlaylistItem>> =
        Catalog.playlists.getPlaylistTracks(playlistId, sort)

    fun getPlaylist(playlistId: Long): Flow<Playlist?> =
        Catalog.playlists.getPlaylistById(playlistId)

    // ─────────────────────────────────────────────
    // Library actions
    // ─────────────────────────────────────────────

    fun scan(force: Boolean = false) {
        viewModelScope.launch { Catalog.scan(force) }
    }

    fun clear() {
        viewModelScope.launch { Catalog.clear() }
    }

    // ─────────────────────────────────────────────
    // Config — min duration
    // ─────────────────────────────────────────────

    fun updateMinDuration(durationMs: Long) {
        Catalog.updateConfig(config.value.copy(minDurationMs = durationMs))
    }

    // ─────────────────────────────────────────────
    // Config — custom splitters
    // ─────────────────────────────────────────────

    fun addCustomSplitter(splitter: String) {
        if (splitter.isBlank()) return
        val current = config.value
        if (!current.customSplitters.contains(splitter)) {
            Catalog.updateConfig(current.copy(customSplitters = current.customSplitters + splitter))
        }
    }

    fun removeCustomSplitter(splitter: String) {
        val current = config.value
        Catalog.updateConfig(current.copy(customSplitters = current.customSplitters - splitter))
    }

    // ─────────────────────────────────────────────
    // Config — split exceptions
    // ─────────────────────────────────────────────

    fun addSplitException(exception: String) {
        if (exception.isBlank()) return
        val current = config.value
        if (!current.splitExceptions.contains(exception)) {
            Catalog.updateConfig(current.copy(splitExceptions = current.splitExceptions + exception))
        }
    }

    fun removeSplitException(exception: String) {
        val current = config.value
        Catalog.updateConfig(current.copy(splitExceptions = current.splitExceptions - exception))
    }

    // ─────────────────────────────────────────────
    // Config — ignored folders
    // ─────────────────────────────────────────────

    fun addIgnoredFolder(path: String) {
        viewModelScope.launch { Catalog.user.ignoreFolder(path) }
    }

    fun removeIgnoredFolder(path: String) {
        viewModelScope.launch { Catalog.user.unignoreFolder(path) }
    }

    // ─────────────────────────────────────────────
    // Favorites
    // ─────────────────────────────────────────────

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch { Catalog.user.toggleFavorite(trackId) }
    }

    fun moveFavoriteEntry(trackId: Long, fromPosition: Int, toPosition: Int) {
        viewModelScope.launch { Catalog.user.moveFavoriteEntry(trackId, fromPosition, toPosition) }
    }

    fun reorderFavorites(updates: Map<Long, Int>) {
        viewModelScope.launch { Catalog.user.reorderFavorites(updates) }
    }

    // ─────────────────────────────────────────────
    // Playback stats
    // ─────────────────────────────────────────────

    fun simulatePlay(track: Track, durationMs: Long = track.durationMs) {
        viewModelScope.launch { Catalog.stats.logPlayback(track.id, durationMs) }
    }

    // ─────────────────────────────────────────────
    // Playlist management
    // ─────────────────────────────────────────────

    fun createPlaylist(name: String) {
        viewModelScope.launch { Catalog.playlists.createPlaylist(name) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { Catalog.playlists.deletePlaylist(playlistId) }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch { Catalog.playlists.renamePlaylist(playlistId, newName) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { Catalog.playlists.addTrackToPlaylist(playlistId, trackId) }
    }

    fun removeEntryFromPlaylist(entryId: Long) {
        viewModelScope.launch { Catalog.playlists.removeEntryFromPlaylist(entryId) }
    }

    fun savePlaylistOrder(playlistId: Long, newList: List<PlaylistItem>) {
        val updates: Map<Long, Int> = newList.mapIndexedNotNull { index, item ->
            if (item.sortOrder != index) item.entryId to index else null
        }.toMap()
        if (updates.isNotEmpty()) {
            viewModelScope.launch { Catalog.playlists.reorderPlaylist(playlistId, updates) }
        }
    }

    fun moveEntryInPlaylist(playlistId: Long, entryId: Long, fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            Catalog.playlists.moveEntryInPlaylist(playlistId, entryId, fromPosition, toPosition)
        }
    }

    suspend fun exportPlaylist(playlistId: Long, file: File): Boolean =
        Catalog.playlists.exportPlaylist(playlistId, file)

    suspend fun importPlaylist(file: File): Long =
        Catalog.playlists.importPlaylist(file)

    fun stressTestPlaylist(playlistId: Long, trackId: Long, count: Int = 1000) {
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until count) {
                Catalog.playlists.addTrackToPlaylist(playlistId, trackId)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Tag editing
    // ─────────────────────────────────────────────

    suspend fun updateTrackTags(
        context: Context,
        track: Track,
        newTags: Map<String, String>,
        allowMediaStoreFallback: Boolean = true
    ): CatalogEditor.EditResult {
        return CatalogEditor.updateTrack(
            context                 = context,
            track                   = track,
            newTags                 = newTags,
            allowMediaStoreFallback = allowMediaStoreFallback
        )
    }

    suspend fun stressTestConcurrency(
        context: Context,
        track: Track,
        newTags: Map<String, String>
    ): CatalogEditor.EditResult {
        scan(force = false)
        return CatalogEditor.updateTrack(context, track, newTags)
    }
}
