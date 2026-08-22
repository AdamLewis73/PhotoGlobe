package com.photoglobe.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The three access tiers from DESIGN.md section 10.
 *
 * M0 proved that CURATED returns unredacted GPS (D-024), which is why the app can survive
 * Play refusing broad access. Note minSdk is 33 but the Curated grant is 34+, so on
 * Android 13 only FULL and NONE exist (D-033).
 */
enum class MediaTier { FULL, CURATED, NONE }

object MediaAccess {

    // Literal rather than the constant so this compiles on any compileSdk.
    private const val VISUAL_USER_SELECTED =
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun tier(context: Context): MediaTier = when {
        granted(context, Manifest.permission.READ_MEDIA_IMAGES) -> MediaTier.FULL
        Build.VERSION.SDK_INT >= 34 && granted(context, VISUAL_USER_SELECTED) -> MediaTier.CURATED
        else -> MediaTier.NONE
    }

    /**
     * Whether GPS will actually come back. Without this the library looks entirely
     * un-geotagged and no error is raised - the single most misleading failure in this
     * whole area (D-023).
     */
    fun hasMediaLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)

    /** Requesting both media permissions together is what shows the three-option dialog. */
    fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        if (Build.VERSION.SDK_INT >= 34) add(VISUAL_USER_SELECTED)
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
