export interface SessionFact {
  id: number;
  subjectId: number | null;
  startedAt: number;
  focusMs: number;
  chapterIds: number[];
}

export interface DayTotal {
  dayKey: string; // YYYY-MM-DD in local time
  date: Date;
  focusMs: number;
}

export interface ChapterStat {
  chapterId: number;
  focusMs: number;
  sessions: number;
  completedTasks: number;
  unfinishedTasks: number;
  lastStudiedAt: number | null;
}

const MIN_STREAK_FOCUS_MS = 60_000;

export function toLocalDayKey(msOrDate: number | Date): string {
  const d = typeof msOrDate === 'number' ? new Date(msOrDate) : msOrDate;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0);
}

export function minusDays(d: Date, days: number): Date {
  const copy = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  copy.setDate(copy.getDate() - days);
  return copy;
}

export const StudyStats = {
  dayOf(ms: number): string {
    return toLocalDayKey(ms);
  },

  focusOnDay(sessions: SessionFact[], day: Date): number {
    const key = toLocalDayKey(day);
    return sessions
      .filter((s) => toLocalDayKey(s.startedAt) === key)
      .reduce((acc, s) => acc + s.focusMs, 0);
  },

  dailyFocus(sessions: SessionFact[], today: Date, days: number): DayTotal[] {
    const byDay = new Map<string, number>();
    for (const s of sessions) {
      const k = toLocalDayKey(s.startedAt);
      byDay.set(k, (byDay.get(k) ?? 0) + s.focusMs);
    }
    const out: DayTotal[] = [];
    for (let back = days - 1; back >= 0; back--) {
      const d = minusDays(today, back);
      const k = toLocalDayKey(d);
      out.push({ dayKey: k, date: d, focusMs: byDay.get(k) ?? 0 });
    }
    return out;
  },

  streak(sessions: SessionFact[], today: Date): number {
    const studied = new Set<string>(
      sessions.filter((s) => s.focusMs >= MIN_STREAK_FOCUS_MS).map((s) => toLocalDayKey(s.startedAt)),
    );
    const todayKey = toLocalDayKey(today);
    let cursor = studied.has(todayKey) ? today : minusDays(today, 1);
    let count = 0;
    while (studied.has(toLocalDayKey(cursor))) {
      count++;
      cursor = minusDays(cursor, 1);
    }
    return count;
  },

  chapterStats(
    chapterIds: number[],
    sessions: SessionFact[],
    completedByChapter: Map<number, number>,
    openByChapter: Map<number, number>,
  ): ChapterStat[] {
    return chapterIds.map((id) => {
      const touching = sessions.filter((s) => s.chapterIds.includes(id));
      let focusMs = 0;
      let lastStudiedAt: number | null = null;
      for (const s of touching) {
        focusMs += Math.floor(s.focusMs / Math.max(1, s.chapterIds.length));
        if (lastStudiedAt == null || s.startedAt > lastStudiedAt) {
          lastStudiedAt = s.startedAt;
        }
      }
      return {
        chapterId: id,
        focusMs,
        sessions: touching.length,
        completedTasks: completedByChapter.get(id) ?? 0,
        unfinishedTasks: openByChapter.get(id) ?? 0,
        lastStudiedAt,
      };
    });
  },
};
