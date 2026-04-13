package aman.catalog.audio.models

/**
 * A cover art path paired with the file's last-modified timestamp.
 * Use [path] as the image source and append [dateModified] as a query parameter
 * (e.g. "${path}?t=${dateModified}") to give Coil a cache-busting key that
 * automatically invalidates whenever the underlying audio file is edited.
 */
data class ArtPath(
    val path: String,
    val dateModified: Long
)
