package aman.catalog.audio.internal.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import aman.catalog.audio.internal.database.entities.IgnoredFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredFolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ignoreFolder(entity: IgnoredFolderEntity)

    @Query("DELETE FROM ignored_folders WHERE path = :path")
    suspend fun unignoreFolder(path: String)

    @Query("SELECT * FROM ignored_folders ORDER BY path ASC")
    fun getIgnoredFolders(): Flow<List<IgnoredFolderEntity>>

    @Query("SELECT path FROM ignored_folders")
    suspend fun getAllIgnoredPaths(): List<String>
}
