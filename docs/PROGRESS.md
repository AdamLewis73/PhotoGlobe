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
