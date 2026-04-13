package aman.catalog.audio.internal

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class MediaWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onContentChanged: suspend () -> Unit
) {
    private var observer: ContentObserver? = null
    private var scanJob: Job? = null

    fun start() {
        if (observer != null) return

        val handler = Handler(Looper.getMainLooper())
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // Debounce: Cancel previous job, wait 2s, then trigger
                scanJob?.cancel()
                scanJob = scope.launch {
                    delay(2000) 
                    onContentChanged()
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer!!
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        scanJob?.cancel()
    }
}
