import React from 'react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { AppCard, SectionLabel, SubScreenHeader, ToggleSwitch } from '../components/ui/Common';

export const AiSettingsScreen: React.FC<{
  onBack: () => void;
  onOpenProviders: () => void;
}> = ({ onBack, onOpenProviders }) => {
  const { settings, updateSettings } = useFocusFlow();

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="AI & voice" onBack={onBack} />

      <AppCard>
        <SectionLabel>API-POWERED STUDY AI</SectionLabel>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
          FocusFlow uses the API provider you configure for task suggestions and session
          summaries. There is no downloaded-model or rule-based AI fallback. Without a working API
          profile, you can still add and manage tasks manually.
        </p>
        <div>
          <button
            type="button"
            onClick={onOpenProviders}
            className="w-full sm:w-auto rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2.5 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Manage API providers
          </button>
        </div>
      </AppCard>

      <AppCard>
        <SectionLabel>VOICE INPUT PRIVACY</SectionLabel>
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">
              Only use offline speech recognition
            </div>
            <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
              On: blocks browser speech recognition because browsers cannot guarantee audio stays on
              the device. Off: allows your browser&apos;s Web Speech API recognizer when planning
              tasks by voice. Typed plans always work.
            </p>
          </div>
          <ToggleSwitch
            checked={settings.voiceOfflineOnly}
            onChange={(voiceOfflineOnly) => updateSettings({ voiceOfflineOnly })}
            ariaLabel="Only use offline speech recognition"
          />
        </div>
      </AppCard>
    </div>
  );
};
