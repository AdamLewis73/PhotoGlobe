# Roadmap

Status values: `not started`, `in progress`, `done`, `blocked`.
Update this file whenever a milestone's status changes.

**M1 is complete. Next: M2 or M3 - see the note under M2.**

Reordered 2026-08-08 around the MVP definition in D-013, then adjusted the same day after
the owner examined the reference implementation (Samsung Gallery) directly. The MVP is M1
alone: a map that opens onto the whole library as numbered clusters that split and merge on
zoom, with tap-through to the photos. Everything after M2 enhances something that already
works.

---

## M0 · Feasibility spike — `done` (2026-08-08)

Run on an Android 16 emulator against 23 real photos copied off the owner's S25+, not on
the phone itself (D-028). All questions answered:

- [x] GPS readable under broad access — 22/25 geotagged, exact match to independently
      established ground truth, 0 errors (D-023)
- [x] **Curated (partial) grant returns unredacted GPS** — 3/3 on known-good photos.
      The app does not depend on Play approving broad access (D-024)
- [x] Photo Picker redacts location — confirmed `latLong = null` (D-025)
- [x] No cheap path — MediaStore lat/lng columns 0 non-zero of 25 (D-026)
- [x] Timing — 4.16 ms/photo, so **persistence is required** (D-027)
- [x] Findings recorded in `docs/PROGRESS.md` and D-023…D-028

`spike/` can now be deleted. Timing should be re-measured on real hardware during M1
(Q-010), but nothing is blocked on it.

## M0.5 · Project setup — `done` (2026-08-08)

- [x] Map SDK: **MapLibre** (D-036) — supersedes the earlier Google Maps choice
- [x] Tile style: CARTO Positron, key-free (D-037)
- [x] Videos: schema-ready, images only in M1 (D-032)
- [x] minSdk 33 / targetSdk 36 (D-033)
- [x] `git init`
- [x] ~~Google Cloud project + Maps API key~~ — **no longer needed at all** (D-036)
- [ ] Register Play developer account ($25) — optional, not blocking. Starts the
      closed-testing clock, which is calendar time and runs in parallel for free

**Nothing blocks M1.**

## M1 · The MVP — `done` (2026-08-08)

**This is the product.** A map opening on the whole library, numbered clusters that divide
and coalesce as you zoom, and tap-through to the photos behind any cluster.

- [x] Compose app shell that launches **straight to the map** — no splash, no menu
- [x] Library scan reading id + lat/lng + date for every geotagged photo
- [x] Room persistence — required (D-027); schema includes mediaType from day one (D-032)
- [x] Clustering with exact-count badges via MapLibre GeoJSON `cluster = true`
      plus circle + symbol layers on `point_count` (D-014, D-018, D-036)
- [x] Tap a cluster → bottom-sheet thumbnail grid → tap a thumbnail → full screen (D-016),
      showing **every** photo in the cluster, no cap (D-043)
- [x] Permission flow covering the Full and Curated tiers (DESIGN.md §10)
- [x] Incremental sync on foreground + WorkManager periodic job (D-006), with deletion
      reconciliation (D-040)
- [x] Empty state — **an empty map is the answer** (D-044). No placeholder screen; the
      action button already reads "Grant photo access" when access is missing
- [x] Full-screen viewer covers the whole screen (Q-013, closed)
- [ ] ~~Measure cold-start-to-map~~ — **deferred** (D-045). The app is still gaining
      features that would make any figure obsolete within a milestone
- [x] ~~Badge bitmap cache keyed by number~~ — **not applicable on MapLibre.** The count is
      text drawn by a SymbolLayer from `point_count`, not a generated bitmap (D-039)

Every item was verified running on an emulator against the 22-photo fixture, not merely
compiled. Highlights: the scan matched independently established ground truth exactly, a
cold second launch showed the map with no rescan, and deleting files dropped their pins.

**Not yet verified at real scale.** The fixture is a couple of dozen photos. Behaviour
against a library of tens of thousands is untested and Q-010 remains open.

Explicitly **not** in M1: geocoding, place names, stats, trips, manual placement, exclusion
zones, photo thumbnails inside markers, cluster split animation. See hard rule 8.

## M2 · Making it hold up — `not started`

- [ ] Incremental sync on foreground + WorkManager periodic job (D-006)
- [ ] Viewport-bounded queries + spatial index, *if the M0 numbers justify it* (DESIGN.md §5)
- [ ] Empty state, permission-denied state, no-geotagged-photos state
- [ ] Photo deletion reconciliation (Q-007)

## M3 · Places & stats — `not started`

- [ ] Bundle offline geocoding datasets, resolve photos to places (D-007)
- [ ] Country-flag cluster icons at world zoom
- [ ] Stats screen (Q-005 — specify against real data, not an imagined library)
- [ ] User-asserted places — mark somewhere visited without photos (D-041). Covers
      places visited before smartphones, and stops a photo deletion erasing a country

## M4 · Placement & trips — `not started`
Scope depends on Q-002. Phone-only owner ⇒ roughly half this milestone disappears.

- [ ] "Unplaced photos" inbox
- [ ] Manual pin drop via long-press; multi-select batch placement
- [ ] Timestamp interpolation as *suggestions* requiring confirmation (D-009)
- [ ] GPX import *(only if Q-002 says camera)*
- [ ] Trip auto-detection and presentation (Q-004)

## M5 · Polish — `not started`

- [ ] Exclusion zones — opt-in, off by default (D-010, D-017)
- [ ] Photo thumbnails inside markers — **reintroduces the memory constraint in
      DESIGN.md §5**, so do the caching work properly here
- [ ] Cluster split/merge animation — hand-built on MapLibre (D-035, D-036). M1 ships
      with markers popping between zoom levels; this is the polish pass
- [ ] Timeline scrub / year filter
- [ ] Share/export card, dark map style

## M6 · Play release — `not started`

- [ ] Broad media permission declaration
- [ ] Privacy policy URL (short and true — nothing leaves the device)
- [ ] Data Safety form
- [ ] Closed testing with 12+ testers over the required period
- [ ] Target API level and 16 KB page size compliance
