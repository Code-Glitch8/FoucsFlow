import React, { useState } from 'react';
import { Check, Pencil, Plus, RotateCcw, X } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { Task } from '../types/models';
import { AppCard, ChipButton, ScreenTitle, SectionLabel } from '../components/ui/Common';

export const TasksScreen: React.FC<{ onManageSubjects: () => void }> = ({ onManageSubjects }) => {
  const { db, addTask, updateTask, markTaskDone, reopenTask, dismissTask } = useFocusFlow();
  const [showAdd, setShowAdd] = useState(false);
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const openTasks = db.tasks
    .filter((t) => t.status === 'OPEN')
    .sort((a, b) => b.createdAt - a.createdAt);
  const doneTasks = db.tasks
    .filter((t) => t.status === 'DONE')
    .sort((a, b) => (b.completedAt ?? b.createdAt) - (a.completedAt ?? a.createdAt))
    .slice(0, 20);

  const contextLine = (task: Task): string => {
    const sub = db.subjects.find((s) => s.id === task.subjectId)?.name;
    const ch = db.chapters.find((c) => c.id === task.chapterId)?.name;
    const est = task.estimatedMinutes ? `~${task.estimatedMinutes} min` : null;
    const orig =
      task.origin === 'EXTERNAL_AI'
        ? 'API AI'
        : task.origin === 'ON_DEVICE_AI'
          ? 'legacy on-device AI'
          : task.origin === 'RULE_BASED'
            ? 'legacy rule-based'
            : null;
    return [sub, ch, est, orig].filter(Boolean).join(' · ');
  };

  return (
    <div className="flex flex-col gap-4 pb-8">
      <ScreenTitle>Tasks</ScreenTitle>
      <p className="text-sm text-[var(--ff-on-surface-variant)]">
        Open tasks, including anything left unfinished from earlier sessions. Every task can be
        edited anytime.
      </p>

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => setShowAdd(true)}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          Add task
        </button>
        <button
          type="button"
          onClick={onManageSubjects}
          className="px-4 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
        >
          Subjects & chapters
        </button>
      </div>

      {toast && (
        <div className="rounded-xl bg-[var(--ff-surface-variant)] px-4 py-2.5 text-sm text-[var(--ff-on-bg)] flex items-center justify-between">
          <span>{toast}</span>
          <button
            type="button"
            onClick={() => setToast(null)}
            className="text-xs text-[var(--ff-on-surface-variant)] cursor-pointer"
          >
            Dismiss
          </button>
        </div>
      )}

      <SectionLabel>OPEN ({openTasks.length})</SectionLabel>
      {openTasks.length === 0 ? (
        <p className="text-sm text-[var(--ff-on-surface-variant)]">
          Nothing open. Add a task, or generate tasks when you start a study session.
        </p>
      ) : (
        <div className="flex flex-col gap-2.5">
          {openTasks.map((task) => {
            const ctx = contextLine(task);
            return (
              <AppCard key={task.id}>
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="text-base font-medium text-[var(--ff-on-bg)]">{task.title}</div>
                    {ctx && (
                      <div className="text-xs text-[var(--ff-on-surface-variant)] pt-0.5">{ctx}</div>
                    )}
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      type="button"
                      onClick={() => setEditingTask(task)}
                      aria-label={`Edit ${task.title}`}
                      className="p-2 rounded-xl text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => markTaskDone(task.id)}
                      aria-label={`Mark ${task.title} done`}
                      className="p-2 rounded-xl text-[var(--ff-primary)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <Check className="h-5 w-5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => dismissTask(task.id)}
                      aria-label={`Dismiss ${task.title}`}
                      className="p-2 rounded-xl text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <X className="h-5 w-5" />
                    </button>
                  </div>
                </div>
              </AppCard>
            );
          })}
        </div>
      )}

      {doneTasks.length > 0 && (
        <>
          <SectionLabel className="pt-2">RECENTLY COMPLETED</SectionLabel>
          <div className="flex flex-col gap-2.5">
            {doneTasks.map((task) => {
              const ctx = contextLine(task);
              return (
                <AppCard key={task.id}>
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="text-base font-medium text-[var(--ff-on-bg)] line-through opacity-70">
                        {task.title}
                      </div>
                      {ctx && (
                        <div className="text-xs text-[var(--ff-on-surface-variant)] pt-0.5">
                          {ctx}
                        </div>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => reopenTask(task.id)}
                      aria-label={`Reopen ${task.title}`}
                      className="p-2 rounded-xl text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <RotateCcw className="h-4 w-4" />
                    </button>
                  </div>
                </AppCard>
              );
            })}
          </div>
        </>
      )}

      {showAdd && (
        <TaskFormDialog
          dialogTitle="Add task"
          confirmLabel="Add"
          initial={null}
          onDismiss={() => setShowAdd(false)}
          onSubmit={(title, subjectId, chapterId, minutes) => {
            const res = addTask(title, subjectId, chapterId, minutes, 'MANUAL');
            if (res.result === 'already_open') {
              setToast('That task is already on your open list.');
            }
            setShowAdd(false);
          }}
        />
      )}

      {editingTask && (
        <TaskFormDialog
          dialogTitle="Edit task"
          confirmLabel="Save"
          initial={editingTask}
          onDismiss={() => setEditingTask(null)}
          onSubmit={(title, subjectId, chapterId, minutes) => {
            const res = updateTask(editingTask.id, {
              title,
              subjectId,
              chapterId,
              estimatedMinutes: minutes,
            });
            if (res === 'BLANK') setToast("Task title can't be empty.");
            setEditingTask(null);
          }}
        />
      )}
    </div>
  );
};

const TaskFormDialog: React.FC<{
  dialogTitle: string;
  confirmLabel: string;
  initial: Task | null;
  onDismiss: () => void;
  onSubmit: (title: string, subjectId: number | null, chapterId: number | null, minutes: number | null) => void;
}> = ({ dialogTitle, confirmLabel, initial, onDismiss, onSubmit }) => {
  const { db } = useFocusFlow();
  const [title, setTitle] = useState(initial?.title ?? '');
  const [minutes, setMinutes] = useState(initial?.estimatedMinutes?.toString() ?? '');
  const [subjectId, setSubjectId] = useState<number | null>(initial?.subjectId ?? null);
  const [chapterId, setChapterId] = useState<number | null>(initial?.chapterId ?? null);

  const chaptersForSubject = db.chapters.filter((c) => c.subjectId === subjectId);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-lg max-h-[85vh] overflow-y-auto rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/45 p-6 flex flex-col gap-4 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">{dialogTitle}</h3>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">What do you want to do?</span>
          <input
            autoFocus
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">
            Estimate in minutes (optional)
          </span>
          <input
            type="number"
            min={1}
            max={300}
            value={minutes}
            onChange={(e) => setMinutes(e.target.value.replace(/\D/g, '').slice(0, 3))}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2.5 text-sm text-[var(--ff-on-bg)] font-mono-tabular focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>

        <SectionLabel>SUBJECT (OPTIONAL)</SectionLabel>
        <div className="flex flex-wrap gap-2">
          {db.subjects.map((s) => (
            <ChipButton
              key={s.id}
              selected={subjectId === s.id}
              onClick={() => {
                setSubjectId(subjectId === s.id ? null : s.id);
                setChapterId(null);
              }}
            >
              {s.name}
            </ChipButton>
          ))}
        </div>

        {chaptersForSubject.length > 0 && (
          <>
            <SectionLabel>CHAPTER (OPTIONAL)</SectionLabel>
            <div className="flex flex-wrap gap-2 max-h-40 overflow-y-auto">
              {chaptersForSubject.map((c) => (
                <ChipButton
                  key={c.id}
                  selected={chapterId === c.id}
                  onClick={() => setChapterId(chapterId === c.id ? null : c.id)}
                >
                  {c.name}
                </ChipButton>
              ))}
            </div>
          </>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onDismiss}
            className="px-4 py-2 rounded-xl text-sm font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!title.trim()}
            onClick={() =>
              onSubmit(title, subjectId, chapterId, minutes ? Number(minutes) : null)
            }
            className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};
