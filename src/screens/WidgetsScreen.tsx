import React, { useState } from 'react';
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2, Check } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import {
  AppCard,
  ChipButton,
  ConfirmDialog,
  SectionLabel,
  SubScreenHeader,
  ToggleSwitch,
} from '../components/ui/Common';
import {
  WIDGET_COLORS,
  WIDGET_ICONS,
  WidgetStyle,
  WidgetTile,
} from '../components/widgets/WidgetStyle';
import {
  DashboardWidget,
  PresetSubjectPlan,
  ProgressRange,
  WidgetAction,
  WidgetType,
} from '../types/models';
import { joinWidgetIds, parseWidgetIds } from '../domain/WidgetDraftBuilder';

const DURATION_CHOICES = [15, 25, 30, 45, 60, 90];
const WIDGET_TYPES: WidgetType[] = [
  'TIMER',
  'OPEN_ENDED',
  'HOMEWORK',
  'SUBJECT',
  'PRESET',
  'CUSTOM',
  'PROGRESS',
];
const WIDGET_ACTIONS: WidgetAction[] = [
  'START_SESSION',
  'OPEN_PREPARE',
  'OPEN_TASKS',
  'OPEN_HISTORY',
  'OPEN_STATISTICS',
];

function defaultIconForType(t: WidgetType): string {
  switch (t) {
    case 'TIMER':
      return 'timer';
    case 'OPEN_ENDED':
      return 'play';
    case 'HOMEWORK':
      return 'book';
    case 'SUBJECT':
      return 'school';
    case 'PRESET':
      return 'bookmark';
    case 'CUSTOM':
      return 'star';
    case 'PROGRESS':
      return 'trophy';
  }
}

function initialPlansFromWidget(w: DashboardWidget): PresetSubjectPlan[] {
  if (w.subjectPlans && w.subjectPlans.length > 0) {
    return w.subjectPlans.map((sp) => ({
      subjectId: sp.subjectId,
      chapterIds: [...sp.chapterIds],
    }));
  }
  if (w.subjectId != null) {
    return [{ subjectId: w.subjectId, chapterIds: parseWidgetIds(w.chapterIds) }];
  }
  return [];
}

export const WidgetsScreen: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const { db, saveWidget, deleteWidget, moveWidget, setWidgetVisible } = useFocusFlow();
  const [editing, setEditing] = useState<DashboardWidget | null>(null);
  const [deleting, setDeleting] = useState<DashboardWidget | null>(null);
  const [error, setError] = useState<string | null>(null);

  const sortedWidgets = [...db.widgets].sort((a, b) => a.position - b.position);

  const openNewWidget = () => {
    setError(null);
    setEditing({
      id: crypto.randomUUID(),
      type: 'CUSTOM',
      title: '',
      icon: 'star',
      colorKey: 'indigo',
      size: 'SMALL',
      position: sortedWidgets.length,
      visible: true,
      mode: 'TIMED',
      durationMinutes: 25,
      strict: true,
      subjectId: null,
      subjectPlans: [],
      chapterIds: '',
      taskIds: '',
      presetId: null,
      action: 'START_SESSION',
      progressRange: 'DAYS',
      voiceAutoStart: false,
      launchMinimized: false,
      createdAt: Date.now(),
    });
  };

  return (
    <div className="flex flex-col gap-4 pb-12">
      <SubScreenHeader
        title="Edit dashboard"
        onBack={onBack}
        right={
          <button
            type="button"
            onClick={openNewWidget}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            Add widget
          </button>
        }
      />

      <p className="text-sm text-[var(--ff-on-surface-variant)]">
        Build custom multi-subject, voice-start, floating-circle, or Study Progress widgets for your
        Home dashboard. Everything here can be edited or reordered anytime.
      </p>

      {sortedWidgets.length === 0 ? (
        <p className="text-sm text-[var(--ff-on-surface-variant)]">
          No widgets yet. Add one above.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          {sortedWidgets.map((w, idx) => {
            const IconComp = WidgetStyle.icon(w.icon);
            const subName = db.subjects.find((s) => s.id === w.subjectId)?.name ?? null;
            const allNames =
              w.subjectPlans && w.subjectPlans.length > 0
                ? w.subjectPlans
                    .map((sp) => db.subjects.find((s) => s.id === sp.subjectId)?.name)
                    .filter((n): n is string => Boolean(n))
                : undefined;
            const presetName = db.presets.find((p) => p.id === w.presetId)?.name ?? null;
            return (
              <AppCard key={w.id}>
                <div className="flex items-center gap-3">
                  <div
                    className="h-10 w-10 shrink-0 rounded-xl flex items-center justify-center"
                    style={{
                      backgroundColor: `${WidgetStyle.color(w.colorKey)}24`,
                      color: WidgetStyle.color(w.colorKey),
                    }}
                  >
                    <IconComp className="h-5 w-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="text-base font-medium text-[var(--ff-on-bg)] truncate">
                      {w.title}
                    </div>
                    <div className="text-xs text-[var(--ff-on-surface-variant)] truncate">
                      {WidgetStyle.typeLabel(w.type)} ·{' '}
                      {WidgetStyle.subtitle(w, subName, presetName, allNames)}
                    </div>
                  </div>
                  <ToggleSwitch
                    checked={w.visible}
                    onChange={(v) => setWidgetVisible(w.id, v)}
                    ariaLabel={`Toggle visibility for ${w.title}`}
                  />
                </div>
                <div className="flex items-center justify-between pt-1">
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      disabled={idx === 0}
                      onClick={() => moveWidget(w.id, -1)}
                      aria-label={`Move ${w.title} up`}
                      className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-35 cursor-pointer"
                    >
                      <ArrowUp className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      disabled={idx === sortedWidgets.length - 1}
                      onClick={() => moveWidget(w.id, 1)}
                      aria-label={`Move ${w.title} down`}
                      className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-35 cursor-pointer"
                    >
                      <ArrowDown className="h-4 w-4" />
                    </button>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => {
                        setError(null);
                        setEditing(w);
                      }}
                      aria-label={`Edit ${w.title}`}
                      className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeleting(w)}
                      aria-label={`Delete ${w.title}`}
                      className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              </AppCard>
            );
          })}
        </div>
      )}

      {editing && (
        <WidgetEditorModal
          initial={editing}
          error={error}
          onSave={(updated) => {
            const res = saveWidget(updated);
            if (res === 'blank_title') {
              setError('Give the widget a title');
            } else {
              setEditing(null);
              setError(null);
            }
          }}
          onDismiss={() => {
            setEditing(null);
            setError(null);
          }}
        />
      )}

      <ConfirmDialog
        open={deleting !== null}
        title={deleting ? `Delete "${deleting.title}"?` : ''}
        message="The widget is removed from your dashboard. Your sessions and tasks are not affected."
        confirmLabel="Delete"
        destructive
        onDismiss={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) deleteWidget(deleting.id);
          setDeleting(null);
        }}
      />
    </div>
  );
};

const WidgetEditorModal: React.FC<{
  initial: DashboardWidget;
  error: string | null;
  onSave: (w: DashboardWidget) => void;
  onDismiss: () => void;
}> = ({ initial, error, onSave, onDismiss }) => {
  const { db } = useFocusFlow();
  const [w, setW] = useState<DashboardWidget>(initial);
  const [plans, setPlans] = useState<PresetSubjectPlan[]>(() => initialPlansFromWidget(initial));
  const [chapterQuery, setChapterQuery] = useState('');

  const taskSel = parseWidgetIds(w.taskIds);
  const openTasks = db.tasks.filter((t) => t.status === 'OPEN');

  const isProgress = w.type === 'PROGRESS';
  const startsSession = !isProgress && (w.type !== 'CUSTOM' || w.action === 'START_SESSION');
  const showSubject =
    w.type === 'SUBJECT' || w.type === 'HOMEWORK' || (w.type === 'CUSTOM' && startsSession);
  const showModeChoice =
    w.type === 'SUBJECT' || w.type === 'HOMEWORK' || (w.type === 'CUSTOM' && startsSession);
  const showDuration = w.type === 'TIMER' || (showModeChoice && w.mode === 'TIMED');
  const showStrict = w.type !== 'PRESET' && startsSession;

  const valid =
    w.title.trim().length > 0 &&
    (w.type !== 'SUBJECT' || plans.length > 0) &&
    (w.type !== 'PRESET' || w.presetId !== null);

  const selectedSubjectIds = new Set(plans.map((p) => p.subjectId));
  const allPlanNames = plans
    .map((p) => db.subjects.find((s) => s.id === p.subjectId)?.name)
    .filter((n): n is string => Boolean(n));
  const presetName = db.presets.find((p) => p.id === w.presetId)?.name ?? null;

  const syncPlansToWidget = (nextPlans: PresetSubjectPlan[], base: DashboardWidget): DashboardWidget => {
    const firstId = nextPlans[0]?.subjectId ?? null;
    const allChapters = Array.from(new Set(nextPlans.flatMap((p) => p.chapterIds)));
    return {
      ...base,
      subjectPlans: nextPlans,
      subjectId: firstId,
      chapterIds: joinWidgetIds(allChapters),
    };
  };

  const toggleSubjectPlan = (subId: number) => {
    const exists = plans.some((p) => p.subjectId === subId);
    const nextPlans = exists
      ? plans.filter((p) => p.subjectId !== subId)
      : [...plans, { subjectId: subId, chapterIds: [] }];
    setPlans(nextPlans);
    setW((prev) => syncPlansToWidget(nextPlans, prev));
  };

  const toggleChapterInPlan = (subId: number, chId: number) => {
    const nextPlans = plans.map((p) => {
      if (p.subjectId !== subId) return p;
      const has = p.chapterIds.includes(chId);
      return {
        ...p,
        chapterIds: has ? p.chapterIds.filter((id) => id !== chId) : [...p.chapterIds, chId],
      };
    });
    setPlans(nextPlans);
    setW((prev) => syncPlansToWidget(nextPlans, prev));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="w-full max-w-2xl max-h-[90vh] rounded-2xl bg-[var(--ff-bg)] border border-[var(--ff-outline)]/45 flex flex-col overflow-hidden shadow-2xl">
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-[var(--ff-outline)]/30 bg-[var(--ff-surface)]">
          <button
            type="button"
            onClick={onDismiss}
            className="text-sm font-medium text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-on-bg)] cursor-pointer"
          >
            Cancel
          </button>
          <h2 className="text-base font-semibold text-[var(--ff-on-bg)]">
            {initial.title === '' ? 'New widget' : 'Edit widget'}
          </h2>
          <button
            type="button"
            disabled={!valid}
            onClick={() => onSave(syncPlansToWidget(plans, w))}
            className="px-4 py-1.5 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            Save
          </button>
        </div>

        <div className="overflow-y-auto p-5 flex flex-col gap-4">
          {error && <div className="text-sm text-[var(--ff-error)]">{error}</div>}

          <AppCard>
            <SectionLabel>TYPE</SectionLabel>
            <div className="flex flex-wrap gap-2">
              {WIDGET_TYPES.map((t) => (
                <ChipButton
                  key={t}
                  selected={w.type === t}
                  onClick={() =>
                    setW((prev) => ({
                      ...prev,
                      type: t,
                      size: t === 'PROGRESS' ? 'LARGE' : prev.size,
                      action: t === 'PROGRESS' ? 'OPEN_STATISTICS' : prev.action,
                      mode:
                        t === 'OPEN_ENDED'
                          ? 'OPEN_ENDED'
                          : t === 'TIMER'
                            ? 'TIMED'
                            : prev.mode,
                      icon:
                        prev.icon === initial.icon && initial.title === ''
                          ? defaultIconForType(t)
                          : prev.icon,
                    }))
                  }
                >
                  {WidgetStyle.typeLabel(t)}
                </ChipButton>
              ))}
            </div>
            <label className="flex flex-col gap-1.5 pt-1">
              <span className="text-xs font-medium text-[var(--ff-on-surface-variant)]">Title</span>
              <input
                type="text"
                value={w.title}
                onChange={(e) => setW({ ...w, title: e.target.value })}
                placeholder={isProgress ? 'e.g. Study progress' : 'e.g. Math + Chemistry 25'}
                className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3.5 py-2.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
              />
            </label>
          </AppCard>

          {isProgress && (
            <AppCard>
              <SectionLabel>DEFAULT TIMEFRAME</SectionLabel>
              <p className="text-xs text-[var(--ff-on-surface-variant)]">
                Shows time spent over past days, past weeks, and past month directly on your Home
                dashboard. You can also switch views live on Home.
              </p>
              <div className="flex flex-wrap gap-2">
                {(
                  [
                    ['DAYS', 'Past days (7d)'],
                    ['WEEKS', 'Past weeks (4w)'],
                    ['MONTH', 'Past month (30d)'],
                  ] as Array<[ProgressRange, string]>
                ).map(([range, label]) => (
                  <ChipButton
                    key={range}
                    selected={(w.progressRange ?? 'DAYS') === range}
                    onClick={() => setW({ ...w, progressRange: range })}
                  >
                    {label}
                  </ChipButton>
                ))}
              </div>
            </AppCard>
          )}

          {w.type === 'CUSTOM' && (
            <AppCard>
              <SectionLabel>WHEN TAPPED</SectionLabel>
              <div className="flex flex-wrap gap-2">
                {WIDGET_ACTIONS.map((a) => (
                  <ChipButton
                    key={a}
                    selected={w.action === a}
                    onClick={() => setW({ ...w, action: a })}
                  >
                    {WidgetStyle.actionLabel(a)}
                  </ChipButton>
                ))}
              </div>
            </AppCard>
          )}

          {startsSession && (
            <AppCard>
              <SectionLabel>VOICE AUTO-START &amp; FLOATING CIRCLE</SectionLabel>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-sm font-medium text-[var(--ff-on-bg)]">
                    Start by speaking
                  </div>
                  <div className="text-xs text-[var(--ff-on-surface-variant)]">
                    Automatically detects subjects, chapters, and rough duration when you speak,
                    and starts the session.
                  </div>
                </div>
                <ToggleSwitch
                  checked={Boolean(w.voiceAutoStart)}
                  onChange={(v) => setW({ ...w, voiceAutoStart: v })}
                  ariaLabel="Start widget by speaking"
                />
              </div>
              <div className="flex items-start justify-between gap-4 pt-1">
                <div>
                  <div className="text-sm font-medium text-[var(--ff-on-bg)]">
                    Open floating circle only
                  </div>
                  <div className="text-xs text-[var(--ff-on-surface-variant)]">
                    Starts the timer in the floating circle without opening the full Focus screen
                    (and launches SnapGPT if enabled and installed).
                  </div>
                </div>
                <ToggleSwitch
                  checked={Boolean(w.launchMinimized)}
                  onChange={(v) => setW({ ...w, launchMinimized: v })}
                  ariaLabel="Open floating circle only"
                />
              </div>
            </AppCard>
          )}

          {showModeChoice && (
            <AppCard>
              <SectionLabel>MODE</SectionLabel>
              <div className="flex flex-wrap gap-2">
                <ChipButton
                  selected={w.mode === 'TIMED'}
                  onClick={() => setW({ ...w, mode: 'TIMED' })}
                >
                  Timed
                </ChipButton>
                <ChipButton
                  selected={w.mode === 'OPEN_ENDED'}
                  onClick={() => setW({ ...w, mode: 'OPEN_ENDED' })}
                >
                  Open-ended
                </ChipButton>
              </div>
            </AppCard>
          )}

          {showDuration && (
            <AppCard>
              <SectionLabel>DURATION</SectionLabel>
              <div className="flex flex-wrap gap-2">
                {DURATION_CHOICES.map((d) => (
                  <ChipButton
                    key={d}
                    selected={w.durationMinutes === d}
                    onClick={() => setW({ ...w, durationMinutes: d })}
                  >
                    {d} min
                  </ChipButton>
                ))}
              </div>
              <div className="flex items-center gap-2 pt-1">
                <button
                  type="button"
                  onClick={() =>
                    setW({ ...w, durationMinutes: Math.max(1, w.durationMinutes - 1) })
                  }
                  className="h-10 w-10 rounded-xl border border-[var(--ff-outline)]/45 flex items-center justify-center text-lg cursor-pointer hover:bg-[var(--ff-surface-variant)]"
                >
                  −
                </button>
                <input
                  type="number"
                  min={1}
                  max={600}
                  value={w.durationMinutes}
                  onChange={(e) => {
                    const n = Number(e.target.value);
                    if (Number.isFinite(n) && n >= 1) {
                      setW({ ...w, durationMinutes: Math.min(600, Math.round(n)) });
                    }
                  }}
                  className="flex-1 rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3.5 py-2 text-sm text-[var(--ff-on-bg)] font-mono-tabular focus:border-[var(--ff-primary)] focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() =>
                    setW({ ...w, durationMinutes: Math.min(600, w.durationMinutes + 1) })
                  }
                  className="h-10 w-10 rounded-xl border border-[var(--ff-outline)]/45 flex items-center justify-center text-lg cursor-pointer hover:bg-[var(--ff-surface-variant)]"
                >
                  +
                </button>
              </div>
            </AppCard>
          )}

          {showStrict && (
            <AppCard>
              <div className="flex items-center justify-between gap-4">
                <div>
                  <div className="text-base font-medium text-[var(--ff-on-bg)]">Strict session</div>
                  <div className="text-xs text-[var(--ff-on-surface-variant)]">
                    Makes leaving the session harder.
                  </div>
                </div>
                <ToggleSwitch checked={w.strict} onChange={(v) => setW({ ...w, strict: v })} />
              </div>
            </AppCard>
          )}

          {showSubject && (
            <>
              <AppCard>
                <SectionLabel>
                  {w.type === 'SUBJECT'
                    ? `SUBJECTS (${plans.length} selected)`
                    : `SUBJECTS (${plans.length} selected, optional)`}
                </SectionLabel>
                <p className="text-xs text-[var(--ff-on-surface-variant)]">
                  Pick one or multiple subjects (for example Mathematics and Chemistry) and select
                  their chapters below.
                </p>
                <div className="flex flex-wrap gap-2">
                  {db.subjects.map((s) => (
                    <ChipButton
                      key={s.id}
                      selected={selectedSubjectIds.has(s.id)}
                      onClick={() => toggleSubjectPlan(s.id)}
                    >
                      {s.name}
                    </ChipButton>
                  ))}
                </div>
              </AppCard>

              {plans.length > 0 && (
                <AppCard>
                  <SectionLabel>
                    CHAPTERS ({plans.reduce((acc, p) => acc + p.chapterIds.length, 0)} selected)
                  </SectionLabel>
                  <input
                    type="text"
                    value={chapterQuery}
                    onChange={(e) => setChapterQuery(e.target.value)}
                    placeholder="Search chapters across selected subjects"
                    className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3.5 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
                  />
                  <div className="max-h-64 overflow-y-auto flex flex-col gap-3">
                    {plans.map((sp) => {
                      const sub = db.subjects.find((s) => s.id === sp.subjectId);
                      if (!sub) return null;
                      const list = db.chapters.filter(
                        (c) =>
                          c.subjectId === sp.subjectId &&
                          c.name.toLowerCase().includes(chapterQuery.trim().toLowerCase()),
                      );
                      return (
                        <div key={sp.subjectId} className="flex flex-col gap-1">
                          <div className="text-xs font-semibold text-[var(--ff-primary)] pt-1">
                            {sub.name} ({sp.chapterIds.length} selected)
                          </div>
                          {list.length === 0 ? (
                            <div className="text-xs text-[var(--ff-on-surface-variant)] pl-1">
                              No matching chapters.
                            </div>
                          ) : (
                            list.map((c) => {
                              const checked = sp.chapterIds.includes(c.id);
                              return (
                                <label
                                  key={c.id}
                                  className="flex items-center gap-2.5 py-1 px-2 rounded-lg hover:bg-[var(--ff-surface-variant)]/50 cursor-pointer text-sm"
                                >
                                  <input
                                    type="checkbox"
                                    checked={checked}
                                    onChange={() => toggleChapterInPlan(sp.subjectId, c.id)}
                                    className="h-4 w-4 accent-[var(--ff-primary)]"
                                  />
                                  <span>{c.name}</span>
                                </label>
                              );
                            })
                          )}
                        </div>
                      );
                    })}
                  </div>
                </AppCard>
              )}

              <AppCard>
                <SectionLabel>SAVED TASKS ({taskSel.length} selected)</SectionLabel>
                {(() => {
                  const pool = openTasks.filter(
                    (t) =>
                      selectedSubjectIds.size === 0 ||
                      t.subjectId === null ||
                      selectedSubjectIds.has(t.subjectId),
                  );
                  if (pool.length === 0) {
                    return (
                      <p className="text-xs text-[var(--ff-on-surface-variant)]">
                        No unfinished tasks match those subjects. Add tasks in the Tasks tab, then
                        pick them here.
                      </p>
                    );
                  }
                  return (
                    <div className="max-h-44 overflow-y-auto flex flex-col gap-1">
                      {pool.map((t) => {
                        const checked = taskSel.includes(t.id);
                        return (
                          <label
                            key={t.id}
                            className="flex items-center gap-2.5 py-1.5 px-2 rounded-lg hover:bg-[var(--ff-surface-variant)]/50 cursor-pointer text-sm"
                          >
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() => {
                                const next = checked
                                  ? taskSel.filter((id) => id !== t.id)
                                  : [...taskSel, t.id];
                                setW({ ...w, taskIds: joinWidgetIds(next) });
                              }}
                              className="h-4 w-4 accent-[var(--ff-primary)]"
                            />
                            <span>{t.title}</span>
                          </label>
                        );
                      })}
                    </div>
                  );
                })()}
              </AppCard>
            </>
          )}

          {w.type === 'PRESET' && (
            <AppCard>
              <SectionLabel>SAVED SETUP (REQUIRED)</SectionLabel>
              {db.presets.length === 0 ? (
                <p className="text-xs text-[var(--ff-on-surface-variant)]">
                  No saved setups yet. On the session setup screen, choose &quot;Save as preset&quot; first.
                </p>
              ) : (
                <div className="flex flex-col gap-1.5">
                  {db.presets.map((p) => (
                    <label
                      key={p.id}
                      className="flex items-center gap-2.5 py-1.5 px-2 rounded-lg hover:bg-[var(--ff-surface-variant)]/50 cursor-pointer text-sm"
                    >
                      <input
                        type="radio"
                        name="widget-preset"
                        checked={w.presetId === p.id}
                        onChange={() => setW({ ...w, presetId: p.id })}
                        className="h-4 w-4 accent-[var(--ff-primary)]"
                      />
                      <span>{p.name}</span>
                    </label>
                  ))}
                </div>
              )}
            </AppCard>
          )}

          <AppCard>
            <SectionLabel>APPEARANCE</SectionLabel>
            <div className="flex flex-wrap gap-2">
              {Object.keys(WIDGET_ICONS).map((key) => {
                const IconComp = WidgetStyle.icon(key);
                const sel = w.icon === key;
                return (
                  <button
                    key={key}
                    type="button"
                    aria-label={key}
                    onClick={() => setW({ ...w, icon: key })}
                    className="h-10 w-10 rounded-full flex items-center justify-center cursor-pointer transition-colors"
                    style={{
                      backgroundColor: sel
                        ? `${WidgetStyle.color(w.colorKey)}35`
                        : 'var(--ff-surface-variant)',
                      color: sel ? WidgetStyle.color(w.colorKey) : 'var(--ff-on-bg)',
                    }}
                  >
                    <IconComp className="h-5 w-5" />
                  </button>
                );
              })}
            </div>
            <div className="flex flex-wrap gap-2.5 pt-1">
              {Object.entries(WIDGET_COLORS).map(([key, hex]) => {
                const sel = w.colorKey === key;
                return (
                  <button
                    key={key}
                    type="button"
                    aria-label={key}
                    onClick={() => setW({ ...w, colorKey: key })}
                    className="h-9 w-9 rounded-full flex items-center justify-center cursor-pointer"
                    style={{ backgroundColor: hex }}
                  >
                    {sel && <Check className="h-4 w-4 text-white" />}
                  </button>
                );
              })}
            </div>
            <div className="flex flex-wrap gap-2 pt-1">
              <ChipButton
                selected={w.size === 'SMALL'}
                onClick={() => setW({ ...w, size: 'SMALL' })}
              >
                Small (half width)
              </ChipButton>
              <ChipButton
                selected={w.size === 'LARGE'}
                onClick={() => setW({ ...w, size: 'LARGE' })}
              >
                Large (full width)
              </ChipButton>
            </div>
            <div className="pt-2">
              <WidgetTile
                widget={{ ...w, title: w.title.trim() || 'Preview' }}
                subtitle={WidgetStyle.subtitle(
                  w,
                  allPlanNames[0] ?? null,
                  presetName,
                  allPlanNames,
                )}
                onClick={() => {}}
              />
            </div>
          </AppCard>
        </div>
      </div>
    </div>
  );
};
