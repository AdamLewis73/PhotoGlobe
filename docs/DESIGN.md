# PhotoGlobe — Technical Design

Deep reference. Read the section relevant to what you're working on; don't read it
end-to-end every session. Hard rules live in `CLAUDE.md`; settled choices live in
`docs/DECISIONS.md`.

---

## 0. Product thesis and the MVP

**The MVP is a map that opens onto your whole photo library as numbered clusters — which
divide as you zoom in and coalesce as you zoom out — plus tap-through: tap a cluster, get a
thumbnail grid in a bottom sheet, tap a thumbnail for full screen.** Nothing else. Every
other section of this document describes post-MVP work.

The reference implementation already exists and is worth studying directly: Samsung Gallery
hides one behind photo details → tap the location. It works well, almost nobody knows it is
there, and reaching it takes four taps from a photo you already had to find.

**That is the whole product thesis: the map is the front door, not a buried detail screen.**
The value being added is not the clustering — that is a solved, documented technique with a
library that does it out of the box (D-015). The value is that the app opens directly onto
it, across the entire library, one tap from the home screen.

Two consequences that should shape every decision downstream:

- **Cold-start-to-map is the headline metric.** No splash screen, no menu, no onboarding
  carousel between launch and the map. An app that takes eight seconds to show a map has
  lost to the four taps it was meant to replace.
- **Scope discipline.** Stats, trips, interpolation, exclusion zones and photo-thumbnail
  markers are all real features, and all post-MVP.

**One inference to avoid:** Samsung Gallery is a preinstalled system app with privileged
access to media location. A third-party app installed from Play does not have that, and
must go through the permission path in §1. Samsung doing this effortlessly tells us nothing
about whether we can. M0 still gates everything.

## 1. The thing to settle before anything else

**Can the app read GPS coordinates from the user's photos on a current Android version,
at scale, without the user hand-picking every file?** Everything downstream depends on
the answer. Tracked as Q-001; answered by the M0 spike.

| Path | Permission needed | GPS available? |
|---|---|---|
| Android Photo Picker (`PickVisualMedia`) | none | **No** — the picker redacts location metadata by design |
| MediaStore, broad access (`READ_MEDIA_IMAGES`) | broad media permission + `ACCESS_MEDIA_LOCATION` | Yes, via `MediaStore.setRequireOriginal(uri)` |
| MediaStore, partial access (`READ_MEDIA_VISUAL_USER_SELECTED`, Android 14+) | partial grant + `ACCESS_MEDIA_LOCATION` | **Unverified — this is the key M0 question** |
| SAF (`ACTION_OPEN_DOCUMENT`) | none | Yes — SAF does not redact EXIF |

Three consequences worth internalising:

- The Photo Picker — the frictionless no-permission path everyone reaches for first — is
  **useless for the core feature**. It remains fine for manual import where the user places
  the pin themselves.
- `ACCESS_MEDIA_LOCATION` is a *separate* runtime permission from the media read
  permission. Without it, MediaStore returns EXIF with GPS stripped and **raises no error**.
  It looks exactly like a library containing no geotagged photos. Days get lost to this.
- Broad `READ_MEDIA_IMAGES` requires a Play Console declaration justifying whole-library
  access. A photo map app is a defensible category, but budget for a review round-trip and
  keep the Curated tier working independently (§10).

## 2. Sync model

Settled in D-006. The app scans for new photos **when opened** and **periodically in the
background**; anything with location metadata is added automatically, anything without is
ignored unless the user places it by hand (D-009).

There is no reliable "a photo was just taken" signal available to a background app —
`Camera.ACTION_NEW_PICTURE` was deprecated at API 24 and is no longer broadcast to manifest
receivers, and background execution limits prevent keeping an observer alive indefinitely.
The scan model gets the same user-visible result: the map is always current.

Implementation:

- **On foreground.** Persist `MediaStore.getVersion()` and the highest `DATE_ADDED` seen.
  On resume, query only rows newer than that — usually a few dozen, milliseconds.
- **Periodic `WorkManager` job** (~6h, battery-not-low) so the map is warm before opening.
- **Foreground `ContentObserver`** for the live "photo lands on the map" moment while the
  app is open. Optional polish, not load-bearing.
- **Reconciliation.** The same pass detects photos deleted since last scan (Q-007).

## 3. Stack

| Concern | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | D-001 |
| Map | **Undecided** — Google Maps vs MapLibre | D-012, resolve after M0 |
| Clustering | maps-compose-utils, or hand-written if MapLibre | Follows from the map decision |
| Local DB | Room | Single source of truth for the map |
| Background work | WorkManager | Scan + periodic sync |
| Image loading | Coil 3 | With a hard-capped memory cache |
| DI | Hilt, or manual | Solo project — don't over-engineer |
| Reverse geocoding | Bundled offline datasets | D-007, §6 |

Start as a **single module** with feature packages (`map/`, `library/`, `stats/`, `data/`).
Modularize only when build times actually hurt.

## 4. Data model

Never copy or move the user's photos (D-008). Store references plus derived metadata.

```
Photo
  id                  (local PK)
  mediaStoreId        (Long, MediaStore._ID)
  contentUri          (String)
  contentSignature    (size + dateTaken + displayName — survives ID churn, enables dedup)
  dateTakenUtc        (Long)
  dateTakenOffset     (String?, from EXIF OffsetTimeOriginal if present)
  lat, lng            (Double?, null = unplaced)
  locationSource      (EXIF | MANUAL | INTERPOLATED | GPX)
  locationConfirmed   (Boolean — see D-009; inferred locations start false)
  altitude            (Double?)
  geohash             (indexed, for spatial queries — see §5)
  cellIdZoom1..4      (indexed, precomputed cluster tiers — see §5)
  placeId             (FK -> Place, resolved offline)
  isHidden            (Boolean — user-excluded)

Place
  id, countryCode, countryName, admin1, cityName, lat, lng

ExclusionZone
  id, label, lat, lng, radiusMeters

Trip                  (shape depends on Q-004)
  id, title, startUtc, endUtc, coverPhotoId

ScanState
  lastMediaStoreVersion, lastDateAddedSeen, lastFullScanAt
```

Three things that will bite if skipped:

- **Photo deletion.** A photo deleted from the gallery leaves a pin pointing at a dead URI.
  Reconcile during scan, and cache a small thumbnail (~256px, app-private storage) so the
  map doesn't develop holes mid-render. Behaviour is Q-007.
- **Old EXIF has no timezone.** `DateTimeOriginal` is naive local time; newer files add
  `OffsetTimeOriginal`. Where it's missing, infer the offset from the photo's own
  coordinates — otherwise photos taken in Japan land on the wrong calendar day and quietly
  corrupt both trip detection and stats.
- **Cloud-only photos.** Photos backed up and freed from the device may not be readable
  locally. Detect and degrade; don't crash the scan.

## 5. Performance: why 40,000 photos breaks a map

> **Scope note (D-014, D-018).** The arithmetic below assumes a *unique photo thumbnail
> inside every marker*. The MVP does not do that — it draws **exact-count badges**
> ("3194"). Counts are unbounded, so badge bitmaps cannot be pre-cached as a fixed set;
> cache them by number with LRU eviction instead.
>
> They are cheap regardless, because **only visible clusters become markers**. At world
> zoom that is perhaps 20 bubbles; at street zoom the counts are small and repeat heavily.
> Peak simultaneous markers is realistically a couple of hundred — about 200 × 64 KB ≈
> **13 MB**. The cheapness comes from how few markers exist at once, not from how few
> distinct images there are.
>
> So limit #1 is a **thumbnail problem, not a clustering problem**, and it returns only
> when photo thumbnails are added to markers in M5. Limits #2 and #3 and all three fixes
> still apply, and counts visible in the reference implementation imply a library well past
> 13,000 geotagged photos — so this is probably *not* premature. Confirm in M0 (Q-008)
> before building any of it.
>
> **Not the same thing as the bottom-sheet grid (D-016).** Thumbnails in a scrolling grid
> decode only the ~20 tiles on screen and recycle them, which is default Coil behaviour.
> Nothing in this section applies to them. The constraint here is thousands of
> *simultaneous marker* bitmaps.

A heavy traveller's library is tens of thousands of photos. Naively putting each one on the
map fails, for **three independent reasons**. Seeing them as separate problems makes the
fixes obvious.

### The three limits

**1. Memory.** Every marker is an object holding a bitmap. This design puts a photo
thumbnail inside each flag. A 128×128 thumbnail at 4 bytes per pixel is
`128 × 128 × 4 = 65,536` bytes ≈ **64 KB**. Forty thousand of those is **2.6 GB** of image
data. An app's memory budget on a phone is a few hundred MB. It dies long before the map
draws.

**2. Frame budget.** To feel smooth the map redraws 60 times per second, giving each frame
**16 milliseconds** for all its work. Panning means recomputing where every marker sits on
screen. 40,000 position calculations plus 40,000 draw calls does not fit in 16 ms. Frames
get skipped, and skipped frames are what "laggy" means.

**3. Database.** "Give me all the photos" against 40,000 rows on every pan is a full table
scan. Even at 200 ms that is twelve missed frames.

### The three fixes

**Only ask for what's on screen** *(fixes #3)*. The map always knows its visible bounds, so
query `WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?`. Looking at Kyoto touches a few
hundred rows, not 40,000.

That only helps if the database can *find* those rows quickly, which is what a **spatial
index** is for. An index is a pre-sorted lookup structure; without one, "find matching
rows" means examining every row. The complication is that latitude and longitude are two
separate columns, and an ordinary index really only accelerates one column at a time. A
**geohash** solves this by encoding a lat/lng pair into a single short string where nearby
places share a prefix — Kyoto might be `xn0m7`, and everything within a few kilometres also
starts `xn0m`. "Photos near here" becomes "rows whose geohash starts with `xn0m`": a plain
indexed prefix search, and very fast. (Google's S2 library does the same job with integers
instead of strings. Either is fine.)

**Precompute the zoomed-out answer** *(fixes #1 and #2)*. At world zoom you don't need
40,000 photos, you need "1,240 in Japan, 300 in Iceland." Compute those counts **once, at
import time**, and store them (`cellIdZoom1..4` in the schema). The world view then reads a
few dozen rows. Do this for four zoom bands — roughly country, region, city, neighbourhood —
so every zoom level has a cheap pre-baked answer waiting. Costs a little disk; converts an
impossible live computation into a lookup.

**Cap and cache the markers** *(fixes #1)*. Never draw more than a few hundred markers at
once — beyond that they visually overlap anyway, so there is nothing to gain. Build each
marker bitmap once, cache it keyed by photo id, reuse it. Generating the flag-with-thumbnail
image is the expensive step, and it must run on a background thread, since anything on the
main thread competes for that same 16 ms.

### The pattern underneath

**Work out what the user can actually perceive at this zoom level, and compute only that.**
Nobody can distinguish 40,000 dots. So never make 40,000 of anything.

Note on sequencing: M0 measures the owner's real library. If it turns out to be 3,000
photos rather than 40,000, most of this is premature and M2 gets simpler. Measure first.

## 6. Reverse geocoding — offline (D-007)

The stats screen needs country and city for every photo. Do **not** use Android's
`Geocoder` or any network geocoding API: it bills per call (violating D-003), is
rate-limited, fails with no network, and turns a 30-second import into a 20-minute one.

Bundle the data instead:

- **Country / admin-1:** simplified Natural Earth polygons, point-in-polygon lookup. A few
  MB, exact, instant.
- **City:** GeoNames `cities1000` (~150k rows) in a bundled SQLite table with a spatial
  index; nearest-neighbour lookup. A few MB more.

Result: thousands of photos per second, fully offline, zero cost, and the user's location
history never leaves the device. Check each dataset's licence and include attribution.

**Related trap:** if place *search* is ever added ("type a city, drop a pin there"), the
Google Places API bills per request with no free-forever tier. Back it with the same
bundled GeoNames table.

## 7. Photos without GPS

Per D-009, these are **ignored by default** — they don't appear on the map, and the app
never invents a location silently. They collect in an **unplaced photos inbox** where the
user can act on them.

Ladder of placement, cheapest-effort first:

1. **EXIF GPS** — automatic, the only path that needs no user action.
2. **Timestamp interpolation** — a photo with no GPS taken between two geotagged photos 40
   minutes apart has an inferable position. Presented as a *suggestion*: distinct visual
   treatment, `locationConfirmed = false`, one-tap accept. Never silently placed.
   **Priority depends on Q-002** — transformative if the owner carries a standalone camera
   (which almost never records GPS), near-irrelevant if everything is shot on the phone.
3. **GPX import** for watches and loggers. Optional; same Q-002 dependency.
4. **"Same place as…"** — copy the location of an already-placed photo.
5. **Manual pin drop** with long-press, plus **multi-select batch placement** ("these 200
   were all in Lisbon"). Batch is essential — one-at-a-time placement is unusable at real
   volume.

## 8. Exclusion zones (D-010)

Most of anyone's library is shot at home. Without suppression, the map is one enormous blob
over the user's house and a scattering of dots elsewhere.

Generalised beyond "home": the user defines **any number** of excluded areas, each with a
centre and radius, plus per-photo hiding and (later) date-range exclusion. Excluded photos
stay in the database and are omitted from the map and stats, reversibly — exclusion is a
view filter, never a delete.

This is also the privacy control. The app is a record of everywhere the user has been; the
ability to redact parts of it is a feature, not a setting.

## 9. Cost model (D-003)

Zero recurring cost is a hard constraint. What it rules in and out:

- **Ruled out:** servers, backends, cloud storage, auth providers, geocoding APIs, Places
  API, paid crash reporting. Local-first (D-002) already delivers most of this.
- **The one unavoidable network dependency is map tiles.**

Two routes, differing in risk shape rather than price:

**Google Maps SDK for Android.** Map loads on the mobile SDK are not billed — but Google
will not issue an API key without a **billing account with a payment method attached**.
Free in practice, card on file. Bound the risk to effectively zero:
  - Restrict the API key to the app's package name + signing certificate
  - Enable **only** Maps SDK for Android on the GCP project — leave Geocoding, Places,
    Directions and Static Maps disabled so they cannot be called by accident
  - Set a $0 budget alert

**MapLibre GL.** No Google billing account, no card. You supply tiles: a free-tier provider
(MapTiler, Stadia, Protomaps) or self-hosted. Free tiers are generous and a personal app
will never approach them, but they are provider discretion rather than guarantee — and you
write the clustering layer yourself.

At personal-app scale both are free, so this is **"card on file + less work" vs "no card +
more work"**, not a cost comparison. Deferred as D-012.

**The only guaranteed cost in the project is the one-time $25 Play registration.**

## 10. Permission tiers

Ships to Play (D-004), so permission strategy is load-bearing. If the app only functions
with broad access, it has a single point of failure outside the owner's control — Google
can reject the declaration, request changes, or revoke it in a later policy sweep.

| Tier | Access | Experience |
|---|---|---|
| **Full** | Broad `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION` | Point it at the library; map fills in and stays current |
| **Curated** | Android 14+ partial grant | "Add photos" flow, batches granted over time, app remembers what it holds. GPS availability is Q-001 |
| **Manual** | Picker / SAF only | No GPS at all. Placement entirely manual. Slower, still usable |

**Build Curated as the primary flow and treat broad access as an accelerator on top.** More
work up front; makes the app immune to a policy decision going the wrong way.

Do not open with a permission wall — show something first, ask after.

## 11. Stats screen

Shape only; specifics are Q-005, deliberately deferred until there is real data to look at.

The design risk worth recording: **country count saturates.** After a few years it moves
once or twice annually. Build a hierarchy where something is always moving:

- **Cities and admin-1 regions** (US states, Japanese prefectures) — these climb even on a
  domestic trip.
- **Trips**, auto-detected — "Japan, Mar 2024 · 6 cities · 1,240 photos." Presentation is
  Q-004.
- **Per-year views** — new countries this year, photos this year.
- **Cheap delights, near-free to compute:** furthest north/south/east/west photo ever taken;
  highest-altitude photo (EXIF carries altitude); longest single travel day; first photo in
  each country, dated.
- **Filled-map view** — photographed countries rendered solid.

## 12. Map interaction

> **MVP (D-014, D-016, D-018):** exact-count badges — a number in a bubble, matching the
> reference implementation in §0 — plus tap-through to a bottom-sheet thumbnail grid and a
> full-screen viewer. The four-tier visual language below, national flags at world zoom,
> and photo thumbnails *inside markers* are all M3–M5 work layered on afterwards. The
> clustering behaviour itself comes from the map SDK, not from hand-written code (D-015).
>
> **Counts are exact and therefore unbounded** ("3194"), so badge bitmaps cannot be
> pre-cached as a fixed set. They are cheap anyway because only visible clusters become
> markers — a couple of hundred at peak, ~13 MB of bitmaps. Cache by number with LRU
> eviction and cap rendered markers. See D-018, which corrects the original reasoning.
>
> **World-zoom regional grouping is free** — it falls out of distance clustering, not place
> lookup, because a US state is a few dozen pixels wide at maximum zoom-out (D-019).

**The branch-out animation is the memorable part of the app.** Clusters splitting as you
zoom is what the concept was built around. Off-the-shelf Android clustering does **not**
animate it — markers simply pop in at new positions. Animating children outward from the
parent cluster's position on zoom-in, and inward on zoom-out, is custom renderer work.
Budget real time for it in M5; it is the difference between "a map with dots" and something
that feels alive.

Four zoom tiers, each with its own visual language:

1. **World** — national flags with count badges
2. **Country** — city clusters carrying a representative photo thumbnail
3. **City** — neighbourhood clusters
4. **Street** — individual photos

Keep two ideas separate in the model, both currently called "flag":

- **Visual clusters** — screen-space, zoom-dependent, purely a rendering concern.
- **Places** — semantic and stable ("Kyoto"). What stats count, and what "photos in this
  region" should mean.

**Tap opens a bottom sheet, not a new screen** — the map stays visible behind it, context is
never lost; drag up for a full grid, tap through to the viewer. This also handles the dense
case: 200 photos from one restaurant must never become 200 markers. Set a hard floor — below
N photos in a cell render individuals, above it always cluster and let the sheet do the work.

**Long-press** does double duty: "place selected photos here" during import, and "what did I
do here?" otherwise.

## 13. Play Store checklist (D-004)

Personal app, but Play compliance still applies.

**Start the developer account clock early — it is calendar time, not work time.** New
personal developer accounts must run a closed test with a cohort of testers over a
continuous period before production access unlocks. Started now it runs in parallel with
development for free; left to the end it is a dead two-week wall.

- [ ] Register developer account ($25, one-time)
- [ ] Recruit 12+ closed testers
- [ ] Broad media permission declaration (Curated tier as the fallback if refused)
- [ ] Privacy policy URL — short and true, since nothing leaves the device
- [ ] Data Safety form — nearly empty by design (D-002)
- [ ] Target API level compliance
- [ ] 16 KB page size support — affects any dependency shipping native code, which includes
      **both** Google Maps SDK and MapLibre. Means staying on current versions.
