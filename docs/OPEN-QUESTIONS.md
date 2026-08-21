# Open Questions

Unresolved items that block or shape future work. When one is answered, move it to
`docs/DECISIONS.md` with its rationale and delete it from here.

Format: **Q-nnn · raised date · blocks what** — the question, then why the answer matters.

---

### Q-001 · 2026-08-07 · blocks M1, D-012
**Can the app read GPS coordinates from photos at scale on current Android?**
Specifically, three sub-questions, all answered by the M0 spike:
1. Does broad `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION` + `setRequireOriginal()`
   return real lat/lng on the current target SDK, on a real device?
2. Does the Android 14+ partial grant (`READ_MEDIA_VISUAL_USER_SELECTED`) also return
   unredacted GPS? This determines whether the "Curated" permission tier is viable.
3. Confirm the Android Photo Picker redacts location, as documented.

If (1) fails the product changes fundamentally. If (2) fails, the app depends entirely on
Play approving broad access — a single point of failure outside the owner's control.

### Q-002 · 2026-08-08 · shapes the interpolation feature's priority
**Does the owner travel with a standalone camera, or shoot everything on the phone?**
Dedicated cameras (mirrorless/DSLR) almost never have GPS, so their photos carry a
timestamp and no location. Timestamp interpolation — inferring position from surrounding
geotagged phone photos — is transformative for camera users and near-irrelevant for
phone-only users. Phone-only means interpolation drops from headline feature to
nice-to-have, and M3 shrinks.

### Q-003 · 2026-08-08 · blocks M1 schema
**Are videos in scope?**
Same MediaStore mechanics and similar metadata. Cheap to include from the start; expensive
to retrofit, because it touches the schema and every query. Needs an answer before the
first migration is written.

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

### Q-006 · 2026-08-08 · shapes M2
**Minimum supported Android version.**
Affects how much legacy permission handling is needed. Higher minimum = less branching,
fewer devices. Should be decided against what the owner and testers actually carry.

### Q-007 · 2026-08-08 · non-blocking
**What happens to a photo deleted from the device after being mapped?**
Options: drop the pin, keep it as a tombstone using the cached thumbnail, or ask. Affects
the reconciliation step of the sync job. Needs a decision before sync is written, but the
answer is not architecturally load-bearing.

### Q-008 · 2026-08-08 · shapes M1 architecture
**Does the MVP need a local database at all, or is a live MediaStore scan fast enough?**
If a full library scan takes ~2 seconds, the MVP can hold everything in memory and skip
Room entirely — 40,000 rows of (id, lat, lng, date) is under 2 MB. If it takes 90 seconds,
persistence is mandatory, because cold-start-to-map is the headline metric (D-013).
Answered directly by the scan timing already scheduled in M0. Do not pre-build the
persistence layer before this number exists.

