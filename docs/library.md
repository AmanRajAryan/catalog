# Library

`Catalog.library` exposes the core browsing and navigation API. Everything here returns a `Flow` that stays live — when the database changes, your UI updates automatically.

If you haven't read [core-concepts.md](core-concepts.md) yet, do that first. The distinction between display strings and rich lists, how `track.album` works, and the `MatchedAlbum` / `MatchedGenre` pattern all come up throughout this doc.

---

## Table of Contents

1. [Track lists](#track-lists)
2. [Category lists](#category-lists)
3. [Navigation queries](#navigation-queries)
   - [Tracks by category](#tracks-by-category)
   - [Albums by category](#albums-by-category)
   - [Genres by category](#genres-by-category)
4. [Get by ID](#get-by-id)
5. [Cover art](#cover-art)
   - [Mosaic API](#mosaic-api)
6. [On-demand metadata](#on-demand-metadata)
   - [Lyrics](#lyrics)
   - [Embedded artwork](#embedded-artwork)

---

## Track lists

### getTracks()

```kotlin
fun getTracks(sort: SortOption.Track = SortOption.Track.TITLE_ASC): Flow<List<Track>>
```

Live stream of all tracks in the library. Re-emits whenever any track changes.

```kotlin
Catalog.library.getTracks(SortOption.Track.DATE_ADDED_DESC)
    .collect { tracks -> adapter.submitList(tracks) }
```

### getRecentlyAddedTracks()

```kotlin
fun getRecentlyAddedTracks(limit: Int = 50): Flow<List<Track>>
```

Tracks ordered by `dateAdded` descending — the most recently added tracks first.

### getRecentlyAddedAlbums()

```kotlin
fun getRecentlyAddedAlbums(limit: Int = 50): Flow<List<Album>>
```

Albums ordered by the most recently added track within them.

---

## Category lists

All category queries follow the same pattern — a `Flow` with a sort parameter defaulting to a sensible value. See [sorting.md](sorting.md) for every available sort option per category.

### Artists and Album Artists

```kotlin
fun getArtists(sort: SortOption.Artist = SortOption.Artist.NAME_ASC): Flow<List<Artist>>
fun getAlbumArtists(sort: SortOption.Artist = SortOption.Artist.NAME_ASC): Flow<List<Artist>>
```

`getArtists()` returns track artists — anyone who appears in the artist tag of at least one track. `getAlbumArtists()` returns album artists — anyone who appears in the album artist tag. Both return `List<Artist>` but the counts differ: an artist's `albumCount` in `getArtists()` reflects albums where they're the album artist, whereas in `getAlbumArtists()` it reflects albums they directly own.

### Albums

```kotlin
fun getAlbums(sort: SortOption.Album = SortOption.Album.TITLE_ASC): Flow<List<Album>>
```

### Genres

```kotlin
fun getGenres(sort: SortOption.Genre = SortOption.Genre.NAME_ASC): Flow<List<Genre>>
```

### Composers

```kotlin
fun getComposers(sort: SortOption.Composer = SortOption.Composer.NAME_ASC): Flow<List<Composer>>
```

### Lyricists

```kotlin
fun getLyricists(sort: SortOption.Lyricist = SortOption.Lyricist.NAME_ASC): Flow<List<Lyricist>>
```

### Years

```kotlin
fun getYears(sort: SortOption.Year = SortOption.Year.YEAR_DESC): Flow<List<Year>>
```

### Folders

```kotlin
fun getFolders(sort: SortOption.Folder = SortOption.Folder.NAME_ASC): Flow<List<Folder>>
```

Only folders that contain at least one indexed track appear here.

---

## Navigation queries

Navigation queries power detail screens — artist detail, album detail, genre detail, and so on. They all take an ID (or path for folders) and a sort option, and return a live `Flow`.

### Tracks by category

```kotlin
fun getTracksForArtist(artistId: Long, sort: ContextualSortOption.Track = TITLE_ASC): Flow<List<Track>>
fun getTracksForAlbumArtist(artistId: Long, sort: ContextualSortOption.Track = TITLE_ASC): Flow<List<Track>>
fun getTracksForAlbum(albumId: Long, sort: ContextualSortOption.Track = TRACK_NUMBER_ASC): Flow<List<Track>>
fun getTracksForGenre(genreId: Long, sort: ContextualSortOption.Track = TITLE_ASC): Flow<List<Track>>
fun getTracksForComposer(composerId: Long, sort: ContextualSortOption.Track = TITLE_ASC): Flow<List<Track>>
fun getTracksForLyricist(lyricistId: Long, sort: ContextualSortOption.Track = TITLE_ASC): Flow<List<Track>>
fun getTracksForYear(year: Int, sort: ContextualSortOption.Track = TITLE_ASC): Flow<List<Track>>
fun getTracksForFolder(folderPath: String, sort: ContextualSortOption.Track = TRACK_NUMBER_ASC): Flow<List<Track>>
```

`getTracksForAlbum()` and `getTracksForFolder()` default to `TRACK_NUMBER_ASC` — disc number first, then track number — which preserves the natural album order. All others default to `TITLE_ASC`.

```kotlin
// Album detail screen
Catalog.library.getTracksForAlbum(albumId).collect { tracks ->
    adapter.submitList(tracks)
}

// Artist detail screen — all tracks by this artist across the whole library
Catalog.library.getTracksForArtist(artistId).collect { tracks ->
    adapter.submitList(tracks)
}
```

### Albums by category

These return `List<MatchedAlbum>` — each result pairs a full `Album` with context about how many tracks matched. See [MatchedAlbum](core-concepts.md#matchedalbum-and-matchedgenre).

```kotlin
// Albums where this artist appears as a track artist but doesn't own the album
fun getAppearsOnAlbumsForTrackArtist(artistId: Long, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<MatchedAlbum>>

fun getAlbumsForGenre(genreId: Long, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<MatchedAlbum>>
fun getAlbumsForComposer(composerId: Long, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<MatchedAlbum>>
fun getAlbumsForLyricist(lyricistId: Long, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<MatchedAlbum>>
fun getAlbumsForFolder(folderPath: String, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<MatchedAlbum>>
fun getAlbumsForYear(year: Int, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<MatchedAlbum>>
```

For album artist detail screens, use `getAlbumsForAlbumArtist()` instead — it returns `List<Album>` directly since the artist owns those albums outright:

```kotlin
fun getAlbumsForAlbumArtist(artistId: Long, sort: ContextualSortOption.Album = TITLE_ASC): Flow<List<Album>>
```

A typical artist detail screen uses both:

```kotlin
// Main releases — albums this artist owns
val mainReleases = Catalog.library.getAlbumsForAlbumArtist(artistId)

// Appears on — albums this artist features on as a track artist
val appearsOn = Catalog.library.getAppearsOnAlbumsForTrackArtist(artistId)
```

### Genres by category

All genre navigation queries return `List<MatchedGenre>`.

```kotlin
fun getGenresForAlbum(albumId: Long, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
fun getGenresForTrackArtist(artistId: Long, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
fun getGenresForAlbumArtist(artistId: Long, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
fun getGenresForComposer(composerId: Long, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
fun getGenresForLyricist(lyricistId: Long, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
fun getGenresForFolder(folderPath: String, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
fun getGenresForYear(year: Int, sort: ContextualSortOption.Genre = NAME_ASC): Flow<List<MatchedGenre>>
```

Useful for displaying a genre chip row on an album or artist detail screen.

---

## Get by ID

Single-entity lookups. All return a `Flow` that emits `null` if the entity doesn't exist.

```kotlin
fun getTrackArtistById(artistId: Long): Flow<Artist?>
fun getAlbumArtistById(artistId: Long): Flow<Artist?>
fun getAlbumById(albumId: Long): Flow<Album?>
fun getGenreById(genreId: Long): Flow<Genre?>
fun getComposerById(composerId: Long): Flow<Composer?>
fun getLyricistById(lyricistId: Long): Flow<Lyricist?>
fun getYearByValue(year: Int): Flow<Year?>
fun getFolderByPath(folderPath: String): Flow<Folder?>
```

Also available — all participating track artists for a given album:

```kotlin
fun getArtistsForAlbum(albumId: Long): Flow<List<Artist>>
```

Useful for showing a collaborating artists row on an album detail screen.

---

## Cover art

Cover art is loaded from the audio file itself. The `coverArtPath` field on `Album`, `Artist`, `Genre`, `Composer`, `Lyricist`, `Year`, `Folder`, and `Playlist` is an `ArtPath` containing the file path and a `dateModified` timestamp for cache-busting.

Always include `dateModified` in your image cache key so artwork updates after tag edits are picked up automatically:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(album.coverArtPath?.path)
        .memoryCacheKey("${album.coverArtPath?.path}?t=${album.coverArtPath?.dateModified}")
        .build()
)
```

### Mosaic API

For categories without a single representative cover — artists, genres, years, folders — the mosaic API returns up to N distinct cover art paths (one per album) for drawing a grid.

Two versions are available depending on context:

**Suspend — for list screens.** Call once per visible item. No continuous observation, no subscription overhead during fast fling.

```kotlin
suspend fun getMosaicForTrackArtist(artistId: Long, limit: Int): List<ArtPath>
suspend fun getMosaicForAlbumArtist(artistId: Long, limit: Int): List<ArtPath>
suspend fun getMosaicForGenre(genreId: Long, limit: Int): List<ArtPath>
suspend fun getMosaicForComposer(composerId: Long, limit: Int): List<ArtPath>
suspend fun getMosaicForLyricist(lyricistId: Long, limit: Int): List<ArtPath>
suspend fun getMosaicForYear(year: Int, limit: Int): List<ArtPath>
suspend fun getMosaicForFolder(folderPath: String, limit: Int): List<ArtPath>
```

**Flow — for detail screens.** Live updates when tracks are added or removed. Use on a detail screen where you're watching a single entity.

```kotlin
fun getMosaicForTrackArtistFlow(artistId: Long, limit: Int): Flow<List<ArtPath>>
fun getMosaicForAlbumArtistFlow(artistId: Long, limit: Int): Flow<List<ArtPath>>
fun getMosaicForGenreFlow(genreId: Long, limit: Int): Flow<List<ArtPath>>
fun getMosaicForComposerFlow(composerId: Long, limit: Int): Flow<List<ArtPath>>
fun getMosaicForLyricistFlow(lyricistId: Long, limit: Int): Flow<List<ArtPath>>
fun getMosaicForYearFlow(year: Int, limit: Int): Flow<List<ArtPath>>
fun getMosaicForFolderFlow(folderPath: String, limit: Int): Flow<List<ArtPath>>
```

Pass `limit = 4` for a 2×2 grid or `limit = 9` for a 3×3 grid. The returned list may have fewer items than `limit` if the entity has fewer distinct albums.

```kotlin
// List screen — call once per visible item
LaunchedEffect(artistId) {
    mosaicPaths = Catalog.library.getMosaicForTrackArtist(artistId, limit = 4)
}

// Detail screen — stays live
val mosaicPaths by Catalog.library.getMosaicForTrackArtistFlow(artistId, limit = 4)
    .collectAsState(initial = emptyList())
```

---

## On-demand metadata

Some metadata is expensive to read and not needed for list browsing. The library provides suspend functions for extracting it on demand when a detail screen opens.

### Lyrics

```kotlin
suspend fun getLyricsForTrack(track: Track): String?
```

Extracts embedded lyrics directly from the audio file. Returns the lyrics string, or `null` if the file has no embedded lyrics. Thread-safe — acquires the internal mutex so it cannot run concurrently with a tag write.

```kotlin
// In a detail screen ViewModel
viewModelScope.launch {
    val lyrics = Catalog.library.getLyricsForTrack(track)
    _lyricsState.value = lyrics ?: "No lyrics found"
}
```

Lyrics are read from tags in this order: `LYRICS` → `USLT` → `©LYR` → `TEXT`.

### Embedded artwork

```kotlin
suspend fun getPicturesForTrack(track: Track): List<TrackPicture>
suspend fun getPicturesForPath(path: String): List<TrackPicture>
```

Extracts all embedded artwork from the audio file. Returns a list of `TrackPicture` objects — a file can have multiple embedded images (front cover, back cover, artist photo, etc.). Thread-safe against concurrent tag writes.

`getPicturesForPath()` is useful when you have a file path but no `Track` object — for example, when building a tag editor that works independently of the library's scan state.

```kotlin
// Show embedded artwork on a track detail screen
viewModelScope.launch {
    val pictures = Catalog.library.getPicturesForTrack(track)
    _artworkState.value = pictures
}
```
