package aman.catalog.audio.internal.database

import androidx.room.Database
import androidx.room.RoomDatabase
import aman.catalog.audio.internal.database.entities.*
import aman.catalog.audio.internal.database.daos.*

@Database(
    entities = [
        TrackEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        GenreEntity::class,
        ComposerEntity::class,
        LyricistEntity::class,
        FavoritesEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        IgnoredFolderEntity::class,

        TrackArtistRef::class,
        TrackGenreRef::class,
        TrackComposerRef::class,
        TrackLyricistRef::class
    ],
    version = 1,
    exportSchema = true
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun genreDao(): GenreDao
    abstract fun composerDao(): ComposerDao
    abstract fun lyricistDao(): LyricistDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun ignoredFolderDao(): IgnoredFolderDao
    abstract fun folderDao(): FolderDao
    abstract fun yearDao(): YearDao
}
