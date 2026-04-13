package aman.catalog.audio.internal.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_artists",
    primaryKeys = ["trackId", "artistId"],
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ArtistEntity::class, parentColumns = ["id"], childColumns = ["artistId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["artistId"])]
)
data class TrackArtistRef(val trackId: Long, val artistId: Long)

@Entity(
    tableName = "track_genres",
    primaryKeys = ["trackId", "genreId"],
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GenreEntity::class, parentColumns = ["id"], childColumns = ["genreId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["genreId"])]
)
data class TrackGenreRef(val trackId: Long, val genreId: Long)

@Entity(
    tableName = "track_composers",
    primaryKeys = ["trackId", "composerId"],
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ComposerEntity::class, parentColumns = ["id"], childColumns = ["composerId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["composerId"])]
)
data class TrackComposerRef(val trackId: Long, val composerId: Long)

@Entity(
    tableName = "track_lyricists",
    primaryKeys = ["trackId", "lyricistId"],
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LyricistEntity::class, parentColumns = ["id"], childColumns = ["lyricistId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["lyricistId"])]
)
data class TrackLyricistRef(val trackId: Long, val lyricistId: Long)
