package tcg.domain.scanning

import kotlinx.coroutines.flow.Flow

/**
 * Camera scanning seam. The domain and UI depend only on this interface, so no CameraX or
 * ML Kit types leak through and a later Kotlin Multiplatform port can supply its own scanner.
 */
interface CardScanner {
    /** Emits Base Number reads until the collector cancels collection (ends the Scan Session). */
    fun scan(scope: ScanScope): Flow<BaseNumberRead>
}
