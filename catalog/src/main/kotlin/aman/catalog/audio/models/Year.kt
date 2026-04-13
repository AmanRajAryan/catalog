package aman.catalog.audio.models

data class Year(
    val year: Int,
    val trackCount: Int,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
