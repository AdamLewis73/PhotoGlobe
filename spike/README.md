# M0 Feasibility Spike

A throwaway diagnostic app. Delete this whole folder once the questions below are answered
and the results are written into `docs/PROGRESS.md`.

**This is a complete, buildable Android Studio project.** It was compiled and verified on
this machine on 2026-08-08 — AGP 8.11.0, Kotlin 2.1.20, Gradle 8.14.3, compileSdk 36.
Nothing needs to be created by hand.

## What it does, in full

- lists photo IDs from MediaStore (`contentResolver.query`)
- opens each photo to read its EXIF header (`contentResolver.openInputStream`)
- counts how many carry GPS coordinates, and times how long that took
- prints the results on screen

## What it does not do

- **No network of any kind.** The manifest declares no `INTERNET` permission, so Android
  blocks network access at the OS level. This is enforced by the operating system, not by
  the code.
- **No writing.** No files, no database, no preferences. Read-only throughout.
- **No modifying, moving, copying or deleting** any photo.
- **Nothing persists.** Close the app and every result is gone.

### Verify that yourself

```
grep -o 'android.permission.[A-Z_]*' app/src/main/AndroidManifest.xml
grep -nE 'INTERNET|http|Socket|\.write|delete\(|insert\(|update\(' app/src/main/java/com/photoglobe/spike/MainActivity.kt
grep -n 'contentResolver\.' app/src/main/java/com/photoglobe/spike/MainActivity.kt
```

Four read permissions, no network calls, no writes, and exactly four `contentResolver`
calls — two `query`, two `openInputStream`.

The compiled APK reports the same four permissions, plus one AndroidX adds automatically
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) — a permission the app defines for itself so
other apps cannot reach its internal broadcast receivers. It grants no device access.

There are three dependencies: `core-ktx`, `activity-ktx`, `exifinterface`. No Compose, no
Material libraries, no networking library, no analytics.

## What it answers

| | Question | How to read the result |
|---|---|---|
| **Q-001** | Can we read GPS from the photo library at all? | `geotagged` is greater than zero under the FULL tier |
| **Q-001b** | Does the Android 14+ **Curated** (partial) grant also return unredacted GPS? | Re-run under `Select photos` and see whether `geotagged` is still non-zero |
| **Q-001c** | Does the Photo Picker redact location, as documented? | Button 4 reports `latLong = null` for a photo you know is geotagged |
| **Q-008** | Does the MVP need a database? | Full-scan time: under ~2s means no, tens of seconds means yes |
| **D-022** | Does the cheap path exist? | `MediaStore lat/lng columns` line — expect `redacted as expected` |

## Running it

**One-time phone setup:**

1. Settings → **About phone** → **Software information**
2. Tap **Build number** seven times — it counts down
3. Back out; **Developer options** now appears at the bottom of Settings
4. Developer options → turn on **USB debugging**

**On the PC:**

5. Android Studio → **Open** → select this `spike` folder (not the repo root)
6. Wait for the Gradle sync to finish
7. Plug the phone in with a USB cable
8. On the phone: *"Allow USB debugging?"* → **Allow**
9. Your device appears in the dropdown at the top right → click the green ▶ **Run**

It installs as "PhotoGlobe Spike" in your app drawer. Uninstall it like any other app:
long-press the icon → Uninstall.

No cable? Developer options → **Wireless debugging** → pair with a code. Same result.

## Test order matters

Once you grant "Allow all", Android stops offering the three-option dialog, so the Curated
tier cannot be tested afterwards without resetting. **Do the partial test first.**

### Pass 1 — Curated tier (Android 14+)

1. Tap **1 · Request media access**
2. Choose **Select photos**, pick ~20 photos you know are geotagged
3. Header should read `Access tier: CURATED (partial)   ACCESS_MEDIA_LOCATION: granted`
4. Tap **3 · Full library scan** — with partial access this covers only what you selected
5. **Record whether `geotagged` is non-zero.** This answers Q-001b and decides whether the
   app can survive Play refusing broad access

### Pass 2 — Full tier

1. **Settings → Apps → PhotoGlobe Spike → Storage → Clear data.** Without this the dialog
   will not reappear
2. Tap **1**, choose **Allow all**. Header should read `FULL`
3. Tap **2 · Quick scan (first 2000)** — finishes in seconds and prints a projected
   full-scan time. If the projection is huge you already have your Q-008 answer
4. Tap **3 · Full library scan** for the real number

### Pass 3 — Photo Picker

Tap **4** and choose a photo you are *certain* is geotagged, ideally one whose coordinates
showed up in the scan samples. A `null` on a photo with no GPS proves nothing.

## The failure to watch for

**Zero geotagged and zero errors is almost never a library without location data.** It is
`ACCESS_MEDIA_LOCATION` missing or denied: Android strips the GPS tags silently and hands
back a perfectly valid photo with no coordinates. The app prints an explicit warning when
it sees that pattern. Check the access tier line before concluding anything.

## Recording results

The log is selectable — long-press to copy it out. Paste the numbers into
`docs/PROGRESS.md` under an `M0 results` heading, then:

- turn them into decisions in `docs/DECISIONS.md`, and delete Q-001 and Q-008 from
  `docs/OPEN-QUESTIONS.md`
- settle the map SDK (D-012), weighted by D-015
- delete this folder

## Note on scan time (D-020)

The reference implementation (Samsung Gallery) has no scan delay because it queries an
index built incrementally over the life of the device, and — being a system app — likely
reads location from a MediaStore column instead of parsing EXIF per file.

PhotoGlobe pays that cost **once**, on first run. From run two onward, incremental sync
touches only new photos and the app is equally instant. So this number sizes exactly two
things: whether the MVP needs persistence (Q-008), and how the first run must be designed
(D-021: newest-first, progressive).
