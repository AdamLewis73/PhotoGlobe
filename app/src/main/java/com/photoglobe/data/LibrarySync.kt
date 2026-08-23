package com.photoglobe.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps Room in step with the photo library (D-006).
 *
 * The first run is slow because every file's EXIF must be read (~4 ms each, D-027). **Every
 * run after that is a handful of rows**, which is what makes the app as instant as the
 * reference implementation from launch two onward (D-020). Do not describe scan cost as
 * recurring - it is not.
 *
 * Three things happen here:
 *  1. **Full or incremental?** MediaStore's version string changes when the store is
 *     rebuilt, at which point ids cannot be trusted and everything is rescanned. Otherwise
 *     only rows added since the last run are read.
 *  2. **Scan** the new ids for GPS and insert them.
 *  3. **Reconcile** - ids in Room that MediaStore no longer has are deleted (D-040).
 */
class LibrarySync(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val scanStateDao: ScanStateDao,
    private val scanner: MediaLibraryScanner = MediaLibraryScanner(context)
) {

    data class Result(
        val fullScan: Boolean,
        val examined: Int,
        val added: Int,
        val removed: Int,
        val elapsedMs: Long
    )

    suspend fun sync(
        onProgress: suspend (MediaLibraryScanner.Progress) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val started = System.nanoTime()

        val state = scanStateDao.get()
        val version = currentVersion()
        val versionChanged = state != null && state.lastMediaStoreVersion != version
        val fullScan = state == null || versionChanged || photoDao.count() == 0

        val since = if (fullScan) 0L else state!!.lastDateAddedSeen
        val (ids, maxDateAdded) = scanner.enumerateSince(since)

        val before = photoDao.count()
        if (ids.isNotEmpty()) scanner.scan(ids, photoDao, onProgress = onProgress)
        val added = photoDao.count() - before

        // Reconciliation: one cursor pass, no file reads. Cheap enough to do every sync.
        val removed = reconcile()

        scanStateDao.put(
            ScanStateEntity(
                lastMediaStoreVersion = version,
                lastDateAddedSeen = maxOf(since, maxDateAdded),
                lastFullScanAt = if (fullScan) System.currentTimeMillis()
                                 else state?.lastFullScanAt ?: System.currentTimeMillis()
            )
        )

        Result(
            fullScan = fullScan,
            examined = ids.size,
            added = added,
            removed = removed,
            elapsedMs = (System.nanoTime() - started) / 1_000_000
        )
    }

    /** Drops rows whose photo no longer exists on the device (D-040). */
    private suspend fun reconcile(): Int {
        val live = scanner.allMediaStoreIds().toHashSet()
        val known = photoDao.knownMediaStoreIds()
        val gone = known.filterNot { it in live }
        if (gone.isNotEmpty()) photoDao.deleteByMediaStoreIds(gone)
        return gone.size
    }

    private fun currentVersion(): String =
        MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL)
}
