package aman.catalogtest.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import coil3.ImageLoader
import coil3.Uri
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

private const val TAG = "AudioCoverFetcher"

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "opus",
    "wav", "aiff", "aif", "wma", "ape", "mka"
)

class AudioCoverFetcher(
    private val path: String
) : Fetcher {

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun fetch(): FetchResult? {
        Log.d(TAG, "fetch() called for: $path")

        val bytes = MediaMetadataRetriever().use { retriever ->
            try {
                retriever.setDataSource(path)
                val pic = retriever.embeddedPicture
                if (pic == null) Log.w(TAG, "embeddedPicture returned null for: $path")
                else Log.d(TAG, "embeddedPicture OK — ${pic.size} bytes")
                pic
            } catch (e: Exception) {
                Log.e(TAG, "MediaMetadataRetriever failed for: $path", e)
                null
            }
        } ?: return null

        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source     = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType   = null,
            dataSource = DataSource.DISK
        )
    }

    // Typed as coil3.Uri — Coil 3 converts String paths to coil3.Uri internally
    // before reaching fetcher factories. Using android.net.Uri here will never match.
    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val path = data.path ?: return null

            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in AUDIO_EXTENSIONS) return null

            Log.d(TAG, "Factory creating fetcher for: $path")
            return AudioCoverFetcher(path)
        }
    }
}
