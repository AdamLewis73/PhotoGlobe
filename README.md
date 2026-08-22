# PhotoGlobe

An Android app that maps your photo library. Geotagged photos appear on a world map as
clusters showing how many were taken in each place; clusters divide as you zoom in and
coalesce as you zoom out. Tap a cluster to see those photos.

Samsung Gallery already has a map like this, buried four taps deep inside photo details
where almost nobody finds it. **The product is making that the front door.**

**Status:** M1 in progress. The map renders, the library scan works, and clustering with
count badges is live. Personal project.

## Why it is built this way

Four constraints shape nearly every decision:

- **Zero recurring cost.** No servers, no paid APIs, no billing account. MapLibre needs no
  API key at all, and reverse geocoding is done offline from bundled data.
- **Local-first.** No account, no upload, no cloud sync. This app is a record of everywhere
  you have been; it stays on the device. Map tiles are the only network dependency.
- **Read-only.** Photos are never moved, modified or deleted — only referenced.
- **Never invent a location.** Photos without GPS are ignored unless you place them
  yourself. Anything inferred is marked as inferred and must be confirmed.

## Documentation

This project is deliberately context-heavy so any session can resume cold.

| File | Purpose |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Entry point — hard rules and current state. **Start here.** |
| [docs/DESIGN.md](docs/DESIGN.md) | Technical reference. §0 defines the MVP |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Numbered decision log with rationale — including the corrections |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestones and status |
| [docs/PROGRESS.md](docs/PROGRESS.md) | Append-only session log |
| [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) | What is still unresolved |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Terms, written for someone new to Android and spatial data |

The decision log records reasoning, not just conclusions, and keeps entries that later
turned out to be wrong alongside the corrections that superseded them. That is deliberate —
a decision made and not logged is one that gets re-argued three sessions later.

## Building

Requires Android Studio with SDK 36 and a JDK 17+.

```
./gradlew assembleDebug
```

No API keys and no configuration are needed. `local.properties` is generated locally and
is not committed.

## Stack

Kotlin, Jetpack Compose, MapLibre Native, Room, WorkManager, Coil.
minSdk 33, targetSdk 36.
