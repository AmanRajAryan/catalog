package aman.catalog.audio.internal.scanner

import aman.catalog.audio.Catalog
import aman.catalog.audio.CatalogConfig
import aman.catalog.audio.ConfigChangeResult
import aman.catalog.audio.ScanResult
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.taglib.TagLibHelper
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Coordinates the full scan lifecycle: diffs MediaStore state against the database,
 * delegates DB writes to [TrackIngestor], and delegates compilation detection to [CompilationManager].
 */
class ScannerService(
        private val context: Context,
        private val db: CatalogDatabase,
        private var config: CatalogConfig
) {
  private var splitter = SmartSplitter(config)

  private val ingestor = TrackIngestor(db, splitter)
  private val compilationManager = CompilationManager(db)

  fun updateConfig(newConfig: CatalogConfig) {
    this.config = newConfig
    this.splitter = SmartSplitter(newConfig)
    this.ingestor.splitter = this.splitter
  }

  /**
   * Re-applies splitting logic when the user changes splitter settings.
   * Processes tracks in chunks of 500 to avoid locking SQLite for too long.
   *
   * Guarded by [Catalog.ioMutex] so a config change cannot corrupt junction tables
   * if it races against a simultaneous MediaWatcher-triggered scan().
   */
  suspend fun applyConfigChanges(): ConfigChangeResult = Catalog.ioMutex.withLock {
          withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            Log.d("CatalogScanner", "Applying config changes...")
            val tracks = db.trackDao().getAllTracksRaw()

            val affectedFolders = mutableSetOf<String>()

            tracks.chunked(500).forEach { chunk ->
              db.withTransaction {
                chunk.forEach { track ->
                  ingestor.regenerateJunctions(
                          track.id,
                          track.rawArtistString,
                          track.rawComposerString,
                          track.rawLyricistString,
                          track.rawGenreString
                  )
                  affectedFolders.add(track.folderPath)
                }
              }
            }

            // Re-evaluate compilations and GC in one final transaction after junction regeneration.
            db.withTransaction {
              affectedFolders.forEach { path -> compilationManager.handleFolder(path) }
              ingestor.performGarbageCollection()
            }

            Log.d("CatalogScanner", "Config changes applied.")
            ConfigChangeResult(
                    durationMs = System.currentTimeMillis() - start,
                    tracksProcessed = tracks.size
            )
          }
        }

  /**
   * Performs a full synchronization with the Android MediaStore.
   *
   * Guarded by [Catalog.ioMutex] so only one scan can run at a time, and a scan
   * cannot overlap with a CatalogEditor file commit or an applyConfigChanges pass.
   */
  suspend fun scan(): ScanResult? = Catalog.ioMutex.withLock {
          withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            Log.d("CatalogScanner", "Starting Scan...")

            val dbIgnored = db.ignoredFolderDao().getAllIgnoredPaths()
            val configIgnored = config.scannerIgnores
            val masterBlacklist = (dbIgnored + configIgnored).distinct()

            val systemSnapshots = queryMediaStoreSnapshots(masterBlacklist)
            val snapshots = db.trackDao().getTrackSnapshots()

            val dbPathMap = snapshots.associateBy { it.path }
            val dbIdMap = snapshots.associateBy { it.mediaStoreId }

            val newTracks = ArrayList<SystemSnapshot>()
            val changedTracks = ArrayList<Pair<SystemSnapshot, Long>>()
            val movedTracks = ArrayList<Pair<SystemSnapshot, Long>>()

            val currentSystemPaths = HashSet<String>()
            val affectedFolders = mutableSetOf<String>()

            systemSnapshots.forEach { snapshot ->
              currentSystemPaths.add(snapshot.path)

              val existingByPath = dbPathMap[snapshot.path]
              val existingById = dbIdMap[snapshot.mediaStoreId]

              if (existingByPath != null) {
                // CASE A: EXACT MATCH (Path is same)
                if (snapshot.dateModified > existingByPath.dateModified + 2000) {
                  changedTracks.add(snapshot to existingByPath.id)
                  getFolderPath(snapshot.path)?.let { affectedFolders.add(it) }
                }
              } else if (existingById != null) {
                // CASE B: MOVED / RENAMED
                movedTracks.add(snapshot to existingById.id)
                getFolderPath(existingById.path)?.let { affectedFolders.add(it) }
                getFolderPath(snapshot.path)?.let { affectedFolders.add(it) }
                Log.d("CatalogScanner", "Detected Move: ${existingById.path} -> ${snapshot.path}")
              } else {
                // CASE C: TRULY NEW
                newTracks.add(snapshot)
                getFolderPath(snapshot.path)?.let { affectedFolders.add(it) }
              }
            }

            val systemIdSet = systemSnapshots.map { it.mediaStoreId }.toHashSet()
            val deletedSnapshots =
                    snapshots.filter {
                      !currentSystemPaths.contains(it.path) &&
                              !systemIdSet.contains(it.mediaStoreId)
                    }

            if (deletedSnapshots.isNotEmpty()) {
              val deletedPaths = deletedSnapshots.map { it.path }
              deletedPaths.forEach { path -> getFolderPath(path)?.let { affectedFolders.add(it) } }
              deletedPaths.chunked(900).forEach { chunk -> db.trackDao().deleteByPaths(chunk) }
              Log.d("CatalogScanner", "Deleted ${deletedPaths.size} tracks.")
            }

            // Early exit (99% of app launches hit this instantly)
            if (newTracks.isEmpty() &&
                            changedTracks.isEmpty() &&
                            movedTracks.isEmpty() &&
                            deletedSnapshots.isEmpty()
            ) {
              Log.d("CatalogScanner", "No changes detected. Scan Complete.")
              return@withContext null
            }

            // A. INSERTS
            if (newTracks.isNotEmpty()) {
              val cores = Runtime.getRuntime().availableProcessors()
              val parallelism = (cores / 2).coerceIn(3, 4)
              val semaphore = Semaphore(parallelism)
              newTracks.chunked(50).forEach { chunk ->
                val paths = chunk.map { it.path }
                val skeletons = queryMediaStore(masterBlacklist, paths)
                val preparedData = skeletons.map { skeleton ->
                  async(Dispatchers.IO) {
                    semaphore.withPermit { skeleton to TagLibHelper.extract(skeleton.path) }
                  }
                }.awaitAll()
                db.withTransaction {
                  preparedData.forEach { (skeleton, extendedMeta) ->
                    ingestor.ingestNewTrack(skeleton, extendedMeta)
                  }
                }
              }
            }
            
           

            // B. UPDATES
            if (changedTracks.isNotEmpty()) {
              changedTracks.chunked(50).forEach { chunk ->
                val chunkMap = chunk.associateBy { it.first.path }
                val paths = chunkMap.keys.toList()
                val skeletons = queryMediaStore(masterBlacklist, paths)
                val preparedData =
                        skeletons.mapNotNull { skeleton ->
                          val id = chunkMap[skeleton.path]?.second ?: return@mapNotNull null
                          Triple(skeleton, TagLibHelper.extract(skeleton.path), id)
                        }
                db.withTransaction {
                  preparedData.forEach { (skeleton, extendedMeta, id) ->
                    ingestor.ingestUpdatedTrack(id, skeleton, extendedMeta)
                  }
                }
              }
            }

            // C. MOVES
            if (movedTracks.isNotEmpty()) {
              movedTracks.chunked(50).forEach { chunk ->
                val chunkMap = chunk.associateBy { it.first.path }
                val paths = chunkMap.keys.toList()
                val skeletons = queryMediaStore(masterBlacklist, paths)
                val preparedData =
                        skeletons.mapNotNull { skeleton ->
                          val id = chunkMap[skeleton.path]?.second ?: return@mapNotNull null
                          Triple(skeleton, TagLibHelper.extract(skeleton.path), id)
                        }
                db.withTransaction {
                  preparedData.forEach { (skeleton, extendedMeta, id) ->
                    ingestor.ingestUpdatedTrack(id, skeleton, extendedMeta)
                  }
                }
              }
            }

            if (affectedFolders.isNotEmpty()) {
              db.withTransaction {
                affectedFolders.forEach { path -> compilationManager.handleFolder(path) }
                ingestor.performGarbageCollection()
              }
            } else {
              db.withTransaction { ingestor.performGarbageCollection() }
            }

            val duration = System.currentTimeMillis() - start
            Log.d("CatalogScanner", "Scan Complete in ${duration}ms")
            ScanResult(
                    durationMs = duration,
                    newTracks = newTracks.size,
                    changedTracks = changedTracks.size,
                    movedTracks = movedTracks.size,
                    deletedTracks = deletedSnapshots.size
            )
          }
        }

  /**
   * Rescans a single specific file. Used when the Editor saves changes.
   *
   * Guarded by [Catalog.ioMutex] so this cannot overlap with a full scan() or
   * another rescanSingleFile() call. Because notifyFileChanged() launches this
   * via scope.launch, it will queue here and wait if the Mutex is already held.
   */
  suspend fun rescanSingleFile(path: String) = Catalog.ioMutex.withLock {
          withContext(Dispatchers.IO) {
            val file = File(path)

            // A. File was deleted
            if (!file.exists()) {
              val parentPath = getFolderPath(path)
              db.trackDao().deleteByPaths(listOf(path))
              if (parentPath != null) {
                db.withTransaction {
                  compilationManager.handleFolder(parentPath)
                  ingestor.performGarbageCollection()
                }
              }
              Log.d("CatalogScanner", "File deleted: $path")
              return@withContext
            }

            // B. File exists, check DB
            val existingTrack = db.trackDao().getTrackByPath(path)
            if (existingTrack == null) {
              Log.w("CatalogScanner", "File not in database: $path (will be indexed on next scan)")
              return@withContext
            }

            // C. Get System Info (ID might have changed if file was replaced)
            val systemSkeleton = getSystemTrack(path)
            if (systemSkeleton == null) {
              Log.w(
                      "CatalogScanner",
                      "Warning: Rescanning $path but MediaStore has no record. Using stale ID."
              )
            }

            // D. Extract & Re-ingest
            val extendedMeta = TagLibHelper.extract(path)
            val skeleton =
                    systemSkeleton
                            ?: SkeletonTrack(
                                    mediaStoreId = existingTrack.mediaStoreId,
                                    path = path,
                                    title = existingTrack.title,
                                    artist = existingTrack.artistDisplay,
                                    album = existingTrack.albumDisplay,
                                    albumArtist = existingTrack.albumArtistDisplay,
                                    genre = existingTrack.genreDisplay,
                                    composer = existingTrack.composerDisplay,
                                    year = existingTrack.year,
                                    trackNumber = existingTrack.trackNumber,
                                    duration = existingTrack.durationMs,
                                    dateAdded = existingTrack.dateAdded,
                                    mimeType = existingTrack.mimeType,
                                    size = file.length(),
                                    dateModified = file.lastModified()
                            )

            db.withTransaction {
              ingestor.ingestUpdatedTrack(
                      id = existingTrack.id,
                      skeleton = skeleton,
                      extendedMeta = extendedMeta,
                      trustTagLibFirst = true
              )

              val parentPath = getFolderPath(path)
              if (parentPath != null) {
                compilationManager.handleFolder(parentPath)
              }
              ingestor.performGarbageCollection()
            }

            Log.d("CatalogScanner", "Rescan complete for: $path")
          }
        }

  private fun getFolderPath(path: String): String? {
    return try {
      File(path).parentFile?.absolutePath
    } catch (e: Exception) {
      null
    }
  }

  private suspend fun queryMediaStoreSnapshots(
          blacklist: List<String>
  ): List<SystemSnapshot> =
          withContext(Dispatchers.IO) {
            val list = mutableListOf<SystemSnapshot>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection =
                    arrayOf(
                            MediaStore.Audio.Media._ID,
                            MediaStore.Audio.Media.DATA,
                            MediaStore.Audio.Media.DATE_MODIFIED
                    )

            val selection =
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ${config.minDurationMs}"

            context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
              val colId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
              val colPath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
              val colDateMod = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

              while (cursor.moveToNext()) {
                val path = cursor.getString(colPath)
                if (path != null && File(path).exists()) {
                  val shouldIgnore = blacklist.any { path == it || path.startsWith("$it/") }
                  if (!shouldIgnore) {
                    val rawDateMod = cursor.getLong(colDateMod)
                    val finalDateMod =
                            if (rawDateMod > 10_000_000_000L) rawDateMod else rawDateMod * 1000L
                    list.add(
                            SystemSnapshot(
                                    mediaStoreId = cursor.getLong(colId),
                                    path = path,
                                    dateModified = finalDateMod
                            )
                    )
                  }
                }
              }
            }
            return@withContext list
          }

  private suspend fun getSystemTrack(path: String): SkeletonTrack? =
          withContext(Dispatchers.IO) {
            val list = queryMediaStore(emptyList(), listOf(path))
            return@withContext list.firstOrNull()
          }

  private suspend fun queryMediaStore(
          blacklist: List<String>,
          pathsToFetch: List<String>? = null
  ): List<SkeletonTrack> =
          withContext(Dispatchers.IO) {
            val list = mutableListOf<SkeletonTrack>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.DATA)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add("album_artist")
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.SIZE)
                add(MediaStore.Audio.Media.DATE_ADDED)
                add(MediaStore.Audio.Media.DATE_MODIFIED)
                add(MediaStore.Audio.Media.MIME_TYPE)
                add(MediaStore.Audio.Media.COMPOSER)
                add(MediaStore.Audio.Media.YEAR)
                add(MediaStore.Audio.Media.TRACK)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    add(MediaStore.Audio.Media.GENRE)
                }
            }.toTypedArray()

            var selection =
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ${config.minDurationMs}"
            var args: Array<String>? = null

            if (!pathsToFetch.isNullOrEmpty()) {
              val placeholders = pathsToFetch.joinToString(",") { "?" }
              selection += " AND ${MediaStore.Audio.Media.DATA} IN ($placeholders)"
              args = pathsToFetch.toTypedArray()
            }

            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
              val colId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
              val colPath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
              val colTitle = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
              val colArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
              val colAlbum = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
              val colAlbumArtist =
                      if (android.os.Build.VERSION.SDK_INT >= 30)
                              cursor.getColumnIndex("album_artist")
                      else -1
              val colGenre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
              val colDur = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
              val colSize = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
              val colDateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
              val colDateMod = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
              val colMime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
              val colComposer = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
              val colYear = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
              val colTrack = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

              while (cursor.moveToNext()) {
                val path = cursor.getString(colPath)
                if (path != null && File(path).exists()) {
                  val shouldIgnore = blacklist.any { path == it || path.startsWith("$it/") }
                  if (!shouldIgnore) {
                    val rawDateMod = cursor.getLong(colDateMod)
                    val finalDateMod =
                            if (rawDateMod > 10_000_000_000L) rawDateMod else rawDateMod * 1000L
                    list.add(
                            SkeletonTrack(
                                    mediaStoreId = cursor.getLong(colId),
                                    path = path,
                                    title = cursor.getString(colTitle) ?: "",

                                    // GARBAGE FILTER: MediaStore injects "<unknown>" into the
                                    // artist tag when a file has no artist. We treat this as blank
                                    // so TagLib's real value wins in processCommonFields.
                                    artist = cursor.getString(colArtist)
                                            ?.takeUnless { it.equals("<unknown>", ignoreCase = true) }
                                            ?: "",

                                    // GARBAGE FILTER: MediaStore uses the folder name as a fallback
                                    // album when a file has no album tag. We reject known garbage
                                    // values so TagLib's real value wins in processCommonFields.
                                    album = cursor.getString(colAlbum)
                                            ?.takeUnless {
                                                it.equals("<unknown>", ignoreCase = true) ||
                                                it.equals("Unknown Album", ignoreCase = true)
                                            }
                                            ?: "",
                                    albumArtist =
                                            if (colAlbumArtist != -1)
                                                    cursor.getString(colAlbumArtist) ?: ""
                                            else "",
                                    genre =
                                            if (colGenre != -1) cursor.getString(colGenre) ?: ""
                                            else "",
                                    duration = cursor.getLong(colDur),
                                    size = cursor.getLong(colSize),
                                    dateAdded = cursor.getLong(colDateAdded),
                                    dateModified = finalDateMod,
                                    mimeType = cursor.getString(colMime) ?: "audio/*",
                                    composer =
                                            if (colComposer != -1)
                                                    cursor.getString(colComposer) ?: ""
                                            else "",
                                    year = if (colYear != -1) cursor.getInt(colYear) else 0,
                                    trackNumber = if (colTrack != -1) cursor.getInt(colTrack) else 0
                            )
                    )
                  }
                }
              }
            }
            return@withContext list
          }
}

internal data class SystemSnapshot(
        val mediaStoreId: Long,
        val path: String,
        val dateModified: Long
)

internal data class SkeletonTrack(
        val mediaStoreId: Long,
        val path: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val composer: String,
        val year: Int,
        val trackNumber: Int,
        val duration: Long,
        val size: Long,
        val dateAdded: Long,
        val dateModified: Long,
        val mimeType: String
)
