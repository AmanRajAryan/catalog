# Track Ingestor

`TrackIngestor` handles the database writes for new and updated tracks. It resolves the final values for every track field, determines album grouping, manages junction tables, and handles play stats transfer when a track moves between albums.

---

## Table of Contents

1. [Overview](#overview)
2. [processCommonFields](#processcommonfields)
3. [TagLib vs MediaStore priority](#taglib-vs-mediastore-priority)
4. [trustTagLibFirst](#trusttaglibfirst)
5. [Album grouping logic](#album-grouping-logic)
6. [Play stats transfer on album rename](#play-stats-transfer-on-album-rename)
7. [Junction tables](#junction-tables)
8. [regenerateJunctions](#regeneratejunctions)
9. [Garbage collection](#garbage-collection)

---

## Overview

`TrackIngestor` has four public methods:

| Method | Called by | Purpose |
|---|---|---|
| `ingestNewTrack(skeleton, meta)` | `ScannerService.scan()` | Insert a new track + create junctions |
| `ingestUpdatedTrack(id, skeleton, meta)` | `ScannerService.scan()` + `rescanSingleFile()` | Update an existing track + rebuild junctions |
| `regenerateJunctions(trackId, ...)` | `ScannerService.applyConfigChanges()` | Rebuild junctions only, no track data change |
| `performGarbageCollection()` | `ScannerService` after any batch write | Delete empty albums/artists/genres/etc. |

---

## processCommonFields

Both `ingestNewTrack` and `ingestUpdatedTrack` start by calling `processCommonFields()`, which resolves the final value for every track field by combining the `SkeletonTrack` (MediaStore data) with `ExtendedMetadata` (TagLib data).

`SkeletonTrack` is the lightweight MediaStore record:

```kotlin
data class SkeletonTrack(
    val mediaStoreId: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val year: Int,
    val trackNumber: Int,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String
)
```

`processCommonFields()` returns a `Triple<ProcessedData, String, String>`:
- `ProcessedData` — all resolved fields for the track row
- `finalAlbumArtistName` — the resolved album artist name
- `rawAlbumArtist` — the raw album artist string (for `rawAlbumArtistString` column)

---

## TagLib vs MediaStore priority

For the main tag fields (title, artist, album, genre), TagLib wins over MediaStore when non-blank:

```
validTitle = extendedMeta.foundTitle (TagLib) if non-blank
           else skeleton.title (MediaStore)

validArtist = extendedMeta.foundArtist (TagLib) if non-blank
            else skeleton.artist (MediaStore)

// same for album, genre
```

For composer and year, TagLib also wins:

```
rawComposer = extendedMeta.foundComposer if non-blank, else skeleton.composer
finalYear = extendedMeta.foundYear if > 0, else skeleton.year
```

Album artist is resolved from TagLib first, then MediaStore, then empty string:

```
rawAlbumArtist = extendedMeta.foundAlbumArtist (TagLib) if non-blank
               else skeleton.albumArtist (MediaStore) if non-blank
               else ""
```

---

## trustTagLibFirst

`ingestUpdatedTrack()` accepts an optional `trustTagLibFirst: Boolean = false` parameter. When `true` (used by `rescanSingleFile()` after a tag edit), TagLib's values always win even if blank:

```kotlin
// Normal scan — blank TagLib value → fall back to MediaStore
validArtist = if (extendedMeta.foundArtist.isNotBlank()) extendedMeta.foundArtist
              else skeleton.artist

// Post-edit rescan (trustTagLibFirst = true) — blank TagLib value → keep it blank
validArtist = if (extendedMeta.foundArtist.isNotBlank()) extendedMeta.foundArtist
              else ""  // user intentionally cleared the field
```

For title specifically, `trustTagLibFirst = true` falls back to the filename (without extension) rather than an empty string — a track with no title tag should still have a displayable name.

This distinction matters: during a normal scan, MediaStore values are useful fallbacks for files with sparse tags. After a tag edit, TagLib's values are authoritative — if the user cleared an artist field, it should be empty, not populated with MediaStore's stale value.

---

## Album grouping logic

Album grouping determines whether two tracks with the same album name belong to the same `AlbumEntity` or different ones. The key is `folderGroup`:

```kotlin
// Real album artist → global grouping (multi-disc albums merge across folders)
val folderGroup = if (finalAlbumArtist.isNotBlank()) "" else processed.folderPath
```

**`folderGroup = ""`** — the album is global. Any track with the same album name and same album artist, regardless of which folder it's in, maps to the same `AlbumEntity`. This is correct for multi-disc albums (Disc 1/ and Disc 2/ share one album entry).

**`folderGroup = folderPath`** — the album is scoped to its folder. Two folders with the same album name but no album artist produce separate `AlbumEntity` rows. This prevents "Greatest Hits" by Queen (in `/Music/Queen/`) and "Greatest Hits" by ABBA (in `/Music/ABBA/`) from merging when neither has an album artist tag.

`AlbumEntity` has a unique index on `(title, albumArtistId, folderGroup)`, so the `insertOrGetId()` call either returns the existing album's ID or creates a new one:

```kotlin
val finalAlbumId = db.albumDao().insertOrGetId(processed.validAlbum, albumArtistId, folderGroup)
```

Album artist ID is always resolved via `insertOrGetId()` even when the name is blank — a blank name maps to a shared `""` artist entity, ensuring every track has a valid `albumArtistId` and appears correctly in all queries.

---

## Play stats transfer on album rename

When an existing track is re-ingested and its album changes (e.g. the album tag was edited), there's a risk of losing the old album's play history. `ingestUpdatedTrack()` handles this:

```kotlin
val oldAlbumId = db.trackDao().getAlbumIdForTrack(id)
if (oldAlbumId != null && oldAlbumId != finalAlbumId) {
    val remainingTracks = db.albumDao().getTrackCountForAlbum(oldAlbumId)
    if (remainingTracks == 1) {
        // This is the last track in the old album — it's about to be GC'd
        // Transfer its play stats to the new album
        db.albumDao().mergePlayStats(fromId = oldAlbumId, toId = finalAlbumId)
    }
}
```

The condition `remainingTracks == 1` means this track is the only one left in the old album. After the update, that album will have 0 tracks and be deleted by GC. The `mergePlayStats()` call adds the old album's `playCount`, `totalPlayTimeMs`, and `lastPlayed` to the new album before GC runs.

This only applies when an entire single-track album is renamed — multi-track albums are not affected because they still have other tracks after the edit.

---

## Junction tables

Junctions link tracks to their multi-value entities. After inserting or updating a track, `insertJunctions()` inserts rows into `track_artists`, `track_genres`, `track_composers`, and `track_lyricists`.

For updates, the old junctions are deleted first, then rebuilt:

```kotlin
db.trackDao().deleteArtistRefs(id)
db.trackDao().deleteGenreRefs(id)
db.trackDao().deleteComposerRefs(id)
db.trackDao().deleteLyricistRefs(id)
insertJunctions(id, processed)
```

`insertJunctions()` calls `SmartSplitter.split()` on each display value, then calls `insertOrGetId()` for each resulting name to get or create the entity, then inserts the junction ref. All inserts use `OnConflictStrategy.IGNORE` — if a junction already exists (which shouldn't happen after a delete, but is safe), it's silently skipped.

---

## regenerateJunctions

`regenerateJunctions()` is called during `applyConfigChanges()`. It rebuilds junction tables for a track without touching the track row itself:

```kotlin
suspend fun regenerateJunctions(
    trackId: Long,
    rawArtist: String,
    rawComposer: String,
    rawLyricist: String,
    rawGenre: String
)
```

The raw strings are re-split using the updated `SmartSplitter`, old junctions are deleted, and new ones are inserted. This is how split rule changes take effect retroactively across the entire library without requiring a full rescan.

---

## Garbage collection

```kotlin
suspend fun performGarbageCollection() {
    db.albumDao().deleteEmptyAlbums()
    db.artistDao().deleteEmptyArtists()
    db.genreDao().deleteEmptyGenres()
    db.composerDao().deleteEmptyComposers()
    db.lyricistDao().deleteEmptyLyricists()
}
```

Each `deleteEmpty*()` query deletes rows that have no linked tracks. For albums:

```sql
DELETE FROM albums WHERE id NOT IN (
    SELECT DISTINCT albumId FROM tracks WHERE albumId IS NOT NULL
)
```

For artists, the check is more complex — an artist can be referenced either as a track artist (via `track_artists`) or as an album artist (via `albums.albumArtistId`):

```sql
DELETE FROM artists 
WHERE id NOT IN (SELECT artistId FROM track_artists) 
AND id NOT IN (SELECT albumArtistId FROM albums WHERE albumArtistId IS NOT NULL)
```

GC always runs after scan writes and after config change re-splits. It's also exposed via `Catalog.performGarbageCollection()` for use after manually deleting tracks via `Catalog.clear()`.
