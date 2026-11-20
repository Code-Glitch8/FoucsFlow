export type SessionMode = 'TIMED' | 'OPEN_ENDED';
export type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'ENDED_EARLY' | 'EMERGENCY_EXIT';
export type TaskStatus = 'OPEN' | 'DONE' | 'DISMISSED';
export type TaskOrigin = 'MANUAL' | 'ON_DEVICE_AI' | 'RULE_BASED' | 'EXTERNAL_AI';
export type AppCategory = 'GAME' | 'SOCIAL' | 'ENTERTAINMENT' | 'CUSTOM';
export type SummarySource = 'ON_DEVICE_AI' | 'TEMPLATE' | 'EXTERNAL_AI';
export type WidgetType =
  | 'TIMER'
  | 'OPEN_ENDED'
  | 'HOMEWORK'
  | 'SUBJECT'
  | 'PRESET'
  | 'CUSTOM'
  | 'PROGRESS';
export type WidgetSize = 'SMALL' | 'LARGE';
export type WidgetAction =
  | 'START_SESSION'
  | 'OPEN_PREPARE'
  | 'OPEN_TASKS'
  | 'OPEN_HISTORY'
  | 'OPEN_STATISTICS';
export type ProgressRange = 'DAYS' | 'WEEKS' | 'MONTH';
export type ThemeMode = 'DARK' | 'LIGHT' | 'SYSTEM';
export type ShortsMode = 'OFF' | 'FOCUS_ONLY' | 'ALWAYS';
export type ProviderType = 'OPENAI_COMPATIBLE' | 'OPENROUTER' | 'GEMINI';

export interface Subject {
  id: number;
  name: string;
  builtIn: boolean;
  createdAt: number;
}

export interface Chapter {
  id: number;
  subjectId: number;
  name: string;
  selfConfidence: number | null;
  createdAt: number;
}

export interface SubjectPlan {
  subjectId: number;
  subjectName: string;
  chapterIds: number[];
}

export interface PresetSubjectPlan {
  subjectId: number;
  chapterIds: number[];
}

export interface Task {
  id: number;
  title: string;
  subjectId: number | null;
  chapterId: number | null;
  estimatedMinutes: number | null;
  origin: TaskOrigin;
  status: TaskStatus;
  dedupeKey: string;
  createdAt: number;
  completedAt: number | null;
}

export interface Subtask {
  id: number;
  taskId: number;
  title: string;
  done: boolean;
  position: number;
}

export interface StudySession {
  id: number;
  subjectId: number | null;
  subjectName: string;
  subjectPlans?: SubjectPlan[];
  mode: SessionMode;
  plannedDurationMs: number | null;
  startedAt: number;
  endedAt: number | null;
  status: SessionStatus;
  strict: boolean;
  description: string;
  presetId: number | null;
  breakMsCompleted: number;
  breakStartedAt: number | null;
  breakEndsAt: number | null;
  shortBreaksUsed: number;
  longBreaksUsed: number;
  floatingAssistant: boolean;
  blockShorts: boolean;
}

export interface SessionChapter {
  sessionId: number;
  chapterId: number;
}

export interface SessionTask {
  id: number;
  sessionId: number;
  taskId: number;
  position: number;
  completed: boolean;
  completedAt: number | null;
}

export interface SessionTaskRow {
  link: SessionTask;
  title: string;
  estimatedMinutes: number | null;
}

export interface StudyPreset {
  id: number;
  name: string;
  subjectId: number | null;
  subjectPlans?: PresetSubjectPlan[];
  mode: SessionMode;
  durationMinutes: number;
  strict: boolean;
  blockYoutubeShorts: boolean;
  floatingAssistant: boolean;
  createdAt: number;
}

export interface PresetChapter {
  presetId: number;
  chapterId: number;
}

export interface PresetTask {
  id: number;
  presetId: number;
  title: string;
  estimatedMinutes: number | null;
  chapterId: number | null;
  subtasks: string;
  position: number;
}

export interface BlockedApplication {
  id: number;
  presetId: number | null;
  packageName: string;
  label: string;
  category: AppCategory;
  blockedDuringBreak: boolean;
}

export interface AllowedApplication {
  id: number;
  presetId: number | null;
  packageName: string;
  label: string;
}

export interface BreakConfig {
  enabled: boolean;
  tasksForShortBreak: number;
  shortBreakMinutes: number;
  tasksForLongBreak: number;
  longBreakMinutes: number;
}

export interface BreakConfigurationEntity extends BreakConfig {
  id: number;
  presetId: number | null;
}

export interface SessionSummary {
  id: number;
  sessionId: number;
  text: string;
  source: SummarySource;
  createdAt: number;
}

export interface DashboardWidget {
  id: string;
  type: WidgetType;
  title: string;
  icon: string;
  colorKey: string;
  size: WidgetSize;
  position: number;
  visible: boolean;
  mode: SessionMode;
  durationMinutes: number;
  strict: boolean;
  subjectId: number | null;
  subjectPlans?: PresetSubjectPlan[];
  chapterIds: string;
  taskIds: string;
  presetId: number | null;
  action: WidgetAction;
  progressRange?: ProgressRange;
  voiceAutoStart?: boolean;
  launchMinimized?: boolean;
  createdAt: number;
}

export interface ProviderConfig {
  id: string;
  type: ProviderType;
  name: string;
  endpoint: string;
  model: string;
  enabled: boolean;
}

export interface AppSettings {
  themeMode: ThemeMode;
  defaultMode: SessionMode;
  defaultDurationMin: number;
  shortsMode: ShortsMode;
  homeRedirect: boolean;
  blockYoutubeApp: boolean;
  blockAllExceptAllowed: boolean;
  floatingEnabled: boolean;
  voiceOfflineOnly: boolean;
  bubblePosition: { x: number; y: number } | null;
  class11SeedVersion: number;
  widgetsSeeded: boolean;
  progressWidgetSeeded?: boolean;
  snapGptEnabled: boolean;
  snapGptInstalled?: boolean;
  snapGptPackage: string;
  snapGptLastSession: number;
  aiProviders: ProviderConfig[];
  aiSelectedProvider: string | null;
  wifiSyncCode: string | null;
}

export interface DraftTask {
  title: string;
  estimatedMinutes: number | null;
  origin: TaskOrigin;
  chapterId: number | null;
  existingTaskId?: number | null;
  subtasks: string[];
}

export interface SessionDraft {
  subjectId: number | null;
  subjectName: string;
  subjectPlans?: SubjectPlan[];
  chapterIds: number[];
  description: string;
  mode: SessionMode;
  plannedMinutes: number | null;
  strict: boolean;
  presetId: number | null;
  tasks: DraftTask[];
  floatingAssistant: boolean;
  blockShorts: boolean;
}

export interface BlockedAppRule {
  packageName: string;
  label: string;
  category: AppCategory;
  blockedDuringBreak: boolean;
}

export interface AllowedAppRule {
  packageName: string;
  label: string;
}

export interface PresetDraft {
  id: number;
  name: string;
  subjectId: number | null;
  subjectPlans?: PresetSubjectPlan[];
  chapterIds: number[];
  mode: SessionMode;
  durationMinutes: number;
  strict: boolean;
  blockYoutubeShorts: boolean;
  floatingAssistant: boolean;
  blockedApps: BlockedAppRule[];
  allowedApps: AllowedAppRule[];
  breakConfig: BreakConfig | null;
  tasks: DraftTask[];
}

export interface ActiveSession {
  session: StudySession;
  tasks: SessionTaskRow[];
  chapterIds: number[];
  subtasks: Subtask[];
  breakConfig: BreakConfig;
}
