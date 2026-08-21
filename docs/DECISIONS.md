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

### D-012 · 2026-08-08 · deferred
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
