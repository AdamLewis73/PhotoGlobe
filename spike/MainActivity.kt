package com.photoglobe.spike

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * PhotoGlobe M0 feasibility spike. Throwaway code - delete once the questions are answered.
 *
 * Answers:
 *   Q-001  Can we read GPS from the photo library at all, and does the Android 14+
 *          partial ("Curated") grant also return unredacted coordinates?
 *   Q-008  How long does a full library scan take, and therefore does the MVP need
 *          a database or can it hold everything in memory?
 *
 * Also confirms that the Photo Picker redacts location, as documented.
 *
 * See spike/README.md for how to run this and what to record.
 */
class MainActivity : ComponentActivity() {

    // String literal rather than Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED so this
    // compiles regardless of compileSdk - the constant only exists on compileSdk 34+.
    private val visualUserSelected = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    private val log = mutableStateListOf<String>()
    private var progress by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var permState by mutableStateOf("(not checked)")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (perm, isGranted) ->
            logLine("  " + perm.substringAfterLast(".") + " = " + if (isGranted) "GRANTED" else "denied")
        }
        refreshPermissionState()
    }

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) logLine("Photo Picker: cancelled") else testPickerRedaction(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        logLine(
            "Device: " + Build.MANUFACTURER + " " + Build.MODEL +
                ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("PhotoGlobe M0 spike", style = MaterialTheme.typography.titleLarge)
                        Text("Access tier: " + permState, style = MaterialTheme.typography.bodyMedium)

                        Button(
                            onClick = { requestPermissions() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("1 - Request media access") }

                        Button(
                            onClick = { runScan(limit = 2000) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("2 - Quick scan (first 2000)") }

                        Button(
                            onClick = { runScan(limit = null) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("3 - Full library scan") }

                        Button(
                            onClick = { launchPicker() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("4 - Test Photo Picker redaction") }

                        if (progress.isNotEmpty()) {
                            HorizontalDivider()
                            Text(progress, style = MaterialTheme.typography.bodyMedium)
                        }

                        HorizontalDivider()

                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        ) {
                            log.forEach {
                                Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- permissions

    private fun readImagesPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(readImagesPermission())
        if (Build.VERSION.SDK_INT >= 34) perms += visualUserSelected
        if (Build.VERSION.SDK_INT >= 29) perms += Manifest.permission.ACCESS_MEDIA_LOCATION

        logLine("Requesting: " + perms.joinToString { it.substringAfterLast(".") })
        logLine("  Android 14+: choose Allow all for the FULL tier, or")
        logLine("  Select photos for the CURATED tier. Test both - see README.")
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun refreshPermissionState() {
        val full = granted(readImagesPermission())
        val partial = Build.VERSION.SDK_INT >= 34 && granted(visualUserSelected)
        val mediaLocation =
            Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_MEDIA_LOCATION)

        val tier = when {
            full -> "FULL"
            partial -> "CURATED (partial)"
            else -> "NONE"
        }
        permState = tier + " - ACCESS_MEDIA_LOCATION=" + (if (mediaLocation) "granted" else "DENIED")
    }

    private fun launchPicker() {
        logLine("Photo Picker: pick a photo you KNOW is geotagged.")
        pickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // ------------------------------------------------------------------- scan

    private fun runScan(limit: Int?) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                scanLibrary(limit)
            } catch (t: Throwable) {
                logLine("SCAN FAILED: " + t.javaClass.simpleName + ": " + t.message)
            }
            progress = ""
            busy = false
        }
    }

    private suspend fun scanLibrary(limit: Int?) = withContext(Dispatchers.IO) {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        logLine("")
        logLine("--- scan start (" + (if (limit == null) "full library" else "first " + limit) + ") ---")

        // Step 1: enumerate. Cheap - one cursor over MediaStore, no file access.
        val ids = ArrayList<Long>()
        val enumStart = System.nanoTime()
        contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            MediaStore.Images.Media.DATE_TAKEN + " DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) ids.add(cursor.getLong(idCol))
        }
        val enumMs = (System.nanoTime() - enumStart) / 1_000_000
        val totalInLibrary = ids.size
        logLine("Enumerated " + totalInLibrary + " photos in " + enumMs + " ms")

        if (totalInLibrary == 0) {
            logLine("Nothing returned. Either permission was denied, or the Curated")
            logLine("grant covers no photos. Check the access tier at the top.")
            return@withContext
        }

        probeLocationColumns(collection)

        val work = if (limit != null && limit < ids.size) ids.subList(0, limit) else ids

        // Step 2: read EXIF per photo. Expensive - this is the number we actually need.
        var geotagged = 0
        var noGps = 0
        var errors = 0
        var firstError: String? = null
        val samples = ArrayList<String>()

        val readStart = System.nanoTime()
        work.forEachIndexed { i, id ->
            val base = ContentUris.withAppendedId(collection, id)
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.setRequireOriginal(base)
                else base
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    errors++
                } else {
                    stream.use {
                        val latLong = ExifInterface(it).latLong
                        if (latLong != null) {
                            geotagged++
                            if (samples.size < 5) {
                                samples.add(
                                    String.format(Locale.US, "%.5f, %.5f", latLong[0], latLong[1])
                                )
                            }
                        } else {
                            noGps++
                        }
                    }
                }
            } catch (t: Throwable) {
                errors++
                if (firstError == null) firstError = t.javaClass.simpleName + ": " + t.message
            }

            if ((i + 1) % 200 == 0) {
                val elapsedSec = (System.nanoTime() - readStart) / 1e9
                val rate = (i + 1) / elapsedSec
                val remaining = (work.size - i - 1) / rate
                progress = String.format(
                    Locale.US,
                    "%d / %d - %.0f photos/sec - ~%.0fs left - %d geotagged",
                    i + 1, work.size, rate, remaining, geotagged
                )
            }
        }
        val readMs = (System.nanoTime() - readStart) / 1_000_000
        val perPhotoMs = readMs.toDouble() / work.size
        val perSec = if (readMs > 0) work.size * 1000.0 / readMs else 0.0

        logLine("--- scan complete ---")
        logLine("scanned:      " + work.size + " of " + totalInLibrary + " in library")
        logLine("geotagged:    " + geotagged + "  (" + pct(geotagged, work.size) + ")")
        logLine("no GPS:       " + noGps)
        logLine("errors:       " + errors)
        if (firstError != null) logLine("first error:  " + firstError)
        logLine("enumerate:    " + enumMs + " ms")
        logLine(
            String.format(
                Locale.US, "exif read:    %d ms  (%.2f ms/photo, %.0f/sec)",
                readMs, perPhotoMs, perSec
            )
        )

        if (limit != null && totalInLibrary > work.size) {
            val projected = perPhotoMs * totalInLibrary / 1000.0
            logLine(
                String.format(
                    Locale.US, "PROJECTED full scan: %.1f s for %d photos",
                    projected, totalInLibrary
                )
            )
        }

        if (samples.isNotEmpty()) logLine("sample coords: " + samples.joinToString(" | "))

        // The single most important diagnostic in this spike.
        if (geotagged == 0 && errors == 0) {
            logLine("")
            logLine("*** ZERO geotagged photos and no errors. ***")
            logLine("This is the classic ACCESS_MEDIA_LOCATION failure: the permission")
            logLine("is missing or denied, so Android strips GPS silently and every")
            logLine("photo looks un-geotagged. Check the access tier line at the top")
            logLine("before concluding the library has no location data.")
        }

        logLine("")
        logLine("Q-008 read: under ~2s full scan  => MVP can skip the database")
        logLine("            tens of seconds      => persistence is required")
    }


    // Cheap-path probe. MediaStore carries LATITUDE/LONGITUDE columns, deprecated at API 29
    // and redacted for non-privileged apps. A system gallery reads a column here; we have to
    // open and parse each file. If these ever returned real values the whole scan cost would
    // collapse to one cursor pass - almost certainly they do not, but confirm rather than
    // assume, because the payoff would be large.
    private fun probeLocationColumns(collection: Uri) {
        try {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, "latitude", "longitude"),
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            )?.use { c ->
                val latCol = c.getColumnIndex("latitude")
                val lngCol = c.getColumnIndex("longitude")
                if (latCol < 0 || lngCol < 0) {
                    logLine("MediaStore lat/lng columns: not present")
                    return
                }
                var checked = 0
                var nonZero = 0
                while (c.moveToNext() && checked < 500) {
                    if (c.getDouble(latCol) != 0.0 || c.getDouble(lngCol) != 0.0) nonZero++
                    checked++
                }
                logLine("MediaStore lat/lng columns: " + nonZero + " of " + checked + " non-zero")
                if (nonZero > 0) {
                    logLine("  *** CHEAP PATH MAY EXIST - investigate before M1 ***")
                } else {
                    logLine("  redacted as expected - per-file EXIF read is required")
                }
            } ?: logLine("MediaStore lat/lng columns: query returned null")
        } catch (t: Throwable) {
            logLine("MediaStore lat/lng columns: unavailable (" + t.javaClass.simpleName + ")")
        }
    }

    // ----------------------------------------------------------------- picker

    private fun testPickerRedaction(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // No setRequireOriginal here - picker URIs do not support it.
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    logLine("Photo Picker: could not open stream")
                } else {
                    stream.use {
                        val latLong = ExifInterface(it).latLong
                        if (latLong == null) {
                            logLine("Photo Picker: latLong = null -> location REDACTED (expected)")
                            logLine("  Only meaningful if that photo really is geotagged.")
                        } else {
                            logLine("Photo Picker: latLong = " + latLong[0] + ", " + latLong[1])
                            logLine("  NOT redacted - surprising. Re-verify before relying on it.")
                        }
                    }
                }
            } catch (t: Throwable) {
                logLine("Photo Picker read failed: " + t.javaClass.simpleName + ": " + t.message)
            }
        }
    }

    // ------------------------------------------------------------------- util

    private fun pct(part: Int, whole: Int): String =
        if (whole == 0) "0%" else String.format(Locale.US, "%.1f%%", part * 100.0 / whole)

    private fun logLine(line: String) {
        runOnUiThread { log.add(line) }
    }
}
