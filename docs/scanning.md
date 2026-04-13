# Scanning

Catalog keeps your library in sync with the device's audio files automatically. For most apps, you never need to think about scanning at all — it just happens. This doc explains what's happening under the hood and the manual controls available when you need them.

---

## Table of Contents

1. [How automatic scanning works](#how-automatic-scanning-works)
2. [Observing scan state](#observing-scan-state)
3. [scan()](#scan)
4. [clear()](#clear)
5. [notifyFileChanged()](#notifyfilechanged)

---

## How automatic scanning works

Two things trigger a scan automatically:

**On launch** — `Catalog.initialize()` runs an initial scan in the background on `Dispatchers.IO`. If the library is already up to date, the scan exits immediately with no database work.

**When files change** — Catalog registers a `MediaStore` content observer that watches for changes to the device's audio collection. When files are added, removed, or modified, the observer waits 2 seconds before triggering a scan. This debounce batches rapid consecutive changes (like copying an album) into a single scan instead of firing one per file.

### What the scanner does

Every scan compares the current `MediaStore` state against what's in the database and handles four cases:

- **New files** — fully ingested with metadata read directly from the file via TagLib. Processed in parallel chunks of 50 files per transaction to keep individual writes short.
- **Changed files** — any file whose `dateModified` timestamp changed by more than 2 seconds is re-read and updated. The 2-second tolerance accounts for filesystem timestamp differences across devices.
- **Moved or renamed files** — detected by matching `mediaStoreId` when the path has changed. The path is updated in-place. Play counts, favorites, and all listening history are fully preserved — no data is lost when a file moves.
- **Deleted files** — tracks whose path AND `mediaStoreId` are both absent from `MediaStore` are removed. The dual check prevents a renamed file from being falsely deleted before the move case handles it.

After processing, the scanner re-evaluates compilation status for every affected folder and runs garbage collection to remove orphaned artists, albums, genres, composers, and lyricists.

If nothing changed, the scan returns immediately — no database writes, no emissions.

---

## Observing scan state

### isScanning

```kotlin
val isScanning: StateFlow<Boolean>
```

Emits `true` while a scan or config change re-split is in progress, `false` when idle. Use this to show a loading indicator or disable scan-triggering UI elements.

```kotlin
lifecycleScope.launch {
    Catalog.isScanning.collect { scanning ->
        swipeRefreshLayout.isRefreshing = scanning
    }
}
```

### lastScanResult

```kotlin
val lastScanResult: StateFlow<ScanResult?>
```

Emits the result of the last scan that detected at least one change. `null` until the first meaningful scan completes. Scans that find nothing do not update this value.

```kotlin
data class ScanResult(
    val durationMs: Long,
    val newTracks: Int,
    val changedTracks: Int,
    val movedTracks: Int,
    val deletedTracks: Int
)
```

```kotlin
lifecycleScope.launch {
    Catalog.lastScanResult.collect { result ->
        result ?: return@collect
        Log.d("Catalog", "Scan found ${result.newTracks} new tracks in ${result.durationMs}ms")
    }
}
```

### lastConfigChangeResult

```kotlin
val lastConfigChangeResult: StateFlow<ConfigChangeResult?>
```

Emits the result of the last config change that triggered a junction re-split. `null` until the first config change. Only updated when `splitExceptions` or `customSplitters` change — other config changes don't produce a `ConfigChangeResult`.

```kotlin
data class ConfigChangeResult(
    val durationMs: Long,
    val tracksProcessed: Int
)
```

---

## scan()

```kotlin
suspend fun scan(force: Boolean = false)
```

Manually triggers a scan. You rarely need this — automatic scanning handles the common cases. The main use case is a pull-to-refresh gesture where the user explicitly wants to check for new files.

```kotlin
// Pull-to-refresh
viewModelScope.launch {
    Catalog.scan()
}
```

Concurrent calls are safe. All scan entry points — manual calls, `MediaWatcher` triggers, and `updateConfig()` side effects — are serialized through a shared internal mutex. If a scan is already running, the next call queues behind it and runs when the first completes.

### Force scan

```kotlin
Catalog.scan(force = true)
```

Passing `force = true` wipes the entire database before scanning — every track, album, artist, genre, playlist entry, and favorite is deleted. The library then re-indexes everything from scratch.

This is a developer/debug tool for cases like modifying split logic or adding new tag fields where a clean rebuild is needed. A few things to be aware of:

- **Playlists and favorites are wiped.** Unlike a normal scan which preserves all user data, a force scan deletes everything.
- **Ignored folders are preserved.** The scanner re-inserts all ignored paths from `CatalogConfig.scannerIgnores` immediately after the wipe, so the user's folder blacklist survives.
- **`dateAdded` on ignored folder entries is reset** to the current time — the original date they were added to the ignore list is lost.

Do not expose `force = true` to end users.

---

## clear()

```kotlin
suspend fun clear()
```

Deletes all tracks from the database and immediately cleans up all orphaned artists, albums, genres, composers, and lyricists in the same atomic transaction. Because favorites and playlist entries have a `CASCADE` foreign key on `tracks`, those are deleted too. Playlist containers (names and IDs) and ignored folder entries survive.

```kotlin
viewModelScope.launch {
    Catalog.clear()
}
```

In most cases `scan(force = true)` is what you want. Use `clear()` only when you need to empty the library without immediately repopulating it — for example, showing an empty state screen before triggering a fresh scan on a different storage path.

---

## notifyFileChanged()

```kotlin
fun notifyFileChanged(path: String)
```

Tells the library that a specific file has been modified externally and should be re-read. The library re-extracts the file's metadata and updates its database entry. If the file no longer exists on disk, it is removed from the database. This is fire-and-forget — it launches on a background coroutine and returns immediately.

```kotlin
Catalog.notifyFileChanged("/storage/emulated/0/Music/track.flac")
```

### When to use it

`CatalogEditor.updateTrack()` calls this automatically after a successful write, so you don't need it when editing tags through the library. The main use cases are:

- You edited a file's tags using an external tool and want the library to reflect the change immediately without waiting for `MediaWatcher` to fire.
- You're implementing your own tag editing functionality outside of `CatalogEditor`.

### What it won't do

- **New files** — if the file isn't already in the database, `notifyFileChanged()` does nothing. New files are picked up by the next full scan triggered by `MediaWatcher` or a manual `scan()` call.
- **Batch updates** — for multiple files, call `scan()` instead.

### Concurrency

`notifyFileChanged()` acquires the same shared mutex as `scan()`. If a full scan is running when it's called, the single-file rescan queues safely behind it. There is no race condition — TagLib will never be called on a file that is simultaneously being written to.
