# FocusFlow

A native Android study-focus app (Kotlin, Jetpack Compose, Material 3, Room, DataStore, Coroutines/Flow).
No account or cloud database is required for core study data. Optional API-powered AI sends only the plan or session facts needed for the requested feature to the provider you configure. Optional same-Wi-Fi sync shares completed session history directly between paired phones.

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
| Tasks from your description | Uses the selected external API provider only. If no provider is configured or a request fails, tasks can be entered manually; there is no local-model or rule-based AI fallback. Suggestions are shown for review and never start or complete anything until accepted. |
| Focus session | Foreground service with persistent notification; timing is derived from stored timestamps so app restarts or process death don't change it; break time is excluded from focus time. |
| Earned breaks | Short/long breaks earned by tasks *you* mark done, configurable counts and lengths. |
| Blocking | Accessibility-service app blocking with an immediate block screen, per-app blocked/allowed lists, optional strict whitelist mode (all non-essential apps blocked except allowed apps), essentials (calls, phone, settings, etc.) never blocked, emergency exit separate from ordinary controls. Optional usage-stats fallback. A dedicated **Block the entire YouTube app** switch closes YouTube during focus sessions; earned breaks remain available unless YouTube is separately configured to stay blocked through breaks. |
| YouTube Shorts | Block Shorts while leaving normal YouTube usable; optional redirect away from the Home feed; a "Test the detector" screen. |
| Floating assistant | Draggable overlay bubble (position remembered) with timer, tasks and breaks. |
| History & summaries | Filter by date, subject, chapter and type. Session summaries are generated only through the configured API provider; if unavailable, the app explains that no AI summary was generated. |
| Unfinished work | Open tasks stay on your list with their subject/chapter and can be added to a new session without duplicates. |
| Statistics | Daily/weekly focus charts, time per subject and chapter, sessions, done/unfinished tasks, last studied, streak. Time is never presented as mastery; optional self-rated confidence (1–5) is kept separate. |
| Data | Export/import backup, delete history, reset everything, preset management. |

## API AI and voice

* Add and select a provider in Settings → AI providers (OpenAI-compatible endpoints, OpenRouter and Gemini are supported). The API is used only when you request task suggestions or a session summary.
* API keys are stored encrypted with Android Keystore and are not written to logs or included in settings exports. Re-enter keys after importing settings.
* Without a configured API, task creation and session management remain available manually; AI features do not fall back to downloaded models, rules or template summaries.
* Voice input uses Android SpeechRecognizer. The privacy switch can restrict recognition to an on-device recognizer; otherwise Android's default recognizer may send audio to its speech provider.

## Honest limitations

* **Not tested on a physical device.** CI builds and unit tests do not prove that accessibility blocking, widgets, local Wi-Fi discovery, the overlay bubble, the foreground service or voice behave correctly on every OEM phone. Test those on both target phones. Expect rough edges and please report them.
* Android cannot truly lock a phone. A determined user can disable the accessibility service, force-stop the app, reboot into safe mode, or uninstall. "Strict" means the app makes leaving harder, not impossible.
* The YouTube Shorts detector relies on YouTube's current screen layout (view IDs and English tab labels). YouTube can change that at any time, and non-English interfaces may not be recognised. Use the detector test screen to check your version. Default mode blocks Shorts only during focus sessions.
* Blocked apps are sent to the home screen and a block screen is shown; some Android versions limit launching activities from the background, so the block screen may occasionally not appear even though the app is closed.
* API output can be wrong. Review task suggestions and summaries; the app applies basic grounding checks but cannot guarantee factual accuracy.
* Database backups are separate from settings exports. Settings exports include provider names/endpoints/models but never API keys; re-enter keys after import.
* On-device speech needs the language pack for your language to be installed.
* Statistics attribute a session to the day it started; a session covering several chapters shares its time equally between them.

## Architecture

MVVM, manual dependency container (`AppContainer`), single Activity with Compose navigation.

* `data/db` – Room entities, DAOs, schema (version 1, schema export enabled, destructive migration deliberately off).
* `data/repo` – repositories (sessions, tasks, subjects, presets, blocking rules, history, backup).
* `data/settings` – DataStore preferences.
* `domain` – pure, unit-tested logic: session clock, break rules, task normalisation, blocking policy, YouTube screen classifier, statistics.
* `service` – foreground session service, accessibility service, block screen, overlay bubble, usage-stats monitor.
* `ai`, `speech` – external API client/provider storage, task parser with grounding checks, API summaries, speech recogniser.
* `ui` – Compose screens.

## Permissions

Notifications (ongoing session), Accessibility (app blocking, YouTube detection — reads package names, and screen elements only inside YouTube), Display over other apps (block screen and bubble), Usage access (optional fallback), Microphone (voice input, asked on tap), Internet (API requests and same-Wi-Fi peer sync).

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

**AI.** The provider you select in *Settings > AI providers* (OpenAI-compatible, OpenRouter, Gemini; endpoint, model, test button, enable switch) is the only AI path. API keys are encrypted with Android Keystore AES-GCM, never logged or placed in URLs, and never included in exports.

**SnapGPT.** Off by default (*Settings > SnapGPT*). FocusFlow can only use what SnapGPT exports. It looks for an activity answering
`com.snapgpt.action.SHOW_FLOATING` (a contract SnapGPT would have to add to show its floating interface) and otherwise opens SnapGPT's normal
launcher activity - once per session - in the package you configure (default `com.snapgpt.assistant`, an assumption: change it to the real id).
If SnapGPT is not installed nothing happens. The focus timer runs in FocusFlow's service, so switching apps never interrupts it.

**Backups.** The database export includes subjects, chapters, tasks, presets and widgets (import replaces the data, so nothing is duplicated;
older v1 backups are migrated on open, newer ones are refused). *Export settings* writes a JSON file with settings and provider endpoints/models,
never API keys.

**CI.** `.github/workflows/android.yml` runs a secret-scan, unit tests (JVM + Robolectric/Room), builds the debug APK and an unsigned release APK,
and uploads the APKs and test reports. Release signing is intentionally not configured in the repo.

**Local Wi-Fi history sync.** Pair both phones with the same shared code in Settings. While both apps are running on the same Wi-Fi, they discover each other and exchange completed session records directly over the LAN using AES-GCM encrypted packets. No cloud account or database is used. Active sessions and API keys are not synced.
