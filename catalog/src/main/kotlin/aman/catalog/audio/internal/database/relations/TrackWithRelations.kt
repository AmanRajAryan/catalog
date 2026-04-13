package aman.catalog.audio.internal.database.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import aman.catalog.audio.internal.database.entities.*

/**
 * A fully-hydrated track including all related entities.
 * Room populates the relation lists automatically via the junction tables.
 */
data class TrackWithRelations(
    @Embedded val track: TrackEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id", // Matches ArtistEntity.id
        associateBy = Junction(
            value = TrackArtistRef::class,
            parentColumn = "trackId",
            entityColumn = "artistId"
        )
    )
    val artists: List<ArtistEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackGenreRef::class,
            parentColumn = "trackId",
            entityColumn = "genreId"
        )
    )
    val genres: List<GenreEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackComposerRef::class,
            parentColumn = "trackId",
            entityColumn = "composerId"
        )
    )
    val composers: List<ComposerEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackLyricistRef::class,
            parentColumn = "trackId",
            entityColumn = "lyricistId"
        )
    )
    val lyricists: List<LyricistEntity>,

    // Connects the track to its live Album entity.
    @Relation(
        parentColumn = "albumId",
        entityColumn = "id"
    )
    val album: AlbumEntity?,

    // Room checks the 'favorites' table for a row where trackId == this track's id.
    // Populated if the track is favorited, null otherwise.
    @Relation(
        parentColumn = "id",
        entityColumn = "trackId"
    )
    val favorite: FavoritesEntity?
)
