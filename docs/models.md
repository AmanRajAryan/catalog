# Models Reference

All public data models returned by Catalog and CatalogEditor. If you haven't read [core-concepts.md](core-concepts.md) yet, do that first — it explains patterns like display strings vs rich lists and `MatchedAlbum` that come up throughout this reference.

---

## Table of Contents

1. [Track](#track)
   - [IdName](#idname)
   - [ExtendedMetadata](#extendedmetadata)
2. [Category models](#category-models)
   - [Artist](#artist)
   - [Album](#album)
   - [Genre](#genre)
   - [Composer](#composer)
   - [Lyricist](#lyricist)
   - [Year](#year)
   - [Folder](#folder)
3. [Navigation wrappers](#navigation-wrappers)
   - [MatchedAlbum](#matchedalbum)
   - [MatchedGenre](#matchedgenre)
4. [Playlist models](#playlist-models)
   - [Playlist](#playlist)
   - [PlaylistItem](#playlistitem)
5. [User models](#user-models)
   - [FavoritesInfo](#favoritesinfo)
   - [IgnoredFolder](#ignoredfolder)
6. [Search models](#search-models)
   - [SearchResult](#searchresult)
   - [SearchFilter](#searchfilter)
7. [Utility types](#utility-types)
   - [ArtPath](#artpath)
   - [TrackPicture](#trackpicture)
   - [ArtworkUpdate](#artworkupdate)
   - [EditResult](#editresult)

---

## Track

The central model. Returned by all track queries.

```kotlin
data class Track(
    val id: Long,
    val uri: Uri,
    val path: String,

    val albumId: Long?,
    val albumArtistId: Long?,

    val artists: List<IdName>,
    val genres: List<IdName>,
    val composers: List<IdName>,
    val lyricists: List<IdName>,

    val folderName: String,
    val folderPath: String,

    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val lyricist: String,

    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val releaseDate: String,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val mimeType: String,

    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0,
    val skipCount: Int = 0,
    val dateFavorited: Long = 0,

    val metadata: ExtendedMetadata = ExtendedMetadata.EMPTY
)
```

### Key fields

| Field | Notes |
|---|---|
| `id` | Database ID. Use this for all API calls — `getTracksForAlbum(albumId)`, `logPlayback(trackId)`, etc. |
| `uri` | Android content URI. Preferred over `path` for opening the file — works correctly under Scoped Storage on all API levels. |
| `path` | Absolute file system path. Use for TagLib operations or when the content URI isn't appropriate. |
| `albumId` | Foreign key to the album entity. Use with `Catalog.library.getAlbumById()` or `getTracksForAlbum()`. Nullable as a schema safety measure — in practice it's always populated. |
| `albumArtistId` | Foreign key to the album artist entity. Use with `Catalog.library.getAlbumArtistById()` or `getTracksForAlbumArtist()`. Null if the track has no album artist tag. |
| `artists`, `genres`, `composers`, `lyricists` | Rich lists of `IdName` for clickable chips and navigation. See [Display strings vs rich lists](core-concepts.md#display-strings-vs-rich-lists). |
| `artist`, `genre`, `composer`, `lyricist` | Display strings for fast list rendering. Reflects the original tag value — the separator between multiple values depends on what the tag contained. |
| `album` | Resolved live from the album entity at query time, not a cached string. Reflects re-groupings and renames immediately. See [How track.album works](core-concepts.md#how-trackalbum-works). |
| `year` | Integer year parsed from the DATE tag. `0` if missing or unparseable. |
| `releaseDate` | The full raw DATE tag string stored verbatim (e.g. `"2023-06-15"`, `"2023"`). Empty string if absent. Unlike `year`, this is not normalized. |
| `dateAdded` | Unix timestamp in **seconds** — MediaStore's native unit. Multiply by 1000 to convert to milliseconds for use with `Date()`. |
| `dateModified` | Unix timestamp in **milliseconds** — normalized by the scanner. Ready to use with `Date()` directly. |
| `lastPlayed` | Unix timestamp in **milliseconds**. `0` if never played. |
| `dateFavorited` | Unix timestamp in **milliseconds** when the track was favorited. `0` if not a favorite. Check `dateFavorited > 0` to determine favorite status — you never need a separate call. |
| `metadata` | Extended technical metadata. See [ExtendedMetadata](#extendedmetadata) below. |

---

### IdName

```kotlin
data class IdName(val id: Long, val name: String)
```

A pair of database ID and display name. Used in `track.artists`, `track.genres`, `track.composers`, and `track.lyricists` for building clickable chips.

```kotlin
track.artists.forEach { artist ->
    Chip(
        text = artist.name,
        onClick = { navigateToArtist(artist.id) }
    )
}
```

---

### ExtendedMetadata

Technical and tag-level metadata extracted by TagLib. The `found*` fields contain raw values read directly from the file, before any fallback logic was applied. Primarily useful for tag editor screens and debugging.

```kotlin
data class ExtendedMetadata(
    val contentRating: Int,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val format: String,

    val foundYear: Int = 0,
    val foundReleaseDate: String = "",
    val foundComposer: String = "",
    val foundLyricist: String = "",
    val foundAlbumArtist: String = "",
    val foundTrackNumber: Int = 0,
    val foundDiscNumber: Int = 0,
    val foundTitle: String = "",
    val foundArtist: String = "",
    val foundAlbum: String = "",
    val foundGenre: String = ""
) {
    companion object {
        val EMPTY = ExtendedMetadata(0, 0, 0, 2, "")
    }
}
```

| Field | Notes |
|---|---|
| `contentRating` | Parental advisory. `0` = none/unknown, `1` = explicit, `2` = clean. Normalized from various raw tag formats at scan time. |
| `bitrate` | In kbps (e.g. `320`). Derived from three sources in order: TagLib → Android `MediaExtractor` → manual calculation from file size ÷ duration. **The manual fallback includes embedded artwork and tag overhead**, so a 320kbps MP3 with large embedded art may report 600+ kbps. |
| `sampleRate` | In Hz (e.g. `44100`). |
| `channels` | Number of audio channels. `2` = stereo, `6` = 5.1 surround. Defaults to `2` if unavailable. |
| `format` | Human-readable string built from TagLib format field → `MediaExtractor` mime type → `"Unknown"`. Examples: `"16-bit FLAC"`, `"AAC"`, `"Dolby Digital+"`. |
| `foundAlbum` | Pure TagLib value — may be `""` even when `track.album` is not, since `track.album` can fall back to MediaStore. Don't use this to pre-populate a tag editor expecting a non-blank value — use `CatalogEditor.readTags()` instead. |
| `foundLyricist` | Read using tag precedence: `LYRICIST` → `TEXT` → `WRITER`. The `TEXT` fallback may pick up a lyrics tag if no dedicated lyricist tag exists. If precision matters, use `CatalogEditor.readTags()`. |
| `foundGenre` | Not a pure TagLib value — follows the same MediaStore fallback as `track.genre`. Use `CatalogEditor.readTags()` for the unmodified tag. |

When no metadata could be extracted, all technical fields default to `0` or `""` — except `channels` which defaults to `2`. Use `ExtendedMetadata.EMPTY` to check for this state:

```kotlin
if (track.metadata == ExtendedMetadata.EMPTY) {
    // No technical metadata available
}
```

---

## Category models

Summary models with aggregate statistics. They don't contain individual tracks — use the navigation methods in `Catalog.library` to get those.

All category models include play stats fields: `playCount`, `lastPlayed`, and `totalPlayTimeMs`.

---

### Artist

```kotlin
data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

Represents both track artists and album artists — the same model is used for both. `albumCount` is the number of albums where this artist is the album artist. For artists who only appear as track artists and never as album artists, `albumCount` is `0`.

`coverArtPath` is derived from the lexicographically smallest track path associated with this artist.

---

### Album

```kotlin
data class Album(
    val id: Long,
    val title: String,
    val albumArtistId: Long?,
    val albumArtist: String,
    val albumArtistCoverArtPath: ArtPath? = null,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0,
    val year: String = "",
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

| Field | Notes |
|---|---|
| `albumArtistId` | ID of the album artist entity. Use with `Catalog.library.getAlbumArtistById()`. Null for albums with no album artist tag. |
| `albumArtist` | Display name of the album artist. `"Various Artists"` for compilations. |
| `albumArtistCoverArtPath` | Cover art path for the album artist — useful for showing the artist image alongside the album on detail screens. |
| `year` | Formatted year range string — `"2023"` for a single year, `"1985-1990"` if tracks span multiple years. Empty string if no tracks have year metadata. |
| `coverArtPath` | Path to a track within the album for loading cover art. `null` only if the album has no tracks, which doesn't happen in practice. This is the lexicographically smallest path in the album — not necessarily the first track by disc/track number. |

---

### Genre

```kotlin
data class Genre(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

`albumCount` is the number of distinct albums that have at least one track tagged with this genre.

---

### Composer

```kotlin
data class Composer(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

---

### Lyricist

```kotlin
data class Lyricist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

---

### Year

```kotlin
data class Year(
    val year: Int,
    val trackCount: Int,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

`year` is the integer year value (e.g. `2023`). Year `0` represents tracks with no year tag.

---

### Folder

```kotlin
data class Folder(
    val name: String,
    val path: String,
    val trackCount: Int,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
```

`name` is the folder's directory name (e.g. `"Music"`). `path` is the full absolute path (e.g. `"/storage/emulated/0/Music"`). Only folders that contain at least one indexed track appear here.

---

## Navigation wrappers

---

### MatchedAlbum

```kotlin
data class MatchedAlbum(
    val album: Album,
    val matchedTrackCount: Int,
    val matchedDurationMs: Long
)
```

Returned by contextual album queries like `getAlbumsForGenre()`, `getAlbumsForComposer()`, `getAppearsOnAlbumsForTrackArtist()`, etc. `album.trackCount` is the album's total track count. `matchedTrackCount` is how many tracks in that album matched the query context.

```kotlin
// Show match context on a genre detail screen
Catalog.library.getAlbumsForGenre(genreId).collect { results ->
    results.forEach { matched ->
        AlbumRow(
            album = matched.album,
            subtitle = "${matched.matchedTrackCount} of ${matched.album.trackCount} tracks"
        )
    }
}
```

---

### MatchedGenre

```kotlin
data class MatchedGenre(
    val genre: Genre,
    val matchedTrackCount: Int,
    val matchedDurationMs: Long
)
```

Returned by all `getGenresFor*()` contextual queries. Same pattern as `MatchedAlbum` — `genre.trackCount` is the genre's total count, `matchedTrackCount` is how many tracks matched in this context.

---

## Playlist models

---

### Playlist

```kotlin
data class Playlist(
    val id: Long,
    val name: String,
    val dateCreated: Long,
    val dateModified: Long,
    val trackCount: Int,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null
)
```

`dateCreated` and `dateModified` are Unix timestamps in milliseconds. `coverArtPath` is derived from the first track in the playlist by sort order.

---

### PlaylistItem

```kotlin
data class PlaylistItem(
    val entryId: Long,
    val sortOrder: Int,
    val track: Track
)
```

A single entry in a playlist. `entryId` is the unique ID of this entry in the playlist — use it with `Catalog.playlists.removeEntryFromPlaylist()` and `moveEntryInPlaylist()`. `sortOrder` is the entry's position (0-indexed). `track` is the full `Track` object with favorite status already populated.

---

## User models

---

### FavoritesInfo

```kotlin
data class FavoritesInfo(
    val trackCount: Int,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null
)
```

Aggregate metadata for the favorites collection. Observe via `Catalog.user.favoritesInfo`. Useful for displaying a "Favorites" card on a home screen with track count and cover art.

---

### IgnoredFolder

```kotlin
data class IgnoredFolder(
    val path: String,
    val dateAdded: Long
)
```

`dateAdded` is a Unix timestamp in milliseconds when the folder was added to the ignore list.

---

## Search models

---

### SearchResult

```kotlin
data class SearchResult(
    val query: String,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albumArtists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val composers: List<Composer> = emptyList(),
    val lyricists: List<Lyricist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val years: List<Year> = emptyList()
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && artists.isEmpty() && albumArtists.isEmpty() &&
                albums.isEmpty() && genres.isEmpty() && composers.isEmpty() &&
                lyricists.isEmpty() && playlists.isEmpty() && folders.isEmpty() &&
                years.isEmpty()
}
```

A unified container holding results from all requested categories. Lists for categories not included in the search filters are empty. `isEmpty` is `true` when all lists are empty — use this to show an empty state screen.

---

### SearchFilter

```kotlin
enum class SearchFilter {
    TRACKS, ARTISTS, ALBUM_ARTISTS, ALBUMS,
    GENRES, COMPOSERS, LYRICISTS,
    PLAYLISTS, FOLDERS, YEARS
}
```

Controls which categories are searched. Pass a `Set<SearchFilter>` to `Catalog.search()`. See [search.md](search.md) for usage.

---

## Utility types

---

### ArtPath

```kotlin
data class ArtPath(
    val path: String,
    val dateModified: Long
)
```

A cover art path paired with the source file's last-modified timestamp. Always use `dateModified` as part of your image cache key so artwork updates are picked up after tag edits. See [Cover art and cache-busting](core-concepts.md#cover-art-and-cache-busting).

---

### TrackPicture

```kotlin
data class TrackPicture(
    val data: ByteArray,
    val mimeType: String,
    val description: String
)
```

Embedded artwork extracted from an audio file via `Catalog.library.getPicturesForTrack()`. `data` contains the raw image bytes. `description` is optional tag metadata (e.g. `"Front Cover"`, `"Artist"`). `equals()` and `hashCode()` use `contentEquals` on `data` for correct structural equality.

---

### ArtworkUpdate

```kotlin
sealed class ArtworkUpdate {
    object NoChange : ArtworkUpdate()
    object Delete : ArtworkUpdate()
    class Set(val data: ByteArray, val mimeType: String) : ArtworkUpdate()
}
```

Passed to `CatalogEditor.updateTrack()` to control what happens to the file's embedded artwork. `NoChange` leaves it untouched. `Delete` removes it. `Set` replaces it with new image bytes.

---

### EditResult

```kotlin
sealed class EditResult {
    object Success : EditResult()
    data class PermissionRequired(val intentSender: IntentSender) : EditResult()
    data class SafPermissionMissing(val folderPath: String) : EditResult()
    data class InputError(val message: String) : EditResult()
    data class IOError(val message: String) : EditResult()
    object TagWriteFailed : EditResult()
    object ArtWriteFailed : EditResult()
}
```

The result of `CatalogEditor.updateTrack()`. See [editor.md](editor.md) for a full explanation of each case and how to handle them.
