# Roadmap

Status values: `not started`, `in progress`, `done`, `blocked`.
Update this file whenever a milestone's status changes.

**Current milestone: M0.5 in progress. Only blocker to M1 is the Maps API key.**

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

## M0.5 · Project setup — `in progress`

- [x] Map SDK settled: **Google Maps** via `maps-compose` (D-031)
- [x] Videos: schema-ready, images only in M1 (D-032)
- [x] minSdk 33 / targetSdk 36 (D-033)
- [ ] **Google Cloud project + Maps SDK for Android API key** — owner action, blocks M1's
      map screen. Must apply all three risk controls in D-031: key restricted to package +
      signing cert, only Maps SDK for Android enabled, $0 budget alert
- [ ] `git init` — done
- [ ] Register Play developer account ($25) — optional, not blocking. Starts the
      closed-testing clock, which is calendar time and runs in parallel for free

## M1 · The MVP — `not started`
**This is the product.** A map opening on the whole library, numbered clusters that divide
and coalesce as you zoom, and tap-through to the photos behind any cluster. Shippable and
genuinely useful on its own.

- [ ] Compose app shell that launches **straight to the map** — no splash, no menu
- [ ] Library scan reading id + lat/lng + date for every geotagged photo
- [ ] Room persistence — required (D-027); schema includes mediaType from day one (D-032)
- [ ] Clustering with exact-count badges via the map SDK's built-in clusterer
      (D-014, D-015, D-018)
- [ ] Badge bitmap cache keyed by number, LRU-evicted, with a cap on rendered markers (D-018)
- [ ] Tap a cluster → bottom-sheet thumbnail grid → tap a thumbnail → full screen (D-016)
- [ ] Permission flow covering the Full and Curated tiers (DESIGN.md §10)
- [ ] Measure cold-start-to-map and keep it honest (D-013)

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
- [ ] Cluster branch-out animation (DESIGN.md §12 — custom renderer work, not free)
- [ ] Timeline scrub / year filter
- [ ] Share/export card, dark map style

## M6 · Play release — `not started`

- [ ] Broad media permission declaration
- [ ] Privacy policy URL (short and true — nothing leaves the device)
- [ ] Data Safety form
- [ ] Closed testing with 12+ testers over the required period
- [ ] Target API level and 16 KB page size compliance
