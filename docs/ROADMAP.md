# Roadmap

Status values: `not started`, `in progress`, `done`, `blocked`.
Update this file whenever a milestone's status changes.

**Current milestone: M0 — not started.**

Reordered 2026-08-08 around the MVP definition in D-013, then adjusted the same day after
the owner examined the reference implementation (Samsung Gallery) directly. The MVP is M1
alone: a map that opens onto the whole library as numbered clusters that split and merge on
zoom, with tap-through to the photos. Everything after M2 enhances something that already
works.

---

## M0 · Feasibility spike — `not started`
*Code written 2026-08-08 and waiting to be run: see `spike/README.md`.*
*Half a day to a day. Throwaway code, deleted afterwards.*

Answers Q-001 and Q-008, informs D-012. **No PhotoGlobe code before this is done.**

- [ ] Empty Android project against the current target SDK
- [ ] Request `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION`, query MediaStore,
      call `setRequireOriginal()`, print lat/lng for ~10 photos
- [ ] Repeat under the Android 14+ partial grant (`READ_MEDIA_VISUAL_USER_SELECTED`)
- [ ] Confirm the Photo Picker redacts location
- [ ] **Time a full scan of the owner's real library** — total photos, how many geotagged,
      seconds end to end
- [ ] Record findings in `docs/PROGRESS.md`; convert Q-001 and Q-008 into decisions

The scan timing matters as much as the permission result. It decides whether the MVP needs
a database at all (Q-008) and whether the performance work in DESIGN.md §5 is urgent or
premature. Expect a large library: counts visible in the reference implementation imply
well over 13,000 geotagged photos, plausibly 20k–50k. Measure it properly.

## M0.5 · Project setup — `not started`
Small; can run alongside M0.

- [ ] `git init`, `.gitignore`, initial commit of the docs
- [ ] Settle map SDK (D-012) using M0 findings, weighted by D-015
- [ ] Register Play developer account ($25) — starts the closed-testing clock, which is
      calendar time and runs in parallel with development for free
- [ ] Minimum supported Android version (Q-006)
- [ ] Videos in or out (Q-003) — only blocking if M1 turns out to need persistence

## M1 · The MVP — `not started`
**This is the product.** A map opening on the whole library, numbered clusters that divide
and coalesce as you zoom, and tap-through to the photos behind any cluster. Shippable and
genuinely useful on its own.

- [ ] Compose app shell that launches **straight to the map** — no splash, no menu
- [ ] Library scan reading id + lat/lng + date for every geotagged photo
- [ ] Persistence *only if Q-008 says it is needed*
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
