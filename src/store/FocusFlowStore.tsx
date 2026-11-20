import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  ActiveSession,
  AllowedApplication,
  AppCategory,
  AppSettings,
  BlockedApplication,
  BreakConfig,
  BreakConfigurationEntity,
  Chapter,
  DashboardWidget,
  DraftTask,
  PresetChapter,
  PresetDraft,
  PresetTask,
  ProgressRange,
  ProviderConfig,
  SessionChapter,
  SessionDraft,
  SessionMode,
  SessionStatus,
  SessionSummary,
  SessionTask,
  SessionTaskRow,
  StudyPreset,
  StudySession,
  Subject,
  SubjectPlan,
  Subtask,
  SummarySource,
  Task,
  TaskOrigin,
  ThemeMode,
  ShortsMode,
  WidgetAction,
} from '../types/models';
import { Class11Chapters } from '../domain/Class11Chapters';
import { DEFAULT_BREAK_CONFIG, BreakRules } from '../domain/BreakRules';
import { BlockPolicy, BlockRule, DEFAULT_ESSENTIAL_PACKAGES, YOUTUBE_PACKAGE } from '../domain/BlockPolicy';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { TaskNormalizer } from '../domain/TaskNormalizer';
import { WidgetDraftBuilder, WidgetOrder } from '../domain/WidgetDraftBuilder';
import { SettingsBackup } from './SettingsBackup';
import { INSTALLED_APPS_CATALOG, CatalogApp } from './InstalledAppsCatalog';

const DB_STORAGE_KEY = 'focusflow_db_v1';
const SETTINGS_STORAGE_KEY = 'focusflow_settings_v1';
const KEYS_STORAGE_KEY = 'focusflow_provider_keys_v1';

export interface FocusFlowDbState {
  nextId: number;
  subjects: Subject[];
  chapters: Chapter[];
  tasks: Task[];
  subtasks: Subtask[];
  sessions: StudySession[];
  sessionChapters: SessionChapter[];
  sessionTasks: SessionTask[];
  presets: StudyPreset[];
  presetChapters: PresetChapter[];
  presetTasks: PresetTask[];
  blockedApps: BlockedApplication[];
  allowedApps: AllowedApplication[];
  breakConfigs: BreakConfigurationEntity[];
  summaries: SessionSummary[];
  widgets: DashboardWidget[];
  customCatalogApps: CatalogApp[];
}

const DEFAULT_SETTINGS: AppSettings = {
  themeMode: 'DARK',
  defaultMode: 'TIMED',
  defaultDurationMin: 25,
  shortsMode: 'FOCUS_ONLY',
  homeRedirect: false,
  blockYoutubeApp: true,
  blockAllExceptAllowed: false,
  floatingEnabled: true,
  voiceOfflineOnly: true,
  bubblePosition: null,
  class11SeedVersion: 0,
  widgetsSeeded: false,
  progressWidgetSeeded: false,
  snapGptEnabled: false,
  snapGptInstalled: true,
  snapGptPackage: 'com.snapgpt.assistant',
  snapGptLastSession: -1,
  aiProviders: [],
  aiSelectedProvider: null,
  wifiSyncCode: null,
};

function createEmptyDb(): FocusFlowDbState {
  return {
    nextId: 1,
    subjects: [],
    chapters: [],
    tasks: [],
    subtasks: [],
    sessions: [],
    sessionChapters: [],
    sessionTasks: [],
    presets: [],
    presetChapters: [],
    presetTasks: [],
    blockedApps: [],
    allowedApps: [],
    breakConfigs: [],
    summaries: [],
    widgets: [],
    customCatalogApps: [],
  };
}

function seedInitialData(db: FocusFlowDbState, settings: AppSettings): { db: FocusFlowDbState; settings: AppSettings } {
  const nextDb = { ...db };
  const nextSettings = { ...settings };
  let id = nextDb.nextId;

  // 1. Ensure built-in subjects
  const subjects = [...nextDb.subjects];
  for (const name of Class11Chapters.builtInSubjects) {
    if (!subjects.some((s) => s.name.toLowerCase() === name.toLowerCase())) {
      subjects.push({
        id: id++,
        name,
        builtIn: true,
        createdAt: Date.now(),
      });
    }
  }
  nextDb.subjects = subjects;

  // 2. Ensure Class 11 chapters once per SEED_VERSION
  const chapters = [...nextDb.chapters];
  if (nextSettings.class11SeedVersion < Class11Chapters.SEED_VERSION) {
    for (const [subjectName, chapterList] of Object.entries(Class11Chapters.bySubject)) {
      const sub = subjects.find((s) => s.name.toLowerCase() === subjectName.toLowerCase());
      if (!sub) continue;
      for (const chName of chapterList) {
        const exists = chapters.some(
          (c) => c.subjectId === sub.id && c.name.toLowerCase() === chName.toLowerCase(),
        );
        if (!exists) {
          chapters.push({
            id: id++,
            subjectId: sub.id,
            name: chName,
            selfConfidence: null,
            createdAt: Date.now(),
          });
        }
      }
    }
    nextSettings.class11SeedVersion = Class11Chapters.SEED_VERSION;
  }
  nextDb.chapters = chapters;

  // 3. Ensure default dashboard widgets once
  if (!nextSettings.widgetsSeeded) {
    if (nextDb.widgets.length === 0) {
      const mathId = subjects.find((s) => s.name.toLowerCase() === 'mathematics')?.id ?? null;
      const physicsId = subjects.find((s) => s.name.toLowerCase() === 'physics')?.id ?? null;
      const now = Date.now();
      const defaults: DashboardWidget[] = [
        {
          id: crypto.randomUUID(),
          type: 'TIMER',
          title: 'Focus 25',
          icon: 'timer',
          colorKey: 'indigo',
          size: 'SMALL',
          position: 0,
          visible: true,
          mode: 'TIMED',
          durationMinutes: 25,
          strict: true,
          subjectId: null,
          chapterIds: '',
          taskIds: '',
          presetId: null,
          action: 'START_SESSION',
          createdAt: now,
        },
        {
          id: crypto.randomUUID(),
          type: 'OPEN_ENDED',
          title: 'Open focus',
          icon: 'play',
          colorKey: 'teal',
          size: 'SMALL',
          position: 1,
          visible: true,
          mode: 'OPEN_ENDED',
          durationMinutes: 25,
          strict: true,
          subjectId: null,
          chapterIds: '',
          taskIds: '',
          presetId: null,
          action: 'START_SESSION',
          createdAt: now,
        },
        {
          id: crypto.randomUUID(),
          type: 'SUBJECT',
          title: 'Mathematics',
          icon: 'calculate',
          colorKey: 'amber',
          size: 'SMALL',
          position: 2,
          visible: true,
          mode: 'TIMED',
          durationMinutes: 60,
          strict: true,
          subjectId: mathId,
          chapterIds: '',
          taskIds: '',
          presetId: null,
          action: 'START_SESSION',
          createdAt: now,
        },
        {
          id: crypto.randomUUID(),
          type: 'SUBJECT',
          title: 'Physics',
          icon: 'science',
          colorKey: 'rose',
          size: 'SMALL',
          position: 3,
          visible: true,
          mode: 'TIMED',
          durationMinutes: 60,
          strict: true,
          subjectId: physicsId,
          chapterIds: '',
          taskIds: '',
          presetId: null,
          action: 'START_SESSION',
          createdAt: now,
        },
      ];
      nextDb.widgets = defaults;
    }
    nextSettings.widgetsSeeded = true;
  }

  // 3b. Ensure one default Progress widget once
  if (!nextSettings.progressWidgetSeeded) {
    if (!nextDb.widgets.some((w) => w.type === 'PROGRESS')) {
      const nextPos = nextDb.widgets.reduce((m, w) => Math.max(m, w.position), -1) + 1;
      nextDb.widgets = [
        ...nextDb.widgets,
        {
          id: crypto.randomUUID(),
          type: 'PROGRESS',
          title: 'Study progress',
          icon: 'trophy',
          colorKey: 'teal',
          size: 'LARGE',
          position: nextPos,
          visible: true,
          mode: 'TIMED',
          durationMinutes: 25,
          strict: true,
          subjectId: null,
          chapterIds: '',
          taskIds: '',
          presetId: null,
          action: 'OPEN_STATISTICS',
          progressRange: 'DAYS',
          createdAt: Date.now(),
        },
      ];
    }
    nextSettings.progressWidgetSeeded = true;
  }

  // Ensure global break config exists
  if (!nextDb.breakConfigs.some((b) => b.presetId === null)) {
    nextDb.breakConfigs = [
      ...nextDb.breakConfigs,
      { id: id++, presetId: null, ...DEFAULT_BREAK_CONFIG },
    ];
  }

  nextDb.nextId = id;
  return { db: nextDb, settings: nextSettings };
}

function loadInitialState(): {
  db: FocusFlowDbState;
  settings: AppSettings;
  keys: Record<string, string>;
} {
  let rawDb = createEmptyDb();
  let rawSettings = { ...DEFAULT_SETTINGS };
  let keys: Record<string, string> = {};

  try {
    const dbStr = localStorage.getItem(DB_STORAGE_KEY);
    if (dbStr) {
      const parsed = JSON.parse(dbStr) as Partial<FocusFlowDbState>;
      rawDb = { ...createEmptyDb(), ...parsed };
    }
  } catch {
    // ignore corrupt storage
  }

  try {
    const setStr = localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (setStr) {
      const parsed = JSON.parse(setStr) as Partial<AppSettings>;
      rawSettings = { ...DEFAULT_SETTINGS, ...parsed };
    }
  } catch {
    // ignore
  }

  try {
    const keyStr = localStorage.getItem(KEYS_STORAGE_KEY);
    if (keyStr) {
      keys = JSON.parse(keyStr) as Record<string, string>;
    }
  } catch {
    // ignore
  }

  const seeded = seedInitialData(rawDb, rawSettings);
  return { db: seeded.db, settings: seeded.settings, keys };
}

export type EditResult = 'OK' | 'BLANK' | 'DUPLICATE' | 'NOT_ALLOWED';
export type BreakKind = 'SHORT' | 'LONG';

export type LaunchOutcome =
  | {
      kind: 'started';
      sessionId: number;
      notes: string[];
      launchMinimized?: boolean;
      snapGptTriggered?: boolean;
    }
  | { kind: 'already_active' }
  | { kind: 'invalid'; reason: string }
  | { kind: 'navigate'; action: WidgetAction }
  | { kind: 'progress' };

export interface ShieldPrompt {
  packageName: string;
  label: string;
  isShorts: boolean;
  reason: string;
  canOpenRegularYoutube: boolean;
  blockedDuringBreak: boolean;
}

export interface SnapGptTrigger {
  sessionId: number;
  packageName: string;
  at: number;
}

export interface FocusFlowContextValue {
  db: FocusFlowDbState;
  settings: AppSettings;
  providerKeys: Record<string, string>;
  activeSession: ActiveSession | null;
  globalBreakConfig: BreakConfig;
  allCatalogApps: CatalogApp[];
  now: number;
  lastEndedSessionId: number | null;
  clearLastEndedSession: () => void;
  lastSnapGptTrigger: SnapGptTrigger | null;
  dismissSnapGptTrigger: () => void;

  // Focus Shield / App Guard
  activeShield: ShieldPrompt | null;
  openSimulatedApp: (packageName: string, label: string, options?: { isShorts?: boolean }) => 'opened' | 'blocked';
  closeShieldedApp: () => void;
  dismissShield: () => void;
  isAppCurrentlyBlocked: (packageName: string, options?: { isShorts?: boolean }) => {
    blocked: boolean;
    blockedDuringBreak: boolean;
    reason: string;
  };
  closedByShieldPackages: string[];

  // Subjects & Chapters
  addSubject: (name: string) => EditResult;
  renameSubject: (subject: Subject, name: string) => EditResult;
  deleteSubject: (subject: Subject) => EditResult;
  addChapter: (subjectId: number, name: string) => EditResult;
  renameChapter: (chapter: Chapter, name: string) => EditResult;
  deleteChapter: (chapter: Chapter) => void;
  setChapterConfidence: (chapterId: number, confidence: number | null) => void;

  // Tasks
  addTask: (
    title: string,
    subjectId: number | null,
    chapterId: number | null,
    estimatedMinutes?: number | null,
    origin?: TaskOrigin,
  ) => { result: 'added' | 'already_open' | 'blank'; taskId?: number };
  updateTask: (
    taskId: number,
    patch: {
      title: string;
      subjectId: number | null;
      chapterId: number | null;
      estimatedMinutes: number | null;
    },
  ) => EditResult;
  markTaskDone: (taskId: number) => void;
  reopenTask: (taskId: number) => void;
  dismissTask: (taskId: number) => void;

  // Sessions
  startSession: (draft: SessionDraft) => LaunchOutcome;
  runWidget: (widgetId: string) => LaunchOutcome;
  setSessionTaskCompleted: (sessionTaskId: number, taskId: number, completed: boolean) => void;
  setSubtaskDone: (subtaskId: number, done: boolean) => void;
  addSessionTask: (sessionId: number, title: string, estimatedMinutes?: number | null) => boolean;
  removeSessionTask: (sessionTaskId: number) => void;
  updateActivePlannedMinutes: (sessionId: number, minutes: number) => void;
  startBreak: (sessionId: number, kind: BreakKind) => boolean;
  endBreakEarly: (sessionId: number) => void;
  endSession: (sessionId: number, status: SessionStatus) => void;
  updateSession: (
    sessionId: number,
    patch: {
      subjectName?: string;
      description?: string;
      status?: SessionStatus;
      plannedDurationMs?: number | null;
    },
  ) => void;
  deleteSession: (sessionId: number) => void;
  saveSessionSummary: (sessionId: number, text: string, source: SummarySource) => void;

  // Presets
  loadPreset: (presetId: number) => PresetDraft | null;
  savePreset: (draft: PresetDraft) => { result: 'saved' | 'blank' | 'duplicate'; id?: number };
  deletePreset: (presetId: number) => void;

  // Widgets
  saveWidget: (widget: DashboardWidget) => 'saved' | 'blank_title';
  updateWidgetProgressRange: (id: string, range: ProgressRange) => void;
  deleteWidget: (id: string) => void;
  moveWidget: (id: string, delta: number) => void;
  setWidgetVisible: (id: string, visible: boolean) => void;

  // Blocking & Breaks
  setAppRule: (app: CatalogApp, choice: 'BLOCK' | 'ALLOW' | 'NONE') => void;
  setBlockedDuringBreak: (packageName: string, blockedDuringBreak: boolean) => void;
  setYoutubeBlockedDuringBreak: (blockedDuringBreak: boolean) => void;
  blockAllInCategory: (category: AppCategory) => void;
  addCustomCatalogApp: (packageName: string, label: string, category?: AppCategory) => void;
  saveGlobalBreakConfig: (cfg: BreakConfig) => void;

  // Settings & AI Providers
  updateSettings: (patch: Partial<AppSettings>) => void;
  resetBubblePosition: () => void;
  saveProvider: (config: ProviderConfig, apiKey: string | null) => void;
  deleteProvider: (id: string) => void;
  selectProvider: (id: string | null) => void;
  getReadyProvider: () => { config: ProviderConfig; apiKey: string } | null;

  // Backups & Resets
  exportDatabaseJson: () => string;
  importDatabaseJson: (json: string) => { ok: true; message: string } | { ok: false; reason: string };
  exportSettingsJson: () => string;
  importSettingsJson: (json: string) => { ok: true; message: string } | { ok: false; reason: string };
  resetHistory: () => string;
  resetEverything: () => string;
}

const FocusFlowContext = createContext<FocusFlowContextValue | null>(null);

export const FocusFlowProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [initial] = useState(loadInitialState);
  const [db, setDb] = useState<FocusFlowDbState>(initial.db);
  const [settings, setSettings] = useState<AppSettings>(initial.settings);
  const [providerKeys, setProviderKeys] = useState<Record<string, string>>(initial.keys);
  const [now, setNow] = useState<number>(() => Date.now());
  const [lastEndedSessionId, setLastEndedSessionId] = useState<number | null>(null);
  const [lastSnapGptTrigger, setLastSnapGptTrigger] = useState<SnapGptTrigger | null>(null);
  const [activeShield, setActiveShield] = useState<ShieldPrompt | null>(null);
  const [closedByShieldPackages, setClosedByShieldPackages] = useState<string[]>([]);

  // Persist to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(DB_STORAGE_KEY, JSON.stringify(db));
    } catch {
      // ignore quota errors
    }
  }, [db]);

  useEffect(() => {
    try {
      localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // ignore
    }
  }, [settings]);

  useEffect(() => {
    try {
      localStorage.setItem(KEYS_STORAGE_KEY, JSON.stringify(providerKeys));
    } catch {
      // ignore
    }
  }, [providerKeys]);

  // Apply theme to <html>
  useEffect(() => {
    const apply = () => {
      const dark =
        settings.themeMode === 'DARK' ||
        (settings.themeMode === 'SYSTEM' &&
          window.matchMedia('(prefers-color-scheme: dark)').matches);
      document.documentElement.classList.toggle('dark', dark);
      document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
    };
    apply();
    if (settings.themeMode === 'SYSTEM') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      mq.addEventListener('change', apply);
      return () => mq.removeEventListener('change', apply);
    }
  }, [settings.themeMode]);

  // Live 1s ticker & session clock housekeeping (settle finished breaks / finish timed sessions)
  useEffect(() => {
    const interval = window.setInterval(() => {
      const currentNow = Date.now();
      setNow(currentNow);

      setDb((prev) => {
        const activeIdx = prev.sessions.findIndex((s) => s.status === 'ACTIVE');
        if (activeIdx < 0) return prev;
        const s = prev.sessions[activeIdx];
        const settled = SessionClock.settleBreak(sessionToClock(s), currentNow);
        const clockChanged =
          settled.breakMsCompleted !== s.breakMsCompleted ||
          settled.breakStartedAt !== s.breakStartedAt ||
          settled.breakEndsAt !== s.breakEndsAt;

        const timedFinished = SessionClock.isTimedFinished(settled, currentNow);
        if (!clockChanged && !timedFinished) return prev;

        const sessions = [...prev.sessions];
        if (timedFinished) {
          const overshoot =
            SessionClock.focusElapsedMs(settled, currentNow) - (settled.plannedMs ?? 0);
          const finishAt = Math.max(s.startedAt, currentNow - Math.max(0, overshoot));
          let finalClock = SessionClock.settleBreak(settled, finishAt);
          if (SessionClock.isOnBreak(finalClock, finishAt)) {
            finalClock = SessionClock.endBreakEarly(finalClock, finishAt);
          }
          sessions[activeIdx] = {
            ...s,
            breakMsCompleted: finalClock.breakMsCompleted,
            breakStartedAt: finalClock.breakStartedAt,
            breakEndsAt: finalClock.breakEndsAt,
            endedAt: finishAt,
            status: 'COMPLETED',
          };
          setTimeout(() => setLastEndedSessionId(s.id), 0);
        } else {
          sessions[activeIdx] = {
            ...s,
            breakMsCompleted: settled.breakMsCompleted,
            breakStartedAt: settled.breakStartedAt,
            breakEndsAt: settled.breakEndsAt,
          };
        }
        return { ...prev, sessions };
      });
    }, 1000);
    return () => window.clearInterval(interval);
  }, []);

  const globalBreakConfig = useMemo<BreakConfig>(() => {
    const found = db.breakConfigs.find((b) => b.presetId === null);
    if (!found) return DEFAULT_BREAK_CONFIG;
    return {
      enabled: found.enabled,
      tasksForShortBreak: found.tasksForShortBreak,
      shortBreakMinutes: found.shortBreakMinutes,
      tasksForLongBreak: found.tasksForLongBreak,
      longBreakMinutes: found.longBreakMinutes,
    };
  }, [db.breakConfigs]);

  const activeSession = useMemo<ActiveSession | null>(() => {
    const s = db.sessions.find((x) => x.status === 'ACTIVE');
    if (!s) return null;
    const links = db.sessionTasks
      .filter((st) => st.sessionId === s.id)
      .sort((a, b) => a.position - b.position);
    const taskMap = new Map(db.tasks.map((t) => [t.id, t]));
    const rows: SessionTaskRow[] = links
      .map((link) => {
        const task = taskMap.get(link.taskId);
        if (!task) return null;
        return { link, title: task.title, estimatedMinutes: task.estimatedMinutes };
      })
      .filter((r): r is SessionTaskRow => r !== null);
    const chapterIds = db.sessionChapters
      .filter((sc) => sc.sessionId === s.id)
      .map((sc) => sc.chapterId);
    const linkedTaskIds = new Set(links.map((l) => l.taskId));
    const subtasks = db.subtasks
      .filter((sub) => linkedTaskIds.has(sub.taskId))
      .sort((a, b) => a.position - b.position);
    const presetBreak = s.presetId
      ? db.breakConfigs.find((b) => b.presetId === s.presetId)
      : undefined;
    const breakConfig = presetBreak ?? globalBreakConfig;

    return {
      session: s,
      tasks: rows,
      chapterIds,
      subtasks,
      breakConfig: {
        enabled: breakConfig.enabled,
        tasksForShortBreak: breakConfig.tasksForShortBreak,
        shortBreakMinutes: breakConfig.shortBreakMinutes,
        tasksForLongBreak: breakConfig.tasksForLongBreak,
        longBreakMinutes: breakConfig.longBreakMinutes,
      },
    };
  }, [db, globalBreakConfig]);

  const allCatalogApps = useMemo(() => {
    const map = new Map<string, CatalogApp>();
    for (const app of INSTALLED_APPS_CATALOG) map.set(app.packageName, app);
    for (const app of db.customCatalogApps) map.set(app.packageName, app);
    return Array.from(map.values()).sort((a, b) =>
      a.label.toLowerCase().localeCompare(b.label.toLowerCase()),
    );
  }, [db.customCatalogApps]);

  const clearLastEndedSession = useCallback(() => setLastEndedSessionId(null), []);

  // ---- Subjects & Chapters ----
  const addSubject = useCallback(
    (rawName: string): EditResult => {
      const name = TaskNormalizer.clean(rawName);
      if (!name) return 'BLANK';
      if (db.subjects.some((s) => s.name.toLowerCase() === name.toLowerCase())) {
        return 'DUPLICATE';
      }
      setDb((prev) => ({
        ...prev,
        nextId: prev.nextId + 1,
        subjects: [
          ...prev.subjects,
          { id: prev.nextId, name, builtIn: false, createdAt: Date.now() },
        ],
      }));
      return 'OK';
    },
    [db.subjects],
  );

  const renameSubject = useCallback(
    (subject: Subject, rawName: string): EditResult => {
      if (subject.builtIn) return 'NOT_ALLOWED';
      const name = TaskNormalizer.clean(rawName);
      if (!name) return 'BLANK';
      if (db.subjects.some((s) => s.id !== subject.id && s.name.toLowerCase() === name.toLowerCase())) {
        return 'DUPLICATE';
      }
      setDb((prev) => ({
        ...prev,
        subjects: prev.subjects.map((s) => (s.id === subject.id ? { ...s, name } : s)),
      }));
      return 'OK';
    },
    [db.subjects],
  );

  const deleteSubject = useCallback((subject: Subject): EditResult => {
    if (subject.builtIn) return 'NOT_ALLOWED';
    setDb((prev) => {
      const removedChapterIds = new Set(
        prev.chapters.filter((c) => c.subjectId === subject.id).map((c) => c.id),
      );
      return {
        ...prev,
        subjects: prev.subjects.filter((s) => s.id !== subject.id),
        chapters: prev.chapters.filter((c) => c.subjectId !== subject.id),
        tasks: prev.tasks.map((t) => ({
          ...t,
          subjectId: t.subjectId === subject.id ? null : t.subjectId,
          chapterId: t.chapterId && removedChapterIds.has(t.chapterId) ? null : t.chapterId,
        })),
        presets: prev.presets.map((p) =>
          p.subjectId === subject.id ? { ...p, subjectId: null } : p,
        ),
        presetChapters: prev.presetChapters.filter((pc) => !removedChapterIds.has(pc.chapterId)),
        sessionChapters: prev.sessionChapters.filter((sc) => !removedChapterIds.has(sc.chapterId)),
      };
    });
    return 'OK';
  }, []);

  const addChapter = useCallback(
    (subjectId: number, rawName: string): EditResult => {
      const name = TaskNormalizer.clean(rawName);
      if (!name) return 'BLANK';
      if (
        db.chapters.some(
          (c) => c.subjectId === subjectId && c.name.toLowerCase() === name.toLowerCase(),
        )
      ) {
        return 'DUPLICATE';
      }
      setDb((prev) => ({
        ...prev,
        nextId: prev.nextId + 1,
        chapters: [
          ...prev.chapters,
          { id: prev.nextId, subjectId, name, selfConfidence: null, createdAt: Date.now() },
        ],
      }));
      return 'OK';
    },
    [db.chapters],
  );

  const renameChapter = useCallback(
    (chapter: Chapter, rawName: string): EditResult => {
      const name = TaskNormalizer.clean(rawName);
      if (!name) return 'BLANK';
      if (
        db.chapters.some(
          (c) =>
            c.id !== chapter.id &&
            c.subjectId === chapter.subjectId &&
            c.name.toLowerCase() === name.toLowerCase(),
        )
      ) {
        return 'DUPLICATE';
      }
      setDb((prev) => ({
        ...prev,
        chapters: prev.chapters.map((c) => (c.id === chapter.id ? { ...c, name } : c)),
      }));
      return 'OK';
    },
    [db.chapters],
  );

  const deleteChapter = useCallback((chapter: Chapter) => {
    setDb((prev) => ({
      ...prev,
      chapters: prev.chapters.filter((c) => c.id !== chapter.id),
      tasks: prev.tasks.map((t) => (t.chapterId === chapter.id ? { ...t, chapterId: null } : t)),
      presetChapters: prev.presetChapters.filter((pc) => pc.chapterId !== chapter.id),
      sessionChapters: prev.sessionChapters.filter((sc) => sc.chapterId !== chapter.id),
    }));
  }, []);

  const setChapterConfidence = useCallback((chapterId: number, confidence: number | null) => {
    const val = confidence == null ? null : Math.max(1, Math.min(5, confidence));
    setDb((prev) => ({
      ...prev,
      chapters: prev.chapters.map((c) => (c.id === chapterId ? { ...c, selfConfidence: val } : c)),
    }));
  }, []);

  // ---- Tasks ----
  const addTask = useCallback(
    (
      title: string,
      subjectId: number | null,
      chapterId: number | null,
      estimatedMinutes: number | null = null,
      origin: TaskOrigin = 'MANUAL',
    ): { result: 'added' | 'already_open' | 'blank'; taskId?: number } => {
      const clean = TaskNormalizer.clean(title);
      if (!clean) return { result: 'blank' };
      const key = TaskNormalizer.dedupeKey(clean, chapterId);
      const existing = db.tasks.find((t) => t.status === 'OPEN' && t.dedupeKey === key);
      if (existing) return { result: 'already_open', taskId: existing.id };
      const newId = db.nextId;
      setDb((prev) => {
        const dup = prev.tasks.find((t) => t.status === 'OPEN' && t.dedupeKey === key);
        if (dup) return prev;
        return {
          ...prev,
          nextId: prev.nextId + 1,
          tasks: [
            ...prev.tasks,
            {
              id: prev.nextId,
              title: clean,
              subjectId,
              chapterId,
              estimatedMinutes: estimatedMinutes && estimatedMinutes > 0 ? estimatedMinutes : null,
              origin,
              status: 'OPEN',
              dedupeKey: key,
              createdAt: Date.now(),
              completedAt: null,
            },
          ],
        };
      });
      return { result: 'added', taskId: newId };
    },
    [db.nextId, db.tasks],
  );

  const markTaskDone = useCallback((taskId: number) => {
    const ts = Date.now();
    setDb((prev) => ({
      ...prev,
      tasks: prev.tasks.map((t) =>
        t.id === taskId ? { ...t, status: 'DONE', completedAt: ts } : t,
      ),
    }));
  }, []);

  const reopenTask = useCallback((taskId: number) => {
    setDb((prev) => ({
      ...prev,
      tasks: prev.tasks.map((t) =>
        t.id === taskId ? { ...t, status: 'OPEN', completedAt: null } : t,
      ),
    }));
  }, []);

  const dismissTask = useCallback((taskId: number) => {
    setDb((prev) => ({
      ...prev,
      tasks: prev.tasks.map((t) =>
        t.id === taskId ? { ...t, status: 'DISMISSED', completedAt: null } : t,
      ),
    }));
  }, []);

  const dismissSnapGptTrigger = useCallback(() => setLastSnapGptTrigger(null), []);

  const updateTask = useCallback(
    (
      taskId: number,
      patch: {
        title: string;
        subjectId: number | null;
        chapterId: number | null;
        estimatedMinutes: number | null;
      },
    ): EditResult => {
      const clean = TaskNormalizer.clean(patch.title);
      if (!clean) return 'BLANK';
      const key = TaskNormalizer.dedupeKey(clean, patch.chapterId);
      setDb((prev) => ({
        ...prev,
        tasks: prev.tasks.map((t) =>
          t.id === taskId
            ? {
                ...t,
                title: clean,
                subjectId: patch.subjectId,
                chapterId: patch.chapterId,
                estimatedMinutes:
                  patch.estimatedMinutes && patch.estimatedMinutes > 0
                    ? patch.estimatedMinutes
                    : null,
                dedupeKey: key,
              }
            : t,
        ),
      }));
      return 'OK';
    },
    [],
  );

  // ---- Sessions ----
  const startSession = useCallback(
    (draft: SessionDraft): LaunchOutcome => {
      if (db.sessions.some((s) => s.status === 'ACTIVE')) {
        return { kind: 'already_active' };
      }

      let createdSessionId = 0;
      const nowMs = Date.now();
      const resolvedPlans: SubjectPlan[] | undefined =
        draft.subjectPlans && draft.subjectPlans.length > 0
          ? draft.subjectPlans
          : draft.subjectId != null
            ? [
                {
                  subjectId: draft.subjectId,
                  subjectName: draft.subjectName,
                  chapterIds: draft.chapterIds,
                },
              ]
            : undefined;

      setDb((prev) => {
        if (prev.sessions.some((s) => s.status === 'ACTIVE')) return prev;
        let nextId = prev.nextId;
        const sessionId = nextId++;
        createdSessionId = sessionId;

        const newSession: StudySession = {
          id: sessionId,
          subjectId: draft.subjectId,
          subjectName: draft.subjectName,
          subjectPlans: resolvedPlans,
          mode: draft.mode,
          plannedDurationMs:
            draft.mode === 'TIMED'
              ? Math.max(1, draft.plannedMinutes ?? 25) * 60_000
              : null,
          startedAt: nowMs,
          endedAt: null,
          status: 'ACTIVE',
          strict: draft.strict,
          description: draft.description.trim(),
          presetId: draft.presetId,
          breakMsCompleted: 0,
          breakStartedAt: null,
          breakEndsAt: null,
          shortBreaksUsed: 0,
          longBreaksUsed: 0,
          floatingAssistant: draft.floatingAssistant,
          blockShorts: draft.blockShorts,
        };

        const tasks = [...prev.tasks];
        const subtasks = [...prev.subtasks];
        const sessionTasks = [...prev.sessionTasks];
        const singleChapter = draft.chapterIds.length === 1 ? draft.chapterIds[0] : null;
        const linkedTaskIds = new Set<number>();
        const chapterSet = new Set<number>(draft.chapterIds);
        let position = 0;

        for (const t of draft.tasks) {
          let taskId: number | null = null;
          let isNew = false;
          if (t.existingTaskId != null) {
            const existing = tasks.find((x) => x.id === t.existingTaskId);
            if (!existing || existing.status !== 'OPEN') continue;
            taskId = existing.id;
            if (existing.chapterId != null) chapterSet.add(existing.chapterId);
          } else {
            const clean = TaskNormalizer.clean(t.title);
            if (!clean) continue;
            const chId = t.chapterId ?? singleChapter;
            const key = TaskNormalizer.dedupeKey(clean, chId);
            const openDup = tasks.find((x) => x.status === 'OPEN' && x.dedupeKey === key);
            if (openDup) {
              taskId = openDup.id;
            } else {
              taskId = nextId++;
              isNew = true;
              tasks.push({
                id: taskId,
                title: clean,
                subjectId: draft.subjectId,
                chapterId: chId,
                estimatedMinutes:
                  t.estimatedMinutes && t.estimatedMinutes > 0 ? t.estimatedMinutes : null,
                origin: t.origin,
                status: 'OPEN',
                dedupeKey: key,
                createdAt: nowMs,
                completedAt: null,
              });
            }
          }

          if (taskId == null || linkedTaskIds.has(taskId)) continue;
          linkedTaskIds.add(taskId);
          sessionTasks.push({
            id: nextId++,
            sessionId,
            taskId,
            position: position++,
            completed: false,
            completedAt: null,
          });
          if (isNew) {
            t.subtasks
              .map((s) => s.trim())
              .filter(Boolean)
              .forEach((subTitle, idx) => {
                subtasks.push({
                  id: nextId++,
                  taskId: taskId!,
                  title: subTitle,
                  done: false,
                  position: idx,
                });
              });
          }
        }

        const sessionChapters = [
          ...prev.sessionChapters,
          ...Array.from(chapterSet).map((cid) => ({ sessionId, chapterId: cid })),
        ];

        return {
          ...prev,
          nextId,
          sessions: [newSession, ...prev.sessions],
          tasks,
          subtasks,
          sessionTasks,
          sessionChapters,
        };
      });

      const snapShouldTrigger =
        settings.snapGptEnabled &&
        (settings.snapGptInstalled ?? true) &&
        settings.snapGptLastSession !== createdSessionId;
      if (snapShouldTrigger) {
        setSettings((prev) => ({ ...prev, snapGptLastSession: createdSessionId }));
        setLastSnapGptTrigger({
          sessionId: createdSessionId,
          packageName: settings.snapGptPackage,
          at: nowMs,
        });
      }
      setClosedByShieldPackages([]);
      setNow(nowMs);
      return {
        kind: 'started',
        sessionId: createdSessionId,
        notes: [],
        snapGptTriggered: snapShouldTrigger,
      };
    },
    [db.sessions, settings.snapGptEnabled, settings.snapGptInstalled, settings.snapGptLastSession, settings.snapGptPackage],
  );

  const loadPreset = useCallback(
    (presetId: number): PresetDraft | null => {
      const p = db.presets.find((x) => x.id === presetId);
      if (!p) return null;
      const chapterIds = db.presetChapters
        .filter((pc) => pc.presetId === presetId)
        .map((pc) => pc.chapterId);
      const blockedApps = db.blockedApps
        .filter((b) => b.presetId === presetId)
        .map((b) => ({
          packageName: b.packageName,
          label: b.label,
          category: b.category,
          blockedDuringBreak: b.blockedDuringBreak,
        }));
      const allowedApps = db.allowedApps
        .filter((a) => a.presetId === presetId)
        .map((a) => ({ packageName: a.packageName, label: a.label }));
      const bc = db.breakConfigs.find((b) => b.presetId === presetId);
      const tasks: DraftTask[] = db.presetTasks
        .filter((pt) => pt.presetId === presetId)
        .sort((a, b) => a.position - b.position)
        .map((pt) => ({
          title: pt.title,
          estimatedMinutes: pt.estimatedMinutes,
          origin: 'MANUAL',
          chapterId: pt.chapterId,
          subtasks: pt.subtasks
            .split('\n')
            .map((s) => s.trim())
            .filter(Boolean),
        }));

      return {
        id: p.id,
        name: p.name,
        subjectId: p.subjectId,
        subjectPlans: p.subjectPlans,
        chapterIds,
        mode: p.mode,
        durationMinutes: p.durationMinutes,
        strict: p.strict,
        blockYoutubeShorts: p.blockYoutubeShorts,
        floatingAssistant: p.floatingAssistant,
        blockedApps,
        allowedApps,
        breakConfig: bc
          ? {
              enabled: bc.enabled,
              tasksForShortBreak: bc.tasksForShortBreak,
              shortBreakMinutes: bc.shortBreakMinutes,
              tasksForLongBreak: bc.tasksForLongBreak,
              longBreakMinutes: bc.longBreakMinutes,
            }
          : null,
        tasks,
      };
    },
    [db],
  );

  const runWidget = useCallback(
    (widgetId: string): LaunchOutcome => {
      const w = db.widgets.find((x) => x.id === widgetId);
      if (!w) return { kind: 'invalid', reason: 'That widget no longer exists.' };
      if (w.type === 'PROGRESS') {
        return { kind: 'progress' };
      }
      if (w.type !== 'CUSTOM' || w.action === 'START_SESSION') {
        if (db.sessions.some((s) => s.status === 'ACTIVE')) {
          return { kind: 'already_active' };
        }
      }

      const subjectName =
        w.subjectId != null
          ? db.subjects.find((s) => s.id === w.subjectId)?.name ?? null
          : null;
      const taskIds = w.taskIds
        .split(',')
        .map((s) => Number(s.trim()))
        .filter((n) => Number.isInteger(n) && n > 0);
      const tasks = taskIds
        .map((id) => db.tasks.find((t) => t.id === id))
        .filter((t): t is Task => Boolean(t));
      const preset = w.type === 'PRESET' && w.presetId != null ? loadPreset(w.presetId) : null;
      const presetSubjectName =
        preset?.subjectId != null
          ? db.subjects.find((s) => s.id === preset.subjectId)?.name ?? null
          : null;

      const plan = WidgetDraftBuilder.plan(w, {
        subjectName,
        subjects: db.subjects,
        tasks,
        preset,
        presetSubjectName,
      });

      if (plan.kind === 'navigate') {
        return { kind: 'navigate', action: plan.action };
      }
      if (plan.kind === 'progress') {
        return { kind: 'progress' };
      }
      if (plan.kind === 'invalid') {
        return { kind: 'invalid', reason: plan.reason };
      }
      const res = startSession(plan.draft);
      if (res.kind === 'started') {
        return {
          ...res,
          notes: plan.notes,
          launchMinimized: plan.launchMinimized,
        };
      }
      return res;
    },
    [db.sessions, db.subjects, db.tasks, db.widgets, loadPreset, startSession],
  );

  const setSessionTaskCompleted = useCallback(
    (sessionTaskId: number, taskId: number, completed: boolean) => {
      const ts = Date.now();
      setDb((prev) => ({
        ...prev,
        sessionTasks: prev.sessionTasks.map((st) =>
          st.id === sessionTaskId
            ? { ...st, completed, completedAt: completed ? ts : null }
            : st,
        ),
        tasks: prev.tasks.map((t) =>
          t.id === taskId
            ? { ...t, status: completed ? 'DONE' : 'OPEN', completedAt: completed ? ts : null }
            : t,
        ),
      }));
    },
    [],
  );

  const setSubtaskDone = useCallback((subtaskId: number, done: boolean) => {
    setDb((prev) => ({
      ...prev,
      subtasks: prev.subtasks.map((s) => (s.id === subtaskId ? { ...s, done } : s)),
    }));
  }, []);

  const addSessionTask = useCallback(
    (sessionId: number, title: string, estimatedMinutes: number | null = null): boolean => {
      const clean = TaskNormalizer.clean(title);
      if (!clean) return false;
      let added = false;
      setDb((prev) => {
        const s = prev.sessions.find((x) => x.id === sessionId);
        if (!s) return prev;
        const chIds = prev.sessionChapters
          .filter((sc) => sc.sessionId === sessionId)
          .map((sc) => sc.chapterId);
        const singleChapter = chIds.length === 1 ? chIds[0] : null;
        const key = TaskNormalizer.dedupeKey(clean, singleChapter);
        let nextId = prev.nextId;
        const tasks = [...prev.tasks];
        let taskId: number;
        const existing = tasks.find((t) => t.status === 'OPEN' && t.dedupeKey === key);
        if (existing) {
          taskId = existing.id;
        } else {
          taskId = nextId++;
          tasks.push({
            id: taskId,
            title: clean,
            subjectId: s.subjectId,
            chapterId: singleChapter,
            estimatedMinutes:
              estimatedMinutes && estimatedMinutes > 0 ? estimatedMinutes : null,
            origin: 'MANUAL',
            status: 'OPEN',
            dedupeKey: key,
            createdAt: Date.now(),
            completedAt: null,
          });
        }
        if (prev.sessionTasks.some((st) => st.sessionId === sessionId && st.taskId === taskId)) {
          return prev;
        }
        added = true;
        const nextPos =
          prev.sessionTasks
            .filter((st) => st.sessionId === sessionId)
            .reduce((m, st) => Math.max(m, st.position), -1) + 1;
        return {
          ...prev,
          nextId,
          tasks,
          sessionTasks: [
            ...prev.sessionTasks,
            {
              id: nextId++,
              sessionId,
              taskId,
              position: nextPos,
              completed: false,
              completedAt: null,
            },
          ],
        };
      });
      return added;
    },
    [],
  );

  const removeSessionTask = useCallback((sessionTaskId: number) => {
    setDb((prev) => ({
      ...prev,
      sessionTasks: prev.sessionTasks.filter((st) => st.id !== sessionTaskId),
    }));
  }, []);

  const startBreak = useCallback(
    (sessionId: number, kind: BreakKind): boolean => {
      const currentNow = Date.now();
      let ok = false;
      setDb((prev) => {
        const idx = prev.sessions.findIndex((s) => s.id === sessionId);
        if (idx < 0) return prev;
        const s = prev.sessions[idx];
        if (s.status !== 'ACTIVE') return prev;
        const presetCfg = s.presetId
          ? prev.breakConfigs.find((b) => b.presetId === s.presetId)
          : undefined;
        const globalCfg = prev.breakConfigs.find((b) => b.presetId === null) ?? DEFAULT_BREAK_CONFIG;
        const cfg = presetCfg ?? globalCfg;
        const completedCount = prev.sessionTasks.filter(
          (st) => st.sessionId === sessionId && st.completed,
        ).length;
        const settled = SessionClock.settleBreak(sessionToClock(s), currentNow);
        if (SessionClock.isOnBreak(settled, currentNow)) return prev;
        const credits = BreakRules.available(
          completedCount,
          s.shortBreaksUsed,
          s.longBreaksUsed,
          cfg,
        );
        const minutes =
          kind === 'SHORT'
            ? credits.shortAvailable > 0
              ? cfg.shortBreakMinutes
              : 0
            : credits.longAvailable > 0
              ? cfg.longBreakMinutes
              : 0;
        if (minutes <= 0) return prev;

        const started = SessionClock.startBreak(settled, currentNow, minutes * 60_000);
        ok = true;
        const sessions = [...prev.sessions];
        sessions[idx] = {
          ...s,
          breakMsCompleted: started.breakMsCompleted,
          breakStartedAt: started.breakStartedAt,
          breakEndsAt: started.breakEndsAt,
          shortBreaksUsed: s.shortBreaksUsed + (kind === 'SHORT' ? 1 : 0),
          longBreaksUsed: s.longBreaksUsed + (kind === 'LONG' ? 1 : 0),
        };
        return { ...prev, sessions };
      });
      setNow(currentNow);
      return ok;
    },
    [],
  );

  const endBreakEarly = useCallback((sessionId: number) => {
    const currentNow = Date.now();
    setDb((prev) => {
      const idx = prev.sessions.findIndex((s) => s.id === sessionId);
      if (idx < 0) return prev;
      const s = prev.sessions[idx];
      const nextClock = SessionClock.endBreakEarly(sessionToClock(s), currentNow);
      const sessions = [...prev.sessions];
      sessions[idx] = {
        ...s,
        breakMsCompleted: nextClock.breakMsCompleted,
        breakStartedAt: nextClock.breakStartedAt,
        breakEndsAt: nextClock.breakEndsAt,
      };
      return { ...prev, sessions };
    });
    setNow(currentNow);
  }, []);

  const endSession = useCallback((sessionId: number, status: SessionStatus) => {
    const endAt = Date.now();
    setDb((prev) => {
      const idx = prev.sessions.findIndex((s) => s.id === sessionId);
      if (idx < 0) return prev;
      const s = prev.sessions[idx];
      if (s.status !== 'ACTIVE') return prev;
      let c = SessionClock.settleBreak(sessionToClock(s), endAt);
      if (SessionClock.isOnBreak(c, endAt)) {
        c = SessionClock.endBreakEarly(c, endAt);
      }
      const sessions = [...prev.sessions];
      sessions[idx] = {
        ...s,
        breakMsCompleted: c.breakMsCompleted,
        breakStartedAt: c.breakStartedAt,
        breakEndsAt: c.breakEndsAt,
        endedAt: endAt,
        status,
      };
      return { ...prev, sessions };
    });
    setLastEndedSessionId(sessionId);
    setClosedByShieldPackages([]);
    setActiveShield(null);
    setNow(endAt);
  }, []);

  const updateActivePlannedMinutes = useCallback((sessionId: number, minutes: number) => {
    const clamped = Math.max(1, Math.min(600, Math.round(minutes)));
    setDb((prev) => ({
      ...prev,
      sessions: prev.sessions.map((s) =>
        s.id === sessionId && s.status === 'ACTIVE' && s.mode === 'TIMED'
          ? { ...s, plannedDurationMs: clamped * 60_000 }
          : s,
      ),
    }));
  }, []);

  const updateSession = useCallback(
    (
      sessionId: number,
      patch: {
        subjectName?: string;
        description?: string;
        status?: SessionStatus;
        plannedDurationMs?: number | null;
      },
    ) => {
      setDb((prev) => ({
        ...prev,
        sessions: prev.sessions.map((s) =>
          s.id === sessionId
            ? {
                ...s,
                ...(patch.subjectName !== undefined
                  ? { subjectName: TaskNormalizer.clean(patch.subjectName) || s.subjectName }
                  : {}),
                ...(patch.description !== undefined ? { description: patch.description } : {}),
                ...(patch.status !== undefined ? { status: patch.status } : {}),
                ...(patch.plannedDurationMs !== undefined
                  ? { plannedDurationMs: patch.plannedDurationMs }
                  : {}),
              }
            : s,
        ),
      }));
    },
    [],
  );

  const deleteSession = useCallback((sessionId: number) => {
    setDb((prev) => ({
      ...prev,
      sessions: prev.sessions.filter((s) => s.id !== sessionId),
      sessionChapters: prev.sessionChapters.filter((sc) => sc.sessionId !== sessionId),
      sessionTasks: prev.sessionTasks.filter((st) => st.sessionId !== sessionId),
      summaries: prev.summaries.filter((sum) => sum.sessionId !== sessionId),
    }));
  }, []);

  const saveSessionSummary = useCallback(
    (sessionId: number, text: string, source: SummarySource) => {
      setDb((prev) => {
        const existingIdx = prev.summaries.findIndex((x) => x.sessionId === sessionId);
        const summaries = [...prev.summaries];
        if (existingIdx >= 0) {
          summaries[existingIdx] = {
            ...summaries[existingIdx],
            text,
            source,
            createdAt: Date.now(),
          };
          return { ...prev, summaries };
        }
        return {
          ...prev,
          nextId: prev.nextId + 1,
          summaries: [
            ...summaries,
            { id: prev.nextId, sessionId, text, source, createdAt: Date.now() },
          ],
        };
      });
    },
    [],
  );

  // ---- Presets ----
  const savePreset = useCallback(
    (draft: PresetDraft): { result: 'saved' | 'blank' | 'duplicate'; id?: number } => {
      const name = TaskNormalizer.clean(draft.name);
      if (!name) return { result: 'blank' };
      const dup = db.presets.some(
        (p) => p.id !== draft.id && p.name.toLowerCase() === name.toLowerCase(),
      );
      if (dup) return { result: 'duplicate' };

      let savedId = draft.id;
      setDb((prev) => {
        let nextId = prev.nextId;
        const presetId = draft.id === 0 ? nextId++ : draft.id;
        savedId = presetId;

        const entity: StudyPreset = {
          id: presetId,
          name,
          subjectId: draft.subjectId,
          subjectPlans: draft.subjectPlans,
          mode: draft.mode,
          durationMinutes: draft.durationMinutes,
          strict: draft.strict,
          blockYoutubeShorts: draft.blockYoutubeShorts,
          floatingAssistant: draft.floatingAssistant,
          createdAt:
            draft.id === 0
              ? Date.now()
              : prev.presets.find((p) => p.id === presetId)?.createdAt ?? Date.now(),
        };

        const presets =
          draft.id === 0
            ? [...prev.presets, entity]
            : prev.presets.map((p) => (p.id === presetId ? entity : p));

        const presetChapters = [
          ...prev.presetChapters.filter((pc) => pc.presetId !== presetId),
          ...draft.chapterIds.map((cid) => ({ presetId, chapterId: cid })),
        ];

        const blockedApps = [
          ...prev.blockedApps.filter((b) => b.presetId !== presetId),
          ...draft.blockedApps.map((b) => ({
            id: nextId++,
            presetId,
            packageName: b.packageName,
            label: b.label,
            category: b.category,
            blockedDuringBreak: b.blockedDuringBreak,
          })),
        ];

        const allowedApps = [
          ...prev.allowedApps.filter((a) => a.presetId !== presetId),
          ...draft.allowedApps.map((a) => ({
            id: nextId++,
            presetId,
            packageName: a.packageName,
            label: a.label,
          })),
        ];

        const breakConfigs = [
          ...prev.breakConfigs.filter((b) => b.presetId !== presetId),
          ...(draft.breakConfig
            ? [{ id: nextId++, presetId, ...draft.breakConfig }]
            : []),
        ];

        const presetTasks = [
          ...prev.presetTasks.filter((pt) => pt.presetId !== presetId),
          ...draft.tasks
            .filter((t) => TaskNormalizer.clean(t.title).length > 0)
            .map((t, idx) => ({
              id: nextId++,
              presetId,
              title: TaskNormalizer.clean(t.title),
              estimatedMinutes: t.estimatedMinutes,
              chapterId: t.chapterId,
              subtasks: t.subtasks.join('\n'),
              position: idx,
            })),
        ];

        return {
          ...prev,
          nextId,
          presets,
          presetChapters,
          blockedApps,
          allowedApps,
          breakConfigs,
          presetTasks,
        };
      });

      return { result: 'saved', id: savedId };
    },
    [db.presets],
  );

  const deletePreset = useCallback((presetId: number) => {
    setDb((prev) => ({
      ...prev,
      presets: prev.presets.filter((p) => p.id !== presetId),
      presetChapters: prev.presetChapters.filter((pc) => pc.presetId !== presetId),
      presetTasks: prev.presetTasks.filter((pt) => pt.presetId !== presetId),
      blockedApps: prev.blockedApps.filter((b) => b.presetId !== presetId),
      allowedApps: prev.allowedApps.filter((a) => a.presetId !== presetId),
      breakConfigs: prev.breakConfigs.filter((b) => b.presetId !== presetId),
    }));
  }, []);

  // ---- Widgets ----
  const saveWidget = useCallback((widget: DashboardWidget): 'saved' | 'blank_title' => {
    const title = TaskNormalizer.clean(widget.title);
    if (!title) return 'blank_title';
    const clean: DashboardWidget = {
      ...widget,
      title,
      durationMinutes: Math.max(1, Math.min(600, widget.durationMinutes)),
    };
    setDb((prev) => {
      const idx = prev.widgets.findIndex((w) => w.id === clean.id);
      if (idx < 0) {
        const nextPos = prev.widgets.reduce((m, w) => Math.max(m, w.position), -1) + 1;
        return {
          ...prev,
          widgets: [...prev.widgets, { ...clean, position: nextPos }],
        };
      }
      const existing = prev.widgets[idx];
      const widgets = [...prev.widgets];
      widgets[idx] = {
        ...clean,
        position: existing.position,
        createdAt: existing.createdAt,
      };
      return { ...prev, widgets };
    });
    return 'saved';
  }, []);

  const deleteWidget = useCallback((id: string) => {
    setDb((prev) => ({
      ...prev,
      widgets: prev.widgets.filter((w) => w.id !== id),
    }));
  }, []);

  const moveWidget = useCallback((id: string, delta: number) => {
    setDb((prev) => {
      const sorted = [...prev.widgets].sort((a, b) => a.position - b.position);
      const ids = sorted.map((w) => w.id);
      const nextOrder = WidgetOrder.move(ids, id, delta);
      const posMap = new Map(nextOrder.map((wid, idx) => [wid, idx]));
      return {
        ...prev,
        widgets: prev.widgets
          .map((w) => ({ ...w, position: posMap.get(w.id) ?? w.position }))
          .sort((a, b) => a.position - b.position),
      };
    });
  }, []);

  const setWidgetVisible = useCallback((id: string, visible: boolean) => {
    setDb((prev) => ({
      ...prev,
      widgets: prev.widgets.map((w) => (w.id === id ? { ...w, visible } : w)),
    }));
  }, []);

  const updateWidgetProgressRange = useCallback((id: string, progressRange: ProgressRange) => {
    setDb((prev) => ({
      ...prev,
      widgets: prev.widgets.map((w) => (w.id === id ? { ...w, progressRange } : w)),
    }));
  }, []);

  // ---- Blocking & Breaks ----
  const setAppRule = useCallback((app: CatalogApp, choice: 'BLOCK' | 'ALLOW' | 'NONE') => {
    setDb((prev) => {
      let nextId = prev.nextId;
      let blockedApps = prev.blockedApps.filter(
        (b) => !(b.presetId === null && b.packageName === app.packageName),
      );
      let allowedApps = prev.allowedApps.filter(
        (a) => !(a.presetId === null && a.packageName === app.packageName),
      );
      if (choice === 'BLOCK') {
        blockedApps = [
          ...blockedApps,
          {
            id: nextId++,
            presetId: null,
            packageName: app.packageName,
            label: app.label,
            category: app.category,
            blockedDuringBreak: false,
          },
        ];
      } else if (choice === 'ALLOW') {
        allowedApps = [
          ...allowedApps,
          {
            id: nextId++,
            presetId: null,
            packageName: app.packageName,
            label: app.label,
          },
        ];
      }
      return { ...prev, nextId, blockedApps, allowedApps };
    });
  }, []);

  const setBlockedDuringBreak = useCallback((packageName: string, blockedDuringBreak: boolean) => {
    setDb((prev) => ({
      ...prev,
      blockedApps: prev.blockedApps.map((b) =>
        b.presetId === null && b.packageName === packageName
          ? { ...b, blockedDuringBreak }
          : b,
      ),
    }));
  }, []);

  const setYoutubeBlockedDuringBreak = useCallback((blockedDuringBreak: boolean) => {
    setDb((prev) => {
      const existing = prev.blockedApps.find(
        (b) => b.presetId === null && b.packageName === YOUTUBE_PACKAGE,
      );
      if (existing) {
        return {
          ...prev,
          blockedApps: prev.blockedApps.map((b) =>
            b.presetId === null && b.packageName === YOUTUBE_PACKAGE
              ? { ...b, blockedDuringBreak }
              : b,
          ),
        };
      }
      if (!blockedDuringBreak) return prev;
      return {
        ...prev,
        nextId: prev.nextId + 1,
        blockedApps: [
          ...prev.blockedApps,
          {
            id: prev.nextId,
            presetId: null,
            packageName: YOUTUBE_PACKAGE,
            label: 'YouTube',
            category: 'ENTERTAINMENT',
            blockedDuringBreak: true,
          },
        ],
      };
    });
  }, []);

  const blockAllInCategory = useCallback(
    (category: AppCategory) => {
      const matching = allCatalogApps.filter((a) => a.category === category);
      setDb((prev) => {
        let nextId = prev.nextId;
        const blockedSet = new Set(
          prev.blockedApps.filter((b) => b.presetId === null).map((b) => b.packageName),
        );
        const matchPkgs = new Set(matching.map((m) => m.packageName));
        const allowedApps = prev.allowedApps.filter(
          (a) => !(a.presetId === null && matchPkgs.has(a.packageName)),
        );
        const added: BlockedApplication[] = [];
        for (const app of matching) {
          if (!blockedSet.has(app.packageName)) {
            added.push({
              id: nextId++,
              presetId: null,
              packageName: app.packageName,
              label: app.label,
              category: app.category,
              blockedDuringBreak: false,
            });
          }
        }
        return {
          ...prev,
          nextId,
          allowedApps,
          blockedApps: [...prev.blockedApps, ...added],
        };
      });
    },
    [allCatalogApps],
  );

  const addCustomCatalogApp = useCallback(
    (packageName: string, label: string, category: AppCategory = 'CUSTOM') => {
      const pkg = packageName.trim();
      const lbl = TaskNormalizer.clean(label);
      if (!pkg || !lbl) return;
      setDb((prev) => {
        if (
          INSTALLED_APPS_CATALOG.some((a) => a.packageName === pkg) ||
          prev.customCatalogApps.some((a) => a.packageName === pkg)
        ) {
          return prev;
        }
        return {
          ...prev,
          customCatalogApps: [...prev.customCatalogApps, { packageName: pkg, label: lbl, category }],
        };
      });
    },
    [],
  );

  const saveGlobalBreakConfig = useCallback((cfg: BreakConfig) => {
    setDb((prev) => {
      const idx = prev.breakConfigs.findIndex((b) => b.presetId === null);
      if (idx < 0) {
        return {
          ...prev,
          nextId: prev.nextId + 1,
          breakConfigs: [...prev.breakConfigs, { id: prev.nextId, presetId: null, ...cfg }],
        };
      }
      const breakConfigs = [...prev.breakConfigs];
      breakConfigs[idx] = { ...breakConfigs[idx], ...cfg };
      return { ...prev, breakConfigs };
    });
  }, []);

  const isAppCurrentlyBlocked = useCallback(
    (
      packageName: string,
      options?: { isShorts?: boolean },
    ): { blocked: boolean; blockedDuringBreak: boolean; reason: string } => {
      const isShorts = options?.isShorts === true;
      const sessionActive = Boolean(activeSession);
      const onBreak = activeSession
        ? SessionClock.isOnBreak(sessionToClock(activeSession.session), now)
        : false;

      const blockedMap = new Map<string, BlockRule>();
      for (const b of db.blockedApps.filter((x) => x.presetId === null)) {
        blockedMap.set(b.packageName, {
          packageName: b.packageName,
          blockedDuringBreak: b.blockedDuringBreak,
        });
      }
      if (activeSession?.session.presetId != null) {
        for (const b of db.blockedApps.filter(
          (x) => x.presetId === activeSession.session.presetId,
        )) {
          blockedMap.set(b.packageName, {
            packageName: b.packageName,
            blockedDuringBreak: b.blockedDuringBreak,
          });
        }
      }
      const allowedSet = new Set(
        db.allowedApps
          .filter(
            (a) => a.presetId === null || a.presetId === activeSession?.session.presetId,
          )
          .map((a) => a.packageName),
      );

      const rule = blockedMap.get(packageName);
      const blockedDuringBreak = rule?.blockedDuringBreak === true;

      const appBlocked = BlockPolicy.shouldBlockApp(packageName, {
        sessionActive,
        onBreak,
        blocked: blockedMap,
        allowed: allowedSet,
        essentials: DEFAULT_ESSENTIAL_PACKAGES,
        blockYoutubeApp: settings.blockYoutubeApp,
        blockAllExceptAllowed: settings.blockAllExceptAllowed,
      });

      const effectiveShortsMode =
        settings.shortsMode === 'OFF' && activeSession?.session.blockShorts
          ? 'FOCUS_ONLY'
          : settings.shortsMode;
      const shortsBlocked =
        isShorts && BlockPolicy.shortsEnabled(effectiveShortsMode, sessionActive, onBreak);

      if (appBlocked) {
        const reason = blockedDuringBreak
          ? onBreak
            ? 'Blacklisted even during breaks. Unblock in Blocking rules to reopen.'
            : 'Blocked during focus and breaks until unblocked in Blocking rules.'
          : 'Blocked while focusing. Earn and start a break or stop the session to unlock.';
        return { blocked: true, blockedDuringBreak, reason };
      }

      if (shortsBlocked) {
        const reason =
          effectiveShortsMode === 'ALWAYS'
            ? 'YouTube Shorts is blocked (Always), while regular YouTube stays open.'
            : 'YouTube Shorts is blocked while focusing. Regular YouTube stays available.';
        return { blocked: true, blockedDuringBreak: false, reason };
      }

      return { blocked: false, blockedDuringBreak, reason: '' };
    },
    [
      activeSession,
      db.allowedApps,
      db.blockedApps,
      now,
      settings.blockAllExceptAllowed,
      settings.blockYoutubeApp,
      settings.shortsMode,
    ],
  );

  const openSimulatedApp = useCallback(
    (
      packageName: string,
      label: string,
      options?: { isShorts?: boolean },
    ): 'opened' | 'blocked' => {
      const isShorts = options?.isShorts === true;
      const verdict = isAppCurrentlyBlocked(packageName, { isShorts });
      if (!verdict.blocked) {
        return 'opened';
      }
      const canOpenRegularYoutube = isShorts && !isAppCurrentlyBlocked(YOUTUBE_PACKAGE).blocked;
      setActiveShield({
        packageName,
        label: isShorts ? 'YouTube Shorts' : label,
        isShorts,
        reason: verdict.reason,
        canOpenRegularYoutube,
        blockedDuringBreak: verdict.blockedDuringBreak,
      });
      return 'blocked';
    },
    [isAppCurrentlyBlocked],
  );

  const closeShieldedApp = useCallback(() => {
    setActiveShield((prev) => {
      if (!prev) return null;
      const key = prev.isShorts ? `${prev.packageName}#shorts` : prev.packageName;
      setClosedByShieldPackages((list) => (list.includes(key) ? list : [...list, key]));
      return null;
    });
  }, []);

  const dismissShield = useCallback(() => setActiveShield(null), []);

  // ---- Settings & Providers ----
  const updateSettings = useCallback((patch: Partial<AppSettings>) => {
    setSettings((prev) => ({ ...prev, ...patch }));
  }, []);

  const resetBubblePosition = useCallback(() => {
    setSettings((prev) => ({ ...prev, bubblePosition: null }));
  }, []);

  const saveProvider = useCallback((config: ProviderConfig, apiKey: string | null) => {
    setSettings((prev) => {
      const exists = prev.aiProviders.some((p) => p.id === config.id);
      const aiProviders = exists
        ? prev.aiProviders.map((p) => (p.id === config.id ? config : p))
        : [...prev.aiProviders, config];
      const aiSelectedProvider =
        prev.aiSelectedProvider ?? (config.enabled ? config.id : null);
      return { ...prev, aiProviders, aiSelectedProvider };
    });
    if (apiKey !== null && apiKey.trim().length > 0) {
      setProviderKeys((prev) => ({ ...prev, [config.id]: apiKey.trim() }));
    }
  }, []);

  const deleteProvider = useCallback((id: string) => {
    setSettings((prev) => ({
      ...prev,
      aiProviders: prev.aiProviders.filter((p) => p.id !== id),
      aiSelectedProvider: prev.aiSelectedProvider === id ? null : prev.aiSelectedProvider,
    }));
    setProviderKeys((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
  }, []);

  const selectProvider = useCallback((id: string | null) => {
    setSettings((prev) => ({ ...prev, aiSelectedProvider: id }));
  }, []);

  const getReadyProvider = useCallback((): { config: ProviderConfig; apiKey: string } | null => {
    if (!settings.aiSelectedProvider) return null;
    const cfg = settings.aiProviders.find((p) => p.id === settings.aiSelectedProvider);
    if (!cfg || !cfg.enabled) return null;
    const key = providerKeys[cfg.id];
    if (!key || !key.trim()) return null;
    return { config: cfg, apiKey: key };
  }, [providerKeys, settings.aiProviders, settings.aiSelectedProvider]);

  // ---- Backups & Resets ----
  const exportDatabaseJson = useCallback(() => {
    return JSON.stringify(
      {
        format: 'focusflow-database',
        version: 2,
        exportedAt: Date.now(),
        data: db,
      },
      null,
      2,
    );
  }, [db]);

  const importDatabaseJson = useCallback(
    (json: string): { ok: true; message: string } | { ok: false; reason: string } => {
      try {
        const parsed = JSON.parse(json) as { format?: string; version?: number; data?: FocusFlowDbState };
        if (parsed.format !== 'focusflow-database' || !parsed.data) {
          return { ok: false, reason: 'That file is not a FocusFlow database backup.' };
        }
        if ((parsed.version ?? 0) > 2 || (parsed.version ?? 0) < 1) {
          return { ok: false, reason: 'Unsupported backup version.' };
        }
        setDb({ ...createEmptyDb(), ...parsed.data });
        return { ok: true, message: 'Backup imported. All subjects, tasks, sessions, presets, and widgets restored.' };
      } catch {
        return { ok: false, reason: 'Could not read that backup file.' };
      }
    },
    [],
  );

  const exportSettingsJson = useCallback(() => {
    return SettingsBackup.toJson({
      theme: settings.themeMode,
      defaultMode: settings.defaultMode,
      defaultDurationMin: settings.defaultDurationMin,
      shortsMode: settings.shortsMode,
      homeRedirect: settings.homeRedirect,
      blockAllExceptAllowed: settings.blockAllExceptAllowed,
      floatingEnabled: settings.floatingEnabled,
      snapGptEnabled: settings.snapGptEnabled,
      snapGptPackage: settings.snapGptPackage,
      providers: settings.aiProviders,
      selectedProvider: settings.aiSelectedProvider,
    });
  }, [settings]);

  const importSettingsJson = useCallback(
    (json: string): { ok: true; message: string } | { ok: false; reason: string } => {
      const res = SettingsBackup.parse(json);
      if (!res.ok) return res;
      const snap = res.snapshot;
      setSettings((prev) => ({
        ...prev,
        themeMode:
          snap.theme === 'DARK' || snap.theme === 'LIGHT' || snap.theme === 'SYSTEM'
            ? (snap.theme as ThemeMode)
            : prev.themeMode,
        defaultMode:
          snap.defaultMode === 'TIMED' || snap.defaultMode === 'OPEN_ENDED'
            ? (snap.defaultMode as SessionMode)
            : prev.defaultMode,
        defaultDurationMin:
          typeof snap.defaultDurationMin === 'number'
            ? Math.max(1, Math.min(600, snap.defaultDurationMin))
            : prev.defaultDurationMin,
        shortsMode:
          snap.shortsMode === 'OFF' ||
          snap.shortsMode === 'FOCUS_ONLY' ||
          snap.shortsMode === 'ALWAYS'
            ? (snap.shortsMode as ShortsMode)
            : prev.shortsMode,
        homeRedirect: snap.homeRedirect ?? prev.homeRedirect,
        blockAllExceptAllowed: snap.blockAllExceptAllowed ?? prev.blockAllExceptAllowed,
        floatingEnabled: snap.floatingEnabled ?? prev.floatingEnabled,
        snapGptEnabled: snap.snapGptEnabled ?? prev.snapGptEnabled,
        snapGptPackage: snap.snapGptPackage ?? prev.snapGptPackage,
        aiProviders: SettingsBackup.mergeProviders(prev.aiProviders, snap.providers),
        aiSelectedProvider:
          snap.selectedProvider !== undefined ? snap.selectedProvider : prev.aiSelectedProvider,
      }));
      return {
        ok: true,
        message: 'Settings imported. Re-enter any API keys in Settings → AI providers.',
      };
    },
    [],
  );

  const resetHistory = useCallback(() => {
    setDb((prev) => ({
      ...prev,
      sessions: prev.sessions.filter((s) => s.status === 'ACTIVE'),
      sessionChapters: prev.sessionChapters.filter((sc) =>
        prev.sessions.some((s) => s.id === sc.sessionId && s.status === 'ACTIVE'),
      ),
      sessionTasks: prev.sessionTasks.filter((st) =>
        prev.sessions.some((s) => s.id === st.sessionId && s.status === 'ACTIVE'),
      ),
      summaries: [],
    }));
    return 'Session history deleted.';
  }, []);

  const resetEverything = useCallback(() => {
    const empty = createEmptyDb();
    const freshSettings = { ...DEFAULT_SETTINGS };
    const seeded = seedInitialData(empty, freshSettings);
    setDb(seeded.db);
    setSettings(seeded.settings);
    setProviderKeys({});
    return 'All data and settings reset to defaults.';
  }, []);

  const value: FocusFlowContextValue = {
    db,
    settings,
    providerKeys,
    activeSession,
    globalBreakConfig,
    allCatalogApps,
    now,
    lastEndedSessionId,
    clearLastEndedSession,
    lastSnapGptTrigger,
    dismissSnapGptTrigger,
    activeShield,
    openSimulatedApp,
    closeShieldedApp,
    dismissShield,
    isAppCurrentlyBlocked,
    closedByShieldPackages,
    addSubject,
    renameSubject,
    deleteSubject,
    addChapter,
    renameChapter,
    deleteChapter,
    setChapterConfidence,
    addTask,
    updateTask,
    markTaskDone,
    reopenTask,
    dismissTask,
    startSession,
    runWidget,
    setSessionTaskCompleted,
    setSubtaskDone,
    addSessionTask,
    removeSessionTask,
    updateActivePlannedMinutes,
    startBreak,
    endBreakEarly,
    endSession,
    updateSession,
    deleteSession,
    saveSessionSummary,
    loadPreset,
    savePreset,
    deletePreset,
    saveWidget,
    updateWidgetProgressRange,
    deleteWidget,
    moveWidget,
    setWidgetVisible,
    setAppRule,
    setBlockedDuringBreak,
    setYoutubeBlockedDuringBreak,
    blockAllInCategory,
    addCustomCatalogApp,
    saveGlobalBreakConfig,
    updateSettings,
    resetBubblePosition,
    saveProvider,
    deleteProvider,
    selectProvider,
    getReadyProvider,
    exportDatabaseJson,
    importDatabaseJson,
    exportSettingsJson,
    importSettingsJson,
    resetHistory,
    resetEverything,
  };

  return <FocusFlowContext.Provider value={value}>{children}</FocusFlowContext.Provider>;
};

export function useFocusFlow(): FocusFlowContextValue {
  const ctx = useContext(FocusFlowContext);
  if (!ctx) throw new Error('useFocusFlow must be used inside FocusFlowProvider');
  return ctx;
}
