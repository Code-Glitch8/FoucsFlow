import React, { useEffect, useState } from 'react';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { Chapter, Subject } from '../types/models';
import {
  AppCard,
  ChipButton,
  ConfirmDialog,
  SubScreenHeader,
  TextInputDialog,
} from '../components/ui/Common';

type SubjectDialog =
  | { kind: 'addSubject' }
  | { kind: 'renameSubject'; subject: Subject }
  | { kind: 'deleteSubject'; subject: Subject }
  | { kind: 'addChapter' }
  | { kind: 'renameChapter'; chapter: Chapter }
  | { kind: 'deleteChapter'; chapter: Chapter };

export const SubjectsScreen: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const {
    db,
    addSubject,
    renameSubject,
    deleteSubject,
    addChapter,
    renameChapter,
    deleteChapter,
  } = useFocusFlow();

  const [selectedId, setSelectedId] = useState<number | null>(() => db.subjects[0]?.id ?? null);
  const [query, setQuery] = useState('');
  const [dialog, setDialog] = useState<SubjectDialog | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!db.subjects.some((s) => s.id === selectedId)) {
      setSelectedId(db.subjects[0]?.id ?? null);
    }
  }, [db.subjects, selectedId]);

  const selected = db.subjects.find((s) => s.id === selectedId) ?? null;
  const chaptersInSubject = db.chapters.filter((c) => c.subjectId === selected?.id);
  const visibleChapters = chaptersInSubject.filter((c) =>
    c.name.toLowerCase().includes(query.trim().toLowerCase()),
  );

  const report = (res: 'OK' | 'BLANK' | 'DUPLICATE' | 'NOT_ALLOWED', what: string) => {
    if (res === 'DUPLICATE') setMessage(`That ${what} already exists.`);
    else if (res === 'BLANK') setMessage(`${what} name can't be empty.`);
    else if (res === 'NOT_ALLOWED') setMessage(`Built-in subjects cannot be changed.`);
    else setMessage(null);
  };

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="Subjects & chapters" onBack={onBack} />

      {message && (
        <div className="rounded-xl bg-[var(--ff-surface-variant)] px-4 py-2.5 text-sm text-[var(--ff-on-bg)] flex justify-between">
          <span>{message}</span>
          <button
            type="button"
            onClick={() => setMessage(null)}
            className="text-xs text-[var(--ff-on-surface-variant)] cursor-pointer"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {db.subjects.map((s) => (
          <ChipButton
            key={s.id}
            selected={s.id === selected?.id}
            onClick={() => {
              setSelectedId(s.id);
              setQuery('');
            }}
          >
            {s.name}
          </ChipButton>
        ))}
        <ChipButton onClick={() => setDialog({ kind: 'addSubject' })}>
          <Plus className="h-4 w-4" />
          New subject
        </ChipButton>
      </div>

      {selected && (
        <>
          <div className="flex items-center justify-between gap-2 pt-1">
            <h2 className="text-xl font-semibold text-[var(--ff-on-bg)]">{selected.name}</h2>
            {!selected.builtIn && (
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  aria-label="Rename subject"
                  onClick={() => setDialog({ kind: 'renameSubject', subject: selected })}
                  className="p-2 rounded-xl text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                >
                  <Pencil className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  aria-label="Delete subject"
                  onClick={() => setDialog({ kind: 'deleteSubject', subject: selected })}
                  className="p-2 rounded-xl text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            )}
          </div>

          <div className="relative">
            <Search className="h-4 w-4 text-[var(--ff-on-surface-variant)] absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search chapters"
              className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-surface)] pl-10 pr-3.5 py-2.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            />
          </div>

          {visibleChapters.length === 0 ? (
            <p className="text-sm text-[var(--ff-on-surface-variant)]">
              {chaptersInSubject.length === 0
                ? 'No chapters yet. Add the chapters you study once and reuse them in every session.'
                : 'No chapters match your search.'}
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              {visibleChapters.map((ch) => (
                <AppCard key={ch.id}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-base font-medium text-[var(--ff-on-bg)] flex-1 min-w-0">
                      {ch.name}
                    </span>
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        type="button"
                        aria-label={`Edit chapter ${ch.name}`}
                        onClick={() => setDialog({ kind: 'renameChapter', chapter: ch })}
                        className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        aria-label={`Remove chapter ${ch.name}`}
                        onClick={() => setDialog({ kind: 'deleteChapter', chapter: ch })}
                        className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </AppCard>
              ))}
            </div>
          )}

          <button
            type="button"
            onClick={() => setDialog({ kind: 'addChapter' })}
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            Add chapter to {selected.name}
          </button>
        </>
      )}

      <TextInputDialog
        open={dialog?.kind === 'addSubject'}
        title="New subject"
        label="Subject name"
        confirmLabel="Add"
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          report(addSubject(val), 'subject');
          setDialog(null);
        }}
      />

      <TextInputDialog
        open={dialog?.kind === 'renameSubject'}
        title="Rename subject"
        label="Subject name"
        initial={dialog?.kind === 'renameSubject' ? dialog.subject.name : ''}
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          if (dialog?.kind === 'renameSubject') report(renameSubject(dialog.subject, val), 'subject');
          setDialog(null);
        }}
      />

      <ConfirmDialog
        open={dialog?.kind === 'deleteSubject'}
        title={dialog?.kind === 'deleteSubject' ? `Delete ${dialog.subject.name}?` : ''}
        message="Its chapters will be removed too. Past session history keeps its subject name."
        confirmLabel="Delete"
        destructive
        onDismiss={() => setDialog(null)}
        onConfirm={() => {
          if (dialog?.kind === 'deleteSubject') deleteSubject(dialog.subject);
          setDialog(null);
        }}
      />

      <TextInputDialog
        open={dialog?.kind === 'addChapter'}
        title="New chapter"
        label="Chapter name"
        confirmLabel="Add"
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          if (selected) report(addChapter(selected.id, val), 'chapter');
          setDialog(null);
        }}
      />

      <TextInputDialog
        open={dialog?.kind === 'renameChapter'}
        title="Edit chapter"
        label="Chapter name"
        initial={dialog?.kind === 'renameChapter' ? dialog.chapter.name : ''}
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          if (dialog?.kind === 'renameChapter') report(renameChapter(dialog.chapter, val), 'chapter');
          setDialog(null);
        }}
      />

      <ConfirmDialog
        open={dialog?.kind === 'deleteChapter'}
        title={dialog?.kind === 'deleteChapter' ? `Remove ${dialog.chapter.name}?` : ''}
        message="It will be removed from presets and session links. Tasks keep their text."
        confirmLabel="Remove"
        destructive
        onDismiss={() => setDialog(null)}
        onConfirm={() => {
          if (dialog?.kind === 'deleteChapter') deleteChapter(dialog.chapter);
          setDialog(null);
        }}
      />
    </div>
  );
};
