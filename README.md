# Catalog

Catalog is an Android library that turns a device's local audio files into a fully queryable, reactive music library.

It scans `MediaStore`, enriches each file with deep metadata from TagLib (composers, lyricists, embedded artwork, bitrate, sample rate, and more), persists everything in a local Room database, and exposes the whole thing as Kotlin `Flow` — so your UI stays in sync automatically without any manual refresh logic.

---

## What it handles

- **Scanning** — syncs with `MediaStore` on launch and whenever files change on device, detecting new, modified, moved, and deleted files
- **Deep metadata** — reads tags directly from audio files via TagLib, filling in what MediaStore misses or gets wrong
- **Multi-value fields** — splits artist, genre, composer, and lyricist tags into individual linked entities, so "Drake feat. Future" becomes two navigable artists
- **Album disambiguation** — "Greatest Hits" by Queen and "Greatest Hits" by ABBA are always stored as separate albums, never merged
- **Compilation detection** — folders with many artists and one album name are automatically grouped under "Various Artists"
- **File move/rename tracking** — when a file moves on disk, play counts, favorites, and listening history are fully preserved
- **Playback stats** — play counts, skip counts, last played, and total listening time tracked per track, album, artist, genre, composer, lyricist, year, and folder
- **Playlists** — create, reorder, and manage playlists with M3U import and export
- **Favorites** — toggle, reorder, and observe the favorites collection
- **Tag editing** — read and write metadata and artwork directly on audio files, with full Android Scoped Storage handling
- **Global search** — search across tracks, artists, album artists, albums, genres, composers, lyricists, playlists, folders, and years in a single call

---

## Quick start

```kotlin
// 1. Add the required permission to AndroidManifest.xml
//    API 32 and below:
//    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
//    API 33+:
//    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

// 2. Initialize in Application.onCreate()
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Catalog.initialize(
            context = this,
            defaultConfig = CatalogConfig(
                splitExceptions = listOf("AC/DC", "Earth, Wind & Fire"),
                scannerIgnores = listOf("/storage/emulated/0/WhatsApp/Media")
            )
        )
    }
}

// 3. In your permission callback — call this after the permission is granted
if (isGranted) {
    Catalog.onPermissionGranted()
}

// 4. Collect in your ViewModel — once, never manually refresh
class LibraryViewModel : ViewModel() {
    val tracks = Catalog.library.getTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

// 5. Observe in your Fragment or Composable
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.tracks.collect { trackList ->
        adapter.submitList(trackList)
    }
}
```

---

## API surfaces

| Object | Purpose |
|---|---|
| `Catalog` | The main entry point. Initialization, scanning, config, and global search. |
| `Catalog.library` | Browsing flows — tracks, artists, albums, genres, composers, lyricists, years, folders, and all navigation queries. |
| `Catalog.playlists` | Playlist lifecycle — create, rename, delete, reorder, and M3U import/export. |
| `Catalog.stats` | Playback tracking — log plays and skips, observe recently played and most played lists. |
| `Catalog.user` | User preferences — favorites and ignored folders. |
| `CatalogEditor` | Tag editing — read and write metadata and artwork directly on audio files. |
| `CatalogConfig` | Configuration — split behavior, folder ignoring, and minimum track duration. |

---

## Documentation

| File | Contents |
|---|---|
| [getting-started.md](docs/getting-started.md) | Permissions, initialization, shutdown, and the sub-manager pattern |
| [configuration.md](docs/configuration.md) | `CatalogConfig` — split exceptions, custom splitters, scanner ignores, min duration |
| [scanning.md](docs/scanning.md) | `scan()`, `clear()`, `isScanning`, `lastScanResult`, `notifyFileChanged()` |
| [core-concepts.md](docs/core-concepts.md) | Flows, favorites, display strings vs rich lists, album disambiguation, thread safety |
| [models.md](docs/models.md) | All public data models — `Track`, `Album`, `Artist`, `Playlist`, and more |
| [library.md](docs/library.md) | Browsing the library — all `Catalog.library` flows and navigation queries |
| [sorting.md](docs/sorting.md) | `SortOption` and `ContextualSortOption` — every sort variant explained |
| [search.md](docs/search.md) | Global search — `Catalog.search()`, `SearchFilter`, multi-token matching |
| [playlists.md](docs/playlists.md) | Playlist management — create, edit, reorder, M3U import/export |
| [stats.md](docs/stats.md) | Playback tracking — `logPlayback()`, `logSkip()`, recently played, most played |
| [user.md](docs/user.md) | Favorites and ignored folders |
| [editor.md](docs/editor.md) | `CatalogEditor` — reading and writing tags and artwork, SAF permissions |

### Internal / contributor docs

| File | Contents |
|---|---|
| [architecture.md](internal/architecture.md) | High-level system overview and data flow |
| [scanner.md](internal/scanner.md) | MediaStore diffing, change detection, chunked writes |
| [ingestor.md](internal/ingestor.md) | Tag resolution, album grouping, junction tables |
| [compilation.md](internal/compilation.md) | Compilation detection and folder-scoping logic |
| [splitter.md](internal/splitter.md) | SmartSplitter — regex building, protection phase, split exceptions |
| [taglib.md](internal/taglib.md) | Metadata extraction, bitrate fallback chain, year parsing |
| [database.md](internal/database.md) | Room schema, entities, indices, garbage collection |
| [concurrency.md](internal/concurrency.md) | `ioMutex`, MediaWatcher debounce, coroutine scope |
| [models.md](internal/models.md) | Entity ↔ public model mapping, display vs raw fields |
