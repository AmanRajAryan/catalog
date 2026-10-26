package aman.catalog.audio.internal.scanner

import aman.catalog.audio.Catalog
import aman.catalog.audio.CatalogConfig
import aman.catalog.audio.ConfigChangeResult
import aman.catalog.audio.ScanResult
import aman.catalog.audio.internal.database.CatalogDatabase
import aman.catalog.audio.internal.taglib.TagLibHelper
import aman.catalog.audio.models.ExtendedMetadata
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
  suspend fun applyConfigChanges(): ConfigChangeResult = Catalog.scanMutex.withLock {
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
  suspend fun scan(): ScanResult? = Catalog.scanMutex.withLock {
          withContext(Dispatchers.IO) {
            ingestor.clearCache()
            val start = System.currentTimeMillis()
            Log.d("CatalogScanner", "Starting Scan...")

            val dbIgnored = db.ignoredFolderDao().getAllIgnoredPaths()
            val configIgnored = config.scannerIgnores
            val masterBlacklist = (dbIgnored + configIgnored).distinct()

            val systemSkeletons = queryMediaStore(masterBlacklist).distinctBy { it.path }
            val snapshots = db.trackDao().getTrackSnapshots()

            val dbPathMap = snapshots.associateBy { it.path }
            val dbIdMap = snapshots.associateBy { it.mediaStoreId }

            val newTracks = ArrayList<SkeletonTrack>()
            val changedTracks = ArrayList<Pair<SkeletonTrack, Long>>()
            val movedTracks = ArrayList<Pair<SkeletonTrack, Long>>()

            val currentSystemPaths = HashSet<String>()
            val affectedFolders = mutableSetOf<String>()

            systemSkeletons.forEach { skeleton ->
              currentSystemPaths.add(skeleton.path)

              val existingByPath = dbPathMap[skeleton.path]
              val existingById = dbIdMap[skeleton.mediaStoreId]

              if (existingByPath != null) {
                // CASE A: EXACT MATCH (Path is same)
                if (skeleton.dateModified > existingByPath.dateModified + 2000) {
                  changedTracks.add(skeleton to existingByPath.id)
                  getFolderPath(skeleton.path)?.let { affectedFolders.add(it) }
                }
              } else if (existingById != null) {
                // CASE B: MOVED / RENAMED
                movedTracks.add(skeleton to existingById.id)
                getFolderPath(existingById.path)?.let { affectedFolders.add(it) }
                getFolderPath(skeleton.path)?.let { affectedFolders.add(it) }
                Log.d("CatalogScanner", "Detected Move: ${existingById.path} -> ${skeleton.path}")
              } else {
                // CASE C: TRULY NEW
                newTracks.add(skeleton)
                getFolderPath(skeleton.path)?.let { affectedFolders.add(it) }
              }
            }

            val systemIdSet = systemSkeletons.map { it.mediaStoreId }.toHashSet()
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

            val cores = Runtime.getRuntime().availableProcessors()
            val parallelism = minOf(4, cores)
            val semaphore = Semaphore(parallelism)

            // A. INSERTS (Chunked into 250 for bulk SQLite transactions)
            if (newTracks.isNotEmpty()) {
              newTracks.chunked(250).forEach { chunk ->
                val preparedData = chunk.map { skeleton ->
                  async(Dispatchers.IO) {
                    semaphore.withPermit { skeleton to TagLibHelper.extract(skeleton.path) }
                  }
                }.awaitAll()

                db.withTransaction {
                  preparedData.forEach { (skeleton, extendedMeta) ->
                    val resolvedDuration = if (skeleton.duration > 0) skeleton.duration else extendedMeta.foundDuration
                    val isTooShort = config.minDurationMs > 0 && resolvedDuration in 1 until config.minDurationMs
                    if (!isTooShort) {
                      ingestor.ingestNewTrack(skeleton.copy(duration = resolvedDuration), extendedMeta)
                    }
                  }
                }
              }
            }

            // B. UPDATES
            if (changedTracks.isNotEmpty()) {
              changedTracks.chunked(250).forEach { chunk ->
                val preparedData = chunk.map { (skeleton, id) ->
                  async(Dispatchers.IO) {
                    semaphore.withPermit { Triple(skeleton, TagLibHelper.extract(skeleton.path), id) }
                  }
                }.awaitAll()

                db.withTransaction {
                  preparedData.forEach { (skeleton, extendedMeta, id) ->
                    val resolvedDuration = if (skeleton.duration > 0) skeleton.duration else extendedMeta.foundDuration
                    val isTooShort = config.minDurationMs > 0 && resolvedDuration in 1 until config.minDurationMs
                    if (!isTooShort) {
                      ingestor.ingestUpdatedTrack(id, skeleton.copy(duration = resolvedDuration), extendedMeta)
                    }
                  }
                }
              }
            }

            // C. MOVES
            if (movedTracks.isNotEmpty()) {
              movedTracks.chunked(250).forEach { chunk ->
                val preparedData = chunk.map { (skeleton, id) ->
                  async(Dispatchers.IO) {
                    semaphore.withPermit { Triple(skeleton, TagLibHelper.extract(skeleton.path), id) }
                  }
                }.awaitAll()

                db.withTransaction {
                  preparedData.forEach { (skeleton, extendedMeta, id) ->
                    val resolvedDuration = if (skeleton.duration > 0) skeleton.duration else extendedMeta.foundDuration
                    val isTooShort = config.minDurationMs > 0 && resolvedDuration in 1 until config.minDurationMs
                    if (!isTooShort) {
                      ingestor.ingestUpdatedTrack(id, skeleton.copy(duration = resolvedDuration), extendedMeta)
                    }
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
  suspend fun rescanSingleFile(path: String) = Catalog.scanMutex.withLock {
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
            val resolvedDuration = if (systemSkeleton != null && systemSkeleton.duration > 0) {
              systemSkeleton.duration
            } else if (extendedMeta.foundDuration > 0) {
              extendedMeta.foundDuration
            } else {
              existingTrack.durationMs
            }
            val skeleton =
                    systemSkeleton?.copy(dateModified = file.lastModified(), duration = resolvedDuration)
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
                                    duration = resolvedDuration,
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
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    add(MediaStore.Audio.Media.ALBUM_ARTIST)
                    add(MediaStore.Audio.Media.GENRE)
                }
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.SIZE)
                add(MediaStore.Audio.Media.DATE_ADDED)
                add(MediaStore.Audio.Media.DATE_MODIFIED)
                add(MediaStore.Audio.Media.MIME_TYPE)
                add(MediaStore.Audio.Media.COMPOSER)
                add(MediaStore.Audio.Media.YEAR)
                add(MediaStore.Audio.Media.TRACK)
            }.toTypedArray()

            var selection: String? = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            var args: Array<String>? = null

            if (!pathsToFetch.isNullOrEmpty()) {
              if (pathsToFetch.size > 900) {
                return@withContext pathsToFetch.chunked(900).flatMap { chunk ->
                  queryMediaStore(blacklist, chunk)
                }
              }
              val placeholders = pathsToFetch.joinToString(",") { "?" }
              selection = "${MediaStore.Audio.Media.DATA} IN ($placeholders)"
              args = pathsToFetch.toTypedArray()
            }

            val cursor = try {
              context.contentResolver.query(uri, projection, selection, args, null)
            } catch (e: IllegalArgumentException) {
              Log.w("CatalogScanner", "Query with extended projection failed, retrying with fallback projection", e)
              try {
                val fallbackProjection = arrayOf(
                  MediaStore.Audio.Media._ID,
                  MediaStore.Audio.Media.DATA,
                  MediaStore.Audio.Media.TITLE,
                  MediaStore.Audio.Media.ARTIST,
                  MediaStore.Audio.Media.ALBUM,
                  MediaStore.Audio.Media.DURATION,
                  MediaStore.Audio.Media.SIZE,
                  MediaStore.Audio.Media.DATE_ADDED,
                  MediaStore.Audio.Media.DATE_MODIFIED,
                  MediaStore.Audio.Media.MIME_TYPE
                )
                context.contentResolver.query(uri, fallbackProjection, selection, args, null)
              } catch (fallbackEx: Exception) {
                Log.e("CatalogScanner", "Fallback MediaStore query also failed", fallbackEx)
                null
              }
            } catch (e: Exception) {
              Log.e("CatalogScanner", "Failed to query MediaStore", e)
              null
            }

            cursor?.use { cur ->
              val colId = cur.getColumnIndex(MediaStore.Audio.Media._ID)
              val colPath = cur.getColumnIndex(MediaStore.Audio.Media.DATA)
              if (colId == -1 || colPath == -1) {
                Log.e("CatalogScanner", "MediaStore cursor missing mandatory _ID or DATA column")
                return@use
              }

              val colTitle = cur.getColumnIndex(MediaStore.Audio.Media.TITLE)
              val colArtist = cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)
              val colAlbum = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM)
              val colAlbumArtist =
                      if (android.os.Build.VERSION.SDK_INT >= 30)
                              cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                      else -1
              val colGenre =
                      if (android.os.Build.VERSION.SDK_INT >= 30)
                              cur.getColumnIndex(MediaStore.Audio.Media.GENRE)
                      else -1
              val colDur = cur.getColumnIndex(MediaStore.Audio.Media.DURATION)
              val colSize = cur.getColumnIndex(MediaStore.Audio.Media.SIZE)
              val colDateAdded = cur.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
              val colDateMod = cur.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
              val colMime = cur.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
              val colComposer = cur.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
              val colYear = cur.getColumnIndex(MediaStore.Audio.Media.YEAR)
              val colTrack = cur.getColumnIndex(MediaStore.Audio.Media.TRACK)

              while (cur.moveToNext()) {
                if (cur.isNull(colId) || cur.isNull(colPath)) continue
                val path = cur.getString(colPath) ?: continue
                if (File(path).exists()) {
                  val shouldIgnore = blacklist.any { path == it || path.startsWith("$it/") }
                  if (!shouldIgnore) {
                    val rawDateMod =
                            if (colDateMod != -1 && !cur.isNull(colDateMod)) cur.getLong(colDateMod) else 0L
                    val finalDateMod = when {
                      rawDateMod > 10_000_000_000L -> rawDateMod
                      rawDateMod > 0L -> rawDateMod * 1000L
                      else -> File(path).lastModified()
                    }

                    val title =
                            if (colTitle != -1 && !cur.isNull(colTitle)) cur.getString(colTitle) ?: "" else ""

                    // GARBAGE FILTER: MediaStore injects "<unknown>" into the
                    // artist tag when a file has no artist. We treat this as blank
                    // so TagLib's real value wins in processCommonFields.
                    val rawArtist =
                            if (colArtist != -1 && !cur.isNull(colArtist)) cur.getString(colArtist) else null
                    val artist = rawArtist?.takeUnless { it.equals("<unknown>", ignoreCase = true) } ?: ""

                    // GARBAGE FILTER: MediaStore uses the folder name as a fallback
                    // album when a file has no album tag. We reject known garbage
                    // values so TagLib's real value wins in processCommonFields.
                    val rawAlbum =
                            if (colAlbum != -1 && !cur.isNull(colAlbum)) cur.getString(colAlbum) else null
                    val album = rawAlbum?.takeUnless {
                      it.equals("<unknown>", ignoreCase = true) ||
                              it.equals("Unknown Album", ignoreCase = true)
                    } ?: ""

                    val albumArtist =
                            if (colAlbumArtist != -1 && !cur.isNull(colAlbumArtist))
                                    cur.getString(colAlbumArtist) ?: ""
                            else ""

                    val genre =
                            if (colGenre != -1 && !cur.isNull(colGenre)) cur.getString(colGenre) ?: ""
                            else ""

                    val duration =
                            if (colDur != -1 && !cur.isNull(colDur)) cur.getLong(colDur) else 0L

                    val size =
                            if (colSize != -1 && !cur.isNull(colSize)) cur.getLong(colSize) else 0L

                    val dateAdded =
                            if (colDateAdded != -1 && !cur.isNull(colDateAdded)) cur.getLong(colDateAdded) else 0L

                    val mimeType =
                            if (colMime != -1 && !cur.isNull(colMime))
                                    cur.getString(colMime) ?: "audio/*"
                            else "audio/*"

                    val composer =
                            if (colComposer != -1 && !cur.isNull(colComposer))
                                    cur.getString(colComposer) ?: ""
                            else ""

                    val year =
                            if (colYear != -1 && !cur.isNull(colYear)) cur.getInt(colYear) else 0

                    val trackNumber =
                            if (colTrack != -1 && !cur.isNull(colTrack)) cur.getInt(colTrack) else 0

                    list.add(
                            SkeletonTrack(
                                    mediaStoreId = cur.getLong(colId),
                                    path = path,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    albumArtist = albumArtist,
                                    genre = genre,
                                    composer = composer,
                                    year = year,
                                    trackNumber = trackNumber,
                                    duration = duration,
                                    size = size,
                                    dateAdded = dateAdded,
                                    dateModified = finalDateMod,
                                    mimeType = mimeType
                            )
                    )
                  }
                }
              }
            }
            return@withContext list
          }
}

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
