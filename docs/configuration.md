# Configuration

`CatalogConfig` controls how Catalog scans and processes your audio library. It is a plain data class with four fields, all optional with sensible defaults.

```kotlin
data class CatalogConfig(
    val splitExceptions: List<String> = emptyList(),
    val scannerIgnores: List<String> = emptyList(),
    val customSplitters: List<String> = emptyList(),
    val minDurationMs: Long = 10000L
)
```

The library automatically persists the active config to `SharedPreferences` and restores it on the next launch — you never need to store it yourself. On first launch, the `defaultConfig` passed to `initialize()` is used and immediately saved.

---

## Table of Contents

1. [splitExceptions](#splitexceptions)
2. [customSplitters](#customsplitters)
3. [scannerIgnores](#scannerignores)
4. [minDurationMs](#mindurationms)
5. [Passing config at initialization](#passing-config-at-initialization)
6. [Updating config at runtime](#updating-config-at-runtime)
7. [Observing the active config](#observing-the-active-config)

---

## splitExceptions

**Type:** `List<String>` — Default: `emptyList()`

The scanner splits multi-value tag fields — artists, genres, composers, and lyricists — on these separators by default: `;`, `&`, `,`, `feat` / `feat.`, `ft` / `ft.` (matched as whole words). This turns a tag like `"Drake feat. Future"` into two separate linked entities: `Drake` and `Future`.

This works well for most cases, but breaks artist names that legitimately contain those characters — `"AC/DC"` is fine, but `"Earth, Wind & Fire"` would be split into three separate entities.

`splitExceptions` protects specific names from being split. Any entry in this list is treated as a single indivisible unit regardless of what characters it contains. Matching is case-insensitive, but the entity stored in the database always uses the casing defined in your exception list — not whatever casing appeared in the tag.

```kotlin
CatalogConfig(
    splitExceptions = listOf("AC/DC", "Earth, Wind & Fire", "Years & Years")
)
```

Without `"Earth, Wind & Fire"` in the list:
```
"Earth, Wind & Fire" → ["Earth", "Wind", "Fire"]  ✗
```

With it:
```
"Earth, Wind & Fire" → ["Earth, Wind & Fire"]  ✓
```

> **Deduplication:** After splitting, duplicate values are removed. Without `"Years & Years"` in the exception list, `"Years & Years"` would split into `["Years", "Years"]`, then deduplicate to `["Years"]` — a single wrong entity. With the exception, it's preserved as one correct entity.

> **Casing:** If your exception list has `"Earth, Wind & Fire"` and the tag contains `"earth, wind & fire"`, the entity stored will be `"Earth, Wind & Fire"` — the casing from your config wins.

---

## customSplitters

**Type:** `List<String>` — Default: `emptyList()`

Additional separators to split on, beyond the built-in defaults. Each entry is treated as a literal string — special characters are escaped automatically, so `"+"` is matched as a literal plus sign, not a regex quantifier. Matching is case-insensitive.

```kotlin
CatalogConfig(
    customSplitters = listOf("+", " x ", " vs ")
)
```

```
"Artist A + Artist B"   → ["Artist A", "Artist B"]
"Artist A x Artist B"   → ["Artist A", "Artist B"]
"Drake vs Kendrick"     → ["Drake", "Kendrick"]
```

Blank entries and whitespace-only strings are silently ignored — you don't need to guard against empty strings in this list.

> **Interaction with splitExceptions:** Custom splitters and split exceptions work together. If `" x "` is a custom splitter and `"Malcolm X"` is a split exception, `"Malcolm X"` will never be split even though it contains `" x "`.

---

## scannerIgnores

**Type:** `List<String>` — Default: `emptyList()`

Absolute folder paths the scanner will skip entirely. Any audio file whose path exactly matches an entry, or whose path starts with `"<entry>/"`, is excluded from indexing. Ignoring a folder also excludes all of its subdirectories.

```kotlin
CatalogConfig(
    scannerIgnores = listOf(
        "/storage/emulated/0/WhatsApp/Media",
        "/storage/emulated/0/Notifications",
        "/storage/emulated/0/Ringtones"
    )
)
```

> **No trailing slash.** The scanner appends its own `/` separator when checking subdirectories. A path ending in `/` will fail to match anything.

> **Runtime management.** Ignored folders can also be managed at runtime through `Catalog.user.ignoreFolder()` and `Catalog.user.unignoreFolder()`. Those methods update and persist the config automatically — you don't need to call `updateConfig()` manually. See [user.md](user.md#ignored-folders).

---

## minDurationMs

**Type:** `Long` — Default: `10000` (10 seconds)

The minimum duration a track must have to be indexed. Audio files shorter than this threshold are silently skipped during scanning. The default of 10 seconds excludes most ringtones, notification sounds, and UI audio.

```kotlin
CatalogConfig(
    minDurationMs = 30_000L // Only index tracks longer than 30 seconds
)
```

> Changing `minDurationMs` via `updateConfig()` triggers an automatic rescan. Raising the threshold removes tracks that are now too short; lowering it discovers files that were previously skipped. You don't need to call `scan()` manually.

---

## Passing config at initialization

Pass a `CatalogConfig` as `defaultConfig` to `initialize()`. This config is only used on first launch — on subsequent launches, the previously saved config is restored automatically.

```kotlin
Catalog.initialize(
    context = this,
    defaultConfig = CatalogConfig(
        splitExceptions = listOf("AC/DC", "Earth, Wind & Fire"),
        scannerIgnores = listOf("/storage/emulated/0/WhatsApp/Media"),
        minDurationMs = 30_000L
    )
)
```

---

## Updating config at runtime

Call `Catalog.updateConfig()` to apply a new config at any time. Changes take effect immediately and are persisted automatically. Use `copy()` to change only the fields you need:

```kotlin
val current = Catalog.configFlow.value

// Add a split exception
Catalog.updateConfig(
    current.copy(
        splitExceptions = current.splitExceptions + "Simon & Garfunkel"
    )
)

// Add a custom splitter
Catalog.updateConfig(
    current.copy(
        customSplitters = current.customSplitters + "+"
    )
)

// Ignore a new folder
Catalog.updateConfig(
    current.copy(
        scannerIgnores = current.scannerIgnores + "/storage/emulated/0/Podcasts"
    )
)

// Unignore a folder
Catalog.updateConfig(
    current.copy(
        scannerIgnores = current.scannerIgnores.filter {
            it != "/storage/emulated/0/Podcasts"
        }
    )
)
```

### What happens when you call updateConfig()

The library inspects what changed and applies only the necessary side effects:

| What changed | Side effect |
|---|---|
| `splitExceptions` or `customSplitters` | All junction tables (artists, genres, composers, lyricists) are re-split in-place using the raw tag strings already in the database. No rescan needed. Processed in chunks of 500 tracks to keep the database responsive. |
| A path added to `scannerIgnores` | Tracks under that path are deleted from the database immediately. |
| A path removed from `scannerIgnores` | A full scan is triggered so those tracks reappear. |
| `minDurationMs` | A full scan is triggered. |

> **Important:** Track display strings (`track.artist`, `track.genre`, etc.) are **not** updated when split rules change — they always reflect the original tag value as it appeared in the file. Only the junction tables (`track.artists`, `track.genres`, etc.) are re-split. This is intentional: display strings show what the tag said, while junctions power navigation and filtering.

---

## Observing the active config

The current config is exposed as `Catalog.configFlow: StateFlow<CatalogConfig>`. Because it's a `StateFlow`, it always holds a current value — you can read it synchronously without a coroutine:

```kotlin
// Synchronous read — no coroutine needed
val current = Catalog.configFlow.value

// Reactive — re-runs whenever the config changes
lifecycleScope.launch {
    Catalog.configFlow.collect { config ->
        // Update your settings UI with the current ignore list, etc.
    }
}
```
