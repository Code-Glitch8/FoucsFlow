# FocusFlow

FocusFlow is a local-first study workspace and focus session tracker built with React, TypeScript, Vite, Tailwind CSS, PWA support, and Capacitor Android packaging.

## Build the Android APK on GitHub (Automated)

This repository includes Capacitor (`capacitor.config.ts` + `android/`) and a GitHub Actions workflow (`.github/workflows/build-apk.yml`) that compiles the exact preview web app into an installable **FocusFlow.apk**.

### Steps:
1. Extract the downloaded zip archive and push (or upload all files, including the hidden `.github/` folder) to a GitHub repository on the `main` (or `master`) branch.
2. Open your repository on GitHub and click the **Actions** tab.
3. Wait for **Build FocusFlow APK** to finish (or click **Build FocusFlow APK → Run workflow** to start a build manually).
4. Open the completed workflow run, scroll to **Artifacts**, and download **FocusFlow-APK** (`FocusFlow.apk`).
5. Transfer/install `FocusFlow.apk` on your Android device (allow "Install unknown apps" if prompted).

## Local Android APK Build

Requires Node.js 20+, Java 21, and the Android SDK (`ANDROID_HOME`):

```bash
npm install
npm run build
npx cap sync android
cd android && ./gradlew assembleDebug
```

Output APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Web Development

```bash
npm install
npm run dev
```
