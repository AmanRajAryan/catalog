# Architecture

A high-level overview of how Catalog is structured and how data flows through the system.

---

## Table of Contents

1. [Package structure](#package-structure)
2. [Data flow](#data-flow)
3. [The facade pattern](#the-facade-pattern)
4. [Sub-managers](#sub-managers)
5. [The internal boundary](#the-internal-boundary)

---

## Package structure

```
aman.catalog.audio/
├── Catalog.kt                  — Main facade, singleton entry point
├── CatalogConfig.kt            — Configuration data class
├── CatalogEditor.kt            — Tag read/write (standalone object)
├── CatalogLibrary.kt           — Browsing and navigation flows
├── CatalogPlaylists.kt         — Playlist management
├── CatalogScanEvents.kt        — ScanResult and ConfigChangeResult models
├── CatalogStats.kt             — Playback tracking
├── CatalogUser.kt              — Favorites and ignored folders
│
├── models/                     — All public data models (Track, Album, Artist, etc.)
│
└── internal/
    ├── ConfigStore.kt          — SharedPreferences persistence for CatalogConfig
    ├── MediaWatcher.kt         — MediaStore content observer with debounce
    ├── ModelMapper.kt          — Entity → public model conversion
    ├── SearchEngine.kt         — Multi-category search implementation
    │
    ├── database/
    │   ├── CatalogDatabase.kt  — Room database definition
    │   ├── daos/               — Data access objects (one per entity)
    │   ├── entities/           — Room entity definitions (schema)
    │   └── relations/          — TrackWithRelations (Room @Relation)
    │
    ├── scanner/
    │   ├── ScannerService.kt   — Scan coordinator (MediaStore diff + orchestration)
    │   ├── TrackIngestor.kt    — Track insert/update logic + field resolution
    │   ├── CompilationManager.kt — Compilation detection and folder re-linking
    │   └── SmartSplitter.kt   — Multi-value tag splitting with exception support
    │
    └── taglib/
        └── TagLib.kt           — TagLib wrapper + MediaExtractor fallbacks
```

---

## Data flow

### Scan flow

```
MediaStore
    ↓ queryMediaStoreSnapshots() — lightweight path + dateModified only
ScannerService (diff)
    ↓ new / changed / moved / deleted
ScannerService (fetch)
    ↓ queryMediaStore() — full metadata for affected files
TagLibHelper.extract()
    ↓ ExtendedMetadata
TrackIngestor.ingestNewTrack() / ingestUpdatedTrack()
    ↓ field resolution (TagLib wins over MediaStore)
Room Database (TrackEntity + junction tables)
    ↓ observed by DAOs
Flow<List<Track>> / Flow<List<Album>> / etc.
    ↓ mapped by ModelMapper
Public API (CatalogLibrary, CatalogStats, etc.)
```

### Edit flow

```
CatalogEditor.updateTrack()
    ↓ copy original → cache
TagLib writes to cache file (tags + artwork)
    ↓ ioMutex acquired
cache → original file committed
    ↓ ioMutex released
Catalog.notifyFileChanged()
    ↓ queues rescan (waits for mutex)
ScannerService.rescanSingleFile()
    ↓
TrackIngestor.ingestUpdatedTrack(trustTagLibFirst = true)
    ↓
Room Database updated
    ↓
All collecting Flows re-emit
```

### Config change flow

```
Catalog.updateConfig(newConfig)
    ↓ persisted to SharedPreferences
    ↓ if splitExceptions/customSplitters changed:
ScannerService.applyConfigChanges()
    ↓ re-splits all junction tables in chunks of 500
    ↓ re-evaluates compilation status for affected folders
    ↓ garbage collection
    ↓ if scannerIgnores added:
TrackDao.deleteTracksByPathPrefix() + GC
    ↓ if scannerIgnores removed or minDurationMs changed:
ScannerService.scan()
```

---

## The facade pattern

`Catalog` is a singleton object that acts as a facade over all internal components. Consumers interact only with `Catalog` and its sub-managers — they never touch the database, scanner, or TagLib directly.

```
Consumer
    ↓
Catalog (facade)
    ├── Catalog.library   → CatalogLibrary  → Database DAOs
    ├── Catalog.playlists → CatalogPlaylists → Database DAOs
    ├── Catalog.stats     → CatalogStats    → Database DAOs
    ├── Catalog.user      → CatalogUser     → Database DAOs + Config
    ├── Catalog.search()  → SearchEngine    → Database DAOs
    └── CatalogEditor     → TagLibHelper + Database DAOs
```

`Catalog` owns the database instance, the scanner, the search engine, the config store, and the media watcher. The sub-managers receive a database provider lambda (`() -> CatalogDatabase`) rather than a direct reference — this ensures they throw a clear `IllegalStateException` if accessed before `initialize()` rather than crashing with a null pointer.

---

## Sub-managers

Each sub-manager is constructed once in `Catalog`'s object initializer and holds a reference to the database provider. They are not initialized lazily — they exist as soon as the class loads, but the database provider will throw if `initialize()` hasn't been called.

```kotlin
val library = CatalogLibrary { requireDb() }
val playlists = CatalogPlaylists { requireDb() }
val stats = CatalogStats { requireDb() }
val user = CatalogUser(
    databaseProvider = { requireDb() },
    configProvider = { _configFlow.value },
    configUpdater = { newConfig -> updateConfig(newConfig) }
)
```

`CatalogUser` also receives `configProvider` and `configUpdater` so it can read and modify the active config when the user ignores or unignores a folder — without creating a circular dependency on `Catalog` itself.

`CatalogEditor` is a standalone `object`, not a sub-manager. It doesn't hold a database reference — it operates directly on files via TagLib and notifies the library of changes via `Catalog.notifyFileChanged()`.

---

## The internal boundary

Everything under `internal/` is package-private (`internal` visibility in Kotlin). Consumers cannot access DAOs, entities, `TrackIngestor`, `SmartSplitter`, or `TagLibHelper` directly. This boundary keeps the public API surface clean and allows internal implementation to change freely without breaking consumers.

The only crossing point from internal → public is `ModelMapper`, which converts internal entity types (`TrackEntity`, `AlbumEntity`, etc.) into public model types (`Track`, `Album`, etc.).
