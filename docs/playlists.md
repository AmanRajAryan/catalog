# Playlists

`Catalog.playlists` manages the full playlist lifecycle — creating, editing, reordering, and importing/exporting playlists.

---

## Table of Contents

1. [Observing playlists](#observing-playlists)
2. [Creating and managing playlists](#creating-and-managing-playlists)
3. [Managing tracks in a playlist](#managing-tracks-in-a-playlist)
4. [Reordering](#reordering)
5. [Cover art and mosaic](#cover-art-and-mosaic)
6. [M3U export](#m3u-export)
7. [M3U import](#m3u-import)

---

## Observing playlists

### All playlists

```kotlin
fun getPlaylists(sort: SortOption.Playlist = SortOption.Playlist.NAME_ASC): Flow<List<Playlist>>
```

Live stream of all playlists with their track count, total duration, and cover art. Re-emits whenever a playlist is created, renamed, deleted, or has tracks added/removed.

```kotlin
Catalog.playlists.getPlaylists(SortOption.Playlist.DATE_MODIFIED_DESC)
    .collect { playlists -> adapter.submitList(playlists) }
```

### Single playlist

```kotlin
fun getPlaylistById(playlistId: Long): Flow<Playlist?>
```

Live stream of a single playlist. Emits `null` if the playlist doesn't exist or has been deleted.

### Playlist tracks

```kotlin
fun getPlaylistTracks(
    playlistId: Long,
    sort: ContextualSortOption.PlaylistTrack = ContextualSortOption.PlaylistTrack.USER_DEFINED
): Flow<List<PlaylistItem>>
```

Live stream of all tracks in a playlist. Each `PlaylistItem` contains the `entryId` needed for removing and reordering, the `sortOrder` position, and the full `Track` object.

The default sort is `USER_DEFINED` — the order the user arranged the tracks. Pass a different option to sort by title, date added, etc.

```kotlin
Catalog.playlists.getPlaylistTracks(playlistId).collect { items ->
    adapter.submitList(items)
}
```

---

## Creating and managing playlists

### Create

```kotlin
suspend fun createPlaylist(name: String): Long
```

Creates a new empty playlist. Returns the new playlist's ID. Throws `IllegalArgumentException` if the name is blank.

```kotlin
val playlistId = Catalog.playlists.createPlaylist("My Playlist")
```

### Rename

```kotlin
suspend fun renamePlaylist(playlistId: Long, newName: String)
```

Renames an existing playlist and updates its `dateModified` timestamp. Throws `IllegalArgumentException` if the new name is blank.

### Delete

```kotlin
suspend fun deletePlaylist(playlistId: Long)
```

Permanently deletes the playlist and all its entries. The tracks themselves are not affected — only the playlist container and its entries are removed.

---

## Managing tracks in a playlist

### Add a track

```kotlin
suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean
```

Adds a track to the end of the playlist. Returns `true` if successful, `false` if the track no longer exists in the library. The playlist's `dateModified` is updated automatically.

```kotlin
val added = Catalog.playlists.addTrackToPlaylist(playlistId, track.id)
```

### Remove an entry

```kotlin
suspend fun removeEntryFromPlaylist(entryId: Long)
```

Removes a single entry from the playlist by its `entryId`. Note that this takes the **entry ID** (from `PlaylistItem.entryId`), not the track ID — the same track can appear multiple times in a playlist, each with its own `entryId`.

```kotlin
// From a PlaylistItem
Catalog.playlists.removeEntryFromPlaylist(item.entryId)
```

---

## Reordering

### Drag and drop

```kotlin
suspend fun moveEntryInPlaylist(
    playlistId: Long,
    entryId: Long,
    fromPosition: Int,
    toPosition: Int
)
```

Optimized for drag-and-drop interactions. Moves a single entry to a new position and shifts all affected entries in one operation. The playlist's `dateModified` is updated automatically.

```kotlin
// In your drag-and-drop callback
Catalog.playlists.moveEntryInPlaylist(
    playlistId = playlistId,
    entryId = item.entryId,
    fromPosition = fromPosition,
    toPosition = toPosition
)
```

### Bulk reorder

```kotlin
suspend fun reorderPlaylist(playlistId: Long, updates: Map<Long, Int>)
```

Applies a new sort order to multiple entries at once. The map key is `entryId`, the value is the new `sortOrder`. Intended for "sort and save" operations where you want to reorder the whole playlist programmatically (e.g. shuffle).

```kotlin
val newOrder = items.shuffled()
    .mapIndexed { index, item -> item.entryId to index }
    .toMap()

Catalog.playlists.reorderPlaylist(playlistId, newOrder)
```

---

## Cover art and mosaic

### Single cover

`Playlist.coverArtPath` — an `ArtPath` derived from the first track in the playlist by sort order. Updates automatically when tracks are added, removed, or reordered.

### Mosaic

```kotlin
// For list screens — call once
suspend fun getMosaicForPlaylist(playlistId: Long, limit: Int): List<ArtPath>

// For detail screens — live updates
fun getMosaicForPlaylistFlow(playlistId: Long, limit: Int): Flow<List<ArtPath>>
```

Returns up to `limit` distinct cover art paths — one per album — for drawing a grid. Use the Flow version on the playlist detail screen so the mosaic updates when tracks are added or removed.

```kotlin
// Detail screen
val mosaicPaths by Catalog.playlists.getMosaicForPlaylistFlow(playlistId, limit = 4)
    .collectAsState(initial = emptyList())
```

---

## M3U export

```kotlin
suspend fun exportPlaylist(playlistId: Long, file: File): Boolean
```

Exports the playlist to an M3U file. Returns `true` on success, `false` if the playlist doesn't exist or if writing fails. The output format is:

```
#EXTM3U
#PLAYLIST:My Playlist
/storage/emulated/0/Music/track1.flac
/storage/emulated/0/Music/track2.mp3
...
```

Tracks are exported in their current playlist order. Only absolute file paths are written — no `#EXTINF` duration/title metadata is included.

```kotlin
val file = File(context.getExternalFilesDir(null), "my_playlist.m3u")
val success = Catalog.playlists.exportPlaylist(playlistId, file)
```

---

## M3U import

```kotlin
suspend fun importPlaylist(file: File): Long
```

Imports an M3U file and creates a new playlist. Returns the new playlist's ID on success, `0L` if the file was parsed successfully but no tracks matched (all paths are missing from the library), or `-1L` on failure (file not found, parse error).

The importer:
- Reads the playlist name from `#PLAYLIST:` if present, otherwise uses the filename without extension
- Matches each path against tracks already in the database — only tracks that exist in the library are added
- Preserves the original file order
- Skips comment lines (starting with `#`) and blank lines
- Handles large playlists safely by chunking the path lookup in batches of 900

```kotlin
val playlistId = Catalog.playlists.importPlaylist(file)

when {
    playlistId == -1L -> showError("Could not read file")
    playlistId == 0L -> showWarning("No matching tracks found in library")
    else -> navigateToPlaylist(playlistId)
}
```

> **Note:** If a track exists in the M3U file but hasn't been scanned into the library yet, it won't be included in the imported playlist. Run a scan first to ensure all tracks are indexed.
