package aman.catalog.audio.internal.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumArtistId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        // Unique constraint prevents duplicate (title + artist + folder scope) combinations,
        // allowing "Greatest Hits" by Queen and ABBA to coexist as separate albums.
        Index(value = ["title", "albumArtistId", "folderGroup"], unique = true),
        Index(value = ["albumArtistId"])
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val albumArtistId: Long?,

    // "" when the Album Artist tag is present (global grouping for multi-disc albums).
    // Folder path when the tag is absent (scoped grouping to prevent unrelated albums merging).
    val folderGroup: String,

    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0
)

