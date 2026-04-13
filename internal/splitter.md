# SmartSplitter

`SmartSplitter` splits multi-value tag strings — artists, genres, composers, lyricists — into individual entity names. It supports configurable separators, split exceptions, and handles edge cases like artist names that contain separator characters.

---

## Table of Contents

1. [Overview](#overview)
2. [Default separators](#default-separators)
3. [Custom splitters](#custom-splitters)
4. [Split exceptions](#split-exceptions)
5. [The split algorithm](#the-split-algorithm)
6. [Protection phase](#protection-phase)
7. [Splitting phase](#splitting-phase)
8. [Restoration phase](#restoration-phase)
9. [Performance notes](#performance-notes)
10. [Edge cases](#edge-cases)

---

## Overview

`SmartSplitter` is constructed once per `ScannerService` instance and rebuilt whenever `updateConfig()` changes the split rules. It pre-compiles all regexes at construction time — not on every `split()` call.

```kotlin
class SmartSplitter(private val config: CatalogConfig)
```

The two pieces built at construction time:

```kotlin
// 1. The split regex — combines defaults + custom splitters
private val splitRegex: Regex

// 2. Pre-compiled exception regexes — one per split exception
private val compiledExceptions: List<Pair<String, Regex>>
```

---

## Default separators

The built-in separators that are always active:

| Separator | Pattern | Notes |
|---|---|---|
| `;` | Literal semicolon | Most common multi-value separator in audio tags |
| `&` | Literal ampersand | Common in "Artist A & Artist B" |
| `,` | Literal comma | Common in "Artist A, Artist B" |
| `feat` | `\bfeat\b\.?` | Whole word, optional trailing period |
| `ft` | `\bft\b\.?` | Whole word, optional trailing period |

The `\b` word boundary ensures `feat` only matches as a standalone word — it won't split `"Featherstone"` or `"defeat"`. The `\.?` makes the trailing period optional so both `"feat."` and `"feat"` are matched.

All matching is case-insensitive (`RegexOption.IGNORE_CASE`).

---

## Custom splitters

Custom splitters from `CatalogConfig.customSplitters` are appended to the defaults. Each entry is treated as a literal string — special regex characters are escaped with `Regex.escape()`:

```kotlin
val customSafe = config.customSplitters
    .filter { it.isNotBlank() }
    .map { Regex.escape(it) }  // "+" → "\+", "." → "\.", etc.
```

Whitespace is intentionally **not** trimmed. `" x "` (with spaces) is treated as a distinct separator from `"x"` — which is the correct behavior since `" x "` matches "Artist A x Artist B" without incorrectly splitting names like "Malcolm X".

The combined regex is built by joining all patterns with `|`:

```
;|&|,|\bfeat\b\.?|\bft\b\.?|<custom1>|<custom2>|...
```

---

## Split exceptions

Split exceptions are names that should never be split, regardless of what separator characters they contain. Common examples: `"AC/DC"`, `"Earth, Wind & Fire"`, `"Years & Years"`.

Each exception is pre-compiled into a case-insensitive regex using `Regex.escape()`:

```kotlin
private val compiledExceptions: List<Pair<String, Regex>> =
    config.splitExceptions.map { exception ->
        val safePattern = Regex.escape(exception)
        exception to Regex(safePattern, RegexOption.IGNORE_CASE)
    }
```

The pair stores both the original string (for restoration) and the compiled regex (for matching). Pre-compiling at init time means the regex is never rebuilt on repeated `split()` calls — important for performance during large library scans.

---

## The split algorithm

`split()` runs in three phases: protect, split, restore.

```kotlin
fun split(rawString: String?): List<String> {
    if (rawString.isNullOrBlank()) return listOf("")
    // Phase 1: Protection
    // Phase 2: Splitting
    // Phase 3: Restoration + cleanup
}
```

A blank or null input returns `listOf("")` — a list with a single empty string. This ensures every track always has at least one entry in each junction table, preserving the "blank artist" entity.

---

## Protection phase

Before splitting, exception names are replaced with unique placeholder tokens:

```kotlin
var workingString = rawString.trim()
val protectedTokens = mutableMapOf<String, String>()
var tokenIndex = 0

compiledExceptions.forEach { (originalException, precompiledRegex) ->
    if (workingString.contains(originalException, ignoreCase = true)) {
        val token = "##PROTECTED_TOKEN_$tokenIndex##"
        workingString = workingString.replace(precompiledRegex, token)
        protectedTokens[token] = originalException
        tokenIndex++
    }
}
```

For example, with `"Earth, Wind & Fire"` as an exception:

```
Input:  "Earth, Wind & Fire, Drake"
After:  "##PROTECTED_TOKEN_0##, Drake"
Map:    { "##PROTECTED_TOKEN_0##" → "Earth, Wind & Fire" }
```

The token format `##PROTECTED_TOKEN_N##` is chosen to be unlikely to appear in real tag data.

---

## Splitting phase

The protected string is split on the `splitRegex`:

```kotlin
val rawParts = workingString.split(splitRegex)
```

From the example above:

```
"##PROTECTED_TOKEN_0##, Drake".split(splitRegex)
→ ["##PROTECTED_TOKEN_0##", " Drake"]
```

---

## Restoration phase

Each part is trimmed, filtered for blanks, and tokens are restored to their original names:

```kotlin
return rawParts
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { part ->
        if (part.contains("##PROTECTED_TOKEN_")) {
            var restored = part
            protectedTokens.forEach { (token, original) ->
                restored = restored.replace(token, original)
            }
            restored
        } else {
            part
        }
    }
    .distinct()
```

Result: `["Earth, Wind & Fire", "Drake"]`

`distinct()` removes duplicates — relevant when a name like `"Years & Years"` would otherwise split into `["Years", "Years"]` and deduplicate to `["Years"]` without an exception. With the exception, it's `["Years & Years"]` (no deduplication needed).

---

## Performance notes

- Regexes are compiled once at `SmartSplitter` construction, not per call.
- Exception regexes are stored as `List<Pair<String, Regex>>` — the string is for the fast `String.contains()` pre-check before running the regex, avoiding unnecessary regex overhead for exceptions that don't appear in the input.
- `SmartSplitter` is rebuilt (a new instance is constructed) whenever `updateConfig()` changes split rules. The scanner's `ingestor.splitter` reference is updated at the same time.

---

## Edge cases

**Blank input** — returns `listOf("")`. A single empty string ensures the track maps to a "blank" artist/genre entity rather than having no junction entry.

**Single value, no separators** — returns `listOf(originalValue)`. No splitting needed.

**Separator at start/end** — trimming + blank filtering removes leading/trailing empty strings from the split result.

**Multiple consecutive separators** — `"Drake,, Future"` produces `["Drake", "Future"]` — the blank middle part is filtered out.

**Exception containing another exception** — not currently handled. If `"A & B"` and `"A"` are both exceptions and the input is `"A & B"`, the longer match should win, but the current implementation processes exceptions in list order. Avoid overlapping exceptions.

**Case preservation** — the restored name always uses the casing from the exception list, not from the tag. If the exception is `"Earth, Wind & Fire"` and the tag has `"earth, wind & fire"`, the stored entity name will be `"Earth, Wind & Fire"`.
