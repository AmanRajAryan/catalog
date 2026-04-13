# Internal Models

The library separates data into two layers: **entities** (Room database schema) and **public models** (the API surface). This doc explains that separation, why it exists, and how `ModelMapper` bridges the two.

---

## Table of Contents

1. [Why two layers](#why-two-layers)
2. [Entity overview](#entity-overview)
3. [Public model overview](#public-model-overview)
4. [Entity → model mapping](#entity--model-mapping)
5. [Display fields vs raw strings](#display-fields-vs-raw-strings)
6. [rawAlbumString vs albumDisplay](#rawalbumstring-vs-albumdisplay)
7. [TrackWithRelations](#trackwithrelations)

---

## Why two layers

Entities are optimized for storage and querying. They're flat, denormalized in some places, and contain fields that are internal implementation details (like `rawArtistString` and `folderGroup`). Exposing them directly would leak schema details into the public API and make every schema change a breaking change for consumers.

Public models are optimized for UI consumption. They're shaped around what a developer actually needs to build screens — display strings for fast rendering, rich lists for navigation, aggregate stats pre-computed.

The tradeoff is that `ModelMapper` must explicitly map every field. When a new field is added to an entity, it also needs to be added to the corresponding model and mapper. This is intentional — it forces a conscious decision about what becomes public API.

---

## Entity overview

| Entity | Table | Purpose |
|---|---|---|
| `TrackEntity` | `tracks` | One row per audio file |
| `AlbumEntity` | `albums` | One row per unique (title + albumArtist + folderGroup) combination |
| `ArtistEntity` | `artists` | One row per unique artist name (shared between track artists and album artists) |
| `GenreEntity` | `genres` | One row per unique genre name |
| `ComposerEntity` | `composers` | One row per unique composer name |
| `LyricistEntity` | `lyricists` | One row per unique lyricist name |
| `FavoritesEntity` | `favorites` | One row per favorited track, with sort order |
| `PlaylistEntity` | `playlists` | One row per playlist |
| `PlaylistEntryEntity` | `playlist_entries` | One row per track-in-playlist, with sort order |
| `IgnoredFolderEntity` | `ignored_folders` | One row per ignored folder path |
| `TrackArtistRef` | `track_artists` | Junction: track ↔ artist |
| `TrackGenreRef` | `track_genres` | Junction: track ↔ genre |
| `TrackComposerRef` | `track_composers` | Junction: track ↔ composer |
| `TrackLyricistRef` | `track_lyricists` | Junction: track ↔ lyricist |

---

## Public model overview

| Model | Source entities | Notes |
|---|---|---|
| `Track` | `TrackEntity` + all junctions + `AlbumEntity` + `FavoritesEntity` | The most complex mapping — hydrated via `TrackWithRelations` |
| `ExtendedMetadata` | Columns on `TrackEntity` | Embedded inside `Track.metadata` |
| `IdName` | `ArtistEntity` / `GenreEntity` / etc. | Used for `track.artists`, `track.genres`, etc. |
| `Album` | `AlbumDao.AlbumDetails` (a DAO projection) | Never maps directly from `AlbumEntity` — always via a JOIN query |
| `Artist` | `ArtistDao.ArtistWithCount` or `ArtistDao.AlbumArtistWithCounts` | Two different DAO projections, same public model |
| `Genre` | `GenreDao.GenreWithCount` | DAO projection |
| `Composer` | `ComposerDao.ComposerWithCount` | DAO projection |
| `Lyricist` | `LyricistDao.LyricistWithCount` | DAO projection |
| `Year` | `YearDao.YearCount` | DAO projection — year is not an entity, derived from `tracks.year` |
| `Folder` | `FolderDao.FolderCount` | DAO projection — folder is not an entity, derived from `tracks.folderPath` |
| `Playlist` | `PlaylistEntity` + `PlaylistDao.PlaylistWithCount` | Count and art from a JOIN projection |
| `PlaylistItem` | `PlaylistEntryEntity` + `TrackWithRelations` | Nested mapping |
| `FavoritesInfo` | `FavoritesDao.FavoritesInfoResult` | Aggregate projection |
| `MatchedAlbum` | `AlbumDao.MatchedAlbumDetails` | Wraps `AlbumDetails` with match context |
| `MatchedGenre` | `GenreDao.MatchedGenreDetails` | Wraps `GenreWithCount` with match context |
| `ArtPath` | Columns on any entity | `path` + `dateModified` for cache-busting |

---

## Entity → model mapping

All mapping is done in `ModelMapper` (`internal/ModelMapper.kt`). Each function is named `to<Model>()` and takes the appropriate DAO projection as input.

```kotlin
// Track — most complex, requires the full TrackWithRelations
fun toTrack(container: TrackWithRelations): Track

// Category models — each takes its DAO projection
fun toArtist(data: ArtistDao.ArtistWithCount): Artist
fun toArtist(data: ArtistDao.AlbumArtistWithCounts): Artist  // overload for album artists
fun toAlbum(data: AlbumDao.AlbumDetails): Album
fun toGenre(data: GenreDao.GenreWithCount): Genre
fun toComposer(data: ComposerDao.ComposerWithCount): Composer
fun toLyricist(data: LyricistDao.LyricistWithCount): Lyricist
fun toYear(data: YearDao.YearCount): Year
fun toFolder(data: FolderDao.FolderCount): Folder

// Navigation wrappers
fun toMatchedAlbum(data: AlbumDao.MatchedAlbumDetails): MatchedAlbum
fun toMatchedGenre(data: GenreDao.MatchedGenreDetails): MatchedGenre

// Other
fun toPlaylist(entity: PlaylistEntity, trackCount: Int, totalDuration: Long, coverArtPath: ArtPath?): Playlist
fun toFavoritesInfo(data: FavoritesDao.FavoritesInfoResult): FavoritesInfo
fun formatYearRange(min: Int?, max: Int?): String
```

`ModelMapper` is an internal `object` — not injected, not mockable. It's a pure transformation layer with no side effects.

---

## Display fields vs raw strings

`TrackEntity` stores two versions of artist, genre, composer, lyricist, album, and album artist:

**Display fields** — the resolved string used for fast list rendering and sorting:
- `artistDisplay`, `albumDisplay`, `albumArtistDisplay`, `genreDisplay`, `composerDisplay`, `lyricistDisplay`

**Raw strings** — the original tag value stored as-is for junction regeneration:
- `rawArtistString`, `rawGenreString`, `rawComposerString`, `rawLyricistString`, `rawAlbumArtistString`, `rawAlbumString`

The display fields are what the user sees in the UI. The raw strings are what `SmartSplitter` processes to rebuild junction tables when split rules change via `updateConfig()`.

**Why keep both?** When split rules change, the library needs to re-split the original tag values — not the already-split display strings. For example, if `rawArtistString = "Drake feat. Future"` and a new splitter is added, the raw string is re-split into `["Drake", "Future"]`. The display string `"Drake feat. Future"` is preserved exactly as the tag said.

This is intentional and documented in [configuration.md](../docs/configuration.md#updateconfig) — display strings always reflect the original tag, only junctions are updated when split rules change.

---

## rawAlbumString vs albumDisplay

`rawAlbumString` is semantically different from all other `rawXxxString` fields:

- `rawArtistString`, `rawGenreString`, etc. use the same fallback logic as their display counterparts — TagLib value if non-blank, MediaStore otherwise.
- `rawAlbumString` is **always** the pure TagLib value, with no MediaStore fallback. It may be `""` even when `albumDisplay` is not.

This distinction exists because album grouping logic needs to know the true TagLib album value to correctly decide `folderGroup`. If `rawAlbumString` used the MediaStore fallback like the other raw fields, albums could be incorrectly grouped based on MediaStore's stale or garbage values.

In `ModelMapper.toTrack()`, `foundAlbum` in `ExtendedMetadata` is populated from `rawAlbumString`, not `albumDisplay`. This is why `track.metadata.foundAlbum` may be `""` even when `track.album` is not — and why `readTags()` is the right way to get the precise current tag value for a tag editor, not `foundAlbum`.

---

## TrackWithRelations

`TrackWithRelations` is a Room `@Relation` aggregate that bundles a track with all its related entities in a single query. Room resolves the relations automatically using secondary queries under the hood.

```kotlin
data class TrackWithRelations(
    @Embedded val track: TrackEntity,

    @Relation(parentColumn = "id", entityColumn = "id",
        associateBy = Junction(TrackArtistRef::class, ...))
    val artists: List<ArtistEntity>,

    @Relation(parentColumn = "id", entityColumn = "id",
        associateBy = Junction(TrackGenreRef::class, ...))
    val genres: List<GenreEntity>,

    @Relation(parentColumn = "id", entityColumn = "id",
        associateBy = Junction(TrackComposerRef::class, ...))
    val composers: List<ComposerEntity>,

    @Relation(parentColumn = "id", entityColumn = "id",
        associateBy = Junction(TrackLyricistRef::class, ...))
    val lyricists: List<LyricistEntity>,

    @Relation(parentColumn = "albumId", entityColumn = "id")
    val album: AlbumEntity?,

    @Relation(parentColumn = "id", entityColumn = "trackId")
    val favorite: FavoritesEntity?
)
```

The `album` relation is why `track.album` (the public model field) is always live — it's populated from `AlbumEntity` at query time, not from the cached `albumDisplay` string. When `CompilationManager` updates an album's `albumArtistId` to "Various Artists", the next emission of any track query automatically picks up the new album title.

The `favorite` relation is why `track.dateFavorited` is always populated without a separate query — Room checks the `favorites` table for a matching `trackId` on every track load.

Every query that returns `TrackWithRelations` must be annotated with `@Transaction` — Room needs to run multiple queries to populate the relations, and `@Transaction` ensures they all see consistent data.
