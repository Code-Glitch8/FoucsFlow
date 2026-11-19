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
| Blocking | Accessibility-service app blocking with a block screen, allowed list, essentials (calls, phone, settings, etc.) never blocked, emergency exit that is separate from ordinary controls. Optional usage-stats fallback. A dedicated **Block the entire YouTube app** switch closes YouTube during focus sessions; earned breaks remain available unless YouTube is separately configured to stay blocked through breaks. |
| YouTube Shorts | Block Shorts while leaving normal YouTube usable; optional redirect away from the Home feed; a "Test the detector" screen. |
| Floating assistant | Draggable overlay bubble (position remembered) with timer, tasks and breaks. |
| History & summaries | Filter by date, subject, chapter, type. Each finished session gets a summary labelled "on-device AI" or "plain summary (not AI)". Numbers in AI text are checked against the real session data. |
| Unfinished work | Open tasks stay on your list with their subject/chapter and can be added to a new session without duplicates. |
| Statistics | Daily/weekly focus charts, time per subject and chapter, sessions, done/unfinished tasks, last studied, streak. Time is never presented as mastery; optional self-rated confidence (1–5) is kept separate. |
| Data | Export/import backup, delete history, reset everything, preset management. |

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

## Dashboard widgets, shared session start, AI providers (schema v2)

**Android home-screen widgets.** Add a **FocusFlow · Saved focus preset** widget from the Android launcher, choose one of your existing saved presets, and then tap **Start focus** to launch that exact setup immediately through the same `SessionLauncher` used by the app. Tap the widget card to change its selected preset. You can also add **FocusFlow · Weekly analytics** to see the total focus time and a seven-bar daily chart for the last seven days. The analytics widget refreshes periodically and when the app opens or a session ends.\n\n**Dashboard widgets.** The Home tab shows configurable widgets (Timer, Open-ended, Homework, Subject, Saved setup, Custom).
Tapping one starts its session immediately - no setup screen. *Edit dashboard* adds, edits, deletes, reorders and hides widgets;
they are stored in Room (`dashboard_widgets`, stable UUID ids) and survive restarts. If a session is already running, the tap
shows a message with an "Open session" action instead of starting a second one. If the floating assistant is wanted but the
overlay permission is missing, a dialog explains it and offers to enable it (the tap resumes when you return) or start without the bubble.

**One start path.** `launch/SessionLauncher` is the only place that starts a session: it writes the session through
`SessionRepository.start` (single active session, tasks/chapters linked, history recorded like any other session), starts
`FocusSessionService` (timer/stopwatch, notification, blocking, floating bubble react to the stored session) and does the optional
SnapGPT hand-off. The preparation screen, widgets and presets all use it. `WidgetDraftBuilder` turns a widget into a `SessionDraft` (pure, unit tested).

**Presets** now also store tasks/subtasks (`preset_tasks`), Shorts and floating-assistant switches, app lists and break rules.
A preset without its own app lists/break rules inherits the global ones. While a preset session runs, its own app lists apply
(global lists otherwise). Updating a preset starts from the stored preset so nothing is erased.

**Class 11 chapters.** NCERT Mathematics (14), Physics (14) and Chemistry (9) are added on first launch after the update, once
per `Class11Chapters.SEED_VERSION`, with `INSERT IGNORE` against the unique (subject, name) index: no duplicates, no overwriting of
user chapters, nothing deleted, and a default chapter you delete does not come back. Lists follow the current rationalised NCERT books
(chapters NCERT removed, e.g. Mathematical Reasoning, States of Matter, Hydrogen, s-Block, are not seeded; add them by hand if your school still uses them).

**AI order.** 1) the provider you select in *Settings > AI > Manage providers* (OpenAI-compatible, OpenRouter, Gemini; endpoint, model,
test button, enable switch), 2) the downloaded on-device model, 3) built-in rules / plain template. Every result says which one produced it.
API keys are encrypted with an Android Keystore AES-GCM key, are never logged, never put in URLs, and are never exported. No key is needed to use the app.

**SnapGPT.** Off by default (*Settings > SnapGPT*). FocusFlow can only use what SnapGPT exports. It looks for an activity answering
`com.snapgpt.action.SHOW_FLOATING` (a contract SnapGPT would have to add to show its floating interface) and otherwise opens SnapGPT's normal
launcher activity - once per session - in the package you configure (default `com.snapgpt.assistant`, an assumption: change it to the real id).
If SnapGPT is not installed nothing happens. The focus timer runs in FocusFlow's service, so switching apps never interrupts it.

**Backups.** The database export includes subjects, chapters, tasks, presets and widgets (import replaces the data, so nothing is duplicated;
older v1 backups are migrated on open, newer ones are refused). *Export settings* writes a JSON file with settings and provider endpoints/models,
never API keys.

**CI.** `.github/workflows/android.yml` runs a secret-scan, unit tests (JVM + Robolectric/Room), builds the debug APK and an unsigned release APK,
and uploads the APKs and test reports. Release signing is intentionally not configured in the repo.
