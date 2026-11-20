import React, { useState } from 'react';
import { ProviderConfig, ProviderType } from '../types/models';
import {
  ExternalAiClient,
  PROVIDER_DEFAULTS,
  defaultProviderConfig,
} from '../ai/ExternalProviders';
import { useFocusFlow } from '../store/FocusFlowStore';
import {
  AppCard,
  ChipButton,
  ConfirmDialog,
  SectionLabel,
  SubScreenHeader,
  ToggleSwitch,
} from '../components/ui/Common';

const PROVIDER_TYPES: ProviderType[] = ['OPENAI_COMPATIBLE', 'OPENROUTER', 'GEMINI'];

const ProviderEditorModal: React.FC<{
  initial: ProviderConfig;
  hasKey: boolean;
  busy: boolean;
  onTest: (cfg: ProviderConfig, typedKey: string) => void;
  onSave: (cfg: ProviderConfig, key: string | null) => void;
  onDismiss: () => void;
}> = ({ initial, hasKey, busy, onTest, onSave, onDismiss }) => {
  const [type, setType] = useState<ProviderType>(initial.type);
  const [name, setName] = useState(initial.name);
  const [endpoint, setEndpoint] = useState(initial.endpoint);
  const [model, setModel] = useState(initial.model);
  const [key, setKey] = useState('');

  const buildDraft = (): ProviderConfig => ({
    ...initial,
    type,
    name: name.trim() || PROVIDER_DEFAULTS[type].label,
    endpoint: endpoint.trim(),
    model: model.trim(),
  });

  const switchType = (nextType: ProviderType) => {
    const prevDefaults = PROVIDER_DEFAULTS[type];
    const nextDefaults = PROVIDER_DEFAULTS[nextType];
    if (endpoint === prevDefaults.defaultEndpoint) setEndpoint(nextDefaults.defaultEndpoint);
    if (model === prevDefaults.defaultModel) setModel(nextDefaults.defaultModel);
    if (name === prevDefaults.label) setName(nextDefaults.label);
    setType(nextType);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-lg max-h-[90vh] overflow-y-auto rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/40 p-6 flex flex-col gap-4 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">AI provider</h3>

        <div className="flex flex-wrap gap-2">
          {PROVIDER_TYPES.map((t) => (
            <ChipButton
              key={t}
              selected={type === t}
              onClick={() => switchType(t)}
              className="px-3 py-1.5 text-xs"
            >
              {PROVIDER_DEFAULTS[t].label}
            </ChipButton>
          ))}
        </div>

        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">Name</span>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">API endpoint</span>
          <input
            type="text"
            value={endpoint}
            onChange={(e) => setEndpoint(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">Model</span>
          <input
            type="text"
            value={model}
            onChange={(e) => setModel(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">
            {hasKey ? 'API key (leave empty to keep the saved key)' : 'API key'}
          </span>
          <input
            type="password"
            value={key}
            autoComplete="off"
            onChange={(e) => setKey(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>

        <div>
          <button
            type="button"
            disabled={busy}
            onClick={() => onTest(buildDraft(), key)}
            className="rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-45 cursor-pointer"
          >
            Test connection
          </button>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onDismiss}
            className="rounded-xl px-4 py-2 text-sm font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!endpoint.trim() || !model.trim()}
            onClick={() => onSave(buildDraft(), key.trim() ? key.trim() : null)}
            className="rounded-xl bg-[var(--ff-primary)] px-4 py-2 text-sm font-semibold text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
};

export const AiProvidersScreen: React.FC<{
  onBack: () => void;
}> = ({ onBack }) => {
  const {
    settings,
    providerKeys,
    saveProvider,
    deleteProvider,
    selectProvider,
  } = useFocusFlow();

  const [editing, setEditing] = useState<ProviderConfig | null>(null);
  const [deleting, setDeleting] = useState<ProviderConfig | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const testProvider = async (cfg: ProviderConfig, typedKey: string | null) => {
    if (busy) return;
    const key = typedKey?.trim() || providerKeys[cfg.id] || '';
    if (!key) {
      setStatus('Enter an API key first.');
      return;
    }
    setBusy(true);
    setStatus('Testing connection…');
    const res = await ExternalAiClient.test(cfg, key);
    setStatus(res.ok ? 'Connection works.' : `Test failed: ${res.message}`);
    setBusy(false);
  };

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="AI providers" onBack={onBack} />

      <AppCard>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
          A selected, working API provider is required for AI task suggestions and session
          summaries. Your study plan and session facts are sent directly to that provider when you
          request AI help. API keys stay in this browser&apos;s local storage and are excluded from
          settings exports. If the API is unavailable, tasks can still be added manually.
        </p>
        {status && <p className="text-sm font-medium text-[var(--ff-primary)]">{status}</p>}
      </AppCard>

      <AppCard
        onClick={() => selectProvider(null)}
        className="flex-row items-center gap-3"
      >
        <input
          type="radio"
          name="selected-provider"
          checked={settings.aiSelectedProvider === null}
          onChange={() => selectProvider(null)}
          className="h-4 w-4 accent-[var(--ff-primary)]"
        />
        <span className="text-sm text-[var(--ff-on-bg)]">
          No API provider (AI features unavailable; manual tasks still work)
        </span>
      </AppCard>

      {settings.aiProviders.map((p) => {
        const hasKey = Boolean(providerKeys[p.id]?.trim());
        const isSelected = settings.aiSelectedProvider === p.id;
        return (
          <AppCard key={p.id}>
            <div className="flex items-center justify-between gap-3">
              <label className="flex items-center gap-3 flex-1 min-w-0 cursor-pointer">
                <input
                  type="radio"
                  name="selected-provider"
                  disabled={!p.enabled}
                  checked={isSelected}
                  onChange={() => selectProvider(p.id)}
                  className="h-4 w-4 accent-[var(--ff-primary)] shrink-0"
                />
                <span className="text-base font-medium text-[var(--ff-on-bg)] truncate">
                  {p.name} · {PROVIDER_DEFAULTS[p.type].label}
                </span>
              </label>
              <ToggleSwitch
                checked={p.enabled}
                onChange={(enabled) => saveProvider({ ...p, enabled }, null)}
                ariaLabel={`Enable ${p.name}`}
              />
            </div>

            <div className="text-xs text-[var(--ff-on-surface-variant)]">
              {p.model} · {hasKey ? 'key saved' : 'no key yet'}
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setEditing(p)}
                className="rounded-xl border border-[var(--ff-outline)]/45 px-3.5 py-1.5 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                Edit
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => void testProvider(p, null)}
                className="rounded-xl border border-[var(--ff-outline)]/45 px-3.5 py-1.5 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-45 cursor-pointer"
              >
                Test
              </button>
              <button
                type="button"
                onClick={() => setDeleting(p)}
                className="rounded-xl px-3 py-1.5 text-xs font-medium text-[var(--ff-error)] hover:bg-[var(--ff-error)]/10 cursor-pointer"
              >
                Delete
              </button>
            </div>
          </AppCard>
        );
      })}

      <div className="flex flex-col gap-2 pt-2">
        <SectionLabel>ADD A PROVIDER</SectionLabel>
        <div className="flex flex-wrap gap-2">
          {PROVIDER_TYPES.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setEditing(defaultProviderConfig(t))}
              className="rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-surface)] px-4 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              {PROVIDER_DEFAULTS[t].label}
            </button>
          ))}
        </div>
      </div>

      {editing && (
        <ProviderEditorModal
          initial={editing}
          hasKey={Boolean(providerKeys[editing.id]?.trim())}
          busy={busy}
          onTest={(cfg, typedKey) => void testProvider(cfg, typedKey)}
          onSave={(cfg, key) => {
            saveProvider(cfg, key);
            setStatus('Saved.');
            setEditing(null);
          }}
          onDismiss={() => setEditing(null)}
        />
      )}

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? ''}?`}
        message="The provider and its stored API key are removed from this browser."
        confirmLabel="Delete"
        destructive
        onDismiss={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) deleteProvider(deleting.id);
          setDeleting(null);
        }}
      />
    </div>
  );
};
