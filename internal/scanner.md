# Scanner

`ScannerService` is the coordinator for all scan operations. It diffs the current `MediaStore` state against the database, classifies every file as new/changed/moved/deleted, delegates ingestion to `TrackIngestor`, and orchestrates post-scan cleanup.

---

## Table of Contents

1. [Overview](#overview)
2. [Two-phase MediaStore querying](#two-phase-mediastore-querying)
3. [The diff algorithm](#the-diff-algorithm)
4. [Batch processing and parallelism](#batch-processing-and-parallelism)
5. [Post-scan cleanup](#post-scan-cleanup)
6. [Single file rescan](#single-file-rescan)
7. [Config change re-split](#config-change-re-split)
8. [MediaStore quirks](#mediastore-quirks)

---

## Overview

`ScannerService` has three public entry points, all guarded by `Catalog.ioMutex`:

| Method | When called | What it does |
|---|---|---|
| `scan()` | On launch, MediaWatcher trigger, config change | Full diff against MediaStore |
| `rescanSingleFile(path)` | After a `CatalogEditor` write | Re-reads one file |
| `applyConfigChanges()` | When split rules change via `updateConfig()` | Re-splits all junctions in-place |

All three are suspend functions that dispatch internally to `Dispatchers.IO`.

---

## Two-phase MediaStore querying

The scanner queries MediaStore in two phases to minimize work:

**Phase 1 — Lightweight snapshots** (`queryMediaStoreSnapshots`):
Fetches only `_ID`, `DATA` (path), and `DATE_MODIFIED` for all audio files. This is a small, fast query that gives us just enough data to compute the diff.

```kotlin
data class SystemSnapshot(
    val mediaStoreId: Long,
    val path: String,
    val dateModified: Long
)
```

**Phase 2 — Full metadata** (`queryMediaStore`):
Fetches all tag fields for only the files that actually need processing (new + changed + moved). On a typical app launch where nothing has changed, Phase 2 never runs.

This two-phase approach means most launches — where the library is already up to date — complete almost instantly with minimal database and MediaStore work.

---

## The diff algorithm

After Phase 1, the scanner maps the database state by both path and `mediaStoreId`:

```kotlin
val dbPathMap = snapshots.associateBy { it.path }
val dbIdMap = snapshots.associateBy { it.mediaStoreId }
```

For each file returned by MediaStore:

```
existingByPath = dbPathMap[snapshot.path]
existingById = dbIdMap[snapshot.mediaStoreId]

if existingByPath != null:
    CASE A: EXACT MATCH — path is the same
    if snapshot.dateModified > existingByPath.dateModified + 2000:
        → changedTracks (re-read tags)
    else:
        → skip (nothing changed)

else if existingById != null:
    CASE B: MOVED / RENAMED — same ID, different path
    → movedTracks (update path + re-read tags)

else:
    CASE C: NEW — not in database at all
    → newTracks (full ingest)
```

**The 2-second tolerance** in Case A accounts for filesystem timestamp differences across Android devices and SD cards. Some filesystems have 2-second timestamp granularity, so a file that was "just saved" might show a dateModified only 1 second newer than what's stored.

**Deletions** are handled separately after the loop:

```kotlin
val deletedSnapshots = dbSnapshots.filter { dbEntry ->
    !currentSystemPaths.contains(dbEntry.path) &&
    !systemIdSet.contains(dbEntry.mediaStoreId)
}
```

The dual check (`path AND mediaStoreId` both absent) prevents a moved/renamed file from being treated as deleted. If the path is gone but the `mediaStoreId` is still present elsewhere, it's a move, not a deletion.

---

## Batch processing and parallelism

### New track ingestion

New tracks are processed in chunks of 50 to keep individual database transactions short:

```kotlin
newTracks.chunked(50).forEach { chunk ->
    val skeletons = queryMediaStore(masterBlacklist, chunk.map { it.path })
    val preparedData = skeletons.map { skeleton ->
        async(Dispatchers.IO) {
            semaphore.withPermit { skeleton to TagLibHelper.extract(skeleton.path) }
        }
    }.awaitAll()
    db.withTransaction {
        preparedData.forEach { (skeleton, meta) -> ingestor.ingestNewTrack(skeleton, meta) }
    }
}
```

TagLib extraction is parallelized within each chunk using `async/awaitAll` with a `Semaphore` that limits concurrent extractions to `(availableProcessors / 2).coerceIn(3, 4)`. This prevents overwhelming the device with too many simultaneous disk reads while still benefiting from parallelism.

### Changed and moved tracks

Changed and moved tracks use the same chunked approach but without parallelism — they're typically fewer in number and the sequential approach keeps the code simpler.

### Config change re-split

Junction regeneration during `applyConfigChanges()` uses chunks of 500 tracks per transaction (much larger than the scan's 50, because there's no TagLib I/O involved — it's pure in-memory splitting and database writes).

---

## Post-scan cleanup

After all inserts, updates, and deletes, the scanner:

1. **Re-evaluates compilation status** for every folder that had a change (track added, updated, or deleted). See [compilation.md](compilation.md).

2. **Runs garbage collection** — deletes albums, artists, genres, composers, and lyricists that have no remaining tracks:

```kotlin
db.albumDao().deleteEmptyAlbums()
db.artistDao().deleteEmptyArtists()
db.genreDao().deleteEmptyGenres()
db.composerDao().deleteEmptyComposers()
db.lyricistDao().deleteEmptyLyricists()
```

Both steps run in a single transaction after all writes are complete — this avoids intermediate states where an album exists but has 0 tracks, or an artist exists but has no albums.

If no tracks changed (the early exit case), neither compilation re-evaluation nor garbage collection runs.

---

## Single file rescan

`rescanSingleFile(path)` is called by `Catalog.notifyFileChanged()` after a `CatalogEditor` write. It handles three cases:

**File deleted** — if the file no longer exists on disk, delete the track from the database, re-evaluate the folder's compilation status, and run GC.

**File not in database** — log a warning and do nothing. The file will be discovered on the next full scan.

**File exists and is in database** — extract fresh metadata via TagLib and call `ingestor.ingestUpdatedTrack(id, skeleton, meta, trustTagLibFirst = true)`. The `trustTagLibFirst = true` flag means TagLib's values always win, even if they're blank — this is correct after a tag edit where the user intentionally cleared a field.

If MediaStore hasn't caught up with the file change yet (common immediately after a write), the scanner falls back to a `SkeletonTrack` built from the existing database entry rather than failing.

---

## Config change re-split

`applyConfigChanges()` re-splits all junction tables without touching the main track data:

1. Load all raw track strings from the database (`getAllTracksRaw()` — a lightweight projection of just the raw tag fields).
2. For each track, call `ingestor.regenerateJunctions()` — this rebuilds artist, genre, composer, and lyricist junction refs using the new `SmartSplitter` rules.
3. Process in chunks of 500 to avoid long-running transactions.
4. After all junctions are rebuilt, re-evaluate compilation status for all affected folders and run GC.

Track display strings (`artistDisplay`, `genreDisplay`, etc.) are intentionally **not** updated — they always reflect the original tag value. Only the junction tables change.

---

## MediaStore quirks

Two garbage values that MediaStore injects into tags are filtered out during `queryMediaStore()`:

**Artist `<unknown>`** — MediaStore injects `"<unknown>"` when a file has no artist tag. Treated as blank so TagLib's real value wins in `processCommonFields()`.

**Album folder fallback** — MediaStore uses the folder name as a fallback album when a file has no album tag. `"<unknown>"` and `"Unknown Album"` are rejected for the same reason.

Both filters use `takeUnless { it.equals("<unknown>", ignoreCase = true) }` — case-insensitive to handle different device manufacturers' capitalizations.

**dateModified normalization** — MediaStore returns `DATE_MODIFIED` in seconds on most devices, but some OEMs return it in milliseconds. The scanner normalizes it:

```kotlin
val finalDateMod = if (rawDateMod > 10_000_000_000L) rawDateMod else rawDateMod * 1000L
```

Any value over 10 billion is already in milliseconds (since 10 billion seconds is the year 2286). Anything smaller is in seconds and gets multiplied by 1000.
