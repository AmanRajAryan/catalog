package aman.catalog.audio.internal.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single audio file in the database.
 *
 * Indices are added for columns that are commonly sorted or filtered on to avoid full-table scans.
 */
@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["albumId"]),
        Index(value = ["title"]),
        Index(value = ["artistDisplay"]),
        Index(value = ["dateAdded"]),
        Index(value = ["folderPath"]),
        Index(value = ["year"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val mediaStoreId: Long,
    val albumId: Long? = null,

    val path: String,
    val folderName: String,
    val folderPath: String,

    val title: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val dateModified: Long = 0,
    val mimeType: String,

    // Denormalized display strings kept here for fast sorting without joins.
    val artistDisplay: String,
    val albumDisplay: String,
    val albumArtistDisplay: String,
    val genreDisplay: String,
    val composerDisplay: String,
    val lyricistDisplay: String,

    val durationMs: Long,
    val year: Int,
    val releaseDate: String,
    val trackNumber: Int,
    val discNumber: Int,

    // Raw tag strings used as the source of truth for junction regeneration.
    val rawArtistString: String,
    val rawGenreString: String,
    val rawComposerString: String,
    val rawLyricistString: String,
    val rawAlbumArtistString: String,

    // Pure TagLib album value — may be "" even if albumDisplay is not, since
    // albumDisplay can fall back to the MediaStore value.
    val rawAlbumString: String,

    val contentRating: Int = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 2,
    val codec: String = "",
    val bitsPerSample: Int = 0,

    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0,
    val skipCount: Int = 0
)
