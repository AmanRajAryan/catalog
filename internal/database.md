# Database

The library uses Room with a single database file (`music_catalog.db`). This doc covers the schema, indexing decisions, and key query patterns.

---

## Table of Contents

1. [Schema overview](#schema-overview)
2. [TrackEntity](#trackentity)
3. [AlbumEntity](#albumentity)
4. [ArtistEntity](#artistentity)
5. [Junction tables](#junction-tables)
6. [Year and Folder — virtual categories](#year-and-folder--virtual-categories)
7. [Indices](#indices)
8. [Garbage collection queries](#garbage-collection-queries)
9. [Dynamic sorting with RawQuery](#dynamic-sorting-with-rawquery)
10. [insertOrGetId pattern](#insertorgetid-pattern)
11. [Database configuration](#database-configuration)

---

## Schema overview

```
tracks ────────────────────┐
  ├── albumId → albums     │
  │                        │
  ├── track_artists ────── artists
  ├── track_genres ─────── genres
  ├── track_composers ──── composers
  └── track_lyricists ──── lyricists

favorites
  └── trackId → tracks (CASCADE)

playlists
playlist_entries
  ├── playlistId → playlists (CASCADE)
  └── trackId → tracks (CASCADE)

ignored_folders
```

---

## TrackEntity

The central table. One row per audio file.

**Key columns:**

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK, autoincrement) | Internal database ID |
| `mediaStoreId` | `Long` | Android MediaStore `_ID`. Used for move/rename detection — stable across file moves. |
| `albumId` | `Long?` (FK → albums, SET_NULL) | Nullable — SET_NULL on album delete prevents orphaned tracks |
| `path` | `String` (UNIQUE) | Absolute file system path |
| `folderPath` | `String` | Parent directory path |
| `folderName` | `String` | Parent directory name |
| `artistDisplay` | `String` | Resolved display string for fast rendering and sorting |
| `rawArtistString` | `String` | Original tag value for junction regeneration |
| `albumDisplay` | `String` | MediaStore + TagLib fallback |
| `rawAlbumString` | `String` | Pure TagLib value only — may be blank when `albumDisplay` is not |
| `albumArtistDisplay` | `String` | Used for sorting by album artist |
| `rawAlbumArtistString` | `String` | Original album artist tag for compilation restore logic |
| `codec` | `String` | Audio codec (e.g. "FLAC", "AAC") |
| `bitsPerSample` | `Int` | Bit depth. 0 if unavailable (lossy formats). |
| `playCount` | `Int` | Incremented by `logPlayback()` |
| `lastPlayed` | `Long` | Unix ms timestamp. 0 if never played. |
| `totalPlayTimeMs` | `Long` | Cumulative listening time in ms |
| `skipCount` | `Int` | Incremented by `logSkip()` |

---

## AlbumEntity

One row per unique `(title, albumArtistId, folderGroup)` combination.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK) | |
| `title` | `String` | Album title |
| `albumArtistId` | `Long?` (FK → artists, SET_NULL) | Nullable — blank album artist maps to the `""` artist row, not NULL |
| `folderGroup` | `String` | `""` for albums with a real artist tag (global), `folderPath` for tagless albums (scoped) |
| `playCount` | `Int` | Updated by `logPlayback()` |
| `lastPlayed` | `Long` | |
| `totalPlayTimeMs` | `Long` | |

The unique index on `(title, albumArtistId, folderGroup)` enforces album uniqueness. Room's `INSERT OR IGNORE` on conflict means `insertOrGetId()` can try to insert and then fall back to a lookup if the row already exists.

---

## ArtistEntity

One row per unique artist name, shared between track artists and album artists.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK) | |
| `name` | `String` (UNIQUE) | Artist name. Blank string `""` is a valid name representing "no artist". |
| `playCount` | `Int` | For track artists: updated directly. For album artists: aggregated from album stats in queries. |
| `lastPlayed` | `Long` | |
| `totalPlayTimeMs` | `Long` | |

The unique index on `name` prevents duplicates and enables fast lookups by `getArtistId(name)`. The blank string `""` is a valid, unique name — it represents tracks and albums with no artist tag and is a real entity in the database.

---

## Junction tables

Four junction tables implement the many-to-many relationships:

```
track_artists    (trackId, artistId)   — composite PK
track_genres     (trackId, genreId)    — composite PK
track_composers  (trackId, composerId) — composite PK
track_lyricists  (trackId, lyricistId) — composite PK
```

All four use `CASCADE` on delete for both FK sides — deleting a track deletes all its junction entries, and deleting an artist/genre/etc. deletes all references to it. In practice, entities are only deleted by GC after they have no remaining tracks, so the cascade from the entity side rarely fires.

Each junction table has an index on the non-primary FK column for efficient reverse lookups (e.g. "all tracks for artist X").

---

## Year and Folder — virtual categories

Year and Folder are not stored as entities. They're derived at query time from `tracks.year` and `tracks.folderPath` respectively:

```sql
-- Year query
SELECT tracks.year, COUNT(tracks.id) as count, ...
FROM tracks
GROUP BY tracks.year
ORDER BY ...

-- Folder query
SELECT folderName as name, folderPath as path, COUNT(tracks.id) as count, ...
FROM tracks
GROUP BY tracks.folderPath, tracks.folderName
ORDER BY ...
```

This means Year and Folder entries appear and disappear automatically as tracks are added and removed — no separate insert/delete logic needed. The downside is that year and folder don't have their own play stats rows — stats are aggregated from track stats at query time.

---

## Indices

Indices on `TrackEntity`:

| Index | Columns | Purpose |
|---|---|---|
| UNIQUE | `path` | Fast path lookup and uniqueness enforcement |
| | `albumId` | FK lookup speed |
| | `title` | Sorting by title |
| | `artistDisplay` | Sorting by artist |
| | `dateAdded` | Recently added queries |
| | `folderPath` | Folder browsing + compilation checks |
| | `year` | Year filtering |

Indices on `AlbumEntity`:

| Index | Columns | Purpose |
|---|---|---|
| UNIQUE | `title, albumArtistId, folderGroup` | Album uniqueness |
| | `albumArtistId` | FK lookup speed |

Indices on `ArtistEntity`:

| Index | Columns | Purpose |
|---|---|---|
| UNIQUE | `name` | Name lookup + uniqueness |

Junction tables each have an index on the non-primary FK column for reverse lookups.

---

## Garbage collection queries

GC deletes entities that have no remaining tracks. The queries must be run in dependency order — albums before artists (since albums reference artists):

```sql
-- Albums with no tracks
DELETE FROM albums WHERE id NOT IN (
    SELECT DISTINCT albumId FROM tracks WHERE albumId IS NOT NULL
)

-- Artists with no track junctions AND not referenced as album artist
DELETE FROM artists 
WHERE id NOT IN (SELECT artistId FROM track_artists) 
AND id NOT IN (SELECT albumArtistId FROM albums WHERE albumArtistId IS NOT NULL)

-- Genres/Composers/Lyricists with no junctions
DELETE FROM genres WHERE id NOT IN (SELECT genreId FROM track_genres)
DELETE FROM composers WHERE id NOT IN (SELECT composerId FROM track_composers)
DELETE FROM lyricists WHERE id NOT IN (SELECT lyricistId FROM track_lyricists)
```

GC is always run inside a transaction, immediately after scan writes complete.

---

## Dynamic sorting with RawQuery

Room's `@Query` annotation doesn't support dynamic `ORDER BY` clauses. To support the library's many sort options, the DAOs use `@RawQuery` with `observedEntities`:

```kotlin
@Transaction
@RawQuery(observedEntities = [TrackEntity::class, AlbumEntity::class, ...])
abstract fun getTracksSortedRaw(query: SupportSQLiteQuery): Flow<List<TrackWithRelations>>

fun getTracksSorted(sort: SortOption.Track): Flow<List<TrackWithRelations>> {
    val sql = "SELECT * FROM tracks ORDER BY ${sort.sqlString}"
    return getTracksSortedRaw(SimpleSQLiteQuery(sql))
}
```

The `observedEntities` list tells Room which tables to watch for changes. Without it, `@RawQuery` Flows would never re-emit. The list must include every table referenced in the query that could change.

Sort SQL strings are defined as extension properties on the sort enum in `SortMappings.kt`. For example:

```kotlin
internal val SortOption.Track.sqlString: String
    get() = when (this) {
        SortOption.Track.TITLE_ASC -> "CASE WHEN title = '' THEN 1 ELSE 0 END ASC, title COLLATE NOCASE ASC"
        SortOption.Track.ARTIST_ASC -> "artistDisplay COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        // ...
    }
```

`COLLATE NOCASE` is used for string columns to ensure case-insensitive alphabetical sorting. `CASE WHEN ... = '' THEN 1 ELSE 0 END ASC` pushes blank values to the end of ascending sorts rather than the beginning.

---

## insertOrGetId pattern

Every entity DAO uses a consistent `insertOrGetId()` pattern to handle concurrent inserts safely:

```kotlin
@Transaction
open suspend fun insertOrGetId(name: String): Long {
    val existingId = getArtistId(name)
    if (existingId != null) return existingId
    val newId = insert(ArtistEntity(name = name))  // INSERT OR IGNORE
    return if (newId == -1L) getArtistId(name)!! else newId
}
```

The flow: try to get the existing ID → if not found, insert → if insert returned -1 (conflict, another coroutine inserted first), get the ID again. The `@Transaction` annotation ensures this is atomic. `INSERT OR IGNORE` means a duplicate insert is silently ignored rather than throwing.

---

## Database configuration

```kotlin
Room.databaseBuilder(context.applicationContext, CatalogDatabase::class.java, "music_catalog.db")
    .fallbackToDestructiveMigration(true)
    .build()
```

`fallbackToDestructiveMigration(true)` — if the database schema version changes and no migration path is defined, Room drops and recreates the database. This is acceptable during development but means all user data (playlists, favorites, play counts) is lost on a schema change.

**Before shipping to real users:** implement proper Room migrations for any schema change, or at minimum prompt the user that their data will be reset. The current version is tracked in `CatalogDatabase`:

```kotlin
@Database(
    entities = [...],
    version = 3,
    exportSchema = true
)
```

`exportSchema = true` causes Room to export the schema to a JSON file at build time. This file should be committed to version control — it enables writing migration tests and tracking schema history.
