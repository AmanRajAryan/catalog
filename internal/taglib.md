# TagLib

`TagLibHelper` wraps the native TagLib library with Android-specific fallbacks, handles metadata extraction, and exposes lyrics and artwork extraction. This doc covers the extraction logic, fallback chains, and known limitations.

---

## Table of Contents

1. [Overview](#overview)
2. [Metadata extraction](#metadata-extraction)
3. [Bitrate fallback chain](#bitrate-fallback-chain)
4. [Codec and format resolution](#codec-and-format-resolution)
5. [Year parsing](#year-parsing)
6. [Lyricist tag precedence](#lyricist-tag-precedence)
7. [Content rating normalization](#content-rating-normalization)
8. [Lyrics extraction](#lyrics-extraction)
9. [Artwork extraction](#artwork-extraction)
10. [Thread safety](#thread-safety)

---

## Overview

`TagLibHelper` is an internal `object` that wraps `aman.taglib.TagLib` (the native JNI bridge). It has three public methods:

```kotlin
fun extract(path: String): ExtendedMetadata
fun extractLyrics(path: String): String
fun extractPictures(path: String): List<TrackPicture>
```

`extract()` is called during scanning for every new or changed file. `extractLyrics()` and `extractPictures()` are called on-demand from `CatalogLibrary`.

All three methods are wrapped in try/catch — any exception returns an empty result rather than crashing the scan.

---

## Metadata extraction

`extract()` calls `NativeTagLib.getMetadata(path)` which returns a `Map<String, String>` of all tag key-value pairs found in the file. Keys are normalized to uppercase (e.g. `"TITLE"`, `"ARTIST"`, `"ALBUMARTIST"`).

If `getMetadata()` returns null (file unreadable, unsupported format), `ExtendedMetadata.EMPTY` is returned immediately.

The raw map is then processed to extract each field, applying fallbacks as needed.

---

## Bitrate fallback chain

Bitrate is resolved in three stages, each only attempted if the previous one returned 0:

**Stage 1 — TagLib native:**
```kotlin
var bitrate = rawMap["BITRATE"]?.toIntOrNull() ?: 0
```
TagLib reads the bitrate from the file's audio stream header. This is accurate and preferred.

**Stage 2 — Android MediaExtractor:**
```kotlin
if (bitrate == 0 || channels == 0 || sampleRate == 0) {
    val androidStats = getAndroidAudioStats(path)
    if (androidStats.isValid) {
        if (bitrate == 0) bitrate = androidStats.bitrate
        // also fills sampleRate, channels, codec if missing
    }
}
```
`getAndroidAudioStats()` uses `MediaExtractor` to read codec-level audio properties. This works for formats that TagLib can't fully parse (some AAC containers, Dolby formats, etc.). The `KEY_BIT_RATE` value from `MediaExtractor` is divided by 1000 to convert from bps to kbps.

**Stage 3 — Manual calculation:**
```kotlin
if (bitrate == 0) {
    bitrate = calculateBitrate(path)
}
```
`calculateBitrate()` estimates bitrate from `fileSize * 8 / durationSeconds / 1000`. This is a last resort and has a known inaccuracy: **it includes the size of embedded artwork, ID3 tag headers, and container overhead**. A 320kbps MP3 with 3MB of embedded art may report 600+ kbps. This value is exposed as-is in `ExtendedMetadata.bitrate` — consumers should be aware of this limitation when Stage 3 was needed (which is rare with well-tagged files).

The same fallback chain applies to `sampleRate` and `channels` — Stage 2 fills them if TagLib returned 0. For `channels`, the MediaExtractor default is 2 (stereo) if the field is missing.

---

## Codec and format resolution

`codec` is resolved as follows:

**TagLib** provides a `FORMAT` key (e.g. `"FLAC"`, `"MP3"`, `"AAC"`, `"Opus"`). This is used directly if non-blank.

**MediaExtractor fallback** — if TagLib's `FORMAT` is blank, `getAndroidAudioStats()` maps the MIME type to a human-readable codec name:

```kotlin
val prettyCodec = when (mime) {
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3" -> "Dolby Digital+"
    "audio/mp4a-latm" -> "AAC"
    "audio/flac" -> "FLAC"
    "audio/opus" -> "Opus"
    else -> mime.substringAfter("/")  // e.g. "mpeg" for "audio/mpeg"
}
```

`bitsPerSample` comes from TagLib's `BITS_PER_SAMPLE` key. There is no MediaExtractor fallback for this — it's 0 if TagLib doesn't provide it (common for lossy formats like MP3 and AAC, which don't have a meaningful bit depth).

Both `codec` and `bitsPerSample` are exposed raw in `ExtendedMetadata` — the UI is responsible for building any display string from them.

---

## Year parsing

The `DATE` tag in audio files is not standardized. Common formats include:

- `"2023"` — plain year
- `"2023-06-15"` — ISO 8601 date
- `"23/05/2023"` — European format
- `"2023-06"` — year + month only

Rather than assuming the first 4 characters are the year, `TagLibHelper` finds the first 4-digit sequence anywhere in the string:

```kotlin
val yearPattern = Regex("\\b\\d{4}\\b")
val parsedYear = yearPattern.find(dateStr)?.value?.toIntOrNull() ?: 0
```

The `\b` word boundaries ensure only standalone 4-digit sequences are matched — not 4-digit substrings of longer numbers. This correctly handles all the formats above and produces 0 if no 4-digit year is found.

The full raw `DATE` string is also stored in `foundReleaseDate` — consumers can use this for finer date display when needed.

---

## Lyricist tag precedence

Lyricist is resolved in this order:

```
LYRICIST → SONGWRITER → WRITER
```

`TEXT` was previously used as a fallback but was removed because it's also used as a lyrics tag in some formats, causing lyricist fields to sometimes contain lyrics content.

`SONGWRITER` is checked before `WRITER` because it's more semantically explicit — a `SONGWRITER` tag specifically means the person who wrote the song, while `WRITER` is more ambiguous.

If none of these tags are present, `foundLyricist` is `""`.

---

## Content rating normalization

The `CONTENT_RATING` (or `CONTENTRATING`) tag is normalized to three values:

| Raw value | Normalized | Meaning |
|---|---|---|
| `"1"`, `"4"` | `1` | Explicit |
| `"2"` | `2` | Clean |
| `"explicit"`, `"e"` | `1` | Explicit (text-based, common in Vorbis/FLAC) |
| `"clean"`, `"c"` | `2` | Clean (text-based) |
| Anything else | `0` | None / unknown |

iTunes stores content rating as integer strings (`"1"` = explicit, `"2"` = clean, `"4"` = explicit). Vorbis/FLAC tags often use text values. The normalization handles both.

---

## Lyrics extraction

```kotlin
fun extractLyrics(path: String): String
```

Lyrics are read from tags in this order:

```
LYRICS → USLT → ©LYR → TEXT
```

- `LYRICS` — standard Vorbis/FLAC lyrics tag
- `USLT` — ID3v2 unsynchronized lyrics frame (common in MP3)
- `©LYR` — iTunes MP4 lyrics tag
- `TEXT` — fallback freeform text tag

Returns the first non-blank value found, or `""` if none.

The result is not trimmed — lyrics often contain intentional leading/trailing whitespace. `CatalogLibrary.getLyricsForTrack()` applies `takeIf { it.isNotBlank() }` before returning to the consumer, converting `""` to `null`.

---

## Artwork extraction

```kotlin
fun extractPictures(path: String): List<TrackPicture>
```

Calls `NativeTagLib.getArtwork(path)` which returns all embedded picture frames from the file. Each picture is mapped to a `TrackPicture`:

```kotlin
TrackPicture(
    data = art.data,              // raw image bytes
    mimeType = art.mimeType ?: "image/*",
    description = art.description ?: ""
)
```

A file can have multiple embedded images — front cover, back cover, artist photo, etc. The description field (from the ID3 `APIC` frame's description or equivalent) often identifies the picture type, but this is not normalized.

If `getArtwork()` returns null or throws, an empty list is returned.

---

## Thread safety

All three public methods are called while holding `Catalog.ioMutex`:

- `extract()` — called from `ScannerService` within the mutex
- `extractLyrics()` — `CatalogLibrary.getLyricsForTrack()` acquires the mutex before calling
- `extractPictures()` — `CatalogLibrary.getPicturesForTrack/Path()` acquires the mutex

This ensures TagLib never reads a file that `CatalogEditor` is simultaneously writing. A TagLib call on a 0-byte file (temporarily during a write commit) causes a native SIGSEGV crash — the mutex prevents this by serializing all TagLib access.
