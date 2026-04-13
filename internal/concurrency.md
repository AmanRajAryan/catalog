# Concurrency

The library coordinates multiple concurrent operations — scanning, tag editing, config changes, and single-file rescans — without data races or native crashes. This doc explains the mechanisms used and why they're necessary.

---

## Table of Contents

1. [The core problem](#the-core-problem)
2. [ioMutex](#iomutex)
3. [What the mutex guards](#what-the-mutex-guards)
4. [Deadlock prevention](#deadlock-prevention)
5. [The coroutine scope](#the-coroutine-scope)
6. [MediaWatcher and debounce](#mediawatcher-and-debounce)
7. [Dispatchers](#dispatchers)

---

## The core problem

TagLib reads directly from the file system. If a file is being written to at the same time TagLib is reading it, the file may be temporarily 0 bytes (during the commit phase when the output stream is opened with `"wt"` mode, which truncates before writing). A TagLib call on a 0-byte file causes a native SIGSEGV crash — not a catchable exception, but a hard process termination.

Additionally, the database can be corrupted if a scan and a config re-split run concurrently — both modify junction tables, and interleaved writes can leave junctions in an inconsistent state.

Both problems are solved by the same mechanism: `ioMutex`.

---

## ioMutex

```kotlin
internal val ioMutex = Mutex()
```

`ioMutex` is a `kotlinx.coroutines.sync.Mutex` — a non-reentrant, suspending mutual exclusion lock. Every operation that touches the file system via TagLib or performs a large batch of database writes acquires this mutex before proceeding.

It's declared `internal` (not `private`) so `CatalogEditor` can access it directly — `CatalogEditor` is in the same module as `Catalog` but is a separate object.

```kotlin
// In ScannerService.scan()
suspend fun scan(): ScanResult? = Catalog.ioMutex.withLock {
    // ...
}

// In ScannerService.rescanSingleFile()
suspend fun rescanSingleFile(path: String) = Catalog.ioMutex.withLock {
    // ...
}

// In ScannerService.applyConfigChanges()
suspend fun applyConfigChanges(): ConfigChangeResult = Catalog.ioMutex.withLock {
    // ...
}

// In CatalogEditor.updateTrack() — only the commit phase
Catalog.ioMutex.withLock {
    // stream cache → original file
}

// In CatalogLibrary.getLyricsForTrack()
Catalog.ioMutex.withLock {
    TagLibHelper.extractLyrics(track.path)
}
```

`withLock` is a suspending extension function — it suspends the current coroutine (without blocking the thread) until the lock is available, then resumes.

---

## What the mutex guards

| Operation | Mutex scope |
|---|---|
| `scan()` | Entire scan, from MediaStore query to GC |
| `rescanSingleFile()` | Entire single-file rescan |
| `applyConfigChanges()` | Entire junction re-split |
| `CatalogEditor.updateTrack()` | Only the final file commit (cache → original), not the full edit |
| `getLyricsForTrack()` | The TagLib read call |
| `getPicturesForTrack/Path()` | The TagLib read call |
| `readTags()` (CatalogEditor) | The TagLib read call |

**Why `updateTrack()` only locks the commit phase:** The earlier steps (validation, copying to cache, TagLib writes to cache) operate on a temporary file in the app's cache directory, not the original. Only the final commit — streaming the cache file to the original location — touches the real file and needs mutual exclusion.

**Why lyrics/artwork reads are locked:** TagLib reads the file directly. If a scan is running and TagLib is reading a file that's being committed by `CatalogEditor`, the commit opens the file with `"wt"` (which truncates it to 0 bytes before writing). At that exact moment, if TagLib reads the file, it sees 0 bytes and crashes.

---

## Deadlock prevention

`Mutex` in Kotlin coroutines is non-reentrant — a coroutine that tries to acquire a lock it already holds will deadlock forever. This is why `notifyFileChanged()` cannot call `scan()` directly while the scan mutex might be held:

```kotlin
fun notifyFileChanged(path: String) {
    requireInit()
    scope.launch { scanner?.rescanSingleFile(path) }  // launches on a separate coroutine
}
```

`scope.launch` creates a new coroutine that will queue on the mutex independently — no deadlock risk. When `CatalogEditor.updateTrack()` calls `notifyFileChanged()` at the end of a write, the mutex has already been released (the `withLock` block for the commit has completed), so the new coroutine can acquire it normally when it runs.

The catch blocks in `CatalogEditor.updateTrack()` deliberately sit **outside** the `withLock` block:

```kotlin
try {
    Catalog.ioMutex.withLock {
        // commit phase
    }
} catch (e: RecoverableSecurityException) {
    // handle
} catch (e: IOException) {
    // handle
}
```

If an exception is thrown inside `withLock`, the `Mutex`'s internal `finally` block releases the lock before the exception propagates. The outer catch then handles the exception with the lock already released — no deadlock.

---

## The coroutine scope

`Catalog` uses a single background scope for all async work:

```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

`SupervisorJob()` means one failed coroutine doesn't cancel the others. If a scan fails with an unexpected exception, `MediaWatcher` continues running and can trigger the next scan normally.

The scope is cancelled in `shutdown()`:

```kotlin
fun shutdown(context: Context) {
    mediaWatcher?.stop()
    scope.cancel()
}
```

All sub-manager flows (from Room DAOs) use `Dispatchers.IO` or `Dispatchers.Default` internally and are not tied to this scope — they're tied to the collector's scope. Cancelling `Catalog.scope` only affects background tasks (scans, config changes), not active Flow collectors.

---

## MediaWatcher and debounce

`MediaWatcher` registers a `ContentObserver` on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`:

```kotlin
context.contentResolver.registerContentObserver(
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    true,  // notifyForDescendants
    observer
)
```

`notifyForDescendants = true` means changes to any audio file, not just the root URI, trigger the observer.

The observer debounces rapid consecutive changes using a `Job`:

```kotlin
override fun onChange(selfChange: Boolean) {
    scanJob?.cancel()
    scanJob = scope.launch {
        delay(2000)
        onContentChanged()
    }
}
```

Each new `onChange` callback cancels the previous pending scan and starts a new 2-second timer. This batches rapid bursts of changes (like copying an album of 10 tracks) into a single scan after the activity stops.

`onContentChanged` calls `scope.launch { scan() }` — which acquires `ioMutex`. If a scan is already running from a previous trigger, the new one queues on the mutex and runs after.

---

## Dispatchers

| Context | Dispatcher | Reason |
|---|---|---|
| All `suspend` functions in `Catalog`, `CatalogLibrary`, etc. | `Dispatchers.IO` (via `withContext`) | Disk I/O and database access |
| Flow mapping in `CatalogLibrary` | `Dispatchers.Default` (via `flowOn`) | CPU work (model mapping) off the main thread |
| Room DAO flows | Room manages internally | Room uses its own executor |
| `MediaWatcher.onChange()` | Main thread (via Handler) | ContentObserver always callbacks on the provided Handler's thread |
| Scan/config work | `Dispatchers.IO` (via Catalog.scope) | The scope itself uses `Dispatchers.IO` |

`Dispatchers.Default` is used for `flowOn` in the library flows rather than `Dispatchers.IO` because the model mapping (`ModelMapper.toTrack()`, etc.) is CPU work, not I/O. Using `Dispatchers.IO` for CPU work would unnecessarily consume threads from the I/O thread pool.
