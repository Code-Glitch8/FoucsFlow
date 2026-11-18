# FocusFlow

An offline-first, native Android study-focus app (Kotlin, Jetpack Compose, Material 3, Room, DataStore, Coroutines/Flow).
No accounts, no analytics, no ads, no uploads. Everything is stored on the phone.

## Getting the APK

Every push to `main` runs the GitHub Actions workflow (`.github/workflows/android.yml`), which runs the unit tests and builds a debug APK.
Open the latest run under **Actions** and download the **focusflow-debug-apk** artifact (also **unit-test-reports**).
To build locally: `./gradlew testDebugUnitTest assembleDebug` (JDK 17, Android SDK 36).

## What it does

| Area | What works |
|---|---|
| Tabs | Home, Tasks, Focus, Statistics, Settings. Dark theme by default, light available. |
| Subjects & chapters | Built-in and custom subjects; reusable chapters with search, add, rename, delete, multi-select. |
| Session setup | Subject, chapters, text or voice description, editable tasks with subtasks and estimates, timed (15/25/30/45/60/90/custom) or open-ended, strict or normal, presets. |
| Tasks from your description | On-device AI when a model is installed, otherwise simple rules. Suggestions are always labelled with their source, shown for review, and **never** start or complete anything until you accept them. |
| Focus session | Foreground service with persistent notification; timing is derived from stored timestamps so app restarts or process death don't change it; break time is excluded from focus time. |
| Earned breaks | Short/long breaks earned by tasks *you* mark done, configurable counts and lengths. |
| Blocking | Accessibility-service app blocking with a block screen, allowed list, essentials (calls, phone, settings, etc.) never blocked, emergency exit that is separate from ordinary controls. Optional usage-stats fallback. |
| YouTube Shorts | Block Shorts while leaving normal YouTube usable; optional redirect away from the Home feed; a "Test the detector" screen. |
| Floating assistant | Draggable overlay bubble (position remembered) with timer, tasks and breaks. |
| History & summaries | Filter by date, subject, chapter, type. Each finished session gets a summary labelled "on-device AI" or "plain summary (not AI)". Numbers in AI text are checked against the real session data. |
| Unfinished work | Open tasks stay on your list with their subject/chapter and can be added to a new session without duplicates. |
| Statistics | Daily/weekly focus charts, time per subject and chapter, sessions, done/unfinished tasks, last studied, streak. Time is never presented as mastery; optional self-rated confidence (1–5) is kept separate. |
| Data | Export/import backup, delete history, reset everything, preset management. |
| Home-screen widgets | Quick Start, saved Focus Profiles (up to four), and 7-day study analytics. Profile widgets start the saved timer and its existing app/site blocking rules in one tap. |
| SnapGPT | A profile can optionally launch the installed SnapGPT Android app (`com.snapgpt.assistant`) when started from a widget. |

## Offline AI and voice

* AI uses the on-device LiteRT-LM runtime. **No model is bundled.** In Settings → Offline AI & voice you can download one (Qwen3 0.6B ≈ 0.6 GB or Gemma 4 E2B ≈ 2.6 GB) after an explicit confirmation, or import a `.litertlm` file you downloaded yourself. This download is the only use of the internet.
* The app checks free storage before downloading and total RAM before loading, and falls back to rules if the model fails or times out.
* Voice input uses Android's `SpeechRecognizer`. By default it only runs with an **on-device** recognizer; if your phone has none you can type instead, or switch the setting to allow Android's default recognizer (which may send audio to the speech provider).

## Honest limitations

* **Not tested on a physical device.** The app compiles, the unit tests pass in CI, but accessibility blocking, the overlay bubble, the foreground service, voice and the on-device model have not been exercised on real hardware by the author. Expect rough edges and please report them.
* Android cannot truly lock a phone. A determined user can disable the accessibility service, force-stop the app, reboot into safe mode, or uninstall. "Strict" means the app makes leaving harder, not impossible.
* The YouTube Shorts detector relies on YouTube's current screen layout (view IDs and English tab labels). YouTube can change that at any time, and non-English interfaces may not be recognised. Use the detector test screen to check your version. Default mode blocks Shorts only during focus sessions.
* Blocked apps are sent to the home screen and a block screen is shown; some Android versions limit launching activities from the background, so the block screen may occasionally not appear even though the app is closed.
* AI output can be wrong. Task suggestions that mention topics you didn't write are dropped, and summaries with unknown numbers are replaced, but you should still review them. Small models are weaker than cloud assistants.
* Gemma/other gated models may require accepting a license in a browser; use "Import model file" in that case. Model download URLs point at Hugging Face and may change.
* Backups copy the whole database and are applied the next time the app starts. They don't include theme/other settings or AI models. Backups from a newer app version are rejected.
* Offline speech needs the language pack for your language to be installed.
* Statistics attribute a session to the day it started; a session covering several chapters shares its time equally between them.
* Widgets are added from the Android launcher’s Widgets picker. The Focus Profiles widget displays up to four saved profiles; configure each profile in Start studying before adding it.

## Architecture

MVVM, manual dependency container (`AppContainer`), single Activity with Compose navigation.

* `data/db` – Room entities, DAOs, schema (version 1, schema export enabled, destructive migration deliberately off).
* `data/repo` – repositories (sessions, tasks, subjects, presets, blocking rules, history, backup).
* `data/settings` – DataStore preferences.
* `domain` – pure, unit-tested logic: session clock, break rules, task normalisation, blocking policy, YouTube screen classifier, statistics.
* `service` – foreground session service, accessibility service, block screen, overlay bubble, usage-stats monitor.
* `ai`, `speech` – model manager, LiteRT-LM wrapper, rule-based extractor, reply parser with grounding checks, template summaries, speech recogniser.
* `ui` – Compose screens.

## Permissions

Notifications (ongoing session), Accessibility (app blocking, YouTube detection — reads package names, and screen elements only inside YouTube), Display over other apps (block screen and bubble), Usage access (optional fallback), Microphone (voice input, asked on tap), Internet (model download only).
