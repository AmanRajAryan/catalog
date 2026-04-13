# Playback Stats

`Catalog.stats` tracks listening history and exposes recently played and most played lists across every category.

---

## Table of Contents

1. [Logging playback](#logging-playback)
2. [Logging skips](#logging-skips)
3. [Recently played](#recently-played)
4. [Most played](#most-played)

---

## Logging playback

```kotlin
suspend fun logPlayback(trackId: Long, durationMs: Long): Boolean
```

Records a playback event. Pass the track ID and how long the track was actually played (not necessarily the full duration — you decide when a play counts). Returns `true` if the track was found and stats were updated, `false` if the track doesn't exist.

In a single atomic transaction, `logPlayback()` increments stats for:
- The track (`playCount`, `lastPlayed`, `totalPlayTimeMs`)
- Its album
- Its track artists
- Its album artist (without double-counting if the album artist is also a track artist)
- Its genres
- Its composers
- Its lyricists

```kotlin
// Log a play when the track finishes
viewModelScope.launch {
    Catalog.stats.logPlayback(
        trackId = currentTrack.id,
        durationMs = currentTrack.durationMs
    )
}

// Or log only if the user listened for at least 30 seconds
viewModelScope.launch {
    if (listenedMs >= 30_000L) {
        Catalog.stats.logPlayback(currentTrack.id, listenedMs)
    }
}
```

---

## Logging skips

```kotlin
suspend fun logSkip(trackId: Long): Boolean
```

Increments the skip count for a track. Returns `true` if the track was found, `false` otherwise. Only the track's `skipCount` is updated — album and category stats are not affected.

```kotlin
// Log a skip when the user skips before 10 seconds
viewModelScope.launch {
    if (playedMs < 10_000L) {
        Catalog.stats.logSkip(currentTrack.id)
    }
}
```

---

## Recently played

Live streams ordered by `lastPlayed` descending — the most recently played item first. Items with `lastPlayed == 0` (never played) are excluded.

```kotlin
fun getRecentlyPlayedTracks(limit: Int = 50): Flow<List<Track>>
fun getRecentlyPlayedAlbums(limit: Int = 20): Flow<List<Album>>
fun getRecentlyPlayedArtists(limit: Int = 20): Flow<List<Artist>>
fun getRecentlyPlayedAlbumArtists(limit: Int = 20): Flow<List<Artist>>
fun getRecentlyPlayedGenres(limit: Int = 20): Flow<List<Genre>>
fun getRecentlyPlayedComposers(limit: Int = 20): Flow<List<Composer>>
fun getRecentlyPlayedLyricists(limit: Int = 20): Flow<List<Lyricist>>
fun getRecentlyPlayedYears(limit: Int = 20): Flow<List<Year>>
fun getRecentlyPlayedFolders(limit: Int = 20): Flow<List<Folder>>
```

All re-emit automatically when playback is logged. Use these to build a "Continue Listening" or "Recently Played" section on a home screen.

```kotlin
class HomeViewModel : ViewModel() {
    val recentTracks = Catalog.stats.getRecentlyPlayedTracks(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAlbums = Catalog.stats.getRecentlyPlayedAlbums(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

---

## Most played

Live streams ordered by `playCount` descending. Items with `playCount == 0` are excluded.

```kotlin
fun getMostPlayedTracks(limit: Int = 50): Flow<List<Track>>
fun getMostPlayedAlbums(limit: Int = 20): Flow<List<Album>>
fun getMostPlayedArtists(limit: Int = 20): Flow<List<Artist>>
fun getMostPlayedAlbumArtists(limit: Int = 20): Flow<List<Artist>>
fun getMostPlayedGenres(limit: Int = 20): Flow<List<Genre>>
fun getMostPlayedComposers(limit: Int = 20): Flow<List<Composer>>
fun getMostPlayedLyricists(limit: Int = 20): Flow<List<Lyricist>>
fun getMostPlayedYears(limit: Int = 20): Flow<List<Year>>
fun getMostPlayedFolders(limit: Int = 20): Flow<List<Folder>>
```

Use these for a "Top Tracks" or "Heavy Rotation" section.

```kotlin
val topTracks = Catalog.stats.getMostPlayedTracks(limit = 25)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```
