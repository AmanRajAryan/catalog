package aman.catalog.audio.models

data class Album(
    val id: Long,
    val title: String,
    val albumArtistId: Long?,
    val albumArtist: String,
    val albumArtistCoverArtPath: ArtPath? = null,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0,
    val year: String = "",
    val coverArtPath: ArtPath? = null,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)
