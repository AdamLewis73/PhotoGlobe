package com.photoglobe.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per media item that has a location.
 *
 * Schema follows DESIGN.md section 4. Two fields exist before they are used, on purpose:
 *  - mediaType   videos are out of scope for M1 but the column avoids a migration (D-032)
 *  - geohash     the spatial index that makes viewport queries cheap (DESIGN.md section 5)
 *
 * Photos are never copied or modified - only referenced (hard rule 3, D-008).
 */
@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["geohash"]),
        Index(value = ["dateTakenUtc"])
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val mediaStoreId: Long,
    val mediaType: String = MEDIA_TYPE_IMAGE,   // D-032: IMAGE today, VIDEO later
    val contentUri: String,

    /** size + dateTaken + displayName. Survives MediaStore id churn, enables dedup. */
    val contentSignature: String,

    val dateTakenUtc: Long,
    /** EXIF OffsetTimeOriginal when present. Null means the timestamp is naive local time. */
    val dateTakenOffset: String? = null,

    val lat: Double,
    val lng: Double,
    val altitude: Double? = null,

    /** EXIF | MANUAL | INTERPOLATED | GPX. Only EXIF is produced in M1 (D-009). */
    val locationSource: String = SOURCE_EXIF,
    /** Inferred locations start false and require confirmation. See D-009. */
    val locationConfirmed: Boolean = true,

    /** Prefix-searchable spatial key. See DESIGN.md section 5. */
    val geohash: String,

    val isHidden: Boolean = false
) {
    companion object {
        const val MEDIA_TYPE_IMAGE = "IMAGE"
        const val MEDIA_TYPE_VIDEO = "VIDEO"
        const val SOURCE_EXIF = "EXIF"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_INTERPOLATED = "INTERPOLATED"
        const val SOURCE_GPX = "GPX"
    }
}

/** Persisted scan cursor so a resume only touches what is new (D-006). */
@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastMediaStoreVersion: String? = null,
    val lastDateAddedSeen: Long = 0,
    val lastFullScanAt: Long = 0
)
