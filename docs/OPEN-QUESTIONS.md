# Open Questions

Unresolved items that block or shape future work. When one is answered, move it to
`docs/DECISIONS.md` with its rationale and delete it from here.

Format: **Q-nnn · raised date · blocks what** — the question, then why the answer matters.

---

### Q-002 · 2026-08-08 · shapes the interpolation feature's priority
**Does the owner travel with a standalone camera, or shoot everything on the phone?**
Dedicated cameras (mirrorless/DSLR) almost never have GPS, so their photos carry a
timestamp and no location. Timestamp interpolation — inferring position from surrounding
geotagged phone photos — is transformative for camera users and near-irrelevant for
phone-only users. Phone-only means interpolation drops from headline feature to
nice-to-have, and M3 shrinks.

### Q-004 · 2026-08-08 · shapes M4
**How are trips presented?**
Auto-detection by time+space gaps is agreed in principle; the presentation is not designed.
Open sub-questions: a separate trips screen vs. a filter on the map; how a trip is named
and by whom; whether trip boundaries are user-editable; whether a trip is a first-class
stored entity or a derived view recomputed on demand.

### Q-005 · 2026-08-08 · shapes M4
**What exactly is on the stats screen?**
General shape agreed (see `docs/DESIGN.md` §11) but not specified. Deliberately deferred
until real data exists to look at — designing stats against an imaginary library produces
metrics nobody wants.

### Q-010 · 2026-08-08 · non-blocking, revisit during M1
**What is the real per-photo EXIF read cost on the owner's actual phone and library?**
D-027 measured 4.16 ms/photo across 25 photos on an emulator reading from a host SSD.
Good enough to settle that persistence is required; not good enough to size the first-run
experience precisely. Also still unknown: the true size of the owner's library and what
fraction is geotagged - the reference implementation's counts imply 20k-50k, but that is an
inference, not a measurement. Re-measure when the real app runs on real hardware.

### Q-011 · 2026-08-08 · non-blocking, decide before M6
**The debug APK is 79 MB. How should native ABIs be handled?**
MapLibre ships native libraries for every ABI, and the debug build packages all of them.
Fine for development; not fine for a store listing. Options: `abiFilters` to arm64-v8a
only, or ABI splits / an App Bundle so Play serves the right slice per device. An App
Bundle is the Play default and probably makes this a non-issue, but it should be verified
rather than assumed.

### Q-012 · 2026-08-08 · shapes M2
**What should tapping a very large cluster do?**
`PhotoMap.photoIdsAt` caps cluster expansion at 500 leaves, which is arbitrary. Tapping a
world-zoom bubble covering 8,000 photos would currently load 500 rows and render a grid
nobody can use. Options: page the sheet, cap and show "showing 500 of 8,000", or zoom into
the cluster instead of opening the sheet above some size threshold. The reference
implementation is worth checking again here. Not urgent - the fixture is 22 photos - but it
will matter immediately on a real library.

### Q-013 · 2026-08-08 · cosmetic
**Full-screen viewer does not cover the full screen.**
The `Dialog` leaves a strip at the bottom where the grid shows through, and the status bar
area stays grey. Needs `decorFitsSystemWindows = false` or an edge-to-edge treatment.
Cosmetic only; the viewer works.
