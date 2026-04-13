# Core Concepts

Understanding these concepts will make the rest of the API click immediately. Most confusion when integrating Catalog comes down to one of the things explained here.

---

## Table of Contents

1. [Everything is a Flow](#everything-is-a-flow)
2. [Favorite status is always attached](#favorite-status-is-always-attached)
3. [Display strings vs rich lists](#display-strings-vs-rich-lists)
4. [How track.album works](#how-trackalbum-works)
5. [Cover art and cache-busting](#cover-art-and-cache-busting)
6. [MatchedAlbum and MatchedGenre](#matchedalbum-and-matchedgenre)
7. [Album disambiguation](#album-disambiguation)
8. [Compilation detection](#compilation-detection)
9. [File renames and moves preserve user data](#file-renames-and-moves-preserve-user-data)
10. [Thread safety](#thread-safety)

---

## Everything is a Flow

Almost every query method returns a `kotlinx.coroutines.flow.Flow`. Data is not fetched once and returned — it is observed. When the underlying database changes (a scan ran, a favorite was toggled, a playlist was updated), your `Flow` emits a new list and your UI updates automatically.

All flows from Catalog apply `distinctUntilChanged()` internally, so re-emission only happens when the data actually changed. If a scan runs and finds nothing new, your collectors are not notified.

The correct pattern is to collect in your `ViewModel` and expose as `StateFlow`:

```kotlin
class LibraryViewModel : ViewModel() {
    val tracks = Catalog.library.getTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

Then collect once in your Fragment or Composable and never call the query again:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.tracks.collect { adapter.submitList(it) }
}
```

**Do not** call query methods repeatedly to refresh data. Collect once and let the Flow do the work.

---

## Favorite status is always attached

Every track returned by any query — `getTracks()`, `getTracksForAlbum()`, playlist tracks, search results, all of them — already has its `dateFavorited` field populated. The library joins the favorites table on every query emission. You never need a separate call to check whether a track is favorited.

```kotlin
// Check favorite status directly on the track — no extra call needed
val isFavorite = track.dateFavorited > 0

// Show when it was favorited
val date = Date(track.dateFavorited) // only meaningful if > 0
```

---

## Display strings vs rich lists

`Track` contains both pre-formatted display strings and structured lists for the same information. They serve different purposes.

**For list rendering** — use display strings. They're fast, already formatted, and require no iteration:

```kotlin
Text(text = track.title)
Text(text = track.artist)  // e.g. "Drake, Future"
Text(text = track.genre)   // e.g. "Hip-Hop, Trap"
```

**For navigation and clickable chips** — use the rich lists. Each `IdName` has an `id` for navigation and a `name` for display:

```kotlin
track.artists.forEach { artist ->
    Chip(
        text = artist.name,
        onClick = { navigateToArtist(artist.id) }
    )
}
```

The same pattern applies to `track.genres`, `track.composers`, and `track.lyricists`.

The display strings (`track.artist`, `track.genre`, etc.) reflect the original tag value as it appeared in the file — the separator between multiple values depends on what the tag contained. These strings are never reformatted by the library. The rich lists are what the library actually uses for navigation and filtering.

---

## How track.album works

`track.album` is not a cached string stored on the track row. It is resolved live from the album entity at query time via a JOIN.

This means:
- If compilation detection re-groups a folder's tracks under "Various Artists", `track.album` for those tracks immediately reflects the updated album title — no rescan needed.
- If an album is renamed internally (e.g. after a tag edit), all tracks in that album show the new name on the next emission.

There is a fallback display string stored on the track row (`albumDisplay`), but it's only used if the album entity is somehow unavailable — in practice this doesn't happen.

---

## Cover art and cache-busting

Cover art paths are returned as `ArtPath` objects, not raw strings:

```kotlin
data class ArtPath(
    val path: String,
    val dateModified: Long
)
```

The `dateModified` field is the last-modified timestamp of the source audio file. Use it as a cache-busting key when loading images, so that artwork updates are picked up automatically after a tag edit:

```kotlin
// With Coil
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(track.coverArtPath?.path)
        .memoryCacheKey("${track.coverArtPath?.path}?t=${track.coverArtPath?.dateModified}")
        .build()
)

// Or construct the key manually
val cacheKey = "${artPath.path}?t=${artPath.dateModified}"
```

Without `dateModified` as part of the cache key, an image library will serve the old cached artwork even after the file's tags have been updated.

### Mosaic art

For categories that don't have a single cover (artists, genres, years, folders), the library provides a mosaic API that returns up to N distinct cover art paths — one per album — for drawing a 2×2 or 3×3 grid:

```kotlin
// Get up to 4 paths for a 2x2 mosaic
val paths: List<ArtPath> = Catalog.library.getMosaicForTrackArtist(artistId, limit = 4)
```

Available for: track artists, album artists, genres, composers, lyricists, years, folders, playlists, and favorites.

---

## MatchedAlbum and MatchedGenre

Several navigation queries return `MatchedAlbum` or `MatchedGenre` instead of plain `Album` or `Genre`. These are wrappers that add context about how many items in the result matched the query.

```kotlin
data class MatchedAlbum(
    val album: Album,
    val matchedTrackCount: Int,
    val matchedDurationMs: Long
)

data class MatchedGenre(
    val genre: Genre,
    val matchedTrackCount: Int,
    val matchedDurationMs: Long
)
```

For example, `getAlbumsForGenre(genreId)` returns `List<MatchedAlbum>`. The `album.trackCount` is the album's total track count. `matchedTrackCount` is how many of those tracks are actually tagged with the queried genre. Use the difference to show context on a detail screen:

```kotlin
Catalog.library.getAlbumsForGenre(genreId).collect { matchedAlbums ->
    matchedAlbums.forEach { matched ->
        displayAlbum(
            album = matched.album,
            subtitle = "${matched.matchedTrackCount} of ${matched.album.trackCount} tracks"
        )
    }
}
```

Queries that return `MatchedAlbum`: `getAppearsOnAlbumsForTrackArtist()`, `getAlbumsForGenre()`, `getAlbumsForComposer()`, `getAlbumsForLyricist()`, `getAlbumsForFolder()`, `getAlbumsForYear()`.

Queries that return `MatchedGenre`: all `getGenresFor*()` navigation queries.

---

## Album disambiguation

Albums are often identified by name alone, which creates a problem: two unrelated albums both called "Greatest Hits" by different artists would be incorrectly merged into one entity. Catalog prevents this through a folder-grouping mechanism controlled by the presence or absence of an `ALBUMARTIST` tag.

**If an `ALBUMARTIST` tag is present** — the album is grouped globally. Tracks from different folders that share the same album name and album artist collapse into one album entity. This is the correct behavior for multi-disc albums where disc 1 and disc 2 live in separate subdirectories.

**If no `ALBUMARTIST` tag is present** — the album is scoped to its folder. Two folders that both contain a track with the album "Greatest Hits" produce two separate album entries, even if the names are identical.

```
/Music/Queen/Greatest Hits/     ← ALBUMARTIST = "Queen"
/Music/ABBA/Greatest Hits/      ← ALBUMARTIST = "ABBA"
→ Two separate albums ✓

/Music/My Album/Disc 1/         ← ALBUMARTIST = "Pink Floyd"
/Music/My Album/Disc 2/         ← ALBUMARTIST = "Pink Floyd"
→ One album ✓

/Music/Downloads/Greatest Hits.mp3  ← no ALBUMARTIST
/Music/Misc/Greatest Hits.flac      ← no ALBUMARTIST
→ Two separate albums (one per folder) ✓
```

**Debugging album grouping issues:**

- Tracks that should be in one album showing up as separate entries → check that all files have a consistent `ALBUMARTIST` tag. Without it, each folder produces its own album.
- Unrelated albums incorrectly merging → the files likely have the same `ALBUMARTIST` value and the same album name. Fixing the tag resolves the collision.

---

## Compilation detection

Catalog automatically detects compilation albums. A folder qualifies as a compilation only if all three conditions are true:

1. It contains **more than 3 distinct artists**
2. It has tracks from **exactly one album name**
3. That album name is **not a generic placeholder** — `"Unknown Album"`, `"<unknown>"`, `"download"`, `"downloads"` do not qualify

The third condition is important: it prevents messy download folders from being incorrectly grouped as a compilation just because the files lack proper tags.

When a folder is marked as a compilation, its `Album.albumArtist` is set to `"Various Artists"`. **The individual `track.artists` list on every track is fully preserved.** Compilation detection only changes the album-level `albumArtist` — per-track artist data is never touched.

This means:
- The album displays correctly as "Various Artists" on an Albums screen
- Tapping an artist chip on any track within the compilation still navigates to that artist's full track list
- `Catalog.library.getTracksForArtist(artistId)` correctly returns tracks from compilations

```kotlin
// This works correctly even for tracks inside a compilation
track.artists.forEach { artist ->
    Chip(
        text = artist.name,
        onClick = { navigateToArtist(artist.id) }  // navigates to this artist across the whole library
    )
}
```

---

## File renames and moves preserve user data

When a file is renamed or moved on device, Catalog detects this by matching its `mediaStoreId` — a stable identifier assigned by Android that doesn't change when a file moves. Rather than deleting and re-inserting the track, the scanner updates its path in-place and re-reads the metadata at the same time.

Play counts, skip counts, last played, total listening time, and favorite status are all preserved across renames and moves. No data is lost.

---

## Thread safety

Every `suspend` function in `Catalog` and `CatalogEditor` dispatches internally to `Dispatchers.IO`. You can call them from any coroutine context without wrapping them in `withContext(Dispatchers.IO)`.

Scanning, config re-splitting, single-file rescanning, and tag editing are all serialized through a shared internal mutex. In practice this means:

- You can call `CatalogEditor.updateTrack()` while a scan is running — the editor waits for the scan to finish before committing, and the subsequent rescan queues behind the write. No extra coordination needed on your side.
- Multiple scan triggers cannot overlap — if `MediaWatcher` fires while a scan is already running, the second scan queues and runs after the first completes.
- TagLib is never called on a file that is simultaneously being written to, preventing native crashes on half-written files.
