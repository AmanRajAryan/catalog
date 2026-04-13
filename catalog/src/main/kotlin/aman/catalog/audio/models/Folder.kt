package aman.catalog.audio.models

data class Folder(
    val name: String,
    val path: String,
    val trackCount: Int,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
