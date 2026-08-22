# Decision Log

Numbered, dated, permanent. Append new decisions at the bottom. Never delete an entry —
if a decision is reversed, add a new entry that supersedes it and mark the old one.

Format: **D-nnn · date · status** — the decision, then *why*.
Status is `active`, `superseded by D-nnn`, or `deferred`.

---

### D-001 · 2026-08-07 · active
**Android only. Kotlin + Jetpack Compose. No cross-platform framework.**
Owner uses Android; this is a personal project. A cross-platform framework would add a
layer of indirection over the two hardest parts of the app (MediaStore permissions and
map rendering) for a portability benefit nobody needs.

### D-002 · 2026-08-07 · active
**Local-first. No backend, no account, no upload, no cloud sync.**
The app holds a complete record of where the user has been. Keeping it on-device is the
honest default, removes a whole class of security and privacy obligations, empties the
Play Data Safety declaration, and — see D-003 — is the only way to run at zero cost.

### D-003 · 2026-08-08 · active
**Zero recurring cost is a hard constraint.**
No servers, no paid APIs, no per-call services, no subscriptions. The one-time $25 Play
developer registration is the only accepted expense. This is an owner constraint, not a
preference, and it vetoes designs rather than being traded off against them.
Consequences: geocoding must be bundled (D-007); place search cannot use the Places API;
crash reporting and analytics must be free-tier or absent.

### D-004 · 2026-08-08 · active
**Ships to the Play Store, but is designed as a personal app.**
Owner wants it on the Play Store and is indifferent to download numbers. So: Play
compliance work is required (developer account, closed testing period, permission
declaration, Data Safety form), but growth, marketing, onboarding-conversion and
monetization are explicitly *not* goals. Supersedes the market-positioning and pricing
material from the 2026-08-07 session, which is now out of scope.

### D-005 · 2026-08-08 · active
**No monetization. Free, no paid tier, no ads.**
Follows from D-004. Ads in particular are ruled out permanently — an ad SDK inside an app
holding the user's full movement history contradicts D-002 and complicates Data Safety
for revenue nobody wants.

### D-006 · 2026-08-08 · active
**Sync model: scan on app open, plus a periodic background job. No real-time capture hook.**
Android has no reliable "a photo was just taken" signal for a background app
(`Camera.ACTION_NEW_PICTURE` was deprecated at API 24; background limits prevent
long-lived observers). Implementation: persist `MediaStore.getVersion()` and the highest
`DATE_ADDED` seen, query only newer rows on resume, plus a periodic WorkManager job. A
foreground-only `ContentObserver` may additionally provide the live "photo lands on the
map" moment.

### D-007 · 2026-08-08 · active
**Reverse geocoding is offline, using bundled datasets.**
Country/admin-1 from simplified Natural Earth polygons via point-in-polygon; city from
GeoNames `cities1000` via nearest-neighbour. Required by D-003 (geocoding APIs bill per
call), and independently better: thousands of photos/second, works with no network, and
no third party ever receives the user's location history. Licences must be checked and
attribution included.

### D-008 · 2026-08-08 · active
**The photo library is read-only. Never modify, move, or delete a user photo.**
The app stores content URIs plus derived metadata, and caches its own small thumbnails in
private storage. Photo files are never touched. Non-negotiable: data loss in a personal
photo library is unrecoverable and unforgivable.

### D-009 · 2026-08-08 · active
**Photos without GPS metadata are ignored by default. Locations are never invented silently.**
Owner's explicit rule. A non-geotagged photo does not appear on the map unless the user
places it. Any *inferred* location (timestamp interpolation, GPX matching) must be
presented as a suggestion in the unplaced-photo inbox, visually distinct from real GPS
data, and confirmed by the user before it counts. Inference proposes; it never decides.

### D-010 · 2026-08-08 · refined by D-017
**Exclusion zones are a v1 feature, generalized beyond "home".**
Most of any library is shot in one or two places, which would otherwise dominate the map.
The user can define any number of excluded areas with a radius, not just a home location,
plus per-photo hiding. Excluded photos stay in the database and are omitted from the map
and stats, reversibly.

### D-011 · 2026-08-08 · active
**Documentation-first working agreement.**
Overview, hard rules, roadmap, decision log, open questions, progress log and glossary are
maintained continuously so any future session can resume cold. Structure and duties are
defined in `CLAUDE.md`. Owner requirement, established at project start.

### D-012 · 2026-08-08 · resolved by D-031
**Map SDK: Google Maps vs MapLibre — deferred until after M0.**
Google Maps gives clustering via `maps-compose-utils` and the familiar look the design
assumes, but requires a billing account with a card attached (map loads on the mobile SDK
are not billed). MapLibre needs no card but requires a self-supplied tile source and a
hand-written clustering layer. At personal-app scale both are free in practice, so this is
a "card on file + less work" vs "no card + more work" decision, not a cost one. Must be
settled before M1; migrating later is expensive.

### D-013 · 2026-08-08 · active
**The MVP is a full-library map with numbered clusters that split and merge on zoom.
Everything else is post-MVP.**
Owner located the exact target behaviour already shipping in Samsung Gallery (photo
details → tap the location): markers showing a live photo count, dividing as you zoom in
and coalescing as you zoom out. It is buried four taps deep and largely undiscovered.
The product thesis is therefore **not** "build clustering" — clustering is a solved,
well-documented technique (§12) — but "make that map the app's front door, across the
whole library, one tap from the home screen." Consequences: **cold-start-to-map is the
headline metric**, and stats, trips, interpolation, exclusion zones and thumbnail markers
all move behind the MVP. This narrows M1 and reorders the roadmap; it changes no hard rule
and invalidates none of D-001…D-012.

### D-014 · 2026-08-08 · active
**MVP markers are count badges, not photo thumbnails.**
Matches the reference behaviour, and carries a large technical payoff. A count badge has
only a handful of distinct appearances ("1", "2", … "9", "10+", "50+", "100+"), so its
bitmaps are generated once and shared across thousands of markers. This removes the
per-marker memory problem in `DESIGN.md` §5 — the 2.6 GB figure assumed a *unique*
thumbnail per marker — for the whole MVP. Photo thumbnails inside markers become an M5
enhancement and reintroduce that constraint when added.

### D-015 · 2026-08-08 · active
**Do not hand-write the clustering algorithm.**
Marker clustering with count badges and zoom-driven split/merge is the documented default
behaviour of Google's `android-maps-utils` / `maps-compose-utils` `Clustering`. Writing it
from scratch would be weeks of work to reproduce a library's out-of-the-box behaviour.
Note this cuts across D-012: choosing MapLibre means writing this layer by hand, which is
a materially larger cost now that clustering *is* the MVP rather than one feature among
many. Revisit D-012 with that weighting.

### D-016 · 2026-08-08 · active
**Tap-through is part of the MVP: tap a cluster, get a bottom-sheet thumbnail grid, tap a
thumbnail for full screen.**
Confirmed against the reference implementation, which does exactly this. Folded into M1
rather than M2 because without it the app shows *that* photos were taken somewhere but not
*what* they were — a dot map, not a photo app — and because it was one of the three core
UI elements in the owner's original description.

**This does not reintroduce the memory constraint in §5.** Thumbnails in a scrolling grid
are unrelated to thumbnails inside markers: a lazy grid decodes only the ~20 tiles on
screen and recycles them, which is default Coil behaviour. The §5 problem was thousands of
*simultaneous* marker bitmaps. Keep the two cases distinct when reading that section.

### D-017 · 2026-08-08 · active · refines D-010
**Exclusion zones are opt-in and off by default. They stay post-MVP (M5).**
Owner's call, confirmed against the reference implementation, which does not suppress home
either. An app that silently omits tens of thousands of a user's photos because it inferred
where they live is doing something surprising with their data — the same instinct behind
hard rule 4. Discoverable in settings, never automatic, never on by default.

### D-018 · 2026-08-08 · active · corrects the rationale in D-014
**Count badges show exact counts. They are cheap because few markers exist at once, not
because few distinct badge images exist.**
D-014 claimed a badge has only ~15 distinct appearances so a handful of bitmaps could be
cached and shared. That is wrong: the reference implementation displays exact counts
("3194"), which are unbounded, so they cannot be pre-cached.

The conclusion survives on different reasoning. **Only visible clusters get markers.** At
world zoom perhaps 20 bubbles are on screen; at street zoom the counts are small and repeat
heavily. Peak simultaneous markers is realistically a couple of hundred, so worst case is
about 200 x 64 KB = ~13 MB. Negligible.

The practical rule for M1: cache badge bitmaps keyed by the number, evict on an LRU, and
cap the number of markers rendered at once. Do not assume a fixed small set of badge images.

### D-019 · 2026-08-08 · active
**World-zoom regional grouping requires no geocoding. It falls out of distance clustering.**
The reference implementation shows counts that read as regional ("3194 in Texas", "8k in
Japan"), which looks like place awareness but almost certainly is not. At maximum zoom-out
a US state is a few dozen pixels across, so every photo in it lands in one cluster cell by
geometry alone. Offline geocoding (D-007) therefore stays in M3, where it is needed for
stats that name places — not in M1 for map bubbles that merely sit over them.

### D-020 · 2026-08-08 · active
**Library scan cost is a first-run cost only. The reference implementation is instant
because it queries an index it already built, not because scanning is cheap.**
Raised by the owner: Samsung Gallery's map appears with no scan delay at all. Two reasons,
neither available to us on first run:

1. **It indexed incrementally over the life of the device.** Every photo was processed once
   as it arrived. The map view is a database query over an already-populated table.
2. **It very likely never reads EXIF.** Android's media provider already extracts location
   during its own media scan, and `MediaStore` exposes LATITUDE/LONGITUDE columns for it -
   deprecated at API 29 and redacted for non-privileged apps. A system gallery reads a
   column; a third-party app must call `setRequireOriginal()`, open each file and parse its
   EXIF header. Database read versus file I/O, per photo.

**Consequence:** from run two onward, PhotoGlobe is exactly as instant as the reference,
because it does the same thing - queries its own index (D-006 incremental sync touches only
photos added since last time). Scan time sizes exactly two things: whether persistence is
needed at all (Q-008), and the first-run experience. It is not a recurring cost, and
should not be described as one.

### D-021 · 2026-08-08 · active
**First-run scan renders newest-first and progressively.**
Photos are enumerated `DATE_TAKEN DESC` and drawn as they resolve, so recent trips - the
ones the user most wants to see - appear within the first second or two while the rest
backfills behind them. Follows from D-020: the first run is the only slow one, so make it
usable immediately rather than blocking on completion. Turns the worst moment in the app
into the most interesting one. Applies to M1 even if the M0 numbers turn out to be fast.

### D-022 · 2026-08-08 · active
**Confirm the cheap path exists or does not, rather than assuming.**
The M0 spike probes MediaStore's deprecated LATITUDE/LONGITUDE columns directly. If they
returned real values the entire scan cost would collapse to a single cursor pass. They
almost certainly will not - but the payoff is large enough that a five-line check beats an
assumption. Result goes in the M0 findings.

### D-023 · 2026-08-08 · active · answers Q-001
**The app CAN read real GPS from the photo library. Broad access works.**
Measured on an Android 16 (API 36) emulator against 23 real photos copied from the owner's
Galaxy S25+, plus 2 stock emulator images.

With `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION` + `MediaStore.setRequireOriginal()`:
**22 of 25 geotagged, 3 without, 0 errors.** Independently verified: a pure-Python EXIF
parser run over the same files beforehand found 22 with GPS out of 23, and the 3 negatives
were exactly the deliberately EXIF-stripped control plus the 2 stock images. Exact match
against ground truth, so the app is genuinely parsing each file rather than guessing.

Q-001 is closed. The product is viable.

### D-024 · 2026-08-08 · active · answers Q-001b
**The Android 14+ Curated (partial) grant DOES return unredacted GPS.**
The most important result of M0. With `READ_MEDIA_VISUAL_USER_SELECTED` granted,
`READ_MEDIA_IMAGES` **denied**, and `ACCESS_MEDIA_LOCATION` granted, MediaStore returned
only the user-selected photos and their coordinates came through in full.

Verified twice. First run: 3 selected, 2 geotagged - the third being the EXIF-stripped
control, correctly detected. Second run with three photos known to carry GPS: **3 of 3,
zero errors.**

**Consequence: the Curated tier is fully functional, so the app does not depend on Google
approving broad library access.** The tiering in DESIGN.md §10 is sound and Curated can
remain the primary flow, as planned. This removes the single largest external risk to the
project.

### D-025 · 2026-08-08 · active · answers Q-001c
**The Photo Picker redacts location, as documented.**
`PickVisualMedia` on a photo confirmed to carry GPS returned `latLong = null`. The
no-permission path cannot support the core feature. DESIGN.md §1 was correct; the picker
remains usable only for manual import where the user places the pin themselves.

### D-026 · 2026-08-08 · active · closes D-022
**The cheap path does not exist. Per-file EXIF reads are mandatory.**
MediaStore's deprecated `latitude`/`longitude` columns returned **0 non-zero values out of
25 rows**. Redacted for non-privileged apps, exactly as predicted in D-020. There is no way
to avoid opening each file. Confirms why the reference implementation is instant and we
cannot be, on first run.

### D-027 · 2026-08-08 · active · answers Q-008
**The MVP needs persistence. A live rescan on every launch is not viable.**
Measured EXIF read cost: **104 ms for 25 photos = 4.16 ms/photo (~240/sec)** on the
emulator. Enumeration itself was trivial - 8 ms for the whole cursor pass - confirming the
two-phase model in DESIGN.md §5: listing is cheap, reading files is not.

Extrapolated at 4.16 ms/photo:

| Library size | Full scan |
|---|---|
| 5,000 | ~21 s |
| 20,000 | ~83 s |
| 40,000 | ~166 s (~2.8 min) |

Against the Q-008 threshold (under ~2 s means skip the database), this is not close. Even
if a real device were five times faster, a 40,000-photo library would still take half a
minute. **Room is in for M1.** Q-003 (videos in or out) therefore becomes blocking, since
it now decides a schema that will actually exist.

**Honest limits on this number.** Only 25 photos were measured, the files are large
Samsung JPEGs (1.5-5 MB, representative), and the emulator reads from a host SSD rather
than phone flash. The precise figure is unreliable; the *direction* is not. Re-measure on
real hardware when D-028 is revisited, but do not delay M1 for it.

This also raises the value of D-021 (newest-first progressive rendering): a first run of
one to three minutes is entirely acceptable if the map is usable from second two, and
unacceptable if it blocks.

### D-028 · 2026-08-08 · active
**M0 was run on an emulator, not the owner's phone. Recorded so nobody mistakes it for
device-measured data.**
The owner has a single personal phone and declined to enable USB debugging, which on
Samsung One UI requires disabling Auto Blocker. That is a legitimate call and the questions
were answered another way: real photos copied off the phone by ordinary file transfer, then
tested against an Android 16 emulator.

Everything about *permissions and platform behaviour* (D-023 to D-026) is device-independent
and can be trusted. Only the *timing* in D-027 carries an emulator caveat.

**Methodology note for future sessions.** Files placed with `adb push` land in MediaStore
with `is_pending=1` owned by `com.android.shell`, which makes them invisible to other apps
*and* to the system photo picker - the picker showed "No photos yet" despite 23 rows
existing. Fix:
`adb shell content call --uri content://media/external --method scan_volume --arg external_primary`.
Bulk-updating `is_pending` on the collection URI is rejected; the rescan is the correct
route. Expect to need this again in M1.

### D-029 · 2026-08-08 · active
**A test photo fixture is retained at `E:\PhotoGlobe-testphotos` (outside the repo).**
22 real geotagged photos from the owner's S25+, kept deliberately for testing M1 against
an emulator without touching the phone. Owner's call. Not in git and never to be committed
- `.gitignore` blocks `testphotos/` and image extensions defensively.

**Verified properties** (established independently in M0): all 22 carry GPS. They are large
Samsung JPEGs, 1.5-5 MB, spanning several months and multiple locations - representative of
the real library rather than synthetic.

**To load them into an emulator:**

```
adb push <folder>/. /sdcard/DCIM/Camera/
adb shell content call --uri content://media/external --method scan_volume --arg external_primary
```

The second line is **not optional**. Files placed by `adb push` land in MediaStore with
`is_pending=1` owned by `com.android.shell`, which makes them invisible to other apps and
to the system photo picker. Without the rescan an app sees nothing and the picker reports
"No photos yet". Bulk-updating `is_pending` on the collection URI is rejected; the rescan is
the correct route.

**Useful additions when testing detection logic:** a copy with its EXIF stripped makes a
negative control, without which a fully-geotagged set cannot distinguish real parsing from
a stub that reports everything as geotagged. M0 used exactly this.

**Emulator available on this machine:** AVD `Pixel_9_Pro` (Android 16 / API 36) and
`Medium_Phone_API_36.1`. Launch detached - starting the emulator from a shell that then
exits kills it.

### D-030 · 2026-08-08 · active · corrects D-015
**MapLibre does have built-in clustering. The claim that choosing it means hand-writing
the clustering layer was wrong.**
D-015 stated that clustering with count badges is the documented default of Google's
`android-maps-utils` and that MapLibre would require writing it from scratch. The first
half is right; the second is not. MapLibre Native on Android supports clustering directly
on a `GeoJsonSource` (`withCluster(true)`, `withClusterRadius`, `withClusterMaxZoom`),
with counts rendered from the `point_count` property via a SymbolLayer - the same mechanism
Mapbox GL has always had.

**The real difference is ergonomics, not capability:**

| | Google Maps | MapLibre |
|---|---|---|
| Clustering | `ClusterManager` - items in, clusters out | GeoJSON source option |
| Count badges | Default renderer draws them | Style a SymbolLayer on `point_count` |
| Cluster taps | `setOnClusterClickListener` | `queryRenderedFeatures` at the tap point |
| Learning material | Very large | Much thinner for Android specifically |
| Account | GCP project + billing account with a card | None |

So MapLibre costs more fiddly setup and worse documentation, not weeks of reimplementation.
D-015's conclusion (do not hand-write clustering) still stands for both options; only its
weighting of D-012 was wrong, and that weighting should not be used to justify Google Maps.

### D-031 · 2026-08-08 · superseded by D-036
**Map SDK: Google Maps, via `maps-compose` + `maps-compose-utils`.**
Chosen on ergonomics and documentation density, not capability - D-030 removed the false
claim that MapLibre would need hand-written clustering. `ClusterManager` supplies
clustering, count badges and cluster tap handling as its default behaviour, and the volume
of worked examples matters disproportionately on the owner's first Android project.

**Accepted cost:** a Google Cloud project with a billing account and a card attached. Map
loads on the mobile SDK are not billed, so this is free in practice, but the card is
required to obtain a key at all - a real if small concession against hard rule 1.

**Mandatory risk controls, all three, before any key is used:**
1. Restrict the API key to the app's package name **and** signing certificate fingerprint
2. Enable **only** Maps SDK for Android on the project. Geocoding, Places, Directions and
   Static Maps stay disabled so they cannot be called by accident
3. Set a budget alert at $0

Place search must never use the Places API (it bills per request); use the bundled GeoNames
data from D-007 instead.

**Note:** the M0 spike used plain Android Views because no Compose artifacts were cached
locally. That was incidental to the spike and does **not** overturn D-001 - M1 uses Compose.

### D-032 · 2026-08-08 · active · answers Q-003
**Videos: schema-ready now, feature later. M1 scans images only.**
The `Photo` table gains a `mediaType` column in the first schema so video support can be
switched on later without a migration. M1 ignores videos entirely (hard rule 8).

**Why this is not free later:** video location is not EXIF. MP4 stores it in a metadata
atom read through `MediaMetadataRetriever.METADATA_KEY_LOCATION`, returned as an ISO-6709
string that must be parsed. It is a second extraction path, not a wider query - so treat it
as a real feature when it arrives, not a checkbox.

### D-033 · 2026-08-08 · active · answers Q-006
**minSdk 33 (Android 13), targetSdk 36 (Android 16).**
`READ_MEDIA_IMAGES` only exists from 33, so this deletes the entire legacy
`READ_EXTERNAL_STORAGE` branch - less code and fewer permission states to test. Device
coverage is irrelevant here because downloads are explicitly not a goal (D-004).

Consequence for the tier model (DESIGN.md §10): the Curated tier needs
`READ_MEDIA_VISUAL_USER_SELECTED`, which is 34+. On Android 13 devices only the Full and
Manual tiers exist. The tier logic must handle that rather than assuming Curated is always
available.

### D-034 · 2026-08-08 · active · corrects D-031
**A budget alert does not cap spending. Google Cloud has no hard spending limit.**
D-031 listed "set a budget alert at $0" as one of three risk controls, which overstates it.
Budgets in GCP are **notification** mechanisms - they email after money is spent, they do
not block it. There is no native "stop at $X" switch. One can be approximated with
budget → Pub/Sub → Cloud Function that disables billing, but that means enabling more
services and is disproportionate here.

**The controls that actually prevent charges, strongest first:**

1. **The thing we use is not billed.** Map loads on Maps SDK for Android carry no per-use
   charge. This is the main reason the project is free, not the settings below.
2. **Enable only Maps SDK for Android on the project.** An API that has never been enabled
   cannot be called and therefore cannot bill. This is prevention, not detection, and it is
   the single most effective control.
3. **Restrict the API key twice** - *Application restriction* to the app's package name +
   signing certificate, and *API restriction* to Maps SDK for Android alone. The second
   matters even if step 2 is done: it means a leaked key is useless to anyone else.
4. **Budget alert at $1**, not $0 - an early-warning email the moment anything is spent.
   Detection only.

**Honest limit:** with a restricted key and one non-billing API enabled, practical risk is
very close to zero, but it is risk *reduction*, not a guarantee. The only absolute
guarantee of zero spend is having no billing account, which means MapLibre (D-030). If the
owner treats hard rule 1 as admitting no residual risk at all, MapLibre is the correct
choice and D-031 should be revisited.

### D-035 · 2026-08-08 · active · corrects D-016 and DESIGN.md §12
**Google's `DefaultClusterRenderer` animates cluster split/merge by default. It is not
custom work.**
DESIGN.md §12 and the M5 roadmap entry claimed "off-the-shelf Android clustering does NOT
animate it - markers pop in at new positions" and budgeted custom renderer work for the
branch-out animation. **Wrong.** `DefaultClusterRenderer` renders in three stages - add
markers, animate to final position, remove old markers - animating markers outward from
the nearest existing cluster on zoom in, and existing clusters inward to the nearest new
cluster on zoom out. Controlled by `setAnimation(boolean)` and on by default, with
animation duration configurable.

**Why this matters beyond removing an M5 task.** D-013 identified the split/merge behaviour
as the memorable core of the product. On Google Maps it is free. On MapLibre it is not:
GeoJSON clustering recomputes clusters per zoom level and markers jump to new positions
with no tweening, so the animation would have to be built by hand there.

This is now the strongest technical argument for D-031 (Google Maps), and it is a better
argument than the ergonomics reasoning D-031 was actually decided on. Surfaced by the owner
noticing the MapLibre demo "lurching" on zoom - that lurch is precisely the un-animated
cluster recomputation.

**Correction history for this area, since it has now moved three times:** D-015 said
MapLibre would need hand-written clustering (wrong, corrected by D-030); D-030 concluded
the difference was only ergonomics (incomplete - it missed animation); D-035 identifies the
actual functional gap. Verify claims about renderer behaviour against the library source
before recording them.

Source: https://github.com/googlemaps/android-maps-utils/blob/main/library/src/main/java/com/google/maps/android/clustering/view/DefaultClusterRenderer.java

### D-036 · 2026-08-08 · active · supersedes D-031
**Map SDK: MapLibre. No Google Cloud account, no billing account, no API key.**
Owner's call after seeing both. Reasons, in their weighting:

1. **Style customization.** MapLibre renders any style URL - OpenFreeMap Liberty/Bright,
   CARTO Positron/Dark Matter/Voyager, or self-hosted - and switching is a one-line change.
   Google Maps offers one look plus limited styling.
2. **Hard rule 1 is satisfied absolutely.** No billing account means zero residual risk,
   not merely small risk (D-034). Given the rule was written to veto designs rather than
   trade against them, this is the reading that matches its intent.
3. Dark mode arrives free with a style swap rather than configuration.

**What is knowingly given up (D-035):** `DefaultClusterRenderer`'s split/merge animation.
MapLibre recomputes GeoJSON clusters per zoom level and markers jump with no tweening -
the "lurch" the owner observed in the demo. **M1 ships with that pop**; hand-built tweening
returns to M5 as polish. This is a real loss on the app's signature motion and was accepted
with eyes open.

**Revisit triggers - what would justify moving to Google Maps:**
- Hand-built cluster animation in MapLibre proves impractical *and* the pop is judged
  unacceptable in use
- The chosen tile provider changes terms, imposes limits, or becomes unreliable
- Some future feature genuinely requires a Google-only capability (unlikely; Places and
  Geocoding are already ruled out by D-003 and D-007)

**Migration cost, corrected.** D-012 called switching map SDKs "expensive," and earlier
sessions repeated that. With the architecture as designed it is not: Room is the source of
truth (D-027), the scan, geohash indexing and cluster-tier precomputation are all SDK-
independent, and only the map screen itself talks to the SDK. A swap is confined to one
composable plus its marker styling and tap handling. That is what makes "MapLibre for now"
a sound position rather than an optimistic one.

**Unblocks M1 immediately** - the Maps API key was the last item gating it, and it no
longer exists.

### D-037 · 2026-08-08 · active
**Default tile style: CARTO Positron. Key-free, and chosen because the pins are the content.**
A pale, low-contrast basemap makes coloured cluster badges read clearly, which matters more
here than street detail. Alternatives already verified working and key-free: OpenFreeMap
Liberty and Bright (full street maps), CARTO Dark Matter (dark), CARTO Voyager. Changing is
one URL, so this is not a commitment.

**Two things to be honest about, both of which apply to Google Maps equally:**

1. **Tile hosts are third-party servers.** Free public tile endpoints are someone else's
   infrastructure, offered at their discretion. Fine at personal-app scale; if it ever
   became a problem the fallbacks are MapTiler's free tier or self-hosted Protomaps.
2. **The tile host sees which part of the map is being viewed.** Not photo locations, not
   EXIF, not the library - but the viewport is a request to an external server. Hard rule 2
   says everything lives on the device; that remains true of all *photo* data, and §9
   already names tiles as the sole unavoidable network dependency. Do not overstate the
   privacy claim to "nothing whatsoever leaves the device."

### D-038 · 2026-08-08 · active
**All changes go on a branch and merge through a pull request. No direct commits to `master`.**
Owner's requirement, effective immediately - this decision was itself delivered on a branch
rather than committed straight to `master`.

**Branch naming:** milestone or type prefix, then a short kebab-case description.
`m1/tap-through-grid`, `m2/incremental-sync`, `docs/pr-workflow`, `fix/scan-crash`.

**Who merges:** the owner. An agent may create the branch, push it and open the PR, but
merging is a decision, not a mechanical step, and stays with the person who has to live
with the code.

**Why it matters here specifically.** This project's value is largely in its written
reasoning - `DECISIONS.md`, `PROGRESS.md`, and the corrections kept alongside the entries
they overturned. A PR gives each change a reviewable diff and a place for that reasoning to
sit before it lands, rather than after. It also makes the "I got this wrong" commits (D-030,
D-034, D-035) legible as discrete events instead of a stream on one branch.

**Tooling gap:** the GitHub CLI is not installed on this machine, so an agent cannot open a
PR directly. Until `gh` is installed and authenticated, the flow is: agent pushes the
branch, then hands over a compare URL of the form
`https://github.com/AdamLewis73/PhotoGlobe/compare/master...BRANCH?expand=1`
for the owner to open the PR in the browser.

**Worth considering:** branch protection on `master` in the repo settings would enforce this
rather than relying on discipline. Owner's call - it is a settings change on their account.
