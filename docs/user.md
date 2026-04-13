# User Data

`Catalog.user` manages user-specific preferences — favorites and ignored folders.

---

## Table of Contents

1. [Favorites](#favorites)
   - [Toggling](#toggling)
   - [Checking status](#checking-status)
   - [Observing favorites](#observing-favorites)
   - [Reordering](#reordering)
   - [Cover art and mosaic](#cover-art-and-mosaic)
2. [Ignored folders](#ignored-folders)

---

## Favorites

### Toggling

```kotlin
suspend fun toggleFavorite(trackId: Long): Boolean
```

Toggles the favorite status of a track. Returns `true` if the track is now a favorite, `false` if it was removed from favorites or if the track doesn't exist.

```kotlin
val isFavorite = Catalog.user.toggleFavorite(track.id)
```

When a track is favorited, it's added to the end of the user-defined favorites order. When unfavorited, all subsequent entries shift up to close the gap — the sort order is always gap-free.

### Checking status

```kotlin
suspend fun isFavorite(trackId: Long): Boolean
```

Returns whether a track is currently favorited. For most UI use cases you don't need this — every `Track` object already has `dateFavorited` populated and `dateFavorited > 0` means the track is favorited. Use `isFavorite()` only when you have a track ID but no `Track` object.

```kotlin
// Prefer this — no extra call needed
val isFav = track.dateFavorited > 0

// Use this only when you don't have the Track object
val isFav = Catalog.user.isFavorite(trackId)
```

### Observing favorites

```kotlin
fun getFavoriteTracks(
    sort: ContextualSortOption.FavoriteTrack = ContextualSortOption.FavoriteTrack.USER_DEFINED
): Flow<List<Track>>
```

Live stream of all favorited tracks. Defaults to the user's arranged order (`USER_DEFINED`). Pass a different sort option to sort by title, date favorited, play count, etc. See [sorting.md](sorting.md#favoritetrack) for all options.

```kotlin
Catalog.user.getFavoriteTracks().collect { tracks ->
    adapter.submitList(tracks)
}
```

### Aggregate info

```kotlin
val favoritesInfo: Flow<FavoritesInfo>
```

A live stream of aggregate metadata for the favorites collection — track count, total duration, and cover art. Useful for a "Favorites" card on a home screen.

```kotlin
data class FavoritesInfo(
    val trackCount: Int,
    val totalDurationMs: Long,
    val coverArtPath: ArtPath?
)
```

```kotlin
Catalog.user.favoritesInfo.collect { info ->
    trackCountLabel.text = "${info.trackCount} tracks"
}
```

### Reordering

```kotlin
suspend fun moveFavoriteEntry(trackId: Long, fromPosition: Int, toPosition: Int)
```

Moves a single favorite to a new position. Optimized for drag-and-drop.

```kotlin
Catalog.user.moveFavoriteEntry(
    trackId = item.id,
    fromPosition = fromPosition,
    toPosition = toPosition
)
```

```kotlin
suspend fun reorderFavorites(updates: Map<Long, Int>)
```

Bulk reorder. Key is `trackId`, value is the new `sortOrder`. Intended for "shuffle favorites" or "sort and save" operations.

```kotlin
val shuffled = favoriteTracks.shuffled()
    .mapIndexed { index, track -> track.id to index }
    .toMap()

Catalog.user.reorderFavorites(shuffled)
```

### Cover art and mosaic

`FavoritesInfo.coverArtPath` — a single `ArtPath` for use as cover art on a favorites card.

```kotlin
// For list screens
suspend fun getMosaicForFavorites(limit: Int): List<ArtPath>

// For detail/favorites screen — live updates
fun getMosaicForFavoritesFlow(limit: Int): Flow<List<ArtPath>>
```

Use the Flow version on the favorites screen so the mosaic updates when tracks are added or removed.

```kotlin
val mosaicPaths by Catalog.user.getMosaicForFavoritesFlow(limit = 4)
    .collectAsState(initial = emptyList())
```

---

## Ignored folders

Ignored folders are excluded from scanning entirely. Any file whose path is inside an ignored folder is never indexed, and any tracks already in the database from that folder are deleted immediately when the folder is ignored.

### Ignoring a folder

```kotlin
suspend fun ignoreFolder(path: String)
```

Marks a folder as ignored, immediately deletes all its tracks from the database, and syncs the path into `CatalogConfig.scannerIgnores` so the scanner won't re-ingest it on the next scan. Orphaned artists, albums, genres, composers, and lyricists are cleaned up in the same transaction.

```kotlin
Catalog.user.ignoreFolder("/storage/emulated/0/WhatsApp/Media")
```

> Pass the absolute folder path without a trailing slash.

### Unignoring a folder

```kotlin
suspend fun unignoreFolder(path: String)
```

Removes a folder from the ignore list and triggers a full scan so its tracks are re-indexed. The tracks won't appear immediately — they'll show up after the scan completes.

```kotlin
Catalog.user.unignoreFolder("/storage/emulated/0/WhatsApp/Media")
```

### Observing ignored folders

```kotlin
val ignoredFolders: Flow<List<IgnoredFolder>>
```

Live stream of all currently ignored folders, ordered by path.

```kotlin
data class IgnoredFolder(
    val path: String,
    val dateAdded: Long  // Unix timestamp in milliseconds
)
```

```kotlin
Catalog.user.ignoredFolders.collect { folders ->
    adapter.submitList(folders)
}
```

### Relationship with CatalogConfig

`ignoreFolder()` and `unignoreFolder()` keep `Catalog.user` and `CatalogConfig.scannerIgnores` in sync automatically — you don't need to call `updateConfig()` separately. The ignore list is the single source of truth regardless of whether a folder was added via `CatalogUser` or `CatalogConfig`.
