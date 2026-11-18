# FoucsFlow

A native Android study-focus app built with Kotlin, Jetpack Compose, Room, DataStore and Coroutines.

## Getting the APK

Every push runs **Android CI**, which runs the unit tests and builds a debug APK. Open the latest run under **Actions** and download the `focusflow-debug-apk` artifact (the unit test report is uploaded separately).

To build locally: `./gradlew testDebugUnitTest assembleDebug` (JDK 17 and Android SDK 36).

## Features

- Subjects, chapters, custom chapters, reusable presets and editable study tasks.
- Timed and open-ended focus sessions, strict/normal mode, session timer and ongoing notification.
- User-completed tasks, configurable earned breaks and unfinished-work tracking.
- Accessibility-based app blocking, allowed apps, YouTube Shorts detection and a dedicated blocking page.
- Draggable floating assistant with timer, tasks and break controls.
- Session history, focus statistics, streaks and chapter statistics.
- App icons in the blocked/allowed application picker.
- AI task suggestions and session summaries through a user-configured API.
- Direct local-network history synchronisation between two paired phones.

## AI API setup

Open Settings → **AI API & voice**. Add a Gemini API profile or an OpenAI-compatible endpoint, enter its model ID and API key, save it and test it. Multiple profiles can be saved and switched. API keys and profile details are encrypted locally using Android Keystore and are not included in the app's Room backup or Wi-Fi sync.

There is deliberately **no bundled model and no rule-based AI fallback**. When no profile is configured or a request fails, the app reports the problem; tasks can still be added manually. The API provider receives the prompt and session facts needed to generate the requested output. Voice recognition has its own offline-only setting.

## Local Wi-Fi sync

Open Settings → **Sync with your other phone** on both phones. Generate a sync code on one phone and enter exactly the same code on the other. When FocusFlow is running and both devices are reachable on the same local Wi-Fi network, they discover one another using LAN broadcast and exchange encrypted, completed-session snapshots. No login, cloud database or external sync server is used.

The sync payload uses AES-GCM encryption derived from the shared code. Each session retains a stable origin-device ID so a relayed record is not duplicated. The merged history feeds the session-history, focus-time, session-count and streak views. Active sessions stay local to the phone where they started. API credentials are never sent between phones.

Sync depends on local network broadcast and device-to-device traffic being allowed by the router. A network with AP/client isolation can prevent it. Android may pause background processes when the app is not running; when FocusFlow is opened again, it retries discovery and synchronisation. Use **Sync now** to request an immediate discovery pass.

## Blocking and Android permissions

- Enable the FocusFlow Accessibility service for app and YouTube Shorts detection.
- Allow display over other apps for the dedicated block screen and floating assistant.
- Notification permission allows the ongoing session notification.
- Usage access is an optional fallback if Accessibility is disabled.
- Microphone permission is requested only when voice input is started.

A normal Android application cannot make the phone impossible to leave. Accessibility can be disabled in system settings, and Android/OEM battery controls may stop background work. Android can also restrict opening a blocking Activity from the background. For these reasons, test blocking and overlays on the target phone and keep essential calling/system apps accessible.

## Local test status

The GitHub Actions workflow is the source of truth for compilation and unit-test results. It does not replace testing on two physical phones: local broadcast discovery, router isolation, OEM battery restrictions, accessibility timing and overlay behaviour need to be checked on the actual devices.
