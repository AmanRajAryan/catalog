# Getting Started

## Table of Contents

1. [Permissions](#permissions)
2. [Installation](#installation)
3. [Initialization](#initialization)
4. [Handling permissions at runtime](#handling-permissions-at-runtime)
5. [The sub-manager pattern](#the-sub-manager-pattern)
6. [Shutdown](#shutdown)

---

## Permissions

Catalog reads audio files and queries `MediaStore`. The required permission depends on the Android version running on the device.

| API Level | Permission |
|---|---|
| API 32 and below | `android.permission.READ_EXTERNAL_STORAGE` |
| API 33+ (Android 13+) | `android.permission.READ_MEDIA_AUDIO` |

Declare both in your `AndroidManifest.xml` — Android will only apply the one relevant to the device:

```xml
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<uses-permission
    android:name="android.permission.READ_MEDIA_AUDIO" />
```

---

## Installation

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
     implementation("io.github.amanrajaryan:Catalog:1.0.0")
}
```

---

## Initialization

Call `Catalog.initialize()` once in your `Application.onCreate()`. This is the only required setup step.

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Catalog.initialize(context = this)
    }
}
```

Don't forget to register your `Application` subclass in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ... >
```

### With configuration

Pass a `CatalogConfig` to customize scanning behavior at startup. If you've previously called `initialize()` with a config, the library loads the last saved config automatically and `defaultConfig` is only used on the very first launch.

```kotlin
Catalog.initialize(
    context = this,
    defaultConfig = CatalogConfig(
        splitExceptions = listOf("AC/DC", "Earth, Wind & Fire"),
        scannerIgnores = listOf("/storage/emulated/0/WhatsApp/Media"),
        minDurationMs = 30_000L
    )
)
```

See [configuration.md](configuration.md) for a full explanation of every config option.

### What happens on initialize()

1. The previously saved `CatalogConfig` is loaded from `SharedPreferences`. If none exists, `defaultConfig` is used and immediately persisted — so first-launch config is saved automatically without any extra call.
2. The Room database (`music_catalog.db`) is built. It uses `fallbackToDestructiveMigration` — if the schema changes in a future library version, the database is wiped and rebuilt from scratch on the next scan. Playlists, favorites, and play counts are not preserved across schema changes. If this matters to your app, pin the library version.
3. The scanner, search engine, and `MediaStore` content observer are wired up.
4. The content observer starts watching for file system changes. When files are added, removed, or modified on device, it triggers a debounced scan after a 2-second delay — multiple rapid changes are batched into a single scan.
5. An initial scan runs in the background on `Dispatchers.IO`.

Calling `initialize()` more than once is safe — subsequent calls are silently ignored.

---

## Handling permissions at runtime

`Catalog.initialize()` can be called before the storage permission is granted — it won't crash, but the initial scan will find nothing because `MediaStore` is inaccessible.

To handle this, call `Catalog.onPermissionGranted()` from your permission callback after the user grants access. It triggers a fresh scan automatically. If the library already has tracks (permission was already granted), it's a no-op.

```kotlin
val requestPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        Catalog.onPermissionGranted()
    }
}

// Determine the right permission for the running API level
val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

requestPermission.launch(permission)
```

This means you can safely call `initialize()` in `Application.onCreate()` unconditionally, and let `onPermissionGranted()` handle the scan trigger whenever the user actually grants access — whether that's on first launch, after a permission rationale screen, or from a settings screen.

---

## The sub-manager pattern

All library functionality is accessed through `Catalog` and its sub-managers. Each sub-manager owns a specific domain:

| Sub-manager | Access via | Responsibilities |
|---|---|---|
| Library | `Catalog.library` | Browsing tracks, artists, albums, genres, composers, lyricists, years, folders, and all navigation queries |
| Playlists | `Catalog.playlists` | Creating, editing, and managing playlists |
| Stats | `Catalog.stats` | Logging playback, observing recently played and most played |
| User | `Catalog.user` | Favorites and ignored folders |

`Catalog` itself handles initialization, scanning, configuration, and global search.

```kotlin
// Browsing
Catalog.library.getTracks()
Catalog.library.getAlbums()
Catalog.library.getTracksForAlbum(albumId)

// Playlists
Catalog.playlists.createPlaylist("My Playlist")
Catalog.playlists.addTrackToPlaylist(playlistId, trackId)

// Stats
Catalog.stats.logPlayback(trackId, durationMs)
Catalog.stats.getRecentlyPlayedTracks()

// User data
Catalog.user.toggleFavorite(trackId)
Catalog.user.ignoreFolder("/storage/emulated/0/Podcasts")

// Search
Catalog.search("pink floyd", setOf(SearchFilter.TRACKS, SearchFilter.ALBUMS))
```

---

## Shutdown

`Catalog.shutdown()` stops the `MediaStore` content observer and cancels all background coroutines. Under normal app usage you don't need to call this — Android handles cleanup when the process ends.

The right place to call it, if needed, is `Application.onTerminate()` — not from an Activity's `onDestroy()`. Because `Catalog` is a singleton that lives for the entire process lifetime, shutting it down from an Activity would tear down the library while other parts of the app may still be using it.

Its main use case is in instrumented tests where you need to fully reset library state between test cases:

```kotlin
@After
fun tearDown() {
    Catalog.shutdown(context)
}
```

After calling `shutdown()`, you can call `initialize()` again to bring the library back up.


