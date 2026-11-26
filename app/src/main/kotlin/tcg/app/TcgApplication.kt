package tcg.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import tcg.app.storage.AppDatabase

class TcgApplication : Application(), ImageLoaderFactory {
    val database: AppDatabase by lazy { AppDatabase.open(this) }

    // Printing images are fetched on first view only (no prefetch) and then served from this disk cache.
    // Bandai's responses may forbid caching, so cache headers are ignored: the cache is the only copy
    // that works offline.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("printing_images")).build() }
        .respectCacheHeaders(false)
        .build()
}
