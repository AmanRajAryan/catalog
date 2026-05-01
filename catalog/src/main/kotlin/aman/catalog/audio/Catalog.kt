package aman.catalog.audio

import aman.catalog.audio.internal.*
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.internal.scanner.ScannerService
import aman.catalog.audio.models.*
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

/**
 * The main public API for the Catalog library.
 *
 * Acts as a facade over the database, scanner, search engine, and sub-managers.
 * All public functions are safe to call from any coroutine context.
 *
 * Must be initialized once via [initialize] before any other function is called.
 */
object Catalog {

    private var database: CatalogDatabase? = null
    private var scanner: ScannerService? = null
    private var searchEngine: SearchEngine? = null
    private var configStore: ConfigStore? = null
    private var mediaWatcher: MediaWatcher? = null

    internal val ioMutex = Mutex()

    private val _configFlow = MutableStateFlow(CatalogConfig())

    /** Live stream of the current [CatalogConfig]. Collect this to react to config changes. */
    val configFlow: StateFlow<CatalogConfig> = _configFlow.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    /** Emits `true` while a scan or config change is in progress, `false` when idle. */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanResult = MutableStateFlow<ScanResult?>(null)
    /** Emits the result of the last scan that detected changes. Null until first meaningful scan. */
    val lastScanResult: StateFlow<ScanResult?> = _lastScanResult.asStateFlow()

    private val _lastConfigChangeResult = MutableStateFlow<ConfigChangeResult?>(null)
    /** Emits the result of the last config change application. Null until first config change. */
    val lastConfigChangeResult: StateFlow<ConfigChangeResult?> = _lastConfigChangeResult.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ----------------------------------------
    // SUB-MANAGERS
    // ----------------------------------------

    private fun requireDb(): CatalogDatabase {
        return database ?: throw IllegalStateException("Catalog is not initialized!")
    }

    /** Manages all playlist creation, modification, and import/export logic. */
    val playlists = CatalogPlaylists { requireDb() }

    /** Manages playback tracking, recently played, and most played lists. */
    val stats = CatalogStats { requireDb() }

    /** Manages core browsing flows (Tracks, Artists, Albums, etc.) and navigation. */
    val library = CatalogLibrary { requireDb() }

    /** Manages user preferences, including Favorites and Ignored Folders. */
    val user = CatalogUser(
        databaseProvider = { requireDb() },
        configProvider = { _configFlow.value },
        configUpdater = { newConfig -> updateConfig(newConfig) }
    )

    // ----------------------------------------
    // INITIALIZATION
    // ----------------------------------------

    fun initialize(context: Context, defaultConfig: CatalogConfig = CatalogConfig()) {
        if (database != null) return

        configStore = ConfigStore(context)
        val loadedConfig = configStore?.load() ?: defaultConfig

        _configFlow.value = loadedConfig
        configStore?.save(loadedConfig)

        database = Room.databaseBuilder(
            context.applicationContext,
            CatalogDatabase::class.java,
            "music_catalog.db"
        )
            .build()

        scanner = ScannerService(context, database!!, loadedConfig)
        searchEngine = SearchEngine(database!!)
        mediaWatcher = MediaWatcher(context, scope) {
            scope.launch { scan() }
        }

        mediaWatcher?.start()
        scope.launch { scan() }
    }

    fun shutdown(context: Context) {
        mediaWatcher?.stop()
        scope.cancel()
    }

    /**
     * Call this from your permission callback after the storage permission is granted.
     *
     * If [initialize] was called before the permission was granted, the initial scan will have
     * returned zero results. This method triggers a fresh scan to pick up all audio files now
     * that access is available.
     *
     * Safe to call even if the library already has tracks — the scan will exit immediately
     * if nothing has changed. Does nothing if [initialize] has not been called yet.
     */
    fun onPermissionGranted() {
        if (database == null) return
        scope.launch { scan() }
    }

    // ----------------------------------------
    // CORE LIFECYCLE & CONFIGURATION
    // ----------------------------------------

    fun updateConfig(newConfig: CatalogConfig) {
        requireInit()

        val oldConfig = _configFlow.value
        val oldIgnores = oldConfig.scannerIgnores
        val newIgnores = newConfig.scannerIgnores

        _configFlow.value = newConfig
        configStore?.save(newConfig)
        scanner?.updateConfig(newConfig)

        scope.launch {
            val splittersChanged = oldConfig.splitExceptions != newConfig.splitExceptions ||
                    oldConfig.customSplitters != newConfig.customSplitters

            if (splittersChanged) {
                applyConfigChanges()
            }

            val addedIgnores = newIgnores.filter { it !in oldIgnores }
            if (addedIgnores.isNotEmpty()) {
                database?.let { db ->
                    db.withTransaction {
                        addedIgnores.forEach { path ->
                            db.trackDao().deleteTracksByPathPrefix(path)
                        }
                        performGarbageCollection(db)
                    }
                }
            }

            val removedIgnores = oldIgnores.filter { it !in newIgnores }
            val durationChanged = oldConfig.minDurationMs != newConfig.minDurationMs

            if (removedIgnores.isNotEmpty() || durationChanged) {
                scan()
            }
        }
    }

    suspend fun scan(force: Boolean = false) {
        requireInit()
        _isScanning.value = true
        try {
            if (force) {
                withContext(Dispatchers.IO) {
                    database?.clearAllTables()
                    val ignoredPaths = _configFlow.value.scannerIgnores
                    if (ignoredPaths.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        ignoredPaths.forEach { path ->
                            database?.ignoredFolderDao()?.ignoreFolder(
                                IgnoredFolderEntity(path = path, dateAdded = now)
                            )
                        }
                    }
                }
            }
            val result = scanner?.scan()
            if (result != null) {
                _lastScanResult.value = result
            }
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun clear() {
        requireInit()
        withContext(Dispatchers.IO) {
            database?.let { db ->
                db.withTransaction {
                    db.trackDao().clearAll()
                    performGarbageCollection(db)
                }
            }
        }
    }

    // ----------------------------------------
    // GLOBAL SEARCH & SYNC
    // ----------------------------------------

    fun search(query: String, filters: Set<SearchFilter>): Flow<SearchResult> {
        requireInit()
        return searchEngine!!.search(query, filters)
    }

    fun notifyFileChanged(path: String) {
        requireInit()
        scope.launch { scanner?.rescanSingleFile(path) }
    }

    // ----------------------------------------
    // INTERNAL HELPERS
    // ----------------------------------------

    private fun requireInit() {
        if (database == null) throw IllegalStateException("Catalog is not initialized!")
    }

    private suspend fun applyConfigChanges() {
        _isScanning.value = true
        try {
            val result = scanner?.applyConfigChanges()
            if (result != null) _lastConfigChangeResult.value = result
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun performGarbageCollection(db: CatalogDatabase) {
        db.albumDao().deleteEmptyAlbums()
        db.artistDao().deleteEmptyArtists()
        db.genreDao().deleteEmptyGenres()
        db.composerDao().deleteEmptyComposers()
        db.lyricistDao().deleteEmptyLyricists()
    }
}
