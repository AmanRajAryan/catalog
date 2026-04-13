# Search

`Catalog.search()` searches the entire library in a single call, returning results across all categories simultaneously as a live `Flow`.

---

## Table of Contents

1. [Basic usage](#basic-usage)
2. [SearchFilter](#searchfilter)
3. [SearchResult](#searchresult)
4. [How track search works](#how-track-search-works)
5. [Empty state](#empty-state)

---

## Basic usage

```kotlin
fun search(query: String, filters: Set<SearchFilter>): Flow<SearchResult>
```

Pass a query string and a set of filters specifying which categories to search. The returned `Flow` emits a new `SearchResult` whenever the query produces different results — for example, when new tracks are added to the library while a search screen is open.

```kotlin
// Search everything
val results = Catalog.search(
    query = "pink floyd",
    filters = SearchFilter.entries.toSet()
)

// Search only tracks and albums
val results = Catalog.search(
    query = "the wall",
    filters = setOf(SearchFilter.TRACKS, SearchFilter.ALBUMS)
)
```

Collect in your ViewModel:

```kotlin
class SearchViewModel : ViewModel() {
    private val _query = MutableStateFlow("")

    val results: StateFlow<SearchResult> = _query
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(SearchResult(query = ""))
            else Catalog.search(query, SearchFilter.entries.toSet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchResult(query = "")
        )

    fun onQueryChanged(query: String) {
        _query.value = query
    }
}
```

If the query is blank, `search()` returns an empty `SearchResult` immediately without touching the database.

---

## SearchFilter

```kotlin
enum class SearchFilter {
    TRACKS,
    ARTISTS,
    ALBUM_ARTISTS,
    ALBUMS,
    GENRES,
    COMPOSERS,
    LYRICISTS,
    PLAYLISTS,
    FOLDERS,
    YEARS
}
```

Only include the filters you actually need — omitting categories reduces database work. A search screen that only shows tracks and artists should only pass `setOf(SearchFilter.TRACKS, SearchFilter.ARTISTS)`.

---

## SearchResult

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
}
```

Lists for categories not included in the filters are always empty. Use `isEmpty` to show an empty state screen:

```kotlin
results.collect { result ->
    if (result.isEmpty) {
        showEmptyState()
    } else {
        showResults(result)
    }
}
```

Each list contains the full model object — `tracks` contains full `Track` objects with favorite status already populated, `albums` contains full `Album` objects with cover art, and so on.

---

## How track search works

Track search uses multi-token AND matching. The query is split on whitespace and each token must match at least one of: title, artist display string, or album display string.

```
Query: "pink floyd wall"

Matches tracks where:
  "pink" appears in title OR artist OR album
  AND "floyd" appears in title OR artist OR album
  AND "wall" appears in title OR artist OR album
```

This means a query like `"comfortably numb pink floyd"` will find the track even though `"comfortably numb"` is in the title and `"pink floyd"` is in the artist — all tokens are checked across all three fields.

All other categories (artists, albums, genres, etc.) use a single `LIKE '%query%'` match on the name field.

Results within each category are sorted alphabetically by name/title.

---

## Empty state

A blank query returns `SearchResult(query = "")` immediately — no database work, all lists empty, `isEmpty = true`. Use this to show a default state (recent searches, suggestions, etc.) before the user starts typing.

```kotlin
results.collect { result ->
    when {
        result.query.isBlank() -> showDefaultState()
        result.isEmpty -> showNoResultsState(result.query)
        else -> showResults(result)
    }
}
```
