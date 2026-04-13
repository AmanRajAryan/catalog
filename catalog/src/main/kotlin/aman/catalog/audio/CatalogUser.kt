package aman.catalog.audio

import aman.catalog.audio.internal.ModelMapper
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.database.entities.FavoritesEntity
import aman.catalog.audio.internal.database.entities.IgnoredFolderEntity
import aman.catalog.audio.models.ContextualSortOption
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.FavoritesInfo
import aman.catalog.audio.models.IgnoredFolder
import aman.catalog.audio.models.Track
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages user-specific preferences, such as Favorite tracks and Ignored Folders.
 * Accessible via [Catalog.user].
 */
class CatalogUser internal constructor(
    private val databaseProvider: () -> CatalogDatabase,
    private val configProvider: () -> CatalogConfig,
    private val configUpdater: (CatalogConfig) -> Unit
) {
    private val database: CatalogDatabase
        get() = databaseProvider()

    // ----------------------------------------
    // FAVORITES API
    // ----------------------------------------

    /**
     * Toggles the favorite status of a track.
     *
     * @param trackId The ID of the track to favorite or unfavorite.
     * @return `true` if the track is now a favorite, `false` if it was removed from favorites
     * or if the track does not exist.
     */
    suspend fun toggleFavorite(trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val dao = database.favoritesDao()
            val trackExists = database.trackDao().getTrackById(trackId) != null
            if (!trackExists) return@withContext false
            
            val isFav = dao.isFavorite(trackId)
            if (isFav) {
                dao.removeFavorite(trackId)
                false
            } else {
                val nextOrder = dao.getNextSortOrder()
                dao.addFavorite(FavoritesEntity(trackId, System.currentTimeMillis(), nextOrder))
                true
            }
        }
    }

    /** Returns whether a given track is currently marked as a favorite. */
    suspend fun isFavorite(trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            database.favoritesDao().isFavorite(trackId)
        }
    }

    /** Live stream of all tracks the user has marked as a favorite, sorted by [sort]. */
    fun getFavoriteTracks(sort: ContextualSortOption.FavoriteTrack = ContextualSortOption.FavoriteTrack.USER_DEFINED): Flow<List<Track>> {
        return database.favoritesDao().getFavoriteTracks(sort)
            .distinctUntilChanged()
            .map { list -> list.map { ModelMapper.toTrack(it) } }
            .flowOn(Dispatchers.Default)
    }

    /** Live stream of aggregate metadata for the favorites collection. */
    val favoritesInfo: Flow<FavoritesInfo>
        get() = database.favoritesDao().getFavoritesInfo()
            .distinctUntilChanged()
            .map { ModelMapper.toFavoritesInfo(it) }
            .flowOn(Dispatchers.Default)

    /**
     * Moves a single favorite track to a new position.
     * Optimized for UI drag-and-drop operations.
     */
    suspend fun moveFavoriteEntry(trackId: Long, fromPosition: Int, toPosition: Int) {
        withContext(Dispatchers.IO) {
            database.favoritesDao().moveFavoriteEntry(trackId, fromPosition, toPosition)
        }
    }

    /**
     * Bulk reorder favorites. Key is trackId, value is the new sortOrder.
     * Intended for shuffle or "sort and save" operations.
     */
    suspend fun reorderFavorites(updates: Map<Long, Int>) {
        withContext(Dispatchers.IO) {
            database.favoritesDao().reorderFavorites(updates)
        }
    }

    /**
     * Returns up to [limit] distinct cover art paths for the favorites collection.
     * Intended for use by Coil or any other image loader to draw a mosaic.
     */
    suspend fun getMosaicForFavorites(limit: Int): List<ArtPath> {
        return withContext(Dispatchers.IO) {
            database.favoritesDao().getMosaicPathsForFavorites(limit)
        }
    }

    /** Live stream of up to [limit] distinct cover art paths for the favorites collection. */
    fun getMosaicForFavoritesFlow(limit: Int): Flow<List<ArtPath>> =
        database.favoritesDao().getMosaicPathsForFavoritesFlow(limit)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // ----------------------------------------
    // IGNORE / BLACKLIST API
    // ----------------------------------------

    /**
     * Marks a folder as ignored, immediately deletes all its tracks, and cleans up
     * any Artists/Albums/etc. that are left empty as a result — all in a single atomic transaction.
     *
     * Also syncs the ignored path into the Catalog config so the scanner won't re-ingest it.
     */
    suspend fun ignoreFolder(path: String) {
        withContext(Dispatchers.IO) {
            database.ignoredFolderDao().ignoreFolder(
                IgnoredFolderEntity(path = path, dateAdded = System.currentTimeMillis())
            )
            val currentConfig = configProvider()
            if (!currentConfig.scannerIgnores.contains(path)) {
                val newConfig = currentConfig.copy(scannerIgnores = currentConfig.scannerIgnores + path)
                configUpdater(newConfig)
            }
        }
    }

    /**
     * Removes a folder from the ignored list and triggers a full rescan so its
     * tracks are re-ingested on the next scan pass.
     */
    suspend fun unignoreFolder(path: String) {
        withContext(Dispatchers.IO) {
            database.ignoredFolderDao().unignoreFolder(path)
            val currentConfig = configProvider()
            val newIgnores = currentConfig.scannerIgnores.filter { it != path }
            configUpdater(currentConfig.copy(scannerIgnores = newIgnores))
        }
    }

    /** Live stream of all currently ignored folders. */
    val ignoredFolders: Flow<List<IgnoredFolder>>
        get() = database.ignoredFolderDao().getIgnoredFolders()
            .distinctUntilChanged()
            .map { list -> list.map { IgnoredFolder(it.path, it.dateAdded) } }
            .flowOn(Dispatchers.Default)
}
