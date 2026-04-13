package aman.catalog.audio.models

data class MatchedGenre(
    val genre: Genre,
    val matchedTrackCount: Int,
    val matchedDurationMs: Long
)
