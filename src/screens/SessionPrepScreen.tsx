import React, { useEffect, useRef, useState } from 'react';
import { Mic, Pencil, Plus, Search, Sparkles, Trash2 } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { AiService } from '../ai/AiService';
import { PlanAnalysisResult, PlanAnalyzer } from '../domain/PlanAnalyzer';
import { WebVoiceRecognizer } from '../speech/VoiceRecognizer';
import { TaskNormalizer } from '../domain/TaskNormalizer';
import {
  DraftTask,
  PresetSubjectPlan,
  SessionMode,
  SubjectPlan,
  Task,
  TaskOrigin,
} from '../types/models';
import {
  AppCard,
  ChipButton,
  SectionLabel,
  SubScreenHeader,
  TextInputDialog,
  ToggleSwitch,
} from '../components/ui/Common';

const DURATION_CHOICES = [15, 25, 30, 45, 60, 90];

interface Proposal {
  title: string;
  minutes: number | null;
  subtasks: string[];
  selected: boolean;
}

export const SessionPrepScreen: React.FC<{
  presetId?: number | null;
  onBack: () => void;
  onStarted: (options?: { launchMinimized?: boolean }) => void;
  onManageSubjects: () => void;
}> = ({ presetId = null, onBack, onStarted, onManageSubjects }) => {
  const {
    db,
    settings,
    globalBreakConfig,
    startSession,
    addSubject,
    addChapter,
    loadPreset,
    savePreset,
    deletePreset,
    getReadyProvider,
  } = useFocusFlow();

  const [selectedPlans, setSelectedPlans] = useState<PresetSubjectPlan[]>([]);
  const [chapterQuery, setChapterQuery] = useState('');
  const [description, setDescription] = useState('');
  const [tasks, setTasks] = useState<DraftTask[]>([]);
  const [mode, setMode] = useState<SessionMode>(settings.defaultMode);
  const [durationMinutes, setDurationMinutes] = useState<number>(settings.defaultDurationMin);
  const [customDuration, setCustomDuration] = useState(
    !DURATION_CHOICES.includes(settings.defaultDurationMin),
  );
  const [strict, setStrict] = useState(true);
  const [blockShorts, setBlockShorts] = useState(true);
  const [floatingAssistant, setFloatingAssistant] = useState(true);
  const [launchMinimized, setLaunchMinimized] = useState(false);
  const [includeBlocking, setIncludeBlocking] = useState(false);
  const [loadedPresetId, setLoadedPresetId] = useState<number | null>(null);

  const [analysisSummary, setAnalysisSummary] = useState<PlanAnalysisResult | null>(null);
  const [generating, setGenerating] = useState(false);
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [proposalOrigin, setProposalOrigin] = useState<TaskOrigin>('EXTERNAL_AI');
  const [proposalProvider, setProposalProvider] = useState<string | undefined>(undefined);
  const [aiNote, setAiNote] = useState<string | null>(null);

  const voiceRef = useRef<WebVoiceRecognizer | null>(null);
  const [voiceAvailable, setVoiceAvailable] = useState(false);
  const [listening, setListening] = useState(false);
  const [partial, setPartial] = useState('');

  const [toast, setToast] = useState<string | null>(null);
  const [dialog, setDialog] = useState<
    | null
    | { kind: 'newSubject' }
    | { kind: 'newChapter'; subjectId: number }
    | { kind: 'savePreset' }
    | { kind: 'newTask' }
    | { kind: 'editTask'; index: number }
  >(null);

  useEffect(() => {
    const rec = new WebVoiceRecognizer();
    voiceRef.current = rec;
    setVoiceAvailable(rec.availability() === 'AVAILABLE');
    return () => rec.stop();
  }, []);

  const applyPreset = (id: number) => {
    const p = loadPreset(id);
    if (!p) return;
    if (p.subjectPlans && p.subjectPlans.length > 0) {
      setSelectedPlans(p.subjectPlans);
    } else if (p.subjectId != null) {
      setSelectedPlans([{ subjectId: p.subjectId, chapterIds: p.chapterIds }]);
    }
    setMode(p.mode);
    setDurationMinutes(p.durationMinutes);
    setCustomDuration(!DURATION_CHOICES.includes(p.durationMinutes));
    setStrict(p.strict);
    setBlockShorts(p.blockYoutubeShorts);
    setFloatingAssistant(p.floatingAssistant);
    if (p.tasks.length > 0) setTasks(p.tasks);
    setLoadedPresetId(p.id);
  };

  useEffect(() => {
    if (presetId != null) applyPreset(presetId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [presetId]);

  const selectedSubjectIds = new Set(selectedPlans.map((p) => p.subjectId));
  const selectedSubjects = selectedPlans
    .map((p) => db.subjects.find((s) => s.id === p.subjectId))
    .filter((s): s is NonNullable<typeof s> => Boolean(s));
  const allChapterIds = Array.from(new Set(selectedPlans.flatMap((p) => p.chapterIds)));

  const inDraftIds = new Set(
    tasks.map((t) => t.existingTaskId).filter((x): x is number => x != null),
  );
  const resumable = db.tasks
    .filter((t) => t.status === 'OPEN' && !inDraftIds.has(t.id))
    .sort(
      (a, b) =>
        Number(a.subjectId != null && selectedSubjectIds.has(a.subjectId)) -
        Number(b.subjectId != null && selectedSubjectIds.has(b.subjectId)),
    )
    .reverse();

  const canStart =
    selectedSubjects.length > 0 && durationMinutes > 0 && proposals.length === 0 && !generating;

  const toggleSubject = (subId: number) => {
    setSelectedPlans((prev) => {
      if (prev.some((p) => p.subjectId === subId)) {
        return prev.filter((p) => p.subjectId !== subId);
      }
      return [...prev, { subjectId: subId, chapterIds: [] }];
    });
  };

  const toggleChapter = (subId: number, chId: number) => {
    setSelectedPlans((prev) =>
      prev.map((p) => {
        if (p.subjectId !== subId) return p;
        const has = p.chapterIds.includes(chId);
        return {
          ...p,
          chapterIds: has ? p.chapterIds.filter((id) => id !== chId) : [...p.chapterIds, chId],
        };
      }),
    );
  };

  const addDraftTasks = (incoming: DraftTask[]) => {
    setTasks((prev) => {
      const keys = new Set(prev.map((t) => TaskNormalizer.dedupeKey(t.title, t.chapterId)));
      const additions: DraftTask[] = [];
      for (const t of incoming) {
        const clean = TaskNormalizer.clean(t.title);
        if (!clean) continue;
        const k = TaskNormalizer.dedupeKey(clean, t.chapterId);
        if (!keys.has(k)) {
          keys.add(k);
          additions.push({ ...t, title: clean });
        }
      }
      return [...prev, ...additions];
    });
  };

  const applyDetectedPlan = (textToAnalyze: string, autoAddTasks = false) => {
    const clean = textToAnalyze.trim();
    if (!clean) {
      setToast('Describe what you want to study first');
      return;
    }
    const analysis = PlanAnalyzer.analyze(clean, db.subjects, db.chapters);
    setAnalysisSummary(analysis);

    if (analysis.detectedSubjects.length > 0) {
      setSelectedPlans(
        analysis.detectedSubjects.map((sp) => ({
          subjectId: sp.subjectId,
          chapterIds: sp.chapterIds,
        })),
      );
    }

    setMode('TIMED');
    setDurationMinutes(analysis.estimatedMinutes);
    setCustomDuration(!DURATION_CHOICES.includes(analysis.estimatedMinutes));

    if (autoAddTasks && analysis.suggestedTasks.length > 0) {
      addDraftTasks(analysis.suggestedTasks);
    }
    setToast(analysis.reason);
  };

  const handleGenerateTasks = async () => {
    const text = description.trim();
    if (!text) {
      setToast('Describe what you want to study first');
      return;
    }
    if (generating) return;
    setGenerating(true);
    setProposals([]);
    setAiNote(null);

    const chapterNames = db.chapters
      .filter((c) => allChapterIds.includes(c.id))
      .map((c) => c.name);
    const subjectTitle =
      selectedSubjects.length > 0 ? selectedSubjects.map((s) => s.name).join(' + ') : null;
    const res = await AiService.extractTasks(
      getReadyProvider(),
      text,
      subjectTitle,
      chapterNames,
    );
    setGenerating(false);
    setProposalOrigin(res.source);
    setProposalProvider(res.provider);
    setAiNote(res.note);
    setProposals(
      res.tasks.map((t) => ({
        title: t.title,
        minutes: t.minutes,
        subtasks: t.subtasks,
        selected: true,
      })),
    );
    if (res.tasks.length === 0 && !res.note) {
      setToast('No tasks found in that description');
    }
  };

  const toggleVoice = () => {
    const rec = voiceRef.current;
    if (!rec) return;
    if (listening) {
      rec.finishListening();
      return;
    }
    const started = rec.start(settings.voiceOfflineOnly, {
      onListening: () => {
        setListening(true);
        setPartial('');
      },
      onPartial: (text) => setPartial(text),
      onFinal: (text) => {
        setListening(false);
        setPartial('');
        if (text.trim()) {
          const next = `${description.trim()} ${text}`.trim();
          setDescription(next);
          applyDetectedPlan(next, true);
        }
      },
      onError: (msg) => {
        setListening(false);
        setPartial('');
        setToast(msg);
      },
    });
    if (!started) {
      setToast("Voice input isn't available in this browser. Type instead and click Auto-detect.");
    }
  };

  const handleSavePreset = (name: string, updateExisting: boolean) => {
    const existing = updateExisting && loadedPresetId ? loadPreset(loadedPresetId) : null;
    const base = existing ?? {
      id: 0,
      name,
      subjectId: null,
      subjectPlans: [],
      chapterIds: [],
      mode: 'TIMED' as SessionMode,
      durationMinutes: 25,
      strict: true,
      blockYoutubeShorts: true,
      floatingAssistant: true,
      blockedApps: [],
      allowedApps: [],
      breakConfig: null,
      tasks: [],
    };
    const draft = {
      ...base,
      name: existing ? existing.name : name,
      subjectId: selectedPlans[0]?.subjectId ?? null,
      subjectPlans: selectedPlans,
      chapterIds: allChapterIds,
      mode,
      durationMinutes,
      strict,
      blockYoutubeShorts: blockShorts,
      floatingAssistant,
      tasks,
      ...(includeBlocking
        ? {
            blockedApps: db.blockedApps
              .filter((b) => b.presetId === null)
              .map((b) => ({
                packageName: b.packageName,
                label: b.label,
                category: b.category,
                blockedDuringBreak: b.blockedDuringBreak,
              })),
            allowedApps: db.allowedApps
              .filter((a) => a.presetId === null)
              .map((a) => ({ packageName: a.packageName, label: a.label })),
            breakConfig: globalBreakConfig,
          }
        : {}),
    };
    const res = savePreset(draft);
    if (res.result === 'saved' && res.id) {
      setLoadedPresetId(res.id);
      setToast('Preset saved');
    } else if (res.result === 'blank') {
      setToast("Preset name can't be empty");
    } else {
      setToast('A preset with that name already exists');
    }
  };

  const handleStart = () => {
    if (selectedSubjects.length === 0) {
      setToast('Choose at least one subject first');
      return;
    }
    const plans: SubjectPlan[] = selectedPlans
      .map((sp) => {
        const sub = db.subjects.find((s) => s.id === sp.subjectId);
        if (!sub) return null;
        return {
          subjectId: sub.id,
          subjectName: sub.name,
          chapterIds: sp.chapterIds,
        };
      })
      .filter((p): p is SubjectPlan => p !== null);

    const combinedName = plans.map((p) => p.subjectName).join(' + ');
    const outcome = startSession({
      subjectId: plans[0].subjectId,
      subjectName: combinedName,
      subjectPlans: plans,
      chapterIds: allChapterIds,
      description,
      mode,
      plannedMinutes: mode === 'TIMED' ? durationMinutes : null,
      strict,
      presetId: loadedPresetId,
      tasks,
      floatingAssistant: floatingAssistant || launchMinimized,
      blockShorts,
    });
    if (outcome.kind === 'started') {
      onStarted({ launchMinimized });
    } else if (outcome.kind === 'already_active') {
      setToast('A session is already running');
    } else if (outcome.kind === 'invalid') {
      setToast(outcome.reason);
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-24">
      <SubScreenHeader title="Start studying" onBack={onBack} />

      {toast && (
        <div className="rounded-xl bg-[var(--ff-surface-variant)] px-4 py-2.5 text-sm text-[var(--ff-on-bg)] flex justify-between">
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

      {/* Speak or Describe first so users can auto-detect subjects, chapters & time immediately */}
      <AppCard>
        <SectionLabel>WHAT DO YOU WANT TO ACCOMPLISH?</SectionLabel>
        <p className="text-xs text-[var(--ff-on-surface-variant)]">
          Speak or type your plan (for example: &quot;I&apos;m doing sets for math and atomic
          structure 20 questions for chemistry&quot;). FocusFlow auto-selects matching subjects,
          chapters, and estimates your time.
        </p>
        <textarea
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="e.g. Doing sets and trigonometry for math and atomic structure for chemistry"
          className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] p-3.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
        />
        {listening && (
          <div className="flex items-center gap-2 text-sm text-[var(--ff-error)]">
            <Mic className="h-4 w-4" />
            <span>Listening…{partial ? ` “${partial}”` : ''}</span>
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={!voiceAvailable}
            onClick={toggleVoice}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 hover:bg-[var(--ff-surface-variant)] disabled:opacity-45 cursor-pointer"
          >
            <Mic className="h-4 w-4" />
            {listening ? 'Stop' : 'Speak'}
          </button>
          <button
            type="button"
            disabled={!description.trim()}
            onClick={() => applyDetectedPlan(description, false)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-primary)]/50 bg-[var(--ff-primary-container)]/35 text-[var(--ff-on-bg)] hover:bg-[var(--ff-primary-container)]/60 disabled:opacity-45 cursor-pointer"
          >
            <Sparkles className="h-4 w-4 text-[var(--ff-primary)]" />
            Auto-detect subjects &amp; time
          </button>
          <button
            type="button"
            disabled={!description.trim() || generating}
            onClick={handleGenerateTasks}
            className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            {generating ? 'Working…' : 'AI task suggestions'}
          </button>
        </div>

        {analysisSummary && (
          <div className="rounded-xl border border-[var(--ff-primary)]/40 bg-[var(--ff-bg)] p-3 flex flex-col gap-2">
            <div className="text-xs font-semibold text-[var(--ff-primary)]">
              {analysisSummary.reason}
            </div>
            {analysisSummary.suggestedTasks.length > 0 && (
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-xs text-[var(--ff-on-surface-variant)]">
                  Suggested {analysisSummary.suggestedTasks.length} draft task
                  {analysisSummary.suggestedTasks.length === 1 ? '' : 's'}
                </span>
                <button
                  type="button"
                  onClick={() => addDraftTasks(analysisSummary.suggestedTasks)}
                  className="text-xs font-semibold text-[var(--ff-primary)] hover:underline cursor-pointer"
                >
                  Add detected tasks
                </button>
              </div>
            )}
          </div>
        )}
      </AppCard>

      {/* Presets */}
      <AppCard>
        <SectionLabel>PRESETS</SectionLabel>
        {db.presets.length === 0 ? (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            Save your setup below to reuse it in one tap.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {db.presets.map((p) => (
              <ChipButton
                key={p.id}
                selected={loadedPresetId === p.id}
                onClick={() => applyPreset(p.id)}
              >
                {p.name}
              </ChipButton>
            ))}
          </div>
        )}
        <div className="flex items-center justify-between gap-4 pt-1">
          <span className="text-sm text-[var(--ff-on-bg)]">
            Also save app-blocking lists and break rules
          </span>
          <ToggleSwitch checked={includeBlocking} onChange={setIncludeBlocking} />
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setDialog({ kind: 'savePreset' })}
            className="px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Save as preset
          </button>
          {loadedPresetId !== null && (
            <>
              <button
                type="button"
                onClick={() => handleSavePreset('', true)}
                className="px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                Update preset
              </button>
              <button
                type="button"
                onClick={() => {
                  deletePreset(loadedPresetId);
                  setLoadedPresetId(null);
                  setToast('Preset deleted');
                }}
                className="px-3.5 py-2 rounded-xl text-sm font-medium text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                Delete
              </button>
            </>
          )}
        </div>
      </AppCard>

      {/* Multi-Subject Selection */}
      <AppCard>
        <div className="flex items-center justify-between">
          <SectionLabel>SUBJECTS ({selectedSubjects.length} selected)</SectionLabel>
          <span className="text-xs text-[var(--ff-on-surface-variant)]">
            Select one or multiple
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          {db.subjects.map((s) => (
            <ChipButton
              key={s.id}
              selected={selectedSubjectIds.has(s.id)}
              onClick={() => toggleSubject(s.id)}
            >
              {s.name}
            </ChipButton>
          ))}
          <ChipButton onClick={() => setDialog({ kind: 'newSubject' })}>
            <Plus className="h-4 w-4" />
            Custom
          </ChipButton>
        </div>
      </AppCard>

      {/* Chapters per Selected Subject */}
      {selectedSubjects.length > 0 && (
        <AppCard>
          <div className="flex items-center justify-between">
            <SectionLabel>CHAPTERS ({allChapterIds.length} selected)</SectionLabel>
            <button
              type="button"
              onClick={onManageSubjects}
              className="text-xs font-medium text-[var(--ff-primary)] hover:underline cursor-pointer"
            >
              Manage
            </button>
          </div>
          <div className="relative">
            <Search className="h-4 w-4 text-[var(--ff-on-surface-variant)] absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={chapterQuery}
              onChange={(e) => setChapterQuery(e.target.value)}
              placeholder="Search chapters across selected subjects"
              className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] pl-10 pr-3.5 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
            />
          </div>

          <div className="flex flex-col gap-4">
            {selectedPlans.map((sp) => {
              const sub = db.subjects.find((s) => s.id === sp.subjectId);
              if (!sub) return null;
              const chaptersInSubject = db.chapters.filter((c) => c.subjectId === sub.id);
              const filtered = chaptersInSubject.filter((c) =>
                c.name.toLowerCase().includes(chapterQuery.trim().toLowerCase()),
              );
              return (
                <div
                  key={sub.id}
                  className="rounded-xl border border-[var(--ff-outline)]/30 bg-[var(--ff-bg)]/60 p-3 flex flex-col gap-2"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="text-sm font-semibold text-[var(--ff-on-bg)]">
                      {sub.name} ({sp.chapterIds.length} selected)
                    </div>
                    <button
                      type="button"
                      onClick={() => setDialog({ kind: 'newChapter', subjectId: sub.id })}
                      className="inline-flex items-center gap-1 text-xs font-medium text-[var(--ff-primary)] hover:underline cursor-pointer"
                    >
                      <Plus className="h-3.5 w-3.5" />
                      Add {sub.name} chapter
                    </button>
                  </div>

                  {filtered.length === 0 ? (
                    <p className="text-xs text-[var(--ff-on-surface-variant)]">
                      {chaptersInSubject.length === 0
                        ? `No chapters for ${sub.name} yet.`
                        : 'No chapters match.'}
                    </p>
                  ) : (
                    <div className="max-h-48 overflow-y-auto flex flex-col gap-1 pr-1">
                      {filtered.slice(0, 40).map((c) => {
                        const checked = sp.chapterIds.includes(c.id);
                        return (
                          <label
                            key={c.id}
                            className="flex items-center gap-2.5 py-1 px-2 rounded-lg hover:bg-[var(--ff-surface-variant)]/50 cursor-pointer text-sm"
                          >
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() => toggleChapter(sub.id, c.id)}
                              className="h-4 w-4 accent-[var(--ff-primary)]"
                            />
                            <span>{c.name}</span>
                          </label>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </AppCard>
      )}

      {(proposals.length > 0 || aiNote) && (
        <AppCard>
          <SectionLabel>
            {proposalOrigin === 'EXTERNAL_AI'
              ? `SUGGESTED BY API AI${proposalProvider ? ` (${proposalProvider})` : ''}`
              : 'API AI UNAVAILABLE'}
          </SectionLabel>
          {aiNote && <p className="text-sm text-[var(--ff-on-surface-variant)]">{aiNote}</p>}
          {proposals.map((p, i) => (
            <label key={`${p.title}-${i}`} className="flex items-start gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={p.selected}
                onChange={() =>
                  setProposals((prev) =>
                    prev.map((item, idx) =>
                      idx === i ? { ...item, selected: !item.selected } : item,
                    ),
                  )
                }
                className="h-4 w-4 mt-1 accent-[var(--ff-primary)]"
              />
              <div className="flex-1">
                <div className="text-sm font-medium text-[var(--ff-on-bg)]">{p.title}</div>
                {(p.minutes || p.subtasks.length > 0) && (
                  <div className="text-xs text-[var(--ff-on-surface-variant)]">
                    {[
                      p.minutes ? `~${p.minutes} min` : null,
                      p.subtasks.length > 0 ? p.subtasks.join(' · ') : null,
                    ]
                      .filter(Boolean)
                      .join(' — ')}
                  </div>
                )}
              </div>
            </label>
          ))}
          {proposals.length > 0 ? (
            <div className="flex gap-2">
              <button
                type="button"
                disabled={!proposals.some((p) => p.selected)}
                onClick={() => {
                  addDraftTasks(
                    proposals
                      .filter((p) => p.selected)
                      .map((p) => ({
                        title: p.title,
                        estimatedMinutes: p.minutes,
                        origin: proposalOrigin,
                        chapterId: null,
                        subtasks: p.subtasks,
                      })),
                  );
                  setProposals([]);
                  setAiNote(null);
                }}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
              >
                Add selected
              </button>
              <button
                type="button"
                onClick={() => {
                  setProposals([]);
                  setAiNote(null);
                }}
                className="px-3.5 py-2 rounded-xl text-sm text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                Discard
              </button>
            </div>
          ) : (
            <div>
              <button
                type="button"
                onClick={() => setAiNote(null)}
                className="text-sm text-[var(--ff-primary)] hover:underline cursor-pointer"
              >
                Dismiss
              </button>
            </div>
          )}
        </AppCard>
      )}

      {/* Tasks */}
      <AppCard>
        <SectionLabel>TASKS ({tasks.length})</SectionLabel>
        {tasks.length === 0 ? (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            Add the concrete things you will finish. Breaks are earned by completing tasks.
          </p>
        ) : (
          <div className="flex flex-col gap-2.5">
            {tasks.map((task, idx) => {
              const chName = db.chapters.find((c) => c.id === task.chapterId)?.name;
              const meta = [
                task.estimatedMinutes ? `~${task.estimatedMinutes} min` : null,
                chName,
                task.existingTaskId != null ? 'unfinished from before' : null,
              ]
                .filter(Boolean)
                .join(' · ');
              return (
                <div
                  key={`${task.existingTaskId ?? -1}-${idx}-${task.title}`}
                  className="rounded-xl border border-[var(--ff-outline)]/30 bg-[var(--ff-bg)] p-3 flex items-start justify-between gap-2"
                >
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium text-[var(--ff-on-bg)]">{task.title}</div>
                    {meta && (
                      <div className="text-xs text-[var(--ff-on-surface-variant)]">{meta}</div>
                    )}
                    {task.subtasks.map((sub, sIdx) => (
                      <div key={sIdx} className="text-xs text-[var(--ff-on-surface-variant)] pl-2 pt-0.5">
                        • {sub}
                      </div>
                    ))}
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    {task.existingTaskId == null && (
                      <button
                        type="button"
                        aria-label={`Edit task ${task.title}`}
                        onClick={() => setDialog({ kind: 'editTask', index: idx })}
                        className="p-1.5 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                    )}
                    <button
                      type="button"
                      aria-label={`Remove task ${task.title}`}
                      onClick={() => setTasks((prev) => prev.filter((_, i) => i !== idx))}
                      className="p-1.5 rounded-lg text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        <div>
          <button
            type="button"
            onClick={() => setDialog({ kind: 'newTask' })}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            Add task
          </button>
        </div>
      </AppCard>

      {resumable.length > 0 && (
        <AppCard>
          <SectionLabel>UNFINISHED FROM EARLIER SESSIONS</SectionLabel>
          <div className="flex flex-col gap-2">
            {resumable.slice(0, 8).map((t: Task) => {
              const sName = db.subjects.find((s) => s.id === t.subjectId)?.name;
              const cName = db.chapters.find((c) => c.id === t.chapterId)?.name;
              const meta = [sName, cName].filter(Boolean).join(' · ');
              return (
                <div
                  key={t.id}
                  className="flex items-center justify-between gap-3 py-1 border-b border-[var(--ff-outline)]/20 last:border-none"
                >
                  <div className="min-w-0 flex-1">
                    <div className="text-sm text-[var(--ff-on-bg)] truncate">{t.title}</div>
                    {meta && (
                      <div className="text-xs text-[var(--ff-on-surface-variant)] truncate">
                        {meta}
                      </div>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      setTasks((prev) => [
                        ...prev,
                        {
                          title: t.title,
                          estimatedMinutes: t.estimatedMinutes,
                          origin: t.origin,
                          chapterId: t.chapterId,
                          existingTaskId: t.id,
                          subtasks: [],
                        },
                      ])
                    }
                    className="text-xs font-semibold text-[var(--ff-primary)] hover:underline px-2 py-1 cursor-pointer"
                  >
                    Add
                  </button>
                </div>
              );
            })}
          </div>
        </AppCard>
      )}

      {/* Mode & Duration */}
      <AppCard>
        <SectionLabel>FOCUS MODE</SectionLabel>
        <div className="flex flex-wrap gap-2">
          <ChipButton selected={mode === 'TIMED'} onClick={() => setMode('TIMED')}>
            Timed
          </ChipButton>
          <ChipButton selected={mode === 'OPEN_ENDED'} onClick={() => setMode('OPEN_ENDED')}>
            Open-ended
          </ChipButton>
        </div>

        {mode === 'TIMED' ? (
          <>
            <SectionLabel className="pt-1">DURATION</SectionLabel>
            <div className="flex flex-wrap gap-2">
              {DURATION_CHOICES.map((d) => (
                <ChipButton
                  key={d}
                  selected={!customDuration && durationMinutes === d}
                  onClick={() => {
                    setCustomDuration(false);
                    setDurationMinutes(d);
                  }}
                >
                  {d} min
                </ChipButton>
              ))}
              <ChipButton
                selected={customDuration || !DURATION_CHOICES.includes(durationMinutes)}
                onClick={() => setCustomDuration(true)}
              >
                Custom ({durationMinutes}m)
              </ChipButton>
            </div>
            {(customDuration || !DURATION_CHOICES.includes(durationMinutes)) && (
              <label className="flex flex-col gap-1.5 pt-1">
                <span className="text-xs text-[var(--ff-on-surface-variant)]">Minutes</span>
                <input
                  type="number"
                  min={1}
                  max={600}
                  value={durationMinutes}
                  onChange={(e) => {
                    const n = Number(e.target.value);
                    if (Number.isFinite(n) && n > 0) setDurationMinutes(Math.min(600, Math.round(n)));
                  }}
                  className="w-full rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-bg)] px-3.5 py-2 text-sm font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
                />
              </label>
            )}
          </>
        ) : (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            No end time. A stopwatch runs until you finish.
          </p>
        )}
      </AppCard>

      {/* Strict */}
      <AppCard>
        <div className="flex items-center justify-between gap-4">
          <div>
            <div className="text-base font-medium text-[var(--ff-on-bg)]">Strict session</div>
            <p className="text-xs text-[var(--ff-on-surface-variant)] pt-0.5">
              No ordinary early exit. Timed sessions end when the timer does; open-ended ones when
              your tasks are done.
            </p>
          </div>
          <ToggleSwitch checked={strict} onChange={setStrict} />
        </div>
      </AppCard>

      {/* Focus environment */}
      <AppCard>
        <SectionLabel>FOCUS ENVIRONMENT</SectionLabel>
        <div className="flex items-center justify-between gap-4">
          <div>
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">Floating assistant</div>
            <div className="text-xs text-[var(--ff-on-surface-variant)]">
              Show the draggable FocusFlow circle while you study.
            </div>
          </div>
          <ToggleSwitch checked={floatingAssistant} onChange={setFloatingAssistant} />
        </div>
        <div className="flex items-center justify-between gap-4 pt-1">
          <div>
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">
              Start minimized to circle
            </div>
            <div className="text-xs text-[var(--ff-on-surface-variant)]">
              Keep the timer in the floating circle without opening the full Focus screen.
            </div>
          </div>
          <ToggleSwitch checked={launchMinimized} onChange={setLaunchMinimized} />
        </div>
        <div className="flex items-center justify-between gap-4 pt-1">
          <div>
            <div className="text-sm font-medium text-[var(--ff-on-bg)]">Block YouTube Shorts</div>
            <div className="text-xs text-[var(--ff-on-surface-variant)]">
              Separate from full-YouTube blocking so you can block only Shorts if desired.
            </div>
          </div>
          <ToggleSwitch checked={blockShorts} onChange={setBlockShorts} />
        </div>
      </AppCard>

      <div className="sticky bottom-4 pt-2">
        <button
          type="button"
          disabled={!canStart}
          onClick={handleStart}
          className="w-full py-3.5 px-6 rounded-2xl text-sm font-semibold bg-[var(--ff-primary)] text-[var(--ff-on-primary)] shadow-lg disabled:opacity-45 disabled:cursor-not-allowed cursor-pointer transition-opacity"
        >
          {selectedSubjects.length === 0
            ? 'Choose at least one subject to start'
            : proposals.length > 0
              ? 'Add or discard suggested tasks first'
              : `Start ${selectedSubjects.map((s) => s.name).join(' + ')} (${
                  mode === 'TIMED' ? `${durationMinutes}m` : 'Open'
                })`}
        </button>
      </div>

      {/* Dialogs */}
      <TextInputDialog
        open={dialog?.kind === 'newSubject'}
        title="New subject"
        label="Subject name"
        confirmLabel="Add"
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          const res = addSubject(val);
          if (res === 'DUPLICATE') setToast('That subject already exists');
          else if (res === 'BLANK') setToast("Subject name can't be empty");
          setDialog(null);
        }}
      />

      <TextInputDialog
        open={dialog?.kind === 'newChapter'}
        title="New chapter"
        label="Chapter name"
        confirmLabel="Add"
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          if (dialog?.kind === 'newChapter') {
            const res = addChapter(dialog.subjectId, val);
            if (res === 'DUPLICATE') setToast('That chapter already exists');
            else if (res === 'BLANK') setToast("Chapter name can't be empty");
          }
          setDialog(null);
        }}
      />

      <TextInputDialog
        open={dialog?.kind === 'savePreset'}
        title="Save preset"
        label="Preset name (e.g. Math + Chemistry)"
        confirmLabel="Save"
        onDismiss={() => setDialog(null)}
        onConfirm={(val) => {
          handleSavePreset(val, false);
          setDialog(null);
        }}
      />

      {(dialog?.kind === 'newTask' || dialog?.kind === 'editTask') && (
        <PrepTaskDialog
          title={dialog.kind === 'newTask' ? 'Add task' : 'Edit task'}
          initial={dialog.kind === 'editTask' ? tasks[dialog.index] : null}
          chapterOptions={db.chapters.filter((c) => allChapterIds.includes(c.id))}
          onDismiss={() => setDialog(null)}
          onConfirm={(t, mins, chId, subs) => {
            if (dialog.kind === 'newTask') {
              addDraftTasks([
                {
                  title: t,
                  estimatedMinutes: mins,
                  origin: 'MANUAL',
                  chapterId: chId,
                  subtasks: subs,
                },
              ]);
            } else {
              const idx = dialog.index;
              setTasks((prev) =>
                prev.map((item, i) =>
                  i === idx
                    ? {
                        ...item,
                        title: TaskNormalizer.clean(t),
                        estimatedMinutes: mins,
                        chapterId: chId,
                        subtasks: subs,
                      }
                    : item,
                ),
              );
            }
            setDialog(null);
          }}
        />
      )}
    </div>
  );
};

const PrepTaskDialog: React.FC<{
  title: string;
  initial: DraftTask | null;
  chapterOptions: Array<{ id: number; name: string }>;
  onDismiss: () => void;
  onConfirm: (
    title: string,
    minutes: number | null,
    chapterId: number | null,
    subtasks: string[],
  ) => void;
}> = ({ title, initial, chapterOptions, onDismiss, onConfirm }) => {
  const [text, setText] = useState(initial?.title ?? '');
  const [minutes, setMinutes] = useState(initial?.estimatedMinutes?.toString() ?? '');
  const [chapterId, setChapterId] = useState<number | null>(initial?.chapterId ?? null);
  const [subtasksText, setSubtasksText] = useState(initial?.subtasks.join('\n') ?? '');

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-md rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/45 p-6 flex flex-col gap-3.5 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">{title}</h3>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">Task</span>
          <input
            autoFocus
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">
            Estimate in minutes (optional)
          </span>
          <input
            type="number"
            min={1}
            max={240}
            value={minutes}
            onChange={(e) => setMinutes(e.target.value.replace(/\D/g, '').slice(0, 3))}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2 text-sm font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">
            Subtasks (optional, one per line)
          </span>
          <textarea
            rows={3}
            value={subtasksText}
            onChange={(e) => setSubtasksText(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        {chapterOptions.length > 0 && (
          <div className="flex flex-col gap-1.5">
            <SectionLabel>CHAPTER (OPTIONAL)</SectionLabel>
            <div className="flex flex-wrap gap-2">
              {chapterOptions.map((c) => (
                <ChipButton
                  key={c.id}
                  selected={chapterId === c.id}
                  onClick={() => setChapterId(chapterId === c.id ? null : c.id)}
                >
                  {c.name}
                </ChipButton>
              ))}
            </div>
          </div>
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
            disabled={!text.trim()}
            onClick={() =>
              onConfirm(
                text,
                minutes ? Number(minutes) : null,
                chapterId,
                subtasksText
                  .split('\n')
                  .map((s) => s.trim())
                  .filter(Boolean),
              )
            }
            className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
};
