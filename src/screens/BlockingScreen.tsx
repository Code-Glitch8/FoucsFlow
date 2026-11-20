import React, { useMemo, useState } from 'react';
import { Plus, Search, Shield } from 'lucide-react';
import { AppCategory, ShortsMode } from '../types/models';
import { useFocusFlow } from '../store/FocusFlowStore';
import { DEFAULT_ESSENTIAL_PACKAGES, YOUTUBE_PACKAGE } from '../domain/BlockPolicy';
import { AppGuardBar } from '../components/AppGuardBar';
import {
  AppCard,
  ChipButton,
  SectionLabel,
  SubScreenHeader,
  ToggleSwitch,
} from '../components/ui/Common';

export const BlockingScreen: React.FC<{
  onBack: () => void;
  onTestDetector: () => void;
}> = ({ onBack, onTestDetector }) => {
  const {
    db,
    settings,
    allCatalogApps,
    updateSettings,
    setAppRule,
    setBlockedDuringBreak,
    setYoutubeBlockedDuringBreak,
    blockAllInCategory,
    addCustomCatalogApp,
  } = useFocusFlow();

  const [query, setQuery] = useState('');
  const [customPkg, setCustomPkg] = useState('');
  const [customLabel, setCustomLabel] = useState('');
  const [customCat, setCustomCat] = useState<AppCategory>('CUSTOM');

  const globalBlockedMap = useMemo(() => {
    const m = new Map<string, (typeof db.blockedApps)[number]>();
    for (const b of db.blockedApps) {
      if (b.presetId === null) m.set(b.packageName, b);
    }
    return m;
  }, [db.blockedApps]);

  const globalAllowedSet = useMemo(() => {
    const s = new Set<string>();
    for (const a of db.allowedApps) {
      if (a.presetId === null) s.add(a.packageName);
    }
    return s;
  }, [db.allowedApps]);

  const selectableApps = useMemo(
    () => allCatalogApps.filter((a) => !DEFAULT_ESSENTIAL_PACKAGES.has(a.packageName)),
    [allCatalogApps],
  );

  const visibleApps = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return selectableApps;
    return selectableApps.filter(
      (a) => a.label.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q),
    );
  }, [query, selectableApps]);

  const countInCategory = (cat: AppCategory) =>
    selectableApps.filter((a) => a.category === cat).length;

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="Blocking" onBack={onBack} />

      <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
        Blocking policies apply while a session runs and are saved in your workspace backups. In a
        web browser, FocusFlow cannot monitor or close other native mobile/desktop apps; OS-level
        blocking and Accessibility Services require the Android companion app. Emergency calls and
        essential system apps are never blocked.
      </p>

      <AppCard>
        <SectionLabel>ANDROID SYSTEM PERMISSIONS (COMPANION ONLY)</SectionLabel>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
          <div className="rounded-xl bg-[var(--ff-surface-variant)]/45 p-3">
            <div className="font-semibold text-[var(--ff-on-bg)] mb-1">Accessibility service</div>
            Reads foreground package and YouTube tab labels to block apps and Shorts without logging
            keystrokes or messages.
          </div>
          <div className="rounded-xl bg-[var(--ff-surface-variant)]/45 p-3">
            <div className="font-semibold text-[var(--ff-on-bg)] mb-1">Usage access</div>
            Optional fallback for foreground app blocking when the Accessibility service is off.
          </div>
          <div className="rounded-xl bg-[var(--ff-surface-variant)]/45 p-3">
            <div className="font-semibold text-[var(--ff-on-bg)] mb-1">Display over other apps</div>
            Shows the system block overlay reliably on Android. Inside this web app, the Floating
            Assistant renders directly inside your browser workspace.
          </div>
        </div>
      </AppCard>

      <AppGuardBar title="TEST YOUTUBE & APP SHIELD RIGHT NOW" />

      <AppCard>
        <SectionLabel>YOUTUBE &amp; SHORTS (INDEPENDENT CONTROLS)</SectionLabel>
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">
              Block the entire YouTube app
            </div>
            <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
              Shows the Focus Shield when opening YouTube during focus sessions and closes it until
              you start a break or unblock it below. Turn this off if you only want to block Shorts.
            </p>
          </div>
          <ToggleSwitch
            checked={settings.blockYoutubeApp}
            onChange={(blockYoutubeApp) => updateSettings({ blockYoutubeApp })}
            ariaLabel="Block the entire YouTube app"
          />
        </div>

        <label className="flex items-center gap-2.5 text-xs text-[var(--ff-on-bg)] cursor-pointer select-none pt-1">
          <input
            type="checkbox"
            checked={globalBlockedMap.get(YOUTUBE_PACKAGE)?.blockedDuringBreak === true}
            onChange={(e) => setYoutubeBlockedDuringBreak(e.target.checked)}
            className="h-4 w-4 rounded accent-[var(--ff-primary)]"
          />
          Keep YouTube blacklisted even during breaks
        </label>

        <div className="border-t border-[var(--ff-outline)]/25 pt-3 flex flex-col gap-2">
          <div className="text-sm font-medium text-[var(--ff-on-bg)]">
            Block YouTube Shorts only
          </div>
          <p className="text-xs text-[var(--ff-on-surface-variant)]">
            Independent of blocking the whole YouTube app. Regular YouTube videos and search stay
            open while Shorts is blocked.
          </p>
          <div className="flex flex-wrap gap-2">
            {(['OFF', 'FOCUS_ONLY', 'ALWAYS'] as ShortsMode[]).map((m) => (
              <ChipButton
                key={m}
                selected={settings.shortsMode === m}
                onClick={() => updateSettings({ shortsMode: m })}
              >
                {m === 'OFF' ? 'Off' : m === 'FOCUS_ONLY' ? 'During sessions' : 'Always'}
              </ChipButton>
            ))}
          </div>
        </div>

        <div className="flex items-start justify-between gap-4 pt-1">
          <div className="flex flex-col gap-1">
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">Skip the Home feed</div>
            <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
              When YouTube opens on Home, jump to Subscriptions instead. Follows the Shorts rule
              timing above.
            </p>
          </div>
          <ToggleSwitch
            checked={settings.homeRedirect}
            onChange={(homeRedirect) => updateSettings({ homeRedirect })}
            ariaLabel="Skip the Home feed"
          />
        </div>

        <div>
          <button
            type="button"
            onClick={onTestDetector}
            className="rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Test the YouTube detector
          </button>
        </div>

        <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
          YouTube has no official API to block Shorts separately, so detection inspects view IDs and
          tab labels. Use the tester above to verify classification rules.
        </p>
      </AppCard>

      <AppCard>
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <SectionLabel>STRICT ALLOWLIST MODE</SectionLabel>
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">
              Block all non-essential apps except allowed
            </div>
            <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
              When active during focus time, any app not marked as Allowed (other than essential
              system apps) is treated as blocked.
            </p>
          </div>
          <ToggleSwitch
            checked={settings.blockAllExceptAllowed}
            onChange={(blockAllExceptAllowed) => updateSettings({ blockAllExceptAllowed })}
            ariaLabel="Block all non-essential apps except allowed"
          />
        </div>
      </AppCard>

      <AppCard>
        <SectionLabel>QUICK ADD</SectionLabel>
        <p className="text-xs text-[var(--ff-on-surface-variant)]">
          Adds every catalog app of that kind to your blocked list (you can undo any below).
        </p>
        <div className="flex flex-wrap gap-2">
          {(
            [
              ['GAME', 'Games'],
              ['SOCIAL', 'Social media'],
              ['ENTERTAINMENT', 'Entertainment'],
            ] as Array<[AppCategory, string]>
          ).map(([cat, name]) => {
            const count = countInCategory(cat);
            return (
              <ChipButton
                key={cat}
                disabled={count === 0}
                onClick={() => blockAllInCategory(cat)}
              >
                {name} ({count})
              </ChipButton>
            );
          })}
        </div>
      </AppCard>

      <AppCard>
        <SectionLabel>ADD CUSTOM APP</SectionLabel>
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (!customLabel.trim() || !customPkg.trim()) return;
            addCustomCatalogApp(customPkg, customLabel, customCat);
            setCustomPkg('');
            setCustomLabel('');
          }}
        >
          <label className="flex-1 min-w-[160px] flex flex-col gap-1">
            <span className="text-xs text-[var(--ff-on-surface-variant)]">App name</span>
            <input
              type="text"
              value={customLabel}
              onChange={(e) => setCustomLabel(e.target.value)}
              placeholder="e.g. Notion"
              className="rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            />
          </label>
          <label className="flex-1 min-w-[180px] flex flex-col gap-1">
            <span className="text-xs text-[var(--ff-on-surface-variant)]">Package identifier</span>
            <input
              type="text"
              value={customPkg}
              onChange={(e) => setCustomPkg(e.target.value)}
              placeholder="e.g. notion.id"
              className="rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-[var(--ff-on-surface-variant)]">Category</span>
            <select
              value={customCat}
              onChange={(e) => setCustomCat(e.target.value as AppCategory)}
              className="rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            >
              <option value="CUSTOM">Custom</option>
              <option value="SOCIAL">Social</option>
              <option value="ENTERTAINMENT">Entertainment</option>
              <option value="GAME">Game</option>
            </select>
          </label>
          <button
            type="submit"
            disabled={!customPkg.trim() || !customLabel.trim()}
            className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--ff-primary)] px-4 py-2 text-sm font-semibold text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            Add app
          </button>
        </form>
      </AppCard>

      <div className="flex flex-col gap-1 pt-2">
        <SectionLabel>
          APPS · {globalBlockedMap.size} blocked · {globalAllowedSet.size} allowed
        </SectionLabel>
        <p className="text-xs text-[var(--ff-on-surface-variant)]">
          Blocked apps close during a session. Allowed apps bypass the list; the separate
          full-YouTube switch can still block YouTube.
        </p>
      </div>

      <div className="relative">
        <Search className="h-4 w-4 text-[var(--ff-on-surface-variant)] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search apps"
          className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-surface)] pl-10 pr-4 py-2.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {visibleApps.map((app) => {
          const blockedRule = globalBlockedMap.get(app.packageName);
          const isAllowed = globalAllowedSet.has(app.packageName);
          const choice = blockedRule ? 'BLOCK' : isAllowed ? 'ALLOW' : 'NONE';
          return (
            <AppCard key={app.packageName}>
              <div className="flex items-center gap-3">
                <div className="h-9 w-9 shrink-0 rounded-xl bg-[var(--ff-surface-variant)] flex items-center justify-center text-[var(--ff-primary)]">
                  <Shield className="h-4 w-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-[var(--ff-on-bg)] truncate">
                    {app.label}
                  </div>
                  <div className="text-xs text-[var(--ff-on-surface-variant)] truncate">
                    {app.packageName}
                  </div>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <ChipButton
                  selected={choice === 'BLOCK'}
                  onClick={() => setAppRule(app, choice === 'BLOCK' ? 'NONE' : 'BLOCK')}
                  className="px-3 py-1.5 text-xs"
                >
                  Block
                </ChipButton>
                <ChipButton
                  selected={choice === 'ALLOW'}
                  onClick={() => setAppRule(app, choice === 'ALLOW' ? 'NONE' : 'ALLOW')}
                  className="px-3 py-1.5 text-xs"
                >
                  Allow
                </ChipButton>
              </div>
              {blockedRule && (
                <label className="flex items-center gap-2 text-xs text-[var(--ff-on-bg)] cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={blockedRule.blockedDuringBreak}
                    onChange={(e) => setBlockedDuringBreak(app.packageName, e.target.checked)}
                    className="h-4 w-4 rounded accent-[var(--ff-primary)]"
                  />
                  Stay blocked during breaks
                </label>
              )}
            </AppCard>
          );
        })}
      </div>
    </div>
  );
};
