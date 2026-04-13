package aman.catalog.audio

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import aman.catalog.audio.models.Track
import aman.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object CatalogEditor {

    /**
     * Defines how to handle artwork during an update.
     */
    sealed class ArtworkUpdate {
        object NoChange : ArtworkUpdate()
        object Delete : ArtworkUpdate()
        class Set(val data: ByteArray, val mimeType: String) : ArtworkUpdate()
    }

    /**
     * Specific result types to tell the UI exactly what went wrong.
     */
    sealed class EditResult {
        object Success : EditResult()

        // Scoped Storage: catch this, launch the IntentSender, and retry on success.
        data class PermissionRequired(val intentSender: IntentSender) : EditResult()

        // Returned when allowMediaStoreFallback = false and no SAF permission covers the file.
        // folderPath gives the UI the exact directory to prompt the user to grant access to.
        data class SafPermissionMissing(val folderPath: String) : EditResult()

        data class InputError(val message: String) : EditResult()
        data class IOError(val message: String) : EditResult()
        object TagWriteFailed : EditResult()
        object ArtWriteFailed : EditResult()
    }

        /**
     * Reads the exact, raw tags from the file (ideal for Tag Editor screens).
     * Thread-safe against native crashes during concurrent edits.
     */
    suspend fun readTags(track: Track): Map<String, String> = withContext(Dispatchers.IO) {
        Catalog.ioMutex.withLock {
            return@withContext TagLib.getMetadata(track.path) ?: emptyMap()
        }
    }

    
    

    /**
     * Updates metadata and/or artwork in a SINGLE pass.
     * Safe against data corruption and handles Android Scoped Storage permissions.
     */
    suspend fun updateTrack(
        context: Context,
        track: Track,
        newTags: Map<String, String>? = null,
        artworkUpdate: ArtworkUpdate = ArtworkUpdate.NoChange,
        allowMediaStoreFallback: Boolean = true
    ): EditResult = withContext(Dispatchers.IO) {
    
        // 1. INPUT VALIDATION
        // We verify the source file exists and has content before doing anything.
        try {
            context.contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd ->
                if (pfd.statSize == 0L) {
                    return@withContext EditResult.InputError("Source file is empty (0 bytes).")
                }
            } ?: return@withContext EditResult.InputError("Could not open file descriptor.")
        } catch (e: Exception) {
             return@withContext EditResult.InputError("File not found or not accessible: ${e.message}")
        }

        // 2. PREPARE TEMP FILE
        // We work on a copy in the cache directory to avoid corrupting the original if the app crashes.
        val extension = track.path.substringAfterLast('.', "mp3")
        val cacheFile = File(context.cacheDir, "edit_${System.currentTimeMillis()}.$extension")

        try {
            try {
                context.contentResolver.openInputStream(track.uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext EditResult.IOError("Failed to open input stream for copying.")
            } catch (e: IOException) {
                return@withContext EditResult.IOError("Copy to cache failed: ${e.message}")
            }

            if (newTags != null) {
                // Convert Map to HashMap for the native interface
                val map = if (newTags is HashMap) newTags else HashMap(newTags)
                val tagSuccess = TagLib.setMetadata(cacheFile.absolutePath, map)
                if (!tagSuccess) {
                    return@withContext EditResult.TagWriteFailed
                }
            }

            val artSuccess = when (artworkUpdate) {
                is ArtworkUpdate.NoChange -> true
                is ArtworkUpdate.Delete -> TagLib.setArtwork(cacheFile.absolutePath, ByteArray(0), "", "")
                is ArtworkUpdate.Set -> TagLib.setArtwork(
                    cacheFile.absolutePath,
                    artworkUpdate.data,
                    artworkUpdate.mimeType,
                    ""
                )
            }

            if (!artSuccess) {
                return@withContext EditResult.ArtWriteFailed
            }

            if (cacheFile.length() == 0L) {
                return@withContext EditResult.IOError("Temp file corrupted during write operation.")
            }

            // 3. FINAL COMMIT (Cache -> Original)
            // This is the critical section where we overwrite the user's file.
            //
            // We acquire the shared ioMutex before touching the real file on disk. This
            // guarantees that ScannerService cannot call TagLibHelper.extract() on this path
            // while we are streaming bytes — preventing a SIGSEGV native crash on a
            // half-written, temporarily 0-byte file.
            //
            // The catch blocks deliberately sit OUTSIDE the withLock block so that the
            // Mutex is always released (via withLock's internal finally) before we return,
            // regardless of whether an exception is thrown.
            try {
                Catalog.ioMutex.withLock {
                    var outputStream: OutputStream? = null

                    // --- ATTEMPT 1: Optimistic SAF Write ---
                    // Try to resolve an O(1) SAF DocumentUri if the host app granted folder permissions.
                    val safUri = getFastSafUri(context, track.path)
                    if (safUri != null) {
                        try {
                            outputStream = context.contentResolver.openOutputStream(safUri, "wt")
                        } catch (e: Exception) {
                            // SAFEGUARD: OEM mismatch or file not found via SAF. Swallow and fallback.
                            e.printStackTrace()
                        }
                    }

                    // --- ATTEMPT 2: MediaStore Fallback ---
                    // If no SAF permission exists, or Attempt 1 crashed, fallback to the raw MediaStore URI.
                    // This intentionally triggers the RecoverableSecurityException prompt on Android 10+.
                    if (outputStream == null) {
                        if (!allowMediaStoreFallback) {
                            val parentFolder = File(track.path).parent ?: "Unknown Directory"
                            return@withContext EditResult.SafPermissionMissing(parentFolder)
                        }
                        outputStream = context.contentResolver.openOutputStream(track.uri, "wt")
                    }

                    if (outputStream == null) {
                        return@withContext EditResult.IOError("Failed to open output stream via SAF and MediaStore.")
                    }

                    outputStream.use { output ->
                        FileInputStream(cacheFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: RecoverableSecurityException) {
                // Android 10/11 Scoped Storage: We need to ask for permission.
                return@withContext EditResult.PermissionRequired(e.userAction.actionIntent.intentSender)
            } catch (e: SecurityException) {
                return@withContext EditResult.IOError("Permission Denied: ${e.message}")
            } catch (e: IOException) {
                return@withContext EditResult.IOError("Final commit failed: ${e.message}")
            }

            // 4. NOTIFY SYSTEM
            // The Mutex is already released at this point. notifyFileChanged launches
            // rescanSingleFile via scope.launch, which will queue up and acquire the
            // Mutex on its own when it runs — no risk of a self-deadlock.
            Catalog.notifyFileChanged(track.path)
            return@withContext EditResult.Success

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext EditResult.IOError("Unexpected crash: ${e.message}")
        } finally {
            // 5. CLEANUP
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }

    /**
     * Pre-flight hint: Returns true if the host app has a persistent SAF folder permission
     * that covers this specific path.
     *
     * NOTE: This is a UI hint only. Due to Android's TOCTOU (Time-Of-Check-To-Use) reality,
     * permissions can be revoked between this check and the actual write. [updateTrack]
     * handles the actual enforcement safely.
     */
    fun hasSafPermission(context: Context, path: String): Boolean {
        return getFastSafUri(context, path) != null
    }

    /**
     * Evaluates a file path against the host app's persisted Storage Access Framework (SAF) permissions.
     * Uses Android's StorageManager to dynamically extract the volume (Primary or SD Card UUID),
     * ensuring O(1) mathematical URI resolution without hardcoding string paths or traversing Document files.
     */
    private fun getFastSafUri(context: Context, path: String): Uri? {
        val file = File(path)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volume = storageManager.getStorageVolume(file) ?: return null
        
        // 1. Identify the volume name ("primary" for internal, or UUID for SD Cards)
        val volumeName = if (volume.isPrimary) "primary" else volume.uuid ?: return null
        
        // 2. Find the relative path of the file on that volume
        val rootPath = if (volume.isPrimary) "/storage/emulated/0/" else "/storage/$volumeName/"
        if (!path.startsWith(rootPath)) return null
        
        val relativePath = path.substringAfter(rootPath)
        
        // 3. The exact Document ID for this specific file
        val documentId = "$volumeName:$relativePath"
        
        // 4. Check if we have a persisted permission that covers this file's folder
        val persistedPermissions = context.contentResolver.persistedUriPermissions
        for (permission in persistedPermissions) {
            if (!permission.isWritePermission) continue
            
            val treeUri = permission.uri
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            
            // Ensure exact folder boundary (prevent "Music2" matching "Music")
            if (documentId.startsWith(treeDocumentId)) {
                if (documentId.length == treeDocumentId.length || documentId[treeDocumentId.length] == '/') {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                }
            }
        }
        
        // No matching folder permission found
        return null
    }
}
