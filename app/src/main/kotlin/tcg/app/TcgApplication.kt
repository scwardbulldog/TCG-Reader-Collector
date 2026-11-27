package tcg.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import tcg.app.scanning.CachePhotoSource
import tcg.app.scanning.ScanDebugLog
import tcg.app.storage.AppDatabase
import java.io.File

class TcgApplication : Application(), ImageLoaderFactory {
    val database: AppDatabase by lazy { AppDatabase.open(this) }

    /** Debug builds only: what the live scanner saw, for replaying misreads. Null in release builds. */
    val scanDebugLog: ScanDebugLog? by lazy {
        if (BuildConfig.DEBUG) ScanDebugLog(File(filesDir, ScanDebugLog.FOLDER)) else null
    }

    override fun onCreate() {
        super.onCreate()
        CachePhotoSource.sweep(this)
        scanDebugLog?.clear()
    }

    // Printing images are fetched on first view only (no prefetch) and then served from this disk cache.
    // Bandai's responses may forbid caching, so cache headers are ignored: the cache is the only copy
    // that works offline.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("printing_images")).build() }
        .respectCacheHeaders(false)
        .build()
}
