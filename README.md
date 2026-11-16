# FocusFlow

An offline-first, native Android study app: strict app blocking, YouTube Shorts
blocking, task-based breaks, a floating study assistant, and on-device AI task
generation. *Track what you actually accomplish, not just how long you studied.*

> **Status: under construction (Phase 1 of 8).** Only the project skeleton, theme,
> bottom navigation and CI exist so far. The feature list below is the plan,
> not what currently works. This file is updated at the end of every phase.

## Build status by phase

| Phase | Scope | State |
|------|-------|-------|
| 1 | Project, theme, navigation shell, CI that builds the APK | in progress |
| 2 | Room database, subjects, chapters, tasks, presets | not started |
| 3 | Session creation, timed and open-ended focus | not started |
| 4 | Strict blocking, YouTube Shorts detection | not started |
| 5 | Floating assistant, break system | not started |
| 6 | On-device AI and offline speech | not started |
| 7 | History, summaries, analytics | not started |
| 8 | Testing, polish, final APK | not started |

## Getting the APK

Every push runs the **Android CI** workflow (`.github/workflows/android.yml`),
which runs the unit tests and builds a debug APK.

1. Open the repository's **Actions** tab and select the latest run.
2. Download the **focusflow-debug-apk** artifact from the run summary.
3. Unzip it and install `app-debug.apk` (enable "install unknown apps" for your file manager).

## Building locally

Requires JDK 17 and the Android SDK (API 36).

```
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Stack

Kotlin, Jetpack Compose with Material 3, MVVM, Room, DataStore, Coroutines/Flow.
minSdk 26, compile/target SDK 36. Versions are pinned in `gradle/libs.versions.toml`.

## Privacy

No accounts, analytics, ads or cloud services. Study data stays on the device.
