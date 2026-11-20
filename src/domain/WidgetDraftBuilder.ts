import {
  DashboardWidget,
  PresetDraft,
  SessionDraft,
  Subject,
  SubjectPlan,
  Task,
  WidgetAction,
} from '../types/models';

export interface WidgetContext {
  subjectName: string | null;
  subjects?: Subject[];
  tasks: Task[];
  preset: PresetDraft | null;
  presetSubjectName: string | null;
}

export type WidgetPlan =
  | { kind: 'start'; draft: SessionDraft; notes: string[]; launchMinimized: boolean; voiceAutoStart: boolean }
  | { kind: 'navigate'; action: WidgetAction }
  | { kind: 'progress' }
  | { kind: 'invalid'; reason: string };

export function parseWidgetIds(csv: string): number[] {
  const seen = new Set<number>();
  for (const part of csv.split(',')) {
    const n = Number(part.trim());
    if (Number.isInteger(n) && n > 0) seen.add(n);
  }
  return Array.from(seen);
}

export function joinWidgetIds(ids: number[]): string {
  return Array.from(new Set(ids)).join(',');
}

export const WidgetOrder = {
  move(ids: string[], id: string, delta: number): string[] {
    const from = ids.indexOf(id);
    if (from < 0) return ids;
    const to = Math.max(0, Math.min(ids.length - 1, from + delta));
    if (to === from) return ids;
    const next = [...ids];
    const [removed] = next.splice(from, 1);
    next.splice(to, 0, removed);
    return next;
  },
};

export const WidgetDraftBuilder = {
  GENERIC_NAME: 'Focus session',
  HOMEWORK_NAME: 'Homework',

  plan(w: DashboardWidget, ctx: WidgetContext): WidgetPlan {
    if (w.type === 'PROGRESS') {
      return { kind: 'progress' };
    }
    if (w.type === 'CUSTOM' && w.action !== 'START_SESSION') {
      return { kind: 'navigate', action: w.action };
    }
    if (w.type === 'PRESET') {
      return WidgetDraftBuilder.fromPreset(w, ctx);
    }

    const notes: string[] = [];
    const mode =
      w.type === 'OPEN_ENDED' ? 'OPEN_ENDED' : w.type === 'TIMER' ? 'TIMED' : w.mode;

    // Resolve multi-subject plans if present, otherwise fallback to single subjectId
    const subjectMap = new Map((ctx.subjects ?? []).map((s) => [s.id, s]));
    const resolvedPlans: SubjectPlan[] = [];
    if (w.subjectPlans && w.subjectPlans.length > 0) {
      for (const sp of w.subjectPlans) {
        const sub = subjectMap.get(sp.subjectId);
        if (sub) {
          resolvedPlans.push({
            subjectId: sub.id,
            subjectName: sub.name,
            chapterIds: Array.from(new Set(sp.chapterIds)),
          });
        }
      }
    }

    let subjectId: number | null = null;
    let name: string;
    let chapterIds: number[] = [];

    if (resolvedPlans.length > 0) {
      subjectId = resolvedPlans[0].subjectId;
      name = resolvedPlans.map((p) => p.subjectName).join(' + ');
      chapterIds = Array.from(new Set(resolvedPlans.flatMap((p) => p.chapterIds)));
    } else {
      name =
        ctx.subjectName ??
        (w.type === 'HOMEWORK'
          ? WidgetDraftBuilder.HOMEWORK_NAME
          : WidgetDraftBuilder.GENERIC_NAME);
      subjectId = ctx.subjectName != null ? w.subjectId : null;
      if (w.subjectId != null && ctx.subjectName == null) {
        notes.push("The widget's subject no longer exists; started without one.");
      }
      chapterIds = subjectId != null ? parseWidgetIds(w.chapterIds) : [];
      if (subjectId != null && ctx.subjectName != null) {
        resolvedPlans.push({
          subjectId,
          subjectName: ctx.subjectName,
          chapterIds,
        });
      }
    }

    const usable = ctx.tasks.filter((t) => t.status === 'OPEN');
    if (usable.length < parseWidgetIds(w.taskIds).length) {
      notes.push('Some saved tasks are finished or deleted and were skipped.');
    }

    return {
      kind: 'start',
      launchMinimized: Boolean(w.launchMinimized),
      voiceAutoStart: Boolean(w.voiceAutoStart),
      draft: {
        subjectId,
        subjectName: name,
        subjectPlans: resolvedPlans.length > 0 ? resolvedPlans : undefined,
        chapterIds,
        description: '',
        mode,
        plannedMinutes: mode === 'TIMED' ? w.durationMinutes : null,
        strict: w.strict,
        presetId: null,
        tasks: usable.map((t) => ({
          title: t.title,
          estimatedMinutes: t.estimatedMinutes,
          origin: t.origin,
          chapterId: t.chapterId,
          existingTaskId: t.id,
          subtasks: [],
        })),
        floatingAssistant: true,
        blockShorts: true,
      },
      notes,
    };
  },

  fromPreset(w: DashboardWidget, ctx: WidgetContext): WidgetPlan {
    const p = ctx.preset;
    if (!p) {
      return {
        kind: 'invalid',
        reason: `The preset for "${w.title}" no longer exists. Edit the widget and choose another.`,
      };
    }
    const subjectMap = new Map((ctx.subjects ?? []).map((s) => [s.id, s]));
    const resolvedPlans: SubjectPlan[] = [];
    if (p.subjectPlans && p.subjectPlans.length > 0) {
      for (const sp of p.subjectPlans) {
        const sub = subjectMap.get(sp.subjectId);
        if (sub) {
          resolvedPlans.push({
            subjectId: sub.id,
            subjectName: sub.name,
            chapterIds: Array.from(new Set(sp.chapterIds)),
          });
        }
      }
    }
    const name =
      resolvedPlans.length > 0
        ? resolvedPlans.map((pl) => pl.subjectName).join(' + ')
        : ctx.presetSubjectName ?? WidgetDraftBuilder.GENERIC_NAME;
    const allChapters =
      resolvedPlans.length > 0
        ? Array.from(new Set(resolvedPlans.flatMap((pl) => pl.chapterIds)))
        : p.chapterIds;

    return {
      kind: 'start',
      launchMinimized: Boolean(w.launchMinimized),
      voiceAutoStart: Boolean(w.voiceAutoStart),
      draft: {
        subjectId: resolvedPlans[0]?.subjectId ?? p.subjectId,
        subjectName: name,
        subjectPlans: resolvedPlans.length > 0 ? resolvedPlans : undefined,
        chapterIds: allChapters,
        description: '',
        mode: p.mode,
        plannedMinutes: p.mode === 'TIMED' ? p.durationMinutes : null,
        strict: p.strict,
        presetId: p.id,
        tasks: p.tasks,
        floatingAssistant: p.floatingAssistant,
        blockShorts: p.blockYoutubeShorts,
      },
      notes: [],
    };
  },
};
