package aman.catalog.audio.models

data class MatchedAlbum(
    val album: Album,
    val matchedTrackCount: Int,
    val matchedDurationMs: Long
)



