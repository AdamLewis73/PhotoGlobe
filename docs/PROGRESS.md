# Progress Log

Append-only. Newest entries at the bottom. One entry per working session.
Keep entries short and factual: what happened, what changed on disk, what's next.

---

## 2026-08-07 — Initial ideation

**What happened.** Owner described the app concept: a Google-Maps-style map where
geotagged photos appear as flags, clustering when zoomed out and branching apart when
zoomed in; a stats screen for countries visited and photos taken; tapping a flag to see
photos from that region.

First pass over the technical design surfaced three things the original concept had not
accounted for:

- The Android Photo Picker redacts location metadata, so the frictionless no-permission
  path cannot support the core feature. Reading real GPS needs MediaStore access plus the
  separate `ACCESS_MEDIA_LOCATION` permission and `setRequireOriginal()` — and missing
  that second permission fails silently, looking exactly like "none of your photos are
  geotagged."
- There is no reliable background "photo was just taken" signal on modern Android.
- Reverse geocoding for the stats screen should be bundled and offline rather than
  API-backed.

**Changed on disk.** Created `PLAN.md` (later moved to `docs/DESIGN.md`).

**Next.** Owner to respond on map SDK, distribution, and direction.

---

## 2026-08-08 — Scope, constraints, and documentation scaffold

**Decisions made.** D-003 through D-011 logged, plus D-012 deferred. The consequential ones:

- **Zero recurring cost is a hard constraint** (D-003), not a preference. It vetoes
  designs rather than trading off against them.
- **Play Store, but personal app** (D-004). Compliance work is in; growth, marketing and
  monetization are out. This retired the market-positioning and pricing sections written
  the previous session.
- **Non-geotagged photos are ignored by default; locations are never invented silently**
  (D-009). Inference proposes, the user decides.
- Map SDK deferred until M0 produces data (D-012).

**Corrections to the previous session's thinking.** Timestamp interpolation was
overweighted — it was called the app's best feature on an unexamined assumption that the
owner shoots with a standalone camera. Logged as Q-002; if the owner is phone-only, M3
shrinks substantially.

**Explained for the owner** (new to Android and spatial data): what a mirrorless camera is
and why it matters here; why 40,000 photos breaks a map, decomposed into the memory limit,
the 16 ms frame budget, and the database scan, with the fix for each. Both are written up
in `docs/DESIGN.md` §5 and `docs/GLOSSARY.md`.

**Cost analysis.** Everything fits the zero-cost constraint except map tiles. Google's
mobile SDK doesn't bill for map loads but requires a billing account with a card;
restricting the API key to the app's package + signing cert, enabling only Maps SDK for
Android, and setting a $0 budget alert bounds that to effectively zero risk. MapLibre
avoids the card at the price of self-supplied tiles and hand-written clustering. Also
noted: the Places API bills per request, so any place search must use bundled data.

**Changed on disk.** Created the full documentation scaffold at owner's request —
`CLAUDE.md`, `docs/DESIGN.md` (revised from `PLAN.md`, which was removed), `DECISIONS.md`,
`ROADMAP.md`, `PROGRESS.md`, `OPEN-QUESTIONS.md`, `GLOSSARY.md`.

**Next.** Answer Q-002 (standalone camera or phone-only). Then M0 spike — it is the gate
on everything else. Project is not yet a git repository; should be initialized before code.

---

## 2026-08-08 (later) — MVP identified and scope narrowed

**What happened.** Owner found the exact target behaviour already shipping on their Galaxy
S25+: in Samsung Gallery, photo details then tapping the location opens a map with numbered
markers that divide when zooming in and coalesce when zooming out, counts updating live.
That is the app they want, as the bare bones.

**This was not a restart.** The behaviour described is standard marker clustering, which
was already M1-M2 in the previous roadmap. What actually changed is the MVP boundary and
the marker design - and both changes make the project smaller.

**Decisions made.** D-013 (MVP = clustered count map; everything else post-MVP; the thesis
is "front door, not buried detail screen"; cold-start-to-map is the headline metric),
D-014 (MVP markers are count badges, not photo thumbnails), D-015 (do not hand-write
clustering - the SDK does it).

**Two technical consequences worth remembering.**

1. The 2.6 GB memory figure in DESIGN.md section 5 assumed a unique thumbnail per marker.
   Count badges have ~15 distinct appearances, generated once and shared across thousands
   of markers, so that limit collapses to kilobytes for the whole MVP. It returns in M5
   when thumbnails go into markers.
2. D-015 cuts across the deferred map SDK decision (D-012): clustering is now *the* MVP
   rather than one feature among many, so choosing MapLibre - which means hand-writing the
   clustering layer - costs materially more than it did yesterday.

**Correction recorded.** Samsung Gallery is a preinstalled system app with privileged
access to media location. It doing this effortlessly implies nothing about a third-party
Play app, which still has to walk the ACCESS_MEDIA_LOCATION path. M0 still gates everything.

**New question with real architectural weight.** Q-008: the MVP may not need a database at
all. 40,000 rows of (id, lat, lng, date) is under 2 MB in memory, so if a full library scan
is fast the persistence layer can be skipped entirely in v1. The scan timing already
scheduled in M0 answers this - do not build persistence before that number exists.

**Changed on disk.** ROADMAP.md restructured around the new MVP (M1 is now the product; old
M1/M2 merged and narrowed; everything else pushed back). DESIGN.md gained section 0
(product thesis and MVP) plus scope notes on sections 5 and 12. D-013 to D-015 and Q-008 to
Q-009 appended. CLAUDE.md current-state updated. GLOSSARY.md gained four terms.

**Next.** Q-002 (standalone camera or phone-only) is still unanswered but now only affects
M4, so it is no longer urgent. git init still pending. The immediate action remains the M0
spike, which now answers three things at once: permissions, scan timing, and whether the
MVP needs a database.

---

## 2026-08-08 (later still) — Reference implementation examined, MVP boundary adjusted

**What happened.** Owner inspected the Samsung Gallery map directly and reported three
behaviours. Each produced a decision.

1. **Tap a cluster** opens a bottom sheet of thumbnails for the photos in it; tapping a
   thumbnail opens it full screen. Matches the design already in DESIGN.md section 12, and
   moved *into* the MVP as D-016 — without it the app shows that photos exist somewhere but
   not what they are.
2. **Home is not suppressed** in the reference. Owner wants exclusion available but opt-in.
   Logged as D-017, which refines D-010: opt-in, off by default, stays in M5.
3. **At world zoom** counts coalesce to regional bubbles — 3194 Texas, 8k Japan, 2k Korea,
   44 Colorado, 22 California, 30 Florida.

**Correction to D-014.** The claim that a count badge has only ~15 distinct appearances,
so a handful of bitmaps could be cached and shared, is wrong — the reference shows *exact*
counts, which are unbounded. D-018 records the correction. The conclusion survives on
different reasoning: only visible clusters become markers, peak is a couple of hundred, so
worst case is roughly 13 MB. Cache by number with LRU eviction; do not assume a fixed set.
DESIGN.md section 5's scope note was rewritten in place so nobody reads the wrong model.

**Useful finding: world-zoom regional grouping is free (D-019).** The regional counts look
like place awareness but almost certainly are not — at maximum zoom-out a US state is a few
dozen pixels wide, so distance clustering produces that grouping by geometry alone. Offline
geocoding stays in M3 for stats that *name* places; M1 needs none of it.

**Library size data point.** The counts the owner listed already exceed 13,000. Realistic
estimate is 20k-50k geotagged photos, which makes the DESIGN.md section 5 performance work
likely relevant rather than hypothetical. M0's scan timing now matters more than previously
assumed; noted in the M0 checklist.

**Changed on disk.** D-016 to D-019 appended; D-010 marked refined by D-017; Q-009 resolved
and removed. ROADMAP M1 gained tap-through and an explicit not-in-M1 list; M2 rescoped.
DESIGN.md section 0 MVP definition updated, section 5 scope note rewritten, section 12 note
rewritten.

**Next.** Unchanged: the M0 spike. Q-002 (camera or phone-only) still open but only affects
M4. git init still pending.

---

## 2026-08-08 (end of session) — Repo initialized, M0 spike written

**Changed on disk.**
- `git init`, `.gitignore`, first commit containing all planning docs.
- `spike/` created: `MainActivity.kt` (364 lines), `AndroidManifest.xml`, `README.md`.

**What the spike does.** Single throwaway Activity, four buttons: request media access,
quick scan of the first 2000 photos with a projected full-scan time, full library scan, and
a Photo Picker redaction test. It reports total photos, geotagged count and percentage,
error count, enumerate time, EXIF read time, ms/photo, photos/sec, and sample coordinates.

It enumerates MediaStore in one cursor pass (cheap), then per photo calls
`MediaStore.setRequireOriginal()` and reads `ExifInterface.latLong` (expensive - this is
the number Q-008 needs).

**Deliberate design choices worth remembering.**
- It special-cases the zero-geotagged-and-zero-errors result and prints an explicit
  ACCESS_MEDIA_LOCATION warning, because that failure is silent and looks exactly like a
  library with no location data.
- The quick-scan button exists so a large library gives a projected time in seconds rather
  than requiring someone to sit through a full run to discover it is too slow.
- Not shipped as a buildable Gradle project on purpose: the wrapper JAR is a binary and AGP
  versions drift, so a half-working tree would likely cost an hour. README instead gives a
  five-minute path - new Empty Activity project, one dependency, paste two files.
- README specifies testing the **Curated tier first**, because granting Allow all stops the
  three-option dialog from reappearing without clearing app data.

**Next session.** Run the spike on the owner's S25+, fill in the results template in
`spike/README.md`, convert Q-001 and Q-008 into decisions, settle D-012 (map SDK), delete
`spike/`, then start M1.

---

## 2026-08-08 (final) — Spike rebuilt as a real project, compiled and verified

**Context.** Owner was uncomfortable running an unfamiliar app on their phone, and then
asked how one would even run it. Both concerns were reasonable and reshaped the deliverable.

**Environment discovered on this machine** (recorded so future sessions do not re-derive it):
- Android Studio at `E:\Android Studio`, config at `%LOCALAPPDATA%\Google\AndroidStudio2025.2.2`
- Bundled JDK (JBR) 21.0.8 at `E:\Android Studio\jbr`
- SDK at `%LOCALAPPDATA%\Android\Sdk` — only platform **android-36**, build-tools 35/36/36.1
- Gradle cache holds 8.14.3, AGP up to 8.11.0, Kotlin 2.1.20, coroutines 1.9.0,
  exifinterface 1.4.1, core-ktx 1.16.0, activity 1.8.0
- **No androidx.compose artifacts cached at all** - Compose has never been built here

**Consequence: the spike was rewritten without Compose.** Plain Android Views constructed
in Kotlin, three dependencies (core-ktx, activity-ktx, exifinterface), all already cached.
This avoided guessing Compose BOM versions that had never been resolved on this machine,
and halved the amount of code the owner has to read before trusting it.

**Delivered as a complete Gradle project**, superseding the earlier
create-it-yourself-and-paste-two-files approach. `gradle wrapper` was generated using the
cached 8.14.3 distribution, and **`./gradlew assembleDebug` succeeds** - 2.5 MB debug APK.
Owner now opens `spike/` in Android Studio and presses Run.

**Verification recorded for the trust question.** The compiled APK declares exactly the four
read permissions and **no INTERNET**, so Android blocks network access at the OS level
regardless of code. Source audit shows no network calls, no writes, and four
`contentResolver` calls total (two `query`, two `openInputStream`). AndroidX auto-adds
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which the app defines for itself to protect its
own broadcast receivers - it grants no device access. README documents the grep commands so
this is verifiable rather than asserted.

**Also corrected.** Earlier claim that shipping a buildable Gradle project was impractical
because the wrapper JAR is a binary. It was practical - the cached Gradle distribution
generates the wrapper locally.

**Next.** Unchanged: run it, or decline and fold Q-001/Q-008 into M1. Owner has not yet
decided. Q-002 still open, affects M4 only.

---

## 2026-08-08 — Spike security audit and hardening

**Why.** Owner has only one phone and it is their daily driver. Requested a thorough
review before installing. Full line-by-line audit performed, not a keyword grep.

**Data-safety audit: clean.** Recorded so it does not need repeating:
- 4 `contentResolver` calls total - 2 `query`, 2 `openInputStream`. All reads.
- No write/delete/modify API anywhere. `ExifInterface.saveAttributes()` is never called
  and could not work regardless: the instance is constructed from an `InputStream`, which
  has no write path.
- No reflection, no `Runtime.exec`, no native loading.
- Merged manifest (after library contributions) declares only the four read permissions.
  No INTERNET. No boot receivers, wake locks, foreground services or exact alarms - the
  app does nothing at all when closed.
- Components: MainActivity plus two stock AndroidX ones (InitializationProvider,
  ProfileInstallReceiver).
- Full dependency tree is AndroidX + Kotlin stdlib only. No networking library, no
  analytics, no ads.

**Four real defects found and fixed** - none destructive, all missed on first write:
1. Rotation destroyed and recreated the Activity mid-scan, leaving the worker thread
   writing to discarded views so the log froze. Fixed with
   `android:configChanges="orientation|screenSize|keyboardHidden"`.
2. No way to stop a running scan. Added a STOP button with cooperative cancellation; it
   deliberately does not check `busy` so the brake always responds.
3. The scan kept running after leaving the app. `onDestroy` now sets `cancelRequested`.
4. `busy` was shared across threads without `@Volatile`. Both flags are now volatile.

Also added `FLAG_KEEP_SCREEN_ON` during a scan so screen-off throttling cannot corrupt the
timing measurement, and partial-result reporting so a stopped scan still prints usable
numbers.

**Privacy note added to the app itself.** The `sample coords:` line prints real
coordinates from the owner's photos. Harmless on-device, but it must be stripped before
the log is shared anywhere. The app now prints that warning immediately above the line.

**Rebuilt and re-audited after the changes** - `assembleDebug` succeeds, APK still 2.5 MB,
permissions unchanged.

**Next.** Unchanged: owner installs via `adb install` (device not yet connected) or
Android Studio, then runs the three passes in `spike/README.md` - Curated tier first.

---

## 2026-08-08 — M0 RUN AND COMPLETE (emulator)

**How it was run.** Owner declined to enable USB debugging on their only phone, which on
Samsung One UI requires disabling Auto Blocker. Reasonable, and unnecessary: 22 real photos
were copied off the phone by ordinary file transfer and tested against an Android 16
emulator that was already installed. The phone was never connected and nothing on it
changed. See D-028 for what this does and does not affect.

**Ground truth established first.** A pure-Python EXIF parser (no dependencies) checked the
22 files before anything ran: all 22 carried GPS. Since 22/22 gives no way to distinguish
"reads GPS correctly" from "claims everything has GPS", a 23rd file was created - a copy
with its APP1/EXIF segments stripped - as a negative control. Expected answer going in:
22 with GPS, 1 without.

**Results.**

| Question | Answer | Evidence |
|---|---|---|
| Q-001 GPS readable? | **YES** | 22/25 geotagged, 0 errors - exact ground-truth match |
| Q-001b Curated grant? | **YES** | 3/3 on known-good photos, READ_MEDIA_IMAGES denied |
| Q-001c Picker redacts? | **YES** | latLong null on a confirmed-geotagged photo |
| D-022 cheap path? | **NO** | MediaStore lat/lng: 0 non-zero of 25 |
| Q-008 need a database? | **YES** | 4.16 ms/photo, so 40k photos is ~2.8 min |

Logged as D-023 through D-028. Q-001 and Q-008 removed from open questions. Q-003 (videos
in or out) escalated to **blocking** - a schema now definitely exists. Q-010 opened for
re-measuring timing on real hardware.

**The headline.** D-024 is the one that matters. The Curated tier returns unredacted GPS,
so PhotoGlobe does not depend on Google approving broad library access. That was the
largest external risk in the project and it is gone. The Curated-primary strategy in
DESIGN.md §10 stands.

**Two obstacles worth remembering.**
1. `adb push` creates MediaStore rows with `is_pending=1` owned by `com.android.shell`,
   invisible to other apps *and* to the photo picker - which showed "No photos yet" despite
   23 rows existing, and made the app enumerate only 2 stock images. Fixed with
   `content call --uri content://media/external --method scan_volume --arg external_primary`.
   Bulk-updating is_pending on the collection URI is rejected outright.
2. Launching the emulator from a shell that then exits kills it. Launch detached.

**Privacy handling.** The spike prints real coordinates as samples. Those were displayed
during the run and are deliberately **not** recorded in any project document. All test
photos, screenshots and the app itself were deleted from the emulator afterwards - verified
0 image rows remaining - and the working copies removed from scratch space. The originals
remain only in `E:\PhotoGlobe-testphotos`, outside the repo, for the owner to delete.

**Next.** M0.5: settle D-012 (map SDK, weighted by D-015), answer Q-003 (videos), pick a
minimum SDK (Q-006), register the Play developer account. Then M1. `spike/` can be deleted.

---

## 2026-08-08 — M0.5 complete: MapLibre chosen, M1 unblocked

**Decisions.** D-031 (Google Maps) **superseded by D-036 (MapLibre)** after the owner tried
a live clustering demo. Also D-032 videos schema-ready, D-033 minSdk 33, D-037 CARTO
Positron as the default key-free tile style.

**Three corrections were needed along the way, all in the same area.** Worth reading
together, because the pattern is that claims about renderer behaviour were recorded without
being verified:
- **D-030** corrected D-015: MapLibre does have built-in GeoJSON clustering; it does not
  need hand-writing.
- **D-034** corrected D-031: a GCP budget alert notifies, it does not cap. Google Cloud has
  no hard spending limit, so "risk controls" were prevention (only enabling a non-billing
  API, restricting the key twice) plus detection - not a guarantee.
- **D-035** corrected D-016 and DESIGN 12: Google's `DefaultClusterRenderer` **does** animate
  cluster split/merge by default. The claim that it was custom renderer work was wrong, and
  that removed an M5 task on the Google path while identifying the real functional gap on
  the MapLibre path.

D-035 was surfaced by the owner noticing the demo "lurched" on zoom. That lurch is exactly
the un-animated cluster recomputation.

**Owner's choice and its reasoning.** MapLibre, for style customization and because it
satisfies hard rule 1 absolutely rather than nearly. Accepted cost: markers pop between zoom
levels in M1; hand-built tweening is M5 polish.

**A claim that was corrected in the owner's favour.** D-012 and later messages called a map
SDK switch "expensive." With Room as the source of truth and scan/geohash/cluster-tier work
all SDK-independent, a swap touches only the map composable. That is what makes "MapLibre
for now" defensible rather than optimistic, and it is recorded in D-036.

**Privacy claim tightened.** D-037 notes that tile hosts are third-party servers and see the
viewport. True of Google Maps equally. Photo data never leaves the device; do not overstate
it to "nothing whatsoever leaves the device."

**Next.** M1. Compose shell launching straight to a map, Room schema with `mediaType`,
library scan, MapLibre GeoJSON clustering with count badges, tap-through to a photo grid.
Testable end-to-end on the emulator against the 22-photo fixture, no phone involved.

---

## 2026-08-08 — M1 scaffold built and verified running

**Built, and confirmed working on the emulator against the 22-photo fixture** - not merely
compiling. Screenshot evidence: the map rendered, the scan reported **"22 of 23 photos have
a location"** (exactly the M0 ground truth), and a **cluster badge reading 9** appeared over
Texas. The full MVP pipeline is proven end to end: MediaStore scan → EXIF → Room → geohash
→ GeoJSON → MapLibre clustering → count badge.

**What exists now.**
- Gradle project at repo root: AGP 8.11.0, Kotlin 2.1.20, Gradle 8.14.3, compileSdk 36,
  minSdk 33 (D-033)
- `data/` - `PhotoEntity` (with `mediaType` per D-032), `ScanStateEntity`, DAOs including a
  ready-but-unused viewport query, Room database, `Geohash`, `MediaLibraryScanner`
- `permission/MediaAccess` - the three tiers from DESIGN.md section 10
- `map/` - `PhotoMap` (GeoJSON clustering + count badge layers), `MapScreen`, `MapViewModel`
- `MainActivity` launching straight onto the map, no splash (D-013)

**Versions were verified against Maven before use**, not guessed - a direct response to
having been burned by version guessing earlier in the project.

**Three build problems worth recording, since they will recur:**
1. **Compose BOM 2026.08.00 requires compileSdk 37.** Only android-36 is installed and AGP
   8.11.0 recommends at most 36. Stepped back to BOM 2026.06.01, activity-compose 1.12.4,
   lifecycle 2.10.0.
2. **Room 2.8.4 drags in kotlin-stdlib 2.4.0** while the project compiles with Kotlin
   2.1.20, which makes Room's *generated* code fail to resolve `mutableListOf` and
   `StringBuilder`. Fixed by forcing kotlin-stdlib to 2.1.20 in a `resolutionStrategy`. The
   error message points at generated code and is thoroughly misleading about its cause.
3. The Git Bash / adb path issue from M0 recurs: `/sdcard/...` gets rewritten to a Windows
   path by MSYS. Use PowerShell for adb, as recorded in D-029.

**New question.** Q-011: the debug APK is 79 MB because MapLibre packages native libraries
for every ABI. Needs `abiFilters` or an App Bundle before M6.

**Not yet built** (still M1 scope): tap-through to a bottom-sheet photo grid (D-016),
incremental sync (D-006), empty and permission-denied states.

**Repo hygiene verified before commit:** no image files tracked, no coordinate-shaped
strings in any tracked file, test photos remain outside the repo entirely.

---

## 2026-08-08 — Tap-through grid built (branch `m1/tap-through-grid`)

**First change under the new branch + PR workflow (D-038).** The workflow change itself
went out as PR #1; this is PR #2.

**Built and verified on the emulator**, again against the 22-photo fixture:
- Tapped the `9` cluster over Texas
- Bottom sheet opened reading **"9 photos"** - matching the badge exactly - with a thumbnail
  grid and the map still visible behind it
- Tapped a thumbnail, full-screen viewer opened

**How cluster tap-through actually works.** MapLibre clusters do not carry their members;
the source stores only an aggregate plus a `cluster_id`. `GeoJsonSource.getClusterLeaves()`
expands that back into individual features, whose `id` property maps to Room rows. That is
the mechanism behind `PhotoMap.photoIdsAt()`.

**Changed:** `PhotoDao.byIds`, `PhotoMap.photoIdsAt`, `MapViewModel.selection` /
`selectPhotos` / `clearSelection`, new `map/PhotoSheet.kt` (bottom sheet + full-screen
viewer), `MapScreen` rewritten to register a map click listener via `rememberUpdatedState`
so the callback stays current without re-registering.

**Two questions opened rather than papered over.** Q-012: the 500-leaf cap is arbitrary and
tapping an 8,000-photo world-zoom bubble would produce an unusable grid. Q-013: the
full-screen dialog leaves a strip of grid visible at the bottom.

**Still in M1:** incremental sync (D-006), empty and permission-denied states.

---

## 2026-08-08 — Documentation audit after PRs #1 and #2 merged

Owner asked for a consistency pass before continuing. Seven things were stale; one was a
genuine correction rather than tidying.

**The correction (D-039).** D-018 prescribed "cache badge bitmaps keyed by the number, evict
on an LRU, and cap the number of markers rendered." That assumed Google Maps, where the app
generates a `BitmapDescriptor` per marker. **MapLibre draws the count as text via a
SymbolLayer from `point_count`** - no bitmaps are created, so there is nothing to cache. The
conclusion (badges are cheap) gets stronger; the implementation guidance was obsolete.
DESIGN.md section 5 and the M1 roadmap item were both corrected; the roadmap line is struck
through rather than deleted so the reasoning stays visible.

**Stale items fixed.**
- `CLAUDE.md` said "M1 is next and nothing blocks it" and "No app code exists yet". Both
  wrong since the scaffold landed. Rewritten, and it now names the remaining M1 work and the
  known rough edges (Q-011, Q-012, Q-013) rather than reading as though everything is fine.
- `CLAUDE.md` document map did not list `README.md`.
- `ROADMAP.md` M1 was `not started` with nothing ticked, despite six of its items being done
  and verified. Now `in progress` with accurate checkboxes.
- `README.md` status did not mention tap-through.
- `DESIGN.md` section 4 documented `cellIdZoom1..4`, `placeId`, `Place` and `ExclusionZone`
  as though they existed. They do not. Now marked `[planned]` with the milestone, and a note
  pointing at the real entity file - a schema doc that silently diverges from the code is
  worse than no schema doc.
- `D-012` pointed at D-031 as its resolution, but D-031 was superseded by D-036. Chain fixed.
- `GLOSSARY.md` had **zero** entries for MapLibre despite it being the chosen SDK. Added
  MapLibre, style URL, GeoJSON source, `point_count`, cluster leaves, KSP, and PR.

**Next.** Finish M1: incremental sync (D-006), then the empty / permission-denied /
no-geotagged-photos states.

---

## 2026-08-08 — Glossary corrections (the audit missed two)

The documentation audit in PR #3 checked `CLAUDE.md`, `ROADMAP.md`, `README.md`,
`DESIGN.md` and the decision chain, and **added** seven glossary entries - but never
re-read the entries that were already there. Two were stale:

- **Marker clustering** described clustering as the default behaviour of Google's
  `android-maps-utils`. True, but that SDK was dropped in D-036. Now says clustering is a
  GeoJSON source option in MapLibre, with the Google reference kept as context for D-015.
- **Count badge** stated that "only a handful of distinct images are ever needed... generated
  once and shared across thousands of markers." That is the original D-014 claim, which
  **D-018 corrected** (counts are exact and unbounded) and **D-039 then made moot entirely**
  (MapLibre draws text, not bitmaps). The glossary - the file specifically for someone new -
  was asserting as fact a thing two decisions had overturned.

**Worth noting as a process lesson:** an audit that only looks for *missing* entries will
not find *wrong* ones. Adding to a glossary is easy to remember; re-reading it against
decisions made since is not. When a decision corrects an earlier one, grep the whole docs
tree for the superseded claim rather than only fixing the file that prompted the correction.
