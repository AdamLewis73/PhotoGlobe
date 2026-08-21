# M0 Feasibility Spike

Throwaway code. Delete this whole folder once the questions below are answered and the
results are written into `docs/PROGRESS.md`.

## What it answers

| | Question | How to read the result |
|---|---|---|
| **Q-001** | Can we read GPS from the photo library at all? | `geotagged` count is greater than zero under the FULL tier |
| **Q-001b** | Does the Android 14+ **Curated** (partial) grant also return unredacted GPS? | Re-run under `Select photos` and see whether `geotagged` is still non-zero |
| **Q-001c** | Does the Photo Picker redact location, as documented? | Button 4 reports `latLong = null` for a photo you know is geotagged |
| **Q-008** | Does the MVP need a database? | Full-scan time: under ~2s means no; tens of seconds means yes |

It also produces the library size and geotagged percentage, which size everything in
`docs/DESIGN.md` §5.

## Why this isn't a ready-to-build Gradle project

A buildable Android project needs a Gradle wrapper JAR, which is a binary, plus an AGP
version matching whatever Android Studio you have installed. Shipping a half-working
project tree is the fastest way to lose an hour to version mismatches. Creating the
project locally and pasting in two files is more robust, and takes about five minutes.

## Setup

1. **Android Studio → New Project → Empty Activity** (the Compose one, which is the
   default). Name it `PhotoGlobeSpike`, package `com.photoglobe.spike`, language Kotlin,
   minimum SDK 26 or higher.

2. **Add one dependency.** In `app/build.gradle.kts`, inside `dependencies { }`:

   ```kotlin
   implementation("androidx.exifinterface:exifinterface:1.3.7")
   ```

   Then click **Sync Now**. Everything else the spike uses ships with the template.

3. **Replace `app/src/main/java/com/photoglobe/spike/MainActivity.kt`** with
   `MainActivity.kt` from this folder.

4. **Merge the permissions** from `AndroidManifest.xml` in this folder into
   `app/src/main/AndroidManifest.xml`. Only the four `<uses-permission>` lines matter —
   keep your generated `<application>` block, since it references the theme and icon the
   template created. If you replace the file wholesale, change
   `@style/Theme.PhotoGlobeSpike` to whatever theme name the template generated.

5. **Run on a real device**, not the emulator. An emulator has no real photo library and
   its handful of sample images will tell you nothing about scan time.

## Running it — do the tiers in this order

**Order matters.** Once you grant "Allow all", Android stops showing the three-option
dialog, so you cannot test the Curated tier afterwards without resetting. Do the partial
test first.

### Pass 1 — Curated tier (Android 14+)

1. Tap **1 · Request media access**
2. In the dialog choose **Select photos**, and pick perhaps 20 photos you know are
   geotagged
3. Confirm the header reads `CURATED (partial) - ACCESS_MEDIA_LOCATION=granted`
4. Tap **3 · Full library scan** — with partial access this only covers what you selected
5. **Record whether `geotagged` is non-zero.** This is the answer to Q-001b, and it decides
   whether the app can survive Play refusing broad access

### Pass 2 — Full tier

1. **Settings → Apps → PhotoGlobe Spike → Storage → Clear data.** Without this the dialog
   will not reappear
2. Tap **1 · Request media access**, choose **Allow all**
3. Confirm the header reads `FULL - ACCESS_MEDIA_LOCATION=granted`
4. Tap **2 · Quick scan (first 2000)** first — it finishes in seconds and prints a
   projected full-scan time. If the projection is enormous, you already have your Q-008
   answer and can skip the full run
5. Tap **3 · Full library scan** for the real number

### Pass 3 — Photo Picker

Tap **4 · Test Photo Picker redaction** and choose a photo you are *certain* is geotagged
— ideally one whose coordinates appeared in the scan samples. A `null` result on a photo
that has no GPS in the first place proves nothing.

## The failure to watch for

If the scan reports **zero geotagged and zero errors**, that is almost certainly not a
library without location data. It is `ACCESS_MEDIA_LOCATION` missing or denied: Android
strips the GPS tags silently and returns a perfectly valid photo with no coordinates. The
spike prints a warning when it sees this pattern. Check the access tier line before
concluding anything.

## What to record in docs/PROGRESS.md

Copy this template and fill it in. These numbers close Q-001 and Q-008 and size the
performance work in `docs/DESIGN.md` §5.

```
M0 results - <date>, <device>, Android <version> (API <n>)

FULL tier
  total photos in library:   ____
  geotagged:                 ____  ( ___ %)
  errors:                    ____
  enumerate:                 ____ ms
  full exif scan:            ____ ms   ( ____ ms/photo, ____ /sec )

CURATED tier (partial grant)
  geotagged returned?        yes / no
  first error, if any:       ____________________

PHOTO PICKER
  latLong on a known-geotagged photo:   null / <coords>

Conclusions
  Q-001  -> D-0__ :
  Q-008  -> D-0__ :  database needed? yes / no
```

## Then

- Turn the results into decisions in `docs/DECISIONS.md` and delete Q-001 and Q-008 from
  `docs/OPEN-QUESTIONS.md`
- Settle the map SDK (D-012), weighted by D-015
- Delete this folder

---

## Note on what "scan time" means (D-020)

The reference implementation has no scan delay because it queries an index it built
incrementally over the life of the device — and, being a system app, probably reads
location straight from a MediaStore column instead of parsing EXIF per file.

PhotoGlobe pays that cost **once**, on first run. From run two onward, incremental sync
touches only new photos and the app is exactly as instant. So the number this spike
produces sizes two things and nothing else:

- whether the MVP needs persistence at all (Q-008)
- how the first-run experience has to be designed (D-021: newest-first, progressive)

The spike also probes MediaStore's deprecated LATITUDE/LONGITUDE columns (D-022). If they
return real values, the cheap path exists and the scan collapses to one cursor pass.
Expect `redacted as expected`; record it either way.
