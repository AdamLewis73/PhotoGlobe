package com.photoglobe.spike

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.util.Locale

/**
 * PhotoGlobe M0 feasibility spike. Throwaway diagnostic - delete once answered.
 *
 * WHAT THIS DOES, in full:
 *   - lists photo IDs from MediaStore          (contentResolver.query)
 *   - opens each photo to read its EXIF header (contentResolver.openInputStream)
 *   - counts how many have GPS coordinates, and times how long that took
 *   - prints the results on screen
 *
 * WHAT THIS DOES NOT DO:
 *   - no network of any kind. The manifest declares no INTERNET permission,
 *     so Android blocks it at the OS level.
 *   - no writing. No files, no database, no preferences. Read-only throughout.
 *   - no modifying, moving, copying or deleting any photo.
 *   - nothing persists. Close the app and every result is gone.
 *
 * Answers Q-001 (can we read GPS at all, and does the Android 14+ partial grant
 * return unredacted coordinates) and Q-008 (how long a full scan takes, and
 * therefore whether the MVP needs a database). See README.md.
 */
class MainActivity : ComponentActivity() {

    // String literal rather than the Manifest constant so this compiles on any
    // compileSdk - READ_MEDIA_VISUAL_USER_SELECTED only exists on 34+.
    private val visualUserSelected = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    private lateinit var tierView: TextView
    private lateinit var progressView: TextView
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    private val logText = StringBuilder()
    private var busy = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (perm, isGranted) ->
            logLine("  " + perm.substringAfterLast(".") + " = " + if (isGranted) "GRANTED" else "denied")
        }
        refreshTier()
    }

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) logLine("Photo Picker: cancelled") else testPickerRedaction(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshTier()
        logLine(
            "Device: " + Build.MANUFACTURER + " " + Build.MODEL +
                ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
        )
        logLine("Read-only. No network permission. Nothing is written or stored.")
    }

    private fun buildUi(): View {
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "PhotoGlobe M0 spike"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title)

        tierView = TextView(this)
        tierView.textSize = 14f
        root.addView(tierView)

        root.addView(button("1  -  Request media access") { requestPermissions() })
        root.addView(button("2  -  Quick scan (first 2000)") { runScan(2000) })
        root.addView(button("3  -  Full library scan") { runScan(null) })
        root.addView(button("4  -  Test Photo Picker redaction") { launchPicker() })

        progressView = TextView(this)
        progressView.textSize = 14f
        root.addView(progressView)

        logView = TextView(this)
        logView.typeface = Typeface.MONOSPACE
        logView.textSize = 11f
        logView.setTextIsSelectable(true)   // long-press to copy the results out

        scrollView = ScrollView(this)
        scrollView.addView(logView)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
        )

        return root
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { if (!busy) onClick() }
        return b
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

        logLine("")
        logLine("Requesting: " + perms.joinToString { it.substringAfterLast(".") })
        logLine("  Android 14+: choose 'Allow all' for the FULL tier, or")
        logLine("  'Select photos' for the CURATED tier. Test CURATED first - see README.")
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun refreshTier() {
        val full = granted(readImagesPermission())
        val partial = Build.VERSION.SDK_INT >= 34 && granted(visualUserSelected)
        val mediaLocation =
            Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_MEDIA_LOCATION)

        val tier = when {
            full -> "FULL"
            partial -> "CURATED (partial)"
            else -> "NONE"
        }
        tierView.text =
            "Access tier: " + tier + "   ACCESS_MEDIA_LOCATION: " +
                (if (mediaLocation) "granted" else "DENIED")
    }

    private fun launchPicker() {
        logLine("")
        logLine("Photo Picker: pick a photo you KNOW is geotagged.")
        pickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // ------------------------------------------------------------------- scan

    private fun runScan(limit: Int?) {
        if (busy) return
        busy = true
        // Plain background thread - the scan must not run on the UI thread.
        Thread {
            try {
                scanLibrary(limit)
            } catch (t: Throwable) {
                logLine("SCAN FAILED: " + t.javaClass.simpleName + ": " + t.message)
            }
            runOnUiThread {
                progressView.text = ""
                busy = false
            }
        }.start()
    }

    private fun scanLibrary(limit: Int?) {
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
            return
        }

        probeLocationColumns(collection)

        val work = if (limit != null && limit < ids.size) ids.subList(0, limit) else ids

        // Step 2: read EXIF per photo. Expensive - this is the number we need.
        var geotagged = 0
        var noGps = 0
        var errors = 0
        var firstError: String? = null
        val samples = ArrayList<String>()

        val readStart = System.nanoTime()
        for (i in work.indices) {
            val base = ContentUris.withAppendedId(collection, work[i])
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
                val line = String.format(
                    Locale.US,
                    "%d / %d   %.0f photos/sec   ~%.0fs left   %d geotagged",
                    i + 1, work.size, rate, remaining, geotagged
                )
                runOnUiThread { progressView.text = line }
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
            logLine(
                String.format(
                    Locale.US, "PROJECTED full scan: %.1f s for %d photos",
                    perPhotoMs * totalInLibrary / 1000.0, totalInLibrary
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
            logLine("photo looks un-geotagged. Check the access tier at the top before")
            logLine("concluding the library has no location data.")
        }

        logLine("")
        logLine("Q-008 read: under ~2s full scan  => MVP can skip the database")
        logLine("            tens of seconds      => persistence is required")
    }

    // Cheap-path probe (D-022). MediaStore carries latitude/longitude columns,
    // deprecated at API 29 and redacted for non-privileged apps. A system gallery
    // reads a column here; we have to open and parse each file. If these ever
    // returned real values the whole scan would collapse to one cursor pass.
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
                if (nonZero > 0) logLine("  *** CHEAP PATH MAY EXIST - investigate before M1 ***")
                else logLine("  redacted as expected - per-file EXIF read required")
            } ?: logLine("MediaStore lat/lng columns: query returned null")
        } catch (t: Throwable) {
            logLine("MediaStore lat/lng columns: unavailable (" + t.javaClass.simpleName + ")")
        }
    }

    // ----------------------------------------------------------------- picker

    private fun testPickerRedaction(uri: Uri) {
        Thread {
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
        }.start()
    }

    // ------------------------------------------------------------------- util

    private fun pct(part: Int, whole: Int): String =
        if (whole == 0) "0%" else String.format(Locale.US, "%.1f%%", part * 100.0 / whole)

    private fun logLine(line: String) {
        runOnUiThread {
            logText.append(line).append('\n')
            logView.text = logText.toString()
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
