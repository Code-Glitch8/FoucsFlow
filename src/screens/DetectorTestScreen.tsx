import React, { useMemo, useState } from 'react';
import { BlockPolicy } from '../domain/BlockPolicy';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { UiNode, YouTubeUiClassifier } from '../domain/YouTubeUiClassifier';
import { useFocusFlow } from '../store/FocusFlowStore';
import { AppCard, ChipButton, SectionLabel, SubScreenHeader } from '../components/ui/Common';

interface SamplePreset {
  id: string;
  label: string;
  tree: UiNode;
}

const SAMPLE_TREES: SamplePreset[] = [
  {
    id: 'shorts',
    label: 'Shorts player',
    tree: {
      viewId: 'com.google.android.youtube:id/reel_watch_fragment_root',
      className: 'android.widget.FrameLayout',
      children: [
        {
          viewId: 'com.google.android.youtube:id/reel_player_page_container',
          children: [
            { viewId: 'com.google.android.youtube:id/reel_player_overlay' },
            { viewId: 'com.google.android.youtube:id/reel_progress_bar' },
          ],
        },
        {
          viewId: 'com.google.android.youtube:id/pivot_bar',
          children: [
            { contentDescription: 'Home', selected: false },
            { contentDescription: 'Shorts', selected: true },
            { contentDescription: 'Subscriptions', selected: false },
          ],
        },
      ],
    },
  },
  {
    id: 'home',
    label: 'Home feed',
    tree: {
      viewId: 'com.google.android.youtube:id/results',
      children: [
        { viewId: 'com.google.android.youtube:id/toolbar' },
        {
          viewId: 'com.google.android.youtube:id/pivot_bar',
          children: [
            { contentDescription: 'Home', selected: true },
            { contentDescription: 'Shorts', selected: false },
            { contentDescription: 'Subscriptions', selected: false },
          ],
        },
      ],
    },
  },
  {
    id: 'video',
    label: 'Normal video',
    tree: {
      viewId: 'com.google.android.youtube:id/watch_while_time_bar_view',
      children: [
        { viewId: 'com.google.android.youtube:id/player_fragment_container' },
        { viewId: 'com.google.android.youtube:id/related_chip_cloud' },
      ],
    },
  },
  {
    id: 'subs',
    label: 'Subscriptions',
    tree: {
      viewId: 'com.google.android.youtube:id/browse_container',
      children: [
        {
          viewId: 'com.google.android.youtube:id/pivot_bar',
          children: [
            { contentDescription: 'Home', selected: false },
            { contentDescription: 'Shorts', selected: false },
            { contentDescription: 'Subscriptions', selected: true },
          ],
        },
      ],
    },
  },
];

export const DetectorTestScreen: React.FC<{
  onBack: () => void;
}> = ({ onBack }) => {
  const { activeSession, settings, now } = useFocusFlow();
  const [selectedPreset, setSelectedPreset] = useState<string>('shorts');
  const [jsonInput, setJsonInput] = useState<string>(() =>
    JSON.stringify(SAMPLE_TREES[0].tree, null, 2),
  );

  const parsed = useMemo<{ ok: true; root: UiNode } | { ok: false; error: string }>(() => {
    try {
      const obj = JSON.parse(jsonInput) as UiNode;
      if (!obj || typeof obj !== 'object') {
        return { ok: false, error: 'Root JSON must be a UiNode object.' };
      }
      return { ok: true, root: obj };
    } catch (e) {
      return {
        ok: false,
        error: e instanceof Error ? e.message : 'Invalid JSON',
      };
    }
  }, [jsonInput]);

  const classification = useMemo(
    () => (parsed.ok ? YouTubeUiClassifier.classify(parsed.root) : null),
    [parsed],
  );
  const sampleIds = useMemo(
    () => (parsed.ok ? YouTubeUiClassifier.sampleIds(parsed.root, 40) : []),
    [parsed],
  );

  const sessionActive = activeSession != null;
  const onBreak = activeSession
    ? SessionClock.isOnBreak(sessionToClock(activeSession.session), now)
    : false;
  const effectiveShortsMode = activeSession?.session.blockShorts
    ? settings.shortsMode
    : settings.shortsMode === 'ALWAYS'
      ? 'ALWAYS'
      : 'OFF';
  const shortsBlockingNow = BlockPolicy.shortsEnabled(effectiveShortsMode, sessionActive, onBreak);
  const homeRedirectNow = BlockPolicy.homeRedirectEnabled(
    settings.homeRedirect,
    effectiveShortsMode,
    sessionActive,
    onBreak,
  );

  const screenText =
    !classification
      ? 'Invalid tree JSON'
      : classification.screen === 'SHORTS_PLAYER'
        ? shortsBlockingNow
          ? 'Shorts player (would be blocked now)'
          : 'Shorts player (detected; rule currently inactive)'
        : classification.screen === 'HOME_FEED'
          ? homeRedirectNow
            ? 'Home feed (would redirect to Subscriptions now)'
            : 'Home feed'
          : 'Other (video, search, subscriptions… never blocked)';

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="Detector test" onBack={onBack} />

      <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
        Inspect and test the exact YouTube screen-structure classifier used by FocusFlow. Live
        Android Accessibility monitoring requires the Android companion app; here you can run sample
        or custom UI node trees directly against <code className="font-mono-tabular">YouTubeUiClassifier</code> and{' '}
        <code className="font-mono-tabular">BlockPolicy</code>.
      </p>

      <AppCard>
        <SectionLabel>ACTIVE POLICY SNAPSHOT</SectionLabel>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm text-[var(--ff-on-bg)]">
          <div>
            Session: <span className="font-semibold">{sessionActive ? (onBreak ? 'On break' : 'Active') : 'Idle'}</span>
          </div>
          <div>
            Shorts blocking: <span className="font-semibold">{shortsBlockingNow ? 'Active' : 'Inactive'}</span>
          </div>
          <div>
            Home redirect: <span className="font-semibold">{homeRedirectNow ? 'Active' : 'Inactive'}</span>
          </div>
        </div>
      </AppCard>

      <AppCard>
        <SectionLabel>SAMPLE UI TREES</SectionLabel>
        <div className="flex flex-wrap gap-2">
          {SAMPLE_TREES.map((preset) => (
            <ChipButton
              key={preset.id}
              selected={selectedPreset === preset.id}
              onClick={() => {
                setSelectedPreset(preset.id);
                setJsonInput(JSON.stringify(preset.tree, null, 2));
              }}
            >
              {preset.label}
            </ChipButton>
          ))}
        </div>
        <label className="flex flex-col gap-1.5 pt-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">
            UiNode JSON tree (editable)
          </span>
          <textarea
            rows={10}
            value={jsonInput}
            onChange={(e) => {
              setSelectedPreset('');
              setJsonInput(e.target.value);
            }}
            className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] p-3 text-xs font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        {!parsed.ok && (
          <p className="text-xs text-[var(--ff-error)]">{parsed.error}</p>
        )}
      </AppCard>

      <AppCard>
        <SectionLabel>CLASSIFIER RESULT</SectionLabel>
        <div className="text-base font-semibold text-[var(--ff-on-bg)]">{screenText}</div>
        {classification && classification.signals.length > 0 && (
          <ul className="list-disc pl-5 text-sm text-[var(--ff-on-surface-variant)] space-y-1">
            {classification.signals.map((sig) => (
              <li key={sig}>{sig}</li>
            ))}
          </ul>
        )}
      </AppCard>

      <AppCard>
        <SectionLabel>VIEW IDS SEEN</SectionLabel>
        <p className="text-sm font-mono-tabular text-[var(--ff-on-surface-variant)] break-words">
          {sampleIds.length === 0 ? 'None' : sampleIds.join(', ')}
        </p>
      </AppCard>
    </div>
  );
};
