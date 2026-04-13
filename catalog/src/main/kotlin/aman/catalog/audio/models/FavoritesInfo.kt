package aman.catalog.audio.models

data class FavoritesInfo(
    val trackCount: Int,
    val totalDurationMs: Long = 0,
    val coverArtPath: ArtPath? = null
)
