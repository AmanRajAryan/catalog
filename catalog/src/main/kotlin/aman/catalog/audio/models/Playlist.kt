package aman.catalog.audio.models

data class Playlist(
    val id: Long,
    val name: String,
    val dateCreated: Long,
    val dateModified: Long,
    val trackCount: Int,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null,
)
