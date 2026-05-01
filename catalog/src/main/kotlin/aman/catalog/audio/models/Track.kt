package aman.catalog.audio.models

import android.net.Uri

// Helper for clickable chips
data class IdName(val id: Long, val name: String)

data class Track(
    val id: Long,
    val uri: Uri,
    val path: String,
    
    val albumId: Long?,
    val albumArtistId: Long?,

    val artists: List<IdName>,
    val genres: List<IdName>,
    val composers: List<IdName>,
    val lyricists: List<IdName>,

    // Location
    val folderName: String,
    val folderPath: String,

    // Display Info (Keep these as fast fallbacks)
    val title: String,
    val artist: String, // Display string (e.g. "Drake, Future")
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val lyricist: String,

    // Technicals
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val releaseDate: String,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val mimeType: String,
    
    // User Stats
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0,
    val skipCount: Int = 0,
    val dateFavorited: Long = 0,
    
    val metadata: ExtendedMetadata = ExtendedMetadata.EMPTY
)

data class ExtendedMetadata(
    val contentRating: Int,
    val bitrate: Int, // Note: May include artwork/tag size in fallback calculation (see TagLibHelper)
    val sampleRate: Int,
    val channels: Int,
    val codec: String,
    val bitsPerSample: Int,

    val replayGainTrackGain: Double = 0.0,
    val replayGainTrackPeak: Double = 0.0,
    val replayGainAlbumGain: Double = 0.0,
    val replayGainAlbumPeak: Double = 0.0,

    val foundYear: Int = 0,
    val foundReleaseDate: String = "",
    val foundComposer: String = "",
    val foundLyricist: String = "",
    val foundAlbumArtist: String = "",
    val foundTrackNumber: Int = 0,
    val foundDiscNumber: Int = 0,
    val foundTitle: String = "",
    val foundArtist: String = "",
    val foundAlbum: String = "",
    val foundGenre: String = ""
) {
    companion object {
        val EMPTY = ExtendedMetadata(
            contentRating = 0,
            bitrate = 0,
            sampleRate = 0,
            channels = 2,
            codec = "",
            bitsPerSample = 0
        )
    }
}