package com.photoglobe.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the photo library and writes geotagged items into Room.
 *
 * Two phases, deliberately separate because they cost wildly different amounts
 * (DESIGN.md section 5):
 *   1. enumerate  - one MediaStore cursor. Cheap: 8 ms for 25 rows in M0.
 *   2. read EXIF  - open each file. Expensive: ~4.16 ms per photo (D-027).
 *
 * Newest first, and results are emitted in batches so the map can fill in progressively
 * rather than blocking on completion (D-021).
 *
 * Photos without GPS are skipped entirely. Locations are never invented (hard rule 4, D-009).
 */
class MediaLibraryScanner(private val context: Context) {

    data class Progress(
        val processed: Int,
        val total: Int,
        val geotagged: Int,
        val done: Boolean = false
    )

    private val collection =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Ids currently visible to us. Fewer than the whole library under the Curated tier. */
    suspend fun enumerate(): List<Long> = withContext(Dispatchers.IO) {
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"     // D-021: newest first
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) ids.add(c.getLong(idCol))
        }
        ids
    }

    /**
     * Reads EXIF for [ids], writing geotagged rows in batches of [batchSize].
     * [onProgress] fires after each batch so the UI can render as results arrive.
     */
    suspend fun scan(
        ids: List<Long>,
        dao: PhotoDao,
        batchSize: Int = 200,
        onProgress: suspend (Progress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val batch = ArrayList<PhotoEntity>(batchSize)
        var processed = 0
        var geotagged = 0

        for (id in ids) {
            readOne(id)?.let { batch += it; geotagged++ }
            processed++

            if (batch.size >= batchSize) {
                dao.insertAll(batch)
                batch.clear()
                onProgress(Progress(processed, ids.size, geotagged))
            }
        }
        if (batch.isNotEmpty()) dao.insertAll(batch)
        onProgress(Progress(processed, ids.size, geotagged, done = true))
    }

    /** Null when the photo has no GPS, or cannot be read. Never throws. */
    private fun readOne(mediaStoreId: Long): PhotoEntity? {
        val base = ContentUris.withAppendedId(collection, mediaStoreId)

        // setRequireOriginal + ACCESS_MEDIA_LOCATION. Without BOTH, Android returns the
        // file with GPS silently stripped and raises no error at all (D-023).
        val uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.setRequireOriginal(base)
            else base

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong ?: return null
                PhotoEntity(
                    mediaStoreId = mediaStoreId,
                    contentUri = base.toString(),
                    contentSignature = signatureOf(exif, mediaStoreId),
                    dateTakenUtc = exif.dateTimeOriginal ?: 0L,
                    dateTakenOffset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                    lat = latLong[0],
                    lng = latLong[1],
                    altitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() },
                    geohash = Geohash.encode(latLong[0], latLong[1])
                )
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun signatureOf(exif: ExifInterface, id: Long): String {
        val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: "?"
        val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: "?"
        val t = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: "?"
        return "$id:$w x $h:$t"
    }
}
