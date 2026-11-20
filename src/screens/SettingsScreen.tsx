import React, { useEffect, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { SessionMode, ThemeMode } from '../types/models';
import {
  AppCard,
  ChipButton,
  ScreenTitle,
  SectionLabel,
  ToggleSwitch,
} from '../components/ui/Common';
import { PWAInstallButton } from '../components/PWAInstallControl';

const DURATION_CHOICES = [15, 25, 30, 45, 60, 90];

const Stepper: React.FC<{
  label: string;
  value: number;
  min: number;
  max: number;
  onChange: (next: number) => void;
}> = ({ label, value, min, max, onChange }) => (
  <div className="flex items-center justify-between gap-3 py-1">
    <span className="text-sm text-[var(--ff-on-bg)]">{label}</span>
    <div className="flex items-center gap-2 shrink-0">
      <button
        type="button"
        disabled={value <= min}
        onClick={() => onChange(Math.max(min, value - 1))}
        className="h-9 w-9 rounded-xl border border-[var(--ff-outline)]/45 text-sm font-semibold text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-40 cursor-pointer"
      >
        −
      </button>
      <span className="w-8 text-center font-mono-tabular tabular-nums text-sm font-semibold text-[var(--ff-on-bg)]">
        {value}
      </span>
      <button
        type="button"
        disabled={value >= max}
        onClick={() => onChange(Math.min(max, value + 1))}
        className="h-9 w-9 rounded-xl border border-[var(--ff-outline)]/45 text-sm font-semibold text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-40 cursor-pointer"
      >
        +
      </button>
    </div>
  </div>
);

export const SettingsScreen: React.FC<{
  onOpenBlocking: () => void;
  onOpenAi: () => void;
  onOpenData: () => void;
}> = ({ onOpenBlocking, onOpenAi, onOpenData }) => {
  const {
    settings,
    globalBreakConfig,
    updateSettings,
    saveGlobalBreakConfig,
    resetBubblePosition,
  } = useFocusFlow();

  const [snapPkgDraft, setSnapPkgDraft] = useState(settings.snapGptPackage);
  useEffect(() => {
    setSnapPkgDraft(settings.snapGptPackage);
  }, [settings.snapGptPackage]);

  const [syncCodeDraft, setSyncCodeDraft] = useState(settings.wifiSyncCode ?? '');
  useEffect(() => {
    if (!syncCodeDraft && settings.wifiSyncCode) {
      setSyncCodeDraft(settings.wifiSyncCode);
    }
  }, [settings.wifiSyncCode, syncCodeDraft]);
  const [syncMsg, setSyncMsg] = useState<string | null>(null);

  const saveSyncCode = (code: string) => {
    const trimmed = code.trim();
    if (trimmed.length < 10 || trimmed.length > 128) {
      setSyncMsg('Sync code must be between 10 and 128 characters.');
      return;
    }
    updateSettings({ wifiSyncCode: trimmed });
    setSyncMsg('Sync code saved. Use the Android companion app on the same Wi-Fi network for peer discovery.');
  };

  return (
    <div className="flex flex-col gap-4 pb-10">
      <ScreenTitle>Settings</ScreenTitle>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <AppCard>
          <SectionLabel>THEME</SectionLabel>
          <div className="flex flex-wrap gap-2">
            {(['DARK', 'LIGHT', 'SYSTEM'] as ThemeMode[]).map((m) => (
              <ChipButton
                key={m}
                selected={settings.themeMode === m}
                onClick={() => updateSettings({ themeMode: m })}
              >
                {m.charAt(0) + m.slice(1).toLowerCase()}
              </ChipButton>
            ))}
          </div>
        </AppCard>

        <AppCard>
          <SectionLabel>DEFAULT FOCUS MODE</SectionLabel>
          <div className="flex flex-wrap gap-2">
            {(['TIMED', 'OPEN_ENDED'] as SessionMode[]).map((m) => (
              <ChipButton
                key={m}
                selected={settings.defaultMode === m}
                onClick={() => updateSettings({ defaultMode: m })}
              >
                {m === 'TIMED' ? 'Timed' : 'Open-ended'}
              </ChipButton>
            ))}
          </div>
          <SectionLabel className="pt-1">DEFAULT SESSION LENGTH (TIMED)</SectionLabel>
          <div className="flex flex-wrap gap-2">
            {DURATION_CHOICES.map((d) => (
              <ChipButton
                key={d}
                selected={settings.defaultDurationMin === d}
                onClick={() => updateSettings({ defaultDurationMin: d })}
              >
                {d} min
              </ChipButton>
            ))}
          </div>
        </AppCard>

        <AppCard>
          <SectionLabel>SNAPGPT</SectionLabel>
          <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <div className="text-sm font-medium text-[var(--ff-on-bg)]">
                Open SnapGPT when a session starts
              </div>
              <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
                On Android, launches SnapGPT once per session while the focus timer keeps running.
                In the browser, your preference and package name are saved and included in exports.
              </p>
            </div>
            <ToggleSwitch
              checked={settings.snapGptEnabled}
              onChange={(v) => updateSettings({ snapGptEnabled: v })}
              ariaLabel="Open SnapGPT when a session starts"
            />
          </div>
          <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <div className="text-sm font-medium text-[var(--ff-on-bg)]">
                SnapGPT is installed on this device
              </div>
              <p className="text-xs text-[var(--ff-on-surface-variant)]">
                Only trigger automatic handoff when SnapGPT is installed.
              </p>
            </div>
            <ToggleSwitch
              checked={settings.snapGptInstalled ?? true}
              onChange={(v) => updateSettings({ snapGptInstalled: v })}
              ariaLabel="SnapGPT is installed"
            />
          </div>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-[var(--ff-on-surface-variant)]">
              SnapGPT package name
            </span>
            <input
              type="text"
              value={snapPkgDraft}
              onChange={(e) => setSnapPkgDraft(e.target.value)}
              className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            />
          </label>
          {snapPkgDraft !== settings.snapGptPackage && (
            <div>
              <button
                type="button"
                onClick={() => updateSettings({ snapGptPackage: snapPkgDraft.trim() || 'com.snapgpt.assistant' })}
                className="rounded-xl border border-[var(--ff-outline)]/45 px-3.5 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                Save package name
              </button>
            </div>
          )}
        </AppCard>

        <AppCard>
          <SectionLabel>SYNC WITH YOUR OTHER PHONE</SectionLabel>
          <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
            Direct LAN Wi-Fi peer-to-peer UDP discovery runs only in the native Android build. You
            can store or generate your shared pairing code here and include your data in JSON
            backups under Data &amp; presets.
          </p>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-[var(--ff-on-surface-variant)]">
              Shared sync code (10–128 characters)
            </span>
            <input
              type="text"
              value={syncCodeDraft}
              maxLength={128}
              onChange={(e) => setSyncCodeDraft(e.target.value)}
              className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            />
          </label>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => saveSyncCode(syncCodeDraft)}
              className="rounded-xl bg-[var(--ff-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--ff-on-primary)] hover:opacity-90 cursor-pointer"
            >
              Save code
            </button>
            <button
              type="button"
              onClick={() => {
                const generated = crypto.randomUUID();
                setSyncCodeDraft(generated);
                saveSyncCode(generated);
              }}
              className="rounded-xl border border-[var(--ff-outline)]/45 px-3.5 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              Generate code
            </button>
            <button
              type="button"
              disabled={!settings.wifiSyncCode}
              onClick={() => {
                updateSettings({ wifiSyncCode: null });
                setSyncCodeDraft('');
                setSyncMsg('Sync code cleared.');
              }}
              className="rounded-xl px-3 py-2 text-xs font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-40 cursor-pointer"
            >
              Clear
            </button>
          </div>
          <div className="text-xs text-[var(--ff-on-surface-variant)]">
            {settings.wifiSyncCode ? 'Pairing code saved locally' : 'Not paired'}
            {syncMsg ? ` · ${syncMsg}` : ''}
          </div>
        </AppCard>

        <AppCard>
          <SectionLabel>EARNED BREAKS</SectionLabel>
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-[var(--ff-on-bg)]">
              Earn breaks by completing tasks
            </span>
            <ToggleSwitch
              checked={globalBreakConfig.enabled}
              onChange={(enabled) => saveGlobalBreakConfig({ ...globalBreakConfig, enabled })}
              ariaLabel="Earn breaks by completing tasks"
            />
          </div>
          {globalBreakConfig.enabled && (
            <div className="flex flex-col gap-1 pt-1">
              <Stepper
                label="Tasks for a short break"
                value={globalBreakConfig.tasksForShortBreak}
                min={1}
                max={20}
                onChange={(tasksForShortBreak) =>
                  saveGlobalBreakConfig({ ...globalBreakConfig, tasksForShortBreak })
                }
              />
              <Stepper
                label="Short break (minutes)"
                value={globalBreakConfig.shortBreakMinutes}
                min={1}
                max={60}
                onChange={(shortBreakMinutes) =>
                  saveGlobalBreakConfig({ ...globalBreakConfig, shortBreakMinutes })
                }
              />
              <Stepper
                label="Tasks for a long break"
                value={globalBreakConfig.tasksForLongBreak}
                min={1}
                max={40}
                onChange={(tasksForLongBreak) =>
                  saveGlobalBreakConfig({ ...globalBreakConfig, tasksForLongBreak })
                }
              />
              <Stepper
                label="Long break (minutes)"
                value={globalBreakConfig.longBreakMinutes}
                min={1}
                max={120}
                onChange={(longBreakMinutes) =>
                  saveGlobalBreakConfig({ ...globalBreakConfig, longBreakMinutes })
                }
              />
              <p className="text-xs text-[var(--ff-on-surface-variant)] pt-1">
                When one completed task qualifies for both, you get the long break only.
              </p>
            </div>
          )}
        </AppCard>

        <AppCard>
          <SectionLabel>FLOATING ASSISTANT</SectionLabel>
          <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <div className="text-sm font-medium text-[var(--ff-on-bg)]">
                Show bubble during sessions
              </div>
              <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
                A draggable in-app bubble displaying your timer, tasks and earned breaks while a
                session is active.
              </p>
            </div>
            <ToggleSwitch
              checked={settings.floatingEnabled}
              onChange={(v) => updateSettings({ floatingEnabled: v })}
              ariaLabel="Show floating bubble during sessions"
            />
          </div>
          <div>
            <button
              type="button"
              onClick={resetBubblePosition}
              className="rounded-xl border border-[var(--ff-outline)]/45 px-3.5 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              Reset bubble position
            </button>
          </div>
        </AppCard>
      </div>

      <AppCard>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex-1 min-w-[240px]">
            <SectionLabel>INSTALL ON ANDROID &amp; GITHUB APK ZIP</SectionLabel>
            <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed mt-1">
              Download the GitHub-ready project zip (with Capacitor Android &amp;{' '}
              <code className="font-mono-tabular text-[var(--ff-on-bg)]">
                .github/workflows/build-apk.yml
              </code>
              ) to build an installable <strong className="text-[var(--ff-on-bg)]">.apk</strong>{' '}
              matching this exact preview on GitHub Actions, or tap{' '}
              <strong className="text-[var(--ff-on-bg)]">Install app</strong> in Android Chrome for
              instant offline installation.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2 shrink-0">
            <a
              href="/focusflow-github-apk.zip"
              download="focusflow-github-apk.zip"
              className="inline-flex items-center justify-center gap-2 rounded-xl border border-[var(--ff-outline)]/45 px-3.5 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] transition-colors whitespace-nowrap"
            >
              Download GitHub + APK (.zip)
            </a>
            <PWAInstallButton />
          </div>
        </div>
      </AppCard>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <AppCard onClick={onOpenBlocking}>
          <div className="flex items-center justify-between gap-2">
            <SectionLabel>BLOCKING &amp; YOUTUBE</SectionLabel>
            <ChevronRight className="h-4 w-4 text-[var(--ff-on-surface-variant)]" />
          </div>
          <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
            Choose which apps are blocked during sessions, block YouTube Shorts and test detector
            rules.
          </p>
        </AppCard>

        <AppCard onClick={onOpenAi}>
          <div className="flex items-center justify-between gap-2">
            <SectionLabel>AI API &amp; VOICE</SectionLabel>
            <ChevronRight className="h-4 w-4 text-[var(--ff-on-surface-variant)]" />
          </div>
          <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
            Configure the API used for task suggestions and summaries, and choose how voice input
            behaves.
          </p>
        </AppCard>

        <AppCard onClick={onOpenData}>
          <div className="flex items-center justify-between gap-2">
            <SectionLabel>DATA, PRESETS &amp; PRIVACY</SectionLabel>
            <ChevronRight className="h-4 w-4 text-[var(--ff-on-surface-variant)]" />
          </div>
          <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
            Manage presets, export or import a backup, and reset your data.
          </p>
        </AppCard>
      </div>
    </div>
  );
};
