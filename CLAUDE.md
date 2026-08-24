# PhotoGlobe

Android app that maps your photo library. Photos appear on a world map as clusters showing
how many were taken in each place; clusters divide as you zoom in and coalesce as you zoom
out. Later: tap a cluster to see those photos, and a stats screen that turns the library
into a personal travel record — countries, cities, trips, counts.

**Status:** Planning. No code written yet.
**Owner:** Adam. Personal project, shipping to the Play Store.

---

## Read this first, every session

1. This file — hard rules and current state
2. `docs/DESIGN.md` §0 — the product thesis and what the MVP is. Short; read it every time
3. `docs/ROADMAP.md` — what milestone we're in and what's next
4. `docs/PROGRESS.md` — the last 1–2 entries, for where the previous session left off
5. `docs/DECISIONS.md` — before proposing anything architectural, check it isn't already decided

The rest of `docs/DESIGN.md` is a deep reference. Read the section relevant to the area
being worked on; don't read it end-to-end every session.

## What this app is, in one sentence

A map that opens onto your whole photo library as numbered clusters that split and merge as
you zoom. Samsung Gallery already has this, buried four taps deep inside photo details where
almost nobody finds it. **The product is making that the front door.** Everything else is an
enhancement to a thing that already works. (D-013)

## Hard rules

These are settled. Do not relitigate them without the owner explicitly reopening one.

1. **Zero recurring cost.** No servers, no backend, no paid APIs, no subscriptions, no
   per-call services. A one-time $25 Play registration is the only accepted cost. If a
   proposed feature needs a billable service, it is the wrong design — find another way.
2. **Local-first.** No account, no login, no upload, no cloud sync, no analytics on
   location data. Everything lives on the device. This app is a complete record of where
   the user has been; it never leaves the phone.
3. **Read-only with respect to the user's photos.** Never move, modify, rename, re-encode
   or delete a photo. Store references and derived metadata only.
4. **Never invent a location silently.** A photo with no GPS is ignored by default. Any
   inferred location must be visibly marked as inferred and explicitly confirmed by the
   user before it counts as real.
5. **Offline geocoding only.** Country/region/city lookup uses bundled datasets. Never
   call a geocoding API. (Follows from rule 1, stated separately because it's easy to
   forget mid-feature.)
6. **Must work with no network.** Except for map tiles, every feature works on a plane.
7. **Android only.** Kotlin, Jetpack Compose, minimum sensible API level. No cross-platform
   framework, no iOS.
8. **Protect the MVP.** Nothing enters M1 that isn't map, scan, or clusters. Good ideas go
   into the roadmap under a later milestone, never into the current one.
9. **Measure before optimizing.** Several design sections assume a 40,000-photo library
   that has never actually been counted. M0 produces the real numbers; do not build against
   the imagined ones.
10. **Branch and PR, always.** Never commit directly to `master`. See Git workflow below (D-038).
11. **Keep the docs current.** See below. A decision made and not logged is a decision that
    will be re-argued in three sessions' time.

## Documentation duties

This project is deliberately context-heavy so future sessions can resume cold.

- **A decision gets made →** append it to `docs/DECISIONS.md` with a number, the date, and
  the *why*. The reasoning matters more than the conclusion.
- **A question comes up that can't be answered now →** add it to `docs/OPEN-QUESTIONS.md`.
  When it gets answered, move it to `docs/DECISIONS.md` and remove it from the questions file.
- **A session ends →** append an entry to `docs/PROGRESS.md`: what happened, what changed,
  what the next session should pick up. Keep it short and factual.
- **Milestone status changes →** update `docs/ROADMAP.md`.
- **A term appears that isn't obvious →** add it to `docs/GLOSSARY.md`. The owner is new to
  Android and to mapping; unexplained jargon is a defect.

Do this as work happens, not in a batch at the end.

## Git workflow

**Never commit directly to `master`.** Every change goes on a branch and merges through a
pull request (D-038).

1. Branch first: `git checkout -b m1/tap-through-grid`
   Naming is `<milestone-or-type>/<short-kebab-description>` — `m1/`, `m2/`, `docs/`, `fix/`.
2. Commit there, then `git push -u origin <branch>`
3. Open a PR with `gh pr create --base master --head <branch> --title ... --body-file ...`.
   The CLI is installed at `C:\Program Files\GitHub CLI\gh.exe` and authenticated.
   Use `--body-file`, not `--body` — a multi-line body passed inline fails argument parsing.
4. **The owner merges.** Never merge a PR unless explicitly asked to.
5. **One PR open at a time** (D-047). Wait for it to merge before opening the next.
   Concurrent PRs always conflict, because every change appends to `PROGRESS.md`.
   Work finished while a PR is open waits on a local branch.

The repo is at https://github.com/AdamLewis73/PhotoGlobe

## Communication notes

- The owner has minimal experience with Android, spatial data, and rendering performance.
  Explain mechanisms rather than naming them. Concrete numbers beat adjectives.
- Flag assumptions explicitly instead of building on them silently.
- **Ask before bulk operations.** Generating many files, consuming significant disk,
  or running long jobs needs the owner's agreement first - an emulator is still their
  machine (D-046). After any interrupted operation, check what actually landed on disk
  before reporting; an interruption does not guarantee nothing happened.
- **Never cite a code without saying what it is.** Writing "this conflicts with D-009" or
  "Q-012 will bite here" is useless to someone who would have to go and look it up. Say
  "photos without GPS are ignored by default (D-009)" or "tapping a huge cluster would load
  an unusable grid (Q-012)". The number is a reference for later, not a substitute for the
  point. This applies to conversation *and* to prose in the docs - the decision log is the
  one place where a bare cross-reference is fine, because the reader is already in it.

## Current state

**M1 is complete. The MVP works.**

The app opens straight onto a world map, scans the photo library for geotagged photos,
renders them as clusters with live counts that divide and coalesce as you zoom, and tapping
a cluster opens every photo inside it in a grid that taps through to full screen. After the
first scan it only reads what is new, so launches are instant.

Everything was verified running on an emulator against a 22-photo fixture, not merely
compiled.

What M0 proved, on an emulator rather than the owner's phone (D-028):

- GPS **is** readable — verified against independently established ground truth, zero
  errors (D-023)
- The Android 14+ **Curated (partial) grant returns unredacted GPS**, so the app does not
  depend on Play approving broad library access (D-024). Largest external risk, closed
- No shortcut exists — per-file EXIF reads are mandatory (D-026)
- ~4.16 ms/photo, so **Room is required** (D-027)

The stack: **MapLibre** with a key-free CARTO Positron style (D-036, D-037), minSdk 33 /
targetSdk 36 (D-033), videos schema-ready but not scanned (D-032). No Google Cloud account,
no API key, no billing.

**The next decision is M2 versus M3, and it depends on data nobody has.** M2 is performance
work - viewport-bounded queries and precomputed cluster tiers - which hard rule 9 says must
not be built against imagined numbers. M3 is offline geocoding and the stats screen, which
is user-visible value. **Run the app against the real library first** (Q-010); if it stays
smooth, M2 is premature and M3 should come next.

**Test fixture:** 22 real geotagged photos at `E:\PhotoGlobe-testphotos`, outside the repo.
See D-029 for the emulator load procedure — the `scan_volume` step is mandatory and
non-obvious. Emulator `Pixel_9_Pro` (API 36) is configured and has been used throughout.

**Known open items:** Q-010 (real-scale behaviour unmeasured), Q-011 (79 MB debug APK —
MapLibre ships every ABI), Q-002 (does the owner shoot with a standalone camera, which
decides how much M4 matters).

**Repo:** https://github.com/AdamLewis73/PhotoGlobe — one PR at a time (D-047).

## Document map

| File | Purpose |
|---|---|
| `README.md` | Public front door for the GitHub repo |
| `CLAUDE.md` | This file. Entry point, hard rules, working agreement |
| `docs/DESIGN.md` | Technical design reference — §0 defines the MVP, read it every session |
| `docs/DECISIONS.md` | Numbered, dated decision log with rationale |
| `docs/ROADMAP.md` | Milestones and status |
| `docs/PROGRESS.md` | Append-only session log |
| `docs/OPEN-QUESTIONS.md` | Unresolved questions blocking or shaping future work |
| `docs/GLOSSARY.md` | Domain and technical terms |
