# Environment and build notes

Machine-specific facts and hard-won gotchas, gathered so a fresh session does not have to
re-derive them. Everything here was established by doing it, not by assuming.

---

## This machine

| | |
|---|---|
| Android Studio | `E:\Android Studio` |
| Bundled JDK (JBR) | `E:\Android Studio\jbr` — JDK 21. Use this, **not** the Java 8 on PATH |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` |
| Installed platform | **android-36 only** — this constrains dependency versions, see below |
| Build tools | 35.0.0, 36.0.0, 36.1.0 |
| adb | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| Emulator AVDs | `Pixel_9_Pro` (API 36) and `Medium_Phone_API_36.1` |
| GitHub CLI | `C:\Program Files\GitHub CLI\gh.exe`, authenticated as AdamLewis73 |
| Python | `C:\Program Files\Python38\python.exe` — no PIL, no piexif |

Build from the repo root with `JAVA_HOME` pointed at the JBR:

```
export JAVA_HOME="/e/Android Studio/jbr"
./gradlew assembleDebug
```

## Build traps that have already cost time

**1. Dependency versions are capped by the installed platform.** Only `android-36` is
installed, and AGP 8.11.0 recommends at most compileSdk 36. Compose BOM `2026.08.00` requires
compileSdk **37** and fails the build with an AAR-metadata error naming
`material-ripple-android`. Pinned versions that work: Compose BOM `2026.06.01`,
`activity-compose 1.12.4`, `lifecycle-* 2.10.0`.

**2. Room drags in a newer Kotlin stdlib than the compiler.** Room 2.8.4 pulls
`kotlin-stdlib 2.4.0` while the project compiles with Kotlin 2.1.20. The symptom is
*Room's own generated code* failing to resolve `mutableListOf` and `StringBuilder` - an error
that points at generated files and says nothing about the real cause. Fixed by a
`resolutionStrategy` in `app/build.gradle.kts` forcing `kotlin-stdlib` to 2.1.20. Revisit
when the Kotlin version moves.

**3. Verify library versions against Maven before using them.** Several versions were guessed
early in the project and were wrong. `curl` the `maven-metadata.xml` from
`https://dl.google.com/dl/android/maven2/...` or `https://repo1.maven.org/maven2/...`.

## Emulator and adb

**Use PowerShell for adb, not Git Bash.** MSYS rewrites Unix-looking paths, so
`adb shell mkdir -p /sdcard/DCIM/Camera` becomes a Windows path and fails with
`mkdir: 'C:': Read-only file system`.

**Launch the emulator detached.** Starting it from a shell that then exits kills it:

```
Start-Process -FilePath "<sdk>\emulator\emulator.exe" -ArgumentList '-avd','Pixel_9_Pro','-no-boot-anim'
```

**Wait for boot properly** — `adb wait-for-device` returns before Android is up. Poll
`getprop sys.boot_completed` until it returns `1`.

## Loading the test fixture (D-029)

22 real geotagged photos live at `E:\PhotoGlobe-testphotos`, deliberately outside the repo.
`.gitignore` blocks `testphotos/` and image extensions so they can never be committed.

```
adb push "E:\PhotoGlobe-testphotos\." /sdcard/DCIM/Camera/
adb shell 'content call --uri content://media/external --method scan_volume --arg external_primary'
```

**The second line is not optional and is the least obvious thing in this document.** Files
placed by `adb push` land in MediaStore with `is_pending=1` owned by `com.android.shell`,
which makes them invisible to other apps *and* to the system photo picker. Without the
rescan the app enumerates nothing and the picker reports "No photos yet", with no error
anywhere to explain it. Bulk-updating `is_pending` on the collection URI is rejected by the
provider; the rescan is the correct route.

Granting permissions without touching the UI:

```
adb shell pm grant com.photoglobe android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.photoglobe android.permission.ACCESS_MEDIA_LOCATION
```

Note this only produces the **Full** tier. The **Curated** tier cannot be simulated with
`pm grant`, because the user-selected set is managed by MediaProvider and would be empty -
it has to go through the real dialog.

## Reading results off the device

Screenshots: `adb shell screencap -p /sdcard/s.png` then `adb pull`. Do not pipe
`exec-out screencap` through PowerShell; it mangles the binary stream.

**Clean up after yourself.** Screenshots written to `/sdcard` get indexed by MediaStore and
will show up as photos in the app on the next scan, quietly corrupting counts. Delete them
and rescan the volume when finished.

## Things not to do

**Do not generate bulk data without asking (D-046).** An agent began creating 800 duplicate
photos on the emulator to stress-test the cluster grid and was rightly stopped. An emulator
is still the owner's disk. Realistic-volume testing is a thing to propose with the cost
stated, not to start.

**After any interrupted operation, check what actually landed on disk before reporting.**
In that same incident 66 files had already been written when the command was cancelled, and
the agent reported that nothing had run.
