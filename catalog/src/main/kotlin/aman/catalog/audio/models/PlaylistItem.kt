package aman.catalog.audio.models

data class PlaylistItem(
    val entryId: Long,   // Unique ID of this entry (Essential for moving/deleting)
    val sortOrder: Int,  // The position in the playlist (0, 1, 2...)
    val track: Track     // The actual song data
)
