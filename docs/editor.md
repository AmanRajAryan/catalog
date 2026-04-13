# Tag Editor

`CatalogEditor` reads and writes metadata and artwork directly on audio files. It handles Android's Scoped Storage permission requirements, protects against native crashes during concurrent operations, and automatically triggers a library rescan after successful writes.

---

## Table of Contents

1. [Reading tags](#reading-tags)
2. [Writing tags and artwork](#writing-tags-and-artwork)
3. [ArtworkUpdate](#artworkupdate)
4. [Handling EditResult](#handling-editresult)
5. [SAF permissions](#saf-permissions)
6. [How the write works internally](#how-the-write-works-internally)

---

## Reading tags

```kotlin
suspend fun readTags(track: Track): Map<String, String>
```

Reads the raw tag map directly from the audio file using TagLib. Returns every tag key and value as strings exactly as they appear in the file — no normalization, no fallbacks, no processing.

Use this to pre-populate a tag editor screen. The keys depend on the file format — common ones include `"TITLE"`, `"ARTIST"`, `"ALBUM"`, `"ALBUMARTIST"`, `"COMPOSER"`, `"LYRICIST"`, `"DATE"`, `"TRACKNUMBER"`, `"DISCNUMBER"`, `"GENRE"`.

```kotlin
viewModelScope.launch {
    val tags = CatalogEditor.readTags(track)

    titleField.setText(tags["TITLE"] ?: "")
    artistField.setText(tags["ARTIST"] ?: "")
    albumField.setText(tags["ALBUM"] ?: "")
    yearField.setText(tags["DATE"] ?: "")
}
```

> `readTags()` is thread-safe — it acquires the internal mutex so it cannot run concurrently with a write operation.

> Do not use `track.metadata.foundTitle`, `track.metadata.foundArtist`, etc. to pre-populate a tag editor. Those values were captured at scan time and may be stale. `readTags()` always reads the current file state.

---

## Writing tags and artwork

```kotlin
suspend fun updateTrack(
    context: Context,
    track: Track,
    newTags: Map<String, String>? = null,
    artworkUpdate: ArtworkUpdate = ArtworkUpdate.NoChange,
    allowMediaStoreFallback: Boolean = true
): EditResult
```

Updates a track's tags and/or artwork in a single pass. All changes are applied to a temporary cache file first — the original is only overwritten once all writes succeed. If anything fails, the original file is untouched.

After a successful write, the library automatically rescans the file and updates the database entry. You don't need to call `notifyFileChanged()` manually.

```kotlin
// Update tags only
val result = CatalogEditor.updateTrack(
    context = context,
    track = track,
    newTags = mapOf(
        "TITLE" to "New Title",
        "ARTIST" to "New Artist",
        "ALBUM" to "New Album",
        "DATE" to "2024"
    )
)

// Update artwork only
val result = CatalogEditor.updateTrack(
    context = context,
    track = track,
    artworkUpdate = ArtworkUpdate.Set(
        data = imageBytes,
        mimeType = "image/jpeg"
    )
)

// Update both at once
val result = CatalogEditor.updateTrack(
    context = context,
    track = track,
    newTags = mapOf("TITLE" to "New Title"),
    artworkUpdate = ArtworkUpdate.Set(imageBytes, "image/jpeg")
)
```

### Parameters

| Parameter | Description |
|---|---|
| `context` | Any context. Used for ContentResolver access. |
| `track` | The track to edit. Must have a valid `uri`. |
| `newTags` | Tag key-value pairs to write. Pass `null` to leave tags unchanged. |
| `artworkUpdate` | How to handle artwork. Defaults to `NoChange`. |
| `allowMediaStoreFallback` | Whether to fall back to MediaStore if no SAF permission covers the file. See [SAF permissions](#saf-permissions). Defaults to `true`. |

---

## ArtworkUpdate

```kotlin
sealed class ArtworkUpdate {
    object NoChange : ArtworkUpdate()  // Leave artwork as-is
    object Delete : ArtworkUpdate()    // Remove all embedded artwork
    class Set(val data: ByteArray, val mimeType: String) : ArtworkUpdate()  // Replace with new artwork
}
```

```kotlin
// Remove artwork
ArtworkUpdate.Delete

// Set new artwork from a bitmap
val stream = ByteArrayOutputStream()
bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
ArtworkUpdate.Set(stream.toByteArray(), "image/jpeg")
```

---

## Handling EditResult

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

Always handle the result:

```kotlin
when (val result = CatalogEditor.updateTrack(context, track, newTags)) {
    is EditResult.Success -> {
        showSuccess()
    }
    is EditResult.PermissionRequired -> {
        // Android 10/11: request permission then retry
        permissionLauncher.launch(result.intentSender)
    }
    is EditResult.SafPermissionMissing -> {
        // allowMediaStoreFallback was false and no SAF permission covers this folder
        // Prompt the user to grant folder access via SAF
        promptSafAccess(result.folderPath)
    }
    is EditResult.InputError -> {
        showError("File not accessible: ${result.message}")
    }
    is EditResult.IOError -> {
        showError("Write failed: ${result.message}")
    }
    EditResult.TagWriteFailed -> {
        showError("TagLib could not write tags to this file format")
    }
    EditResult.ArtWriteFailed -> {
        showError("TagLib could not write artwork to this file format")
    }
}
```

### Result descriptions

| Result | When it occurs |
|---|---|
| `Success` | Tags and/or artwork written successfully. The library has already started rescanning the file. |
| `PermissionRequired` | Android 10/11 Scoped Storage: you need to launch the `intentSender` to ask for write permission, then retry. |
| `SafPermissionMissing` | `allowMediaStoreFallback = false` and no SAF folder permission covers the file path. `folderPath` is the directory you need to request access to. |
| `InputError` | The source file is missing, empty (0 bytes), or inaccessible. Check `message` for details. |
| `IOError` | A filesystem error occurred — copy failed, write failed, permissions denied. Check `message` for details. |
| `TagWriteFailed` | TagLib rejected the tag write. Can happen with DRM-protected files or unsupported formats. |
| `ArtWriteFailed` | TagLib rejected the artwork write. Some formats don't support embedded artwork. |

### Retrying after PermissionRequired

On Android 10 and 11, the first write attempt to a file outside the app's own directories will return `PermissionRequired`. Launch the `intentSender` and retry the operation after the user grants permission:

```kotlin
private var pendingTrack: Track? = null
private var pendingTags: Map<String, String>? = null

private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        // Permission granted — retry
        val track = pendingTrack ?: return@registerForActivityResult
        val tags = pendingTags ?: return@registerForActivityResult
        viewModelScope.launch {
            CatalogEditor.updateTrack(context, track, tags)
        }
    }
}

// In your edit handler
when (val result = CatalogEditor.updateTrack(context, track, newTags)) {
    is EditResult.PermissionRequired -> {
        pendingTrack = track
        pendingTags = newTags
        permissionLauncher.launch(
            IntentSenderRequest.Builder(result.intentSender).build()
        )
    }
    // ...
}
```

---

## SAF permissions

On Android 11+ (API 30+), you can grant persistent folder-level write access via the Storage Access Framework (SAF). This lets the library write to any folder the user approves without triggering a permission prompt each time.

### Checking permission

```kotlin
fun hasSafPermission(context: Context, path: String): Boolean
```

Returns `true` if the host app has a persistent SAF permission covering the given file path. Use this as a UI hint — for example, to show a lock icon on files in folders that haven't been granted access yet.

```kotlin
val canEdit = CatalogEditor.hasSafPermission(context, track.path)
```

> This is a pre-flight hint only. Permissions can be revoked between the check and the actual write. `updateTrack()` handles enforcement safely regardless of what `hasSafPermission()` returns.

### Using allowMediaStoreFallback

By default (`allowMediaStoreFallback = true`), if no SAF permission covers the file, the editor falls back to the raw MediaStore URI. This triggers `PermissionRequired` on Android 10/11 or succeeds silently on Android 12+.

Set `allowMediaStoreFallback = false` if you want full control over the permission flow — for example, in a tag editor that only operates on SAF-approved folders:

```kotlin
val result = CatalogEditor.updateTrack(
    context = context,
    track = track,
    newTags = newTags,
    allowMediaStoreFallback = false  // Return SafPermissionMissing instead of falling back
)

if (result is EditResult.SafPermissionMissing) {
    // Launch folder picker for result.folderPath
    openDocumentTree.launch(Uri.parse(result.folderPath))
}
```

---

## How the write works internally

Understanding the write flow helps when debugging unexpected results:

1. **Validation** — the source file is opened and verified to be non-empty. Returns `InputError` if the file is missing or 0 bytes.

2. **Copy to cache** — the original file is copied to the app's cache directory (`edit_<timestamp>.<ext>`). All tag and artwork writes happen on this copy, never the original.

3. **Apply tags** — if `newTags` is provided, TagLib writes the tag map to the cache file.

4. **Apply artwork** — if `artworkUpdate` is not `NoChange`, TagLib modifies the artwork on the cache file.

5. **Integrity check** — the cache file is verified to be non-empty after writing. Returns `IOError` if it's 0 bytes.

6. **Commit** — the cache file is streamed into the original file location, protected by the internal `ioMutex`. This ensures TagLib cannot read the file at the same time it's being written (which would cause a native crash on a temporarily 0-byte file).

7. **Cleanup** — the cache file is deleted regardless of success or failure.

8. **Rescan** — `notifyFileChanged()` is called automatically to update the database entry.

The `ioMutex` is the key safety mechanism — it serializes all TagLib reads and writes so a scan cannot read a file that's in the middle of being committed.
