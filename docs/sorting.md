# Sorting

Every list query in `Catalog.library` accepts a sort parameter. There are two types of sort options depending on context.

**`SortOption`** — for top-level lists (all tracks, all albums, all artists, etc.)
**`ContextualSortOption`** — for navigation queries (tracks for an album, albums for a genre, etc.)

All sort parameters have sensible defaults so you only need to pass one when you want something other than the default.

---

## Table of Contents

1. [SortOption](#sortoption)
   - [Track](#track)
   - [Album](#album)
   - [Artist](#artist)
   - [Genre](#genre)
   - [Composer](#composer)
   - [Lyricist](#lyricist)
   - [Year](#year)
   - [Folder](#folder)
   - [Playlist](#playlist)
2. [ContextualSortOption](#contextualsortoption)
   - [Track](#contextual-track)
   - [Album](#contextual-album)
   - [Genre](#contextual-genre)
   - [PlaylistTrack](#playlisttrack)
   - [FavoriteTrack](#favoritetrack)

---

## SortOption

Used with top-level list queries: `getTracks()`, `getAlbums()`, `getArtists()`, etc.

```kotlin
Catalog.library.getTracks(SortOption.Track.DATE_ADDED_DESC)
Catalog.library.getAlbums(SortOption.Album.YEAR_DESC)
Catalog.library.getArtists(SortOption.Artist.TRACK_COUNT_DESC)
```

---

### Track

| Option | Description |
|---|---|
| `TITLE_ASC` *(default)* | A → Z by title. Tracks with blank titles sort last. |
| `TITLE_DESC` | Z → A by title. |
| `ARTIST_ASC` | A → Z by artist display string, then title. Tracks with blank artists sort last. |
| `ARTIST_DESC` | Z → A by artist. |
| `ALBUM_ASC` | A → Z by album, then track number, then title. Blank albums sort last. |
| `ALBUM_DESC` | Z → A by album. |
| `ALBUM_ARTIST_ASC` | A → Z by album artist. Falls back to track artist for tracks with no album artist tag, so no tracks are orphaned in a blank group. |
| `ALBUM_ARTIST_DESC` | Z → A by album artist. |
| `DATE_ADDED_DESC` | Most recently added first. |
| `DATE_ADDED_ASC` | Oldest first. |
| `DATE_MODIFIED_DESC` | Most recently modified first. |
| `DATE_MODIFIED_ASC` | Oldest modification first. |
| `YEAR_DESC` | Newest year first. |
| `YEAR_ASC` | Oldest year first. Tracks with no year tag sort last. |
| `DURATION_DESC` | Longest first. |
| `DURATION_ASC` | Shortest first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Album

| Option | Description |
|---|---|
| `TITLE_ASC` *(default)* | A → Z by album title. Blank titles sort last. |
| `TITLE_DESC` | Z → A. |
| `ARTIST_ASC` | A → Z by album artist name, then title. |
| `ARTIST_DESC` | Z → A by album artist. |
| `DATE_ADDED_DESC` | Album with most recently added track first. |
| `DATE_ADDED_ASC` | Album with oldest track first. |
| `YEAR_DESC` | Newest year first (uses max year across all tracks in the album). |
| `YEAR_ASC` | Oldest year first (uses min year). Albums with no year sort last. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Artist

Used for both `getArtists()` (track artists) and `getAlbumArtists()`.

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z by name. Blank names sort last. |
| `NAME_DESC` | Z → A. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `ALBUM_COUNT_DESC` | Most albums first. |
| `ALBUM_COUNT_ASC` | Fewest albums first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Genre

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z. Blank names sort last. |
| `NAME_DESC` | Z → A. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `ALBUM_COUNT_DESC` | Most albums first. |
| `ALBUM_COUNT_ASC` | Fewest albums first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Composer

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z. |
| `NAME_DESC` | Z → A. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `ALBUM_COUNT_DESC` | Most albums first. |
| `ALBUM_COUNT_ASC` | Fewest albums first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Lyricist

Same options as Composer.

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z. |
| `NAME_DESC` | Z → A. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `ALBUM_COUNT_DESC` | Most albums first. |
| `ALBUM_COUNT_ASC` | Fewest albums first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Year

| Option | Description |
|---|---|
| `YEAR_DESC` *(default)* | Newest year first. |
| `YEAR_ASC` | Oldest year first. |
| `TRACK_COUNT_DESC` | Year with most tracks first. |
| `TRACK_COUNT_ASC` | Year with fewest tracks first. |
| `ALBUM_COUNT_DESC` | Year with most albums first. |
| `ALBUM_COUNT_ASC` | Year with fewest albums first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `PLAY_COUNT_DESC` | Most played year first. |
| `PLAY_COUNT_ASC` | Least played year first. |
| `RECENTLY_PLAYED` | Most recently played year first. |

---

### Folder

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z by folder name. |
| `NAME_DESC` | Z → A by folder name. |
| `PATH_ASC` | A → Z by full path. |
| `PATH_DESC` | Z → A by full path. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `ALBUM_COUNT_DESC` | Most albums first. |
| `ALBUM_COUNT_ASC` | Fewest albums first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `PLAY_COUNT_DESC` | Most played folder first. |
| `PLAY_COUNT_ASC` | Least played folder first. |
| `RECENTLY_PLAYED` | Most recently played folder first. |

---

### Playlist

Used with `Catalog.playlists.getPlaylists()`.

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z. Blank names sort last. |
| `NAME_DESC` | Z → A. |
| `TRACK_COUNT_DESC` | Most tracks first. |
| `TRACK_COUNT_ASC` | Fewest tracks first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest total duration first. |
| `DATE_CREATED_DESC` | Most recently created first. |
| `DATE_CREATED_ASC` | Oldest first. |
| `DATE_MODIFIED_DESC` | Most recently modified first. |
| `DATE_MODIFIED_ASC` | Oldest modification first. |

---

## ContextualSortOption

Used with navigation queries — when you're already inside a category and want to sort the results within that context.

```kotlin
Catalog.library.getTracksForAlbum(albumId, ContextualSortOption.Track.TRACK_NUMBER_ASC)
Catalog.library.getAlbumsForGenre(genreId, ContextualSortOption.Album.YEAR_DESC)
```

---

### Contextual Track

Used with all `getTracksFor*()` navigation queries.

| Option | Description |
|---|---|
| `TRACK_NUMBER_ASC` | Disc number first, then track number. The natural album order. Default for `getTracksForAlbum()` and `getTracksForFolder()`. |
| `TITLE_ASC` *(default for others)* | A → Z by title. |
| `TITLE_DESC` | Z → A. |
| `DATE_ADDED_DESC` | Most recently added first. |
| `DATE_ADDED_ASC` | Oldest first. |
| `DATE_MODIFIED_DESC` | Most recently modified first. |
| `DATE_MODIFIED_ASC` | Oldest modification first. |
| `YEAR_DESC` | Newest year first. |
| `YEAR_ASC` | Oldest year first. Tracks with no year sort last. |
| `DURATION_DESC` | Longest first. |
| `DURATION_ASC` | Shortest first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Contextual Album

Used with all `getAlbumsFor*()` navigation queries that return `MatchedAlbum`.

| Option | Description |
|---|---|
| `TITLE_ASC` *(default)* | A → Z by album title. |
| `TITLE_DESC` | Z → A. |
| `DATE_ADDED_DESC` | Album with most recently added track first. |
| `DATE_ADDED_ASC` | Album with oldest track first. |
| `YEAR_DESC` | Newest year first. |
| `YEAR_ASC` | Oldest year first. |
| `TOTAL_TRACK_COUNT_DESC` | Albums with most total tracks first. |
| `TOTAL_TRACK_COUNT_ASC` | Albums with fewest total tracks first. |
| `MATCHED_TRACK_COUNT_DESC` | Albums with most matched tracks first — e.g. albums where most tracks belong to the queried genre. |
| `MATCHED_TRACK_COUNT_ASC` | Albums with fewest matched tracks first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### Contextual Genre

Used with all `getGenresFor*()` navigation queries that return `MatchedGenre`.

| Option | Description |
|---|---|
| `NAME_ASC` *(default)* | A → Z. |
| `NAME_DESC` | Z → A. |
| `TOTAL_TRACK_COUNT_DESC` | Genre's total track count, descending. |
| `TOTAL_TRACK_COUNT_ASC` | Genre's total track count, ascending. |
| `MATCHED_TRACK_COUNT_DESC` | Genres with most matched tracks in this context first. |
| `MATCHED_TRACK_COUNT_ASC` | Genres with fewest matched tracks first. |
| `DURATION_DESC` | Longest total duration first. |
| `DURATION_ASC` | Shortest first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### PlaylistTrack

Used with `Catalog.playlists.getPlaylistTracks()`.

| Option | Description |
|---|---|
| `USER_DEFINED` *(default)* | The order the user arranged the tracks — by `sortOrder` ascending. |
| `TITLE_ASC` | A → Z by title. |
| `TITLE_DESC` | Z → A. |
| `DATE_ADDED_DESC` | Most recently added to library first. |
| `DATE_ADDED_ASC` | Oldest library entry first. |
| `DATE_MODIFIED_DESC` | Most recently modified file first. |
| `DATE_MODIFIED_ASC` | Oldest file modification first. |
| `YEAR_DESC` | Newest year first. |
| `YEAR_ASC` | Oldest year first. |
| `DURATION_DESC` | Longest first. |
| `DURATION_ASC` | Shortest first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |

---

### FavoriteTrack

Used with `Catalog.user.getFavoriteTracks()`.

| Option | Description |
|---|---|
| `USER_DEFINED` *(default)* | The order the user arranged favorites — by `sortOrder` ascending. |
| `DATE_FAVORITED_DESC` | Most recently favorited first. |
| `DATE_FAVORITED_ASC` | Oldest favorite first. |
| `TITLE_ASC` | A → Z by title. |
| `TITLE_DESC` | Z → A. |
| `DATE_ADDED_DESC` | Most recently added to library first. |
| `DATE_ADDED_ASC` | Oldest library entry first. |
| `DATE_MODIFIED_DESC` | Most recently modified file first. |
| `DATE_MODIFIED_ASC` | Oldest file modification first. |
| `YEAR_DESC` | Newest year first. |
| `YEAR_ASC` | Oldest year first. |
| `DURATION_DESC` | Longest first. |
| `DURATION_ASC` | Shortest first. |
| `PLAY_COUNT_DESC` | Most played first. |
| `PLAY_COUNT_ASC` | Least played first. |
| `RECENTLY_PLAYED` | Most recently played first. |
