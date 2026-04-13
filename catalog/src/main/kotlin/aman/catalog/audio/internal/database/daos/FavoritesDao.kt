package aman.catalog.audio.internal.database.daos

import androidx.room.*
import aman.catalog.audio.internal.database.entities.FavoritesEntity
import aman.catalog.audio.internal.database.entities.TrackEntity
import aman.catalog.audio.internal.database.entities.TrackArtistRef
import aman.catalog.audio.internal.database.entities.ArtistEntity
import aman.catalog.audio.internal.database.entities.TrackGenreRef
import aman.catalog.audio.internal.database.entities.GenreEntity
import aman.catalog.audio.internal.database.entities.TrackComposerRef
import aman.catalog.audio.internal.database.entities.ComposerEntity
import aman.catalog.audio.internal.database.entities.TrackLyricistRef
import aman.catalog.audio.internal.database.entities.LyricistEntity
import aman.catalog.audio.internal.database.entities.AlbumEntity
import aman.catalog.audio.internal.database.relations.TrackWithRelations
import aman.catalog.audio.models.ContextualSortOption
import aman.catalog.audio.models.ArtPath
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoritesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addFavorite(entity: FavoritesEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    abstract suspend fun isFavorite(trackId: Long): Boolean

    @Query("SELECT * FROM favorites")
    abstract fun getAllFavorites(): Flow<List<FavoritesEntity>>

    // ------------------------------------
    //  SORT ORDER MANAGEMENT
    // ------------------------------------

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM favorites")
    abstract suspend fun getNextSortOrder(): Int

    @Query("UPDATE favorites SET sortOrder = :newOrder WHERE trackId = :trackId")
    protected abstract suspend fun updateFavoriteSortOrder(trackId: Long, newOrder: Int)

    @Query("UPDATE favorites SET sortOrder = sortOrder - 1 WHERE sortOrder > :deletedOrder")
    protected abstract suspend fun shiftSortOrders(deletedOrder: Int)

    @Query("UPDATE favorites SET sortOrder = sortOrder - 1 WHERE sortOrder > :fromPosition AND sortOrder <= :toPosition")
    protected abstract suspend fun shiftUpForMove(fromPosition: Int, toPosition: Int)

    @Query("UPDATE favorites SET sortOrder = sortOrder + 1 WHERE sortOrder >= :toPosition AND sortOrder < :fromPosition")
    protected abstract suspend fun shiftDownForMove(fromPosition: Int, toPosition: Int)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    protected abstract suspend fun deleteRawFavorite(trackId: Long)

    @Query("SELECT sortOrder FROM favorites WHERE trackId = :trackId")
    protected abstract suspend fun getFavoriteSortOrder(trackId: Long): Int?

    // ------------------------------------
    //  REMOVE (gap-safe)
    // ------------------------------------

    @Transaction
    open suspend fun removeFavorite(trackId: Long) {
        val order = getFavoriteSortOrder(trackId) ?: return
        deleteRawFavorite(trackId)
        shiftSortOrders(order)
    }

    // ------------------------------------
    //  REORDER
    // ------------------------------------

    @Transaction
    open suspend fun moveFavoriteEntry(trackId: Long, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        updateFavoriteSortOrder(trackId, -1)
        if (fromPosition < toPosition) {
            shiftUpForMove(fromPosition, toPosition)
        } else {
            shiftDownForMove(fromPosition, toPosition)
        }
        updateFavoriteSortOrder(trackId, toPosition)
    }

    @Transaction
    open suspend fun reorderFavorites(updates: Map<Long, Int>) {
        updates.forEach { (trackId, newOrder) ->
            updateFavoriteSortOrder(trackId, newOrder)
        }
    }

    // ------------------------------------
    //  AGGREGATE INFO
    // ------------------------------------

    data class FavoritesInfoResult(
        val trackCount: Int,
        val totalDurationMs: Long,
        val coverArtPath: String?,
        val coverArtDateModified: Long
    )

    @Query("""
        SELECT 
            COUNT(tracks.id) as trackCount,
            COALESCE(SUM(tracks.durationMs), 0) as totalDurationMs,
            MIN(tracks.path) as coverArtPath,
            MAX(tracks.dateModified) as coverArtDateModified
        FROM favorites
        INNER JOIN tracks ON favorites.trackId = tracks.id
    """)
    abstract fun getFavoritesInfo(): Flow<FavoritesInfoResult>

    // ------------------------------------
    //  DYNAMIC SORTING: FAVORITE TRACKS
    // ------------------------------------

    @Transaction
    @RawQuery(
        observedEntities = [
            FavoritesEntity::class,
            TrackEntity::class,
            TrackArtistRef::class,
            ArtistEntity::class,
            TrackGenreRef::class,
            GenreEntity::class,
            TrackComposerRef::class,
            ComposerEntity::class,
            TrackLyricistRef::class,
            LyricistEntity::class,
            AlbumEntity::class
        ]
    )
    protected abstract fun getFavoriteTracksSortedRaw(query: SupportSQLiteQuery): Flow<List<TrackWithRelations>>

    fun getFavoriteTracks(sort: ContextualSortOption.FavoriteTrack = ContextualSortOption.FavoriteTrack.USER_DEFINED): Flow<List<TrackWithRelations>> {
        val sql = """
            SELECT tracks.* FROM tracks
            INNER JOIN favorites ON tracks.id = favorites.trackId
            ORDER BY ${sort.sqlString}
        """
        return getFavoriteTracksSortedRaw(SimpleSQLiteQuery(sql))
    }

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (lazy, on-demand)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN favorites f ON t.id = f.trackId
        WHERE t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract suspend fun getMosaicPathsForFavorites(limit: Int): List<ArtPath>

    // ------------------------------------------------------------------------
    // MOSAIC QUERY (live, for detail screens)
    // ------------------------------------------------------------------------

    @Query("""
        SELECT MIN(t.path) as path, MAX(t.dateModified) as dateModified
        FROM tracks t
        INNER JOIN favorites f ON t.id = f.trackId
        WHERE t.albumId IS NOT NULL
        GROUP BY t.albumId
        ORDER BY t.albumId ASC
        LIMIT :limit
    """)
    abstract fun getMosaicPathsForFavoritesFlow(limit: Int): Flow<List<ArtPath>>
}
