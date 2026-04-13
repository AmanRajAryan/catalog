package aman.catalogtest

import aman.catalog.audio.Catalog
import aman.catalog.audio.CatalogConfig
import aman.catalogtest.util.AudioCoverFetcher
import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath

class CatalogTestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                startActivity(CrashActivity.createIntent(this, throwable))
            } catch (e: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        Catalog.initialize(
            context       = this,
            defaultConfig = CatalogConfig()
        )

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder()
                       
                        .maxSizePercent(this@CatalogTestApp, 0.70)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil_audio_covers").toOkioPath())
                        .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                        .build()
                }
                .components {
                    add(AudioCoverFetcher.Factory(this@CatalogTestApp))
                }
                .build()
        }
    }
}
