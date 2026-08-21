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
