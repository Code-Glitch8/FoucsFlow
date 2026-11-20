import React, { useRef, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { StudyPreset } from '../types/models';
import { useFocusFlow } from '../store/FocusFlowStore';
import {
  AppCard,
  ConfirmDialog,
  SectionLabel,
  SubScreenHeader,
} from '../components/ui/Common';

type ConfirmKind = 'HISTORY' | 'EVERYTHING' | 'IMPORT_DB' | null;

function downloadJson(filename: string, content: string) {
  const blob = new Blob([content], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export const DataSettingsScreen: React.FC<{
  onBack: () => void;
}> = ({ onBack }) => {
  const {
    db,
    deletePreset,
    exportDatabaseJson,
    importDatabaseJson,
    exportSettingsJson,
    importSettingsJson,
    resetHistory,
    resetEverything,
  } = useFocusFlow();

  const [confirm, setConfirm] = useState<ConfirmKind>(null);
  const [deletingPreset, setDeletingPreset] = useState<StudyPreset | null>(null);
  const [pendingDbJson, setPendingDbJson] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  const dbInputRef = useRef<HTMLInputElement | null>(null);
  const settingsInputRef = useRef<HTMLInputElement | null>(null);

  const handleDbFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setPendingDbJson(reader.result);
        setConfirm('IMPORT_DB');
      }
    };
    reader.readAsText(file);
  };

  const handleSettingsFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        const res = importSettingsJson(reader.result);
        setStatus(res.ok ? res.message : res.reason);
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="Data & presets" onBack={onBack} />

      <AppCard>
        <SectionLabel>PRIVACY</SectionLabel>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
          Everything stays in this browser&apos;s local storage. FocusFlow has no accounts,
          analytics, ads or background uploads. Network requests are sent only when you invoke an AI
          provider you configured yourself.
        </p>
      </AppCard>

      <div className="flex flex-col gap-2 pt-1">
        <SectionLabel>PRESETS</SectionLabel>
        {db.presets.length === 0 ? (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            No presets yet. Save one from the session setup screen.
          </p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {db.presets.map((p) => (
              <AppCard key={p.id} className="flex-row items-center justify-between gap-3">
                <span className="text-sm font-medium text-[var(--ff-on-bg)] truncate">
                  {p.name}
                </span>
                <button
                  type="button"
                  onClick={() => setDeletingPreset(p)}
                  aria-label={`Delete preset ${p.name}`}
                  className="p-2 rounded-xl text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </AppCard>
            ))}
          </div>
        )}
      </div>

      <AppCard>
        <SectionLabel>BACKUP</SectionLabel>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
          Export saves your subjects, chapters, tasks, sessions, summaries, presets, dashboard
          widgets and blocking choices to a JSON file. Importing a backup replaces your data, so
          widgets and chapters are never duplicated.
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              downloadJson('focusflow-backup.json', exportDatabaseJson());
              setStatus('Backup exported to focusflow-backup.json.');
            }}
            className="rounded-xl bg-[var(--ff-primary)] px-4 py-2 text-sm font-semibold text-[var(--ff-on-primary)] hover:opacity-90 cursor-pointer"
          >
            Export
          </button>
          <button
            type="button"
            onClick={() => dbInputRef.current?.click()}
            className="rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Import
          </button>
          <input
            ref={dbInputRef}
            type="file"
            accept="application/json,.json"
            onChange={handleDbFileSelected}
            className="hidden"
          />
        </div>

        <SectionLabel className="pt-2">SETTINGS (NO SECRETS)</SectionLabel>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
          Exports theme, defaults, switches, SnapGPT option and AI provider endpoints/models. API
          keys are never exported, so enter them again after restoring.
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              downloadJson('focusflow-settings.json', exportSettingsJson());
              setStatus('Settings exported to focusflow-settings.json (API keys excluded).');
            }}
            className="rounded-xl bg-[var(--ff-primary)] px-4 py-2 text-sm font-semibold text-[var(--ff-on-primary)] hover:opacity-90 cursor-pointer"
          >
            Export settings
          </button>
          <button
            type="button"
            onClick={() => settingsInputRef.current?.click()}
            className="rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Import settings
          </button>
          <input
            ref={settingsInputRef}
            type="file"
            accept="application/json,.json"
            onChange={handleSettingsFileSelected}
            className="hidden"
          />
        </div>

        {status && <p className="text-sm font-medium text-[var(--ff-primary)]">{status}</p>}
      </AppCard>

      <AppCard>
        <SectionLabel>RESET</SectionLabel>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setConfirm('HISTORY')}
            className="rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Delete session history
          </button>
          <button
            type="button"
            onClick={() => setConfirm('EVERYTHING')}
            className="rounded-xl border border-[var(--ff-error)]/45 px-4 py-2 text-sm font-medium text-[var(--ff-error)] hover:bg-[var(--ff-error)]/10 cursor-pointer"
          >
            Reset everything
          </button>
        </div>
      </AppCard>

      <ConfirmDialog
        open={confirm === 'HISTORY'}
        title="Delete session history?"
        message="All finished sessions and their summaries are deleted. Tasks, subjects, chapters and presets stay. This cannot be undone."
        confirmLabel="Delete"
        destructive
        onDismiss={() => setConfirm(null)}
        onConfirm={() => {
          setStatus(resetHistory());
          setConfirm(null);
        }}
      />

      <ConfirmDialog
        open={confirm === 'EVERYTHING'}
        title="Reset everything?"
        message="Deletes all subjects, chapters, tasks, sessions, presets, blocking choices and settings. This cannot be undone. Export a backup first if unsure."
        confirmLabel="Reset"
        destructive
        onDismiss={() => setConfirm(null)}
        onConfirm={() => {
          setStatus(resetEverything());
          setConfirm(null);
        }}
      />

      <ConfirmDialog
        open={confirm === 'IMPORT_DB'}
        title="Replace your data with this backup?"
        message="Everything currently stored will be replaced by the backup's contents."
        confirmLabel="Import"
        onDismiss={() => {
          setPendingDbJson(null);
          setConfirm(null);
        }}
        onConfirm={() => {
          if (pendingDbJson) {
            const res = importDatabaseJson(pendingDbJson);
            setStatus(res.ok ? res.message : res.reason);
          }
          setPendingDbJson(null);
          setConfirm(null);
        }}
      />

      <ConfirmDialog
        open={deletingPreset !== null}
        title="Delete preset?"
        message={`"${deletingPreset?.name ?? ''}" will be deleted.`}
        confirmLabel="Delete"
        destructive
        onDismiss={() => setDeletingPreset(null)}
        onConfirm={() => {
          if (deletingPreset) deletePreset(deletingPreset.id);
          setDeletingPreset(null);
        }}
      />
    </div>
  );
};
