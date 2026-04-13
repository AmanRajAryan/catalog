# Compilation Detection

`CompilationManager` decides whether a folder should be treated as a compilation (Various Artists) or as a standard artist folder, and updates album links accordingly.

---

## Table of Contents

1. [What makes a compilation](#what-makes-a-compilation)
2. [The handleFolder algorithm](#the-handlefolder-algorithm)
3. [relinkCompilationAlbums](#relinkcompilationalbums)
4. [restoreArtistAlbums](#restoreartistalbums)
5. [The blank album artist bug and its fix](#the-blank-album-artist-bug-and-its-fix)
6. [When handleFolder is called](#when-handlefolder-is-called)

---

## What makes a compilation

A folder qualifies as a compilation if and only if all three conditions are true:

1. **More than 3 distinct track artists** — `getDistinctArtistsInFolder()` returns more than 3 unique `artistDisplay` values.
2. **Exactly one album name** — `getDistinctAlbumsInFolder()` returns exactly 1 result.
3. **That album name is not generic** — the album name is not blank, `"unknown album"`, `"<unknown>"`, `"download"`, or `"downloads"` (case-insensitive).

The third condition is the critical safeguard. Without it, a messy downloads folder with 10 poorly-tagged files and no album tag would be incorrectly grouped as a "Various Artists" compilation just because they all default to the same generic album name.

The threshold of 3 (strictly greater than) prevents false positives from small multi-artist releases. A folder with 3 tracks by 3 different artists might be a standard album with features — requiring more than 3 distinct artists before triggering compilation mode is a reasonable heuristic.

---

## The handleFolder algorithm

```kotlin
suspend fun handleFolder(folderPath: String) {
    val distinctArtists = db.trackDao().getDistinctArtistsInFolder(folderPath)
    val distinctAlbums = db.trackDao().getDistinctAlbumsInFolder(folderPath)
    val albumName = distinctAlbums.firstOrNull() ?: ""

    val isGenericAlbum = albumName.isBlank() ||
        albumName.equals("unknown album", ignoreCase = true) ||
        albumName.equals("<unknown>", ignoreCase = true) ||
        albumName.equals("download", ignoreCase = true) ||
        albumName.equals("downloads", ignoreCase = true)

    if (distinctArtists.size > 3 && distinctAlbums.size == 1 && !isGenericAlbum) {
        relinkCompilationAlbums(folderPath)
    } else {
        restoreArtistAlbums(folderPath)
    }
}
```

This is called for every folder that had any track change during a scan. If the conditions flip (e.g. a track is deleted and the folder now has fewer than 4 artists), `restoreArtistAlbums()` runs and unlinks the "Various Artists" grouping.

---

## relinkCompilationAlbums

When a folder qualifies as a compilation, all its album entries are moved under the "Various Artists" umbrella:

```kotlin
private suspend fun relinkCompilationAlbums(folderPath: String) {
    val vaId = db.artistDao().insertOrGetId("Various Artists")
    val albumNames = db.trackDao().getDistinctAlbumsInFolder(folderPath)

    albumNames.forEach { albumName ->
        val vaAlbumId = db.albumDao().insertOrGetId(albumName, vaId, "")
        db.trackDao().updateAlbumIdForFolder(folderPath, albumName, vaAlbumId)
    }
}
```

`insertOrGetId("Various Artists")` creates the "Various Artists" artist entity if it doesn't exist, or returns the existing one. `insertOrGetId(albumName, vaId, "")` creates or finds the album under "Various Artists" with `folderGroup = ""` (global) — compilations can span multiple discs/folders.

`updateAlbumIdForFolder()` only updates tracks that have **no** specific `ALBUMARTIST` tag (`rawAlbumArtistString IS NULL OR rawAlbumArtistString = ''`). Tracks with a specific album artist tag are intentionally left alone — they belong to their tagged artist, not "Various Artists".

---

## restoreArtistAlbums

When a folder does not qualify as a compilation (or previously was one but no longer is), albums are restored to their original per-artist links:

```kotlin
private suspend fun restoreArtistAlbums(folderPath: String) {
    val tracks = db.trackDao().getTracksForRestore(folderPath)

    tracks.forEach { track ->
        val targetArtistName = if (track.rawAlbumArtistString.isNotBlank()) {
            track.rawAlbumArtistString
        } else {
            ""
        }

        val artistId = db.artistDao().insertOrGetId(targetArtistName)
        val folderGroup = if (targetArtistName.isNotBlank()) "" else folderPath
        val originalAlbumId = db.albumDao().insertOrGetId(track.albumDisplay, artistId, folderGroup)
        db.trackDao().updateAlbumIdOnly(track.id, originalAlbumId)
    }
}
```

This deliberately only uses `rawAlbumArtistString` — it does **not** fall back to `artistDisplay` (the track artist). If it did, track artists would incorrectly appear in the "Album Artists" tab for albums where they're not the album artist.

---

## The blank album artist bug and its fix

A subtle bug existed in earlier versions of `restoreArtistAlbums()`:

```kotlin
// OLD (buggy)
val artistId = if (targetArtistName.isNotBlank()) db.artistDao().insertOrGetId(name) else null
val originalAlbumId = db.albumDao().getAlbumId(track.albumDisplay, artistId, folderGroup)
```

When `targetArtistName` was blank, `artistId` was `null`. The album lookup used `albumArtistId IS NULL` in SQL. But `TrackIngestor` had already created the album with `albumArtistId = <id of "" artist>` (not NULL) — so the lookup found a different row, created a duplicate orphaned album, and updated the track's `albumId` to point at it. The original album became invisible in the Album Artists tab because `getAlbumArtistsSorted()` uses an INNER JOIN on `albumArtistId`.

**The fix:** always call `insertOrGetId()` even for a blank name. A blank name resolves to the shared `""` artist entity — the same one `TrackIngestor` uses — so the album lookup always finds the correct existing row:

```kotlin
// FIXED
val artistId = db.artistDao().insertOrGetId(targetArtistName)  // blank → "" artist entity
```

This mirrors `TrackIngestor`'s logic exactly, ensuring `CompilationManager` and `TrackIngestor` always agree on which album entity a track belongs to.

---

## When handleFolder is called

`ScannerService` collects all folder paths that were affected by the scan (any folder where a track was added, updated, moved, or deleted) into `affectedFolders`. After all writes complete, it calls `compilationManager.handleFolder(path)` for each one in a single transaction.

This means:
- Adding one track to a folder with 4 artists → compilation is detected
- Deleting a track that brought a folder below the 4-artist threshold → compilation is removed
- Renaming an album across all tracks in a folder → folder is re-evaluated

Re-evaluation always happens after the track data is committed — `handleFolder()` reads the current database state, so it always sees the post-write state.
