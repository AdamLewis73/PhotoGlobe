# Glossary

Terms used across the project docs. The owner is new to Android and to spatial data, so
anything non-obvious belongs here. Add to it rather than assuming.

---

## Photography

**EXIF** — Metadata embedded inside a photo file: timestamp, camera model, exposure, and
optionally GPS coordinates and altitude. It travels with the file.

**Geotagged** — A photo whose EXIF contains GPS coordinates. Phones do this automatically
when location is enabled; most standalone cameras do not.

**Mirrorless / DSLR** — Standalone digital cameras with detachable lenses (Sony A7,
Fujifilm X-T5, Canon R-series). DSLRs are the older mirror-based design; mirrorless is the
modern successor. **Almost none have GPS**, so their photos carry a timestamp and no
location — which is the entire reason timestamp interpolation exists as a feature idea.

**Redacted metadata** — Android deliberately stripping GPS from a photo before handing it
to an app that lacks permission to see it. The app receives a valid photo that simply
appears to have no location. It fails silently, which makes it a common source of lost days.

## Android platform

**MediaStore** — Android's system-wide database of media files. The way an app enumerates
the user's photo library: query it and get back rows with an id, a URI, a date, and so on.

**Content URI** — A reference to a file owned by another app or by the system, e.g.
`content://media/external/images/media/12345`. PhotoGlobe stores these instead of copying
photos. They can change or go stale if a photo is moved or deleted, which is why the schema
also stores a content signature.

**`READ_MEDIA_IMAGES`** — The runtime permission for reading the whole photo library.
Play requires a declaration justifying it. "Broad access."

**`READ_MEDIA_VISUAL_USER_SELECTED`** — Android 14+ partial access: the user picks specific
photos, the app sees only those, and can ask for more later. "Curated access."

**`ACCESS_MEDIA_LOCATION`** — A *separate* runtime permission required to see unredacted
GPS in EXIF. Easy to forget; without it every photo looks un-geotagged and no error is
raised.

**`setRequireOriginal()`** — The MediaStore call that asks for the unredacted file. Needed
in combination with `ACCESS_MEDIA_LOCATION`; the permission alone is not enough.

**Photo Picker** — Android's built-in photo selection UI. Needs no permission at all, which
makes it appealing, but it **strips location metadata** from what it returns, so it cannot
support this app's core feature.

**SAF (Storage Access Framework)** — The system file-picker (`ACTION_OPEN_DOCUMENT`). Unlike
the Photo Picker it does not redact EXIF.

**Jetpack Compose** — The modern Android UI toolkit. You describe what the screen should
look like for a given state, rather than mutating widgets step by step.

**Room** — Android's standard local database layer, a typed wrapper over SQLite. Where
PhotoGlobe's photo index, places and settings live.

**WorkManager** — Android's scheduler for background jobs that must survive app close and
device reboot. Used for the periodic library sync.

**ContentObserver** — A callback fired when a content provider's data changes. Can be used
to notice new photos, but only reliably while the app is in the foreground.

**Target SDK / minimum SDK** — Target SDK is the Android version the app declares it is
built for, which determines which platform behaviours apply; Play requires it to stay
recent. Minimum SDK is the oldest Android version the app will install on.

**Main thread** — The single thread that draws the UI and handles input. Anything slow that
runs on it makes the app visibly stutter, so expensive work (decoding images, generating
marker bitmaps, database queries) must run elsewhere.

## Maps and spatial data

**Tiles** — The small square images or vector chunks a map is assembled from. They are
fetched over the network, which makes them the app's only unavoidable network dependency.

**Clustering** — Merging nearby markers into one when zoomed out, and splitting them apart
when zoomed in. What "flags combine and branch out" means in implementation terms.

**Viewport / bounds** — The geographic rectangle currently visible on screen. Querying only
what's inside it is the main reason the app can handle a large library.

**Spatial index** — A database index that makes "find everything near here" fast. Without
one, answering that question means examining every row in the table.

**Geohash** — An encoding that turns a latitude/longitude pair into a single short string
where nearby places share a leading prefix (Kyoto ≈ `xn0m7`; everything within a few km
also starts `xn0m`). This converts a two-column geographic search into an ordinary indexed
prefix search, which databases are very good at.

**S2 cell** — Google's equivalent idea using integers instead of strings, dividing the
globe into a hierarchy of cells. Interchangeable with geohash for this project's purposes.

**Reverse geocoding** — Turning coordinates into a place name ("35.01, 135.76" → "Kyoto,
Japan"). PhotoGlobe does this offline from bundled data rather than calling an API.

**Point-in-polygon** — The test for whether a coordinate falls inside a shape. How country
lookup works against bundled border polygons.

**Natural Earth** — A free public-domain dataset of country and region borders.

**GeoNames** — A free geographic database; `cities1000` is its set of ~150,000 cities with
populations over 1,000, used here for nearest-city lookup.

**GPX** — A standard file format for recorded GPS tracks, exported by watches and loggers.
An optional source for placing photos that lack their own coordinates.

**Frame budget** — At 60 frames per second, each frame has ~16 milliseconds to complete all
its work. Exceeding it drops frames, which the user perceives as lag.

## Project conventions

**M0, M1, …** — Milestones in `docs/ROADMAP.md`.

**D-nnn** — A numbered entry in `docs/DECISIONS.md`.

**Q-nnn** — A numbered entry in `docs/OPEN-QUESTIONS.md`.

**Tier: Full / Curated / Manual** — The three levels of photo access the app must handle,
depending on what permission the user grants. See `docs/DESIGN.md` §10.

**Marker clustering** — The specific technique behind the reference feature: nearby markers
merge into one bubble labelled with how many items it contains, splitting apart as you zoom
in and merging as you zoom out. It is the documented default behaviour of Google's
`android-maps-utils` library, not something this project needs to invent (D-015).

**Count badge** — A cluster marker showing a number rather than a photo. Cheap, because only
a handful of distinct images are ever needed ("1", "2", … "9", "10+", "50+", "100+"), and
those are generated once and shared across thousands of markers. The MVP marker style (D-014).

**Cold-start-to-map** — Time from tapping the app icon to a usable map on screen. The
project's headline metric (D-013), because the app's premise is being faster to reach than
the buried version in Samsung Gallery.

**System app / preinstalled app** — An app shipped with the phone by the manufacturer, such
as Samsung Gallery. These hold privileged permissions that apps installed from the Play
Store cannot obtain. Relevant because the reference implementation is one of them.
