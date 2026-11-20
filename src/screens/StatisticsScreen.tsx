import React, { useMemo } from 'react';
import { History } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { SessionFact, StudyStats, minusDays, startOfLocalDay } from '../domain/StudyStats';
import { formatMinutes, formatWhen } from '../domain/Format';
import {
  AppCard,
  BarChart,
  ChartBarItem,
  ChipButton,
  ProgressBar,
  ScreenTitle,
  SectionLabel,
  StatTile,
} from '../components/ui/Common';

export const StatisticsScreen: React.FC<{
  onOpenHistory: () => void;
}> = ({ onOpenHistory }) => {
  const { db, now, setChapterConfidence } = useFocusFlow();

  const finishedSessions = useMemo(
    () => db.sessions.filter((s) => s.status !== 'ACTIVE'),
    [db.sessions],
  );

  const facts = useMemo<SessionFact[]>(() => {
    const chMap = new Map<number, number[]>();
    for (const sc of db.sessionChapters) {
      const list = chMap.get(sc.sessionId) ?? [];
      list.push(sc.chapterId);
      chMap.set(sc.sessionId, list);
    }
    return finishedSessions.map((s) => ({
      id: s.id,
      subjectId: s.subjectId,
      startedAt: s.startedAt,
      focusMs: SessionClock.focusElapsedMs(sessionToClock(s), s.endedAt ?? now),
      chapterIds: chMap.get(s.id) ?? [],
    }));
  }, [db.sessionChapters, finishedSessions, now]);

  const today = useMemo(() => startOfLocalDay(new Date(now)), [now]);
  const weekDays = useMemo(() => StudyStats.dailyFocus(facts, today, 7), [facts, today]);
  const weekTotal = useMemo(
    () => weekDays.reduce((acc, d) => acc + d.focusMs, 0),
    [weekDays],
  );
  const allTimeTotal = useMemo(() => facts.reduce((acc, f) => acc + f.focusMs, 0), [facts]);
  const streakDays = useMemo(() => StudyStats.streak(facts, today), [facts, today]);

  const dailyBars: ChartBarItem[] = useMemo(
    () =>
      weekDays.map((d) => ({
        label: d.date.toLocaleDateString(undefined, { weekday: 'short' }),
        value: d.focusMs,
        valueLabel: d.focusMs > 0 ? `${Math.floor(d.focusMs / 60_000)}m` : '',
      })),
    [weekDays],
  );

  const weeklyBars: ChartBarItem[] = useMemo(() => {
    const out: ChartBarItem[] = [];
    for (let w = 3; w >= 0; w--) {
      const end = minusDays(today, w * 7);
      const days = StudyStats.dailyFocus(facts, end, 7);
      const sum = days.reduce((acc, d) => acc + d.focusMs, 0);
      out.push({
        label: w === 0 ? 'This wk' : `${w}w ago`,
        value: sum,
        valueLabel: `${Math.floor(sum / 60_000)}m`,
      });
    }
    return out;
  }, [facts, today]);

  const bySubject = useMemo(() => {
    const totals = new Map<number | null, number>();
    const factById = new Map(facts.map((f) => [f.id, f]));
    for (const s of finishedSessions) {
      const f = factById.get(s.id);
      if (!f) continue;
      if (s.subjectPlans && s.subjectPlans.length > 0) {
        const shareMs = Math.round(f.focusMs / s.subjectPlans.length);
        for (const sp of s.subjectPlans) {
          totals.set(sp.subjectId, (totals.get(sp.subjectId) ?? 0) + shareMs);
        }
      } else {
        totals.set(f.subjectId, (totals.get(f.subjectId) ?? 0) + f.focusMs);
      }
    }
    return Array.from(totals.entries())
      .map(([id, ms]) => {
        const subName =
          db.subjects.find((s) => s.id === id)?.name ??
          finishedSessions.find((s) => s.subjectId === id)?.subjectName ??
          'Other';
        return { id, name: subName, ms };
      })
      .sort((a, b) => b.ms - a.ms);
  }, [db.subjects, facts, finishedSessions]);

  const chapterStatsList = useMemo(() => {
    const completed = new Map<number, number>();
    const open = new Map<number, number>();
    for (const t of db.tasks) {
      if (t.chapterId == null) continue;
      if (t.status === 'DONE') completed.set(t.chapterId, (completed.get(t.chapterId) ?? 0) + 1);
      if (t.status === 'OPEN') open.set(t.chapterId, (open.get(t.chapterId) ?? 0) + 1);
    }
    return StudyStats.chapterStats(
      db.chapters.map((c) => c.id),
      facts,
      completed,
      open,
    )
      .filter((st) => st.sessions > 0 || st.unfinishedTasks > 0 || st.completedTasks > 0)
      .sort((a, b) => b.focusMs - a.focusMs);
  }, [db.chapters, db.tasks, facts]);

  const doneCount = useMemo(() => db.tasks.filter((t) => t.status === 'DONE').length, [db.tasks]);
  const openCount = useMemo(() => db.tasks.filter((t) => t.status === 'OPEN').length, [db.tasks]);

  const maxSubjectMs = Math.max(1, ...bySubject.map((s) => s.ms));
  const maxChapterMs = Math.max(1, ...chapterStatsList.map((c) => c.focusMs));

  return (
    <div className="flex flex-col gap-5 pb-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <ScreenTitle>Statistics</ScreenTitle>
        <button
          type="button"
          onClick={onOpenHistory}
          className="inline-flex items-center gap-2 rounded-xl border border-[var(--ff-outline)]/45 bg-[var(--ff-surface)] px-4 py-2 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] transition-colors cursor-pointer"
        >
          <History className="h-4 w-4 text-[var(--ff-primary)]" />
          Open session history
        </button>
      </div>

      {finishedSessions.length === 0 ? (
        <AppCard>
          <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
            Nothing to show yet. Finish a session and your time, tasks and chapters will appear here.
          </p>
        </AppCard>
      ) : (
        <>
          <AppCard>
            <SectionLabel>OVERVIEW</SectionLabel>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-5 pt-1">
              <StatTile label="all-time focus" value={formatMinutes(allTimeTotal)} />
              <StatTile label="sessions" value={`${finishedSessions.length}`} />
              <StatTile label="streak (days)" value={`${streakDays}`} />
              <StatTile label="last 7 days" value={formatMinutes(weekTotal)} />
              <StatTile label="tasks done" value={`${doneCount}`} />
              <StatTile label="tasks open" value={`${openCount}`} />
            </div>
          </AppCard>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <AppCard>
              <SectionLabel>DAILY FOCUS, LAST 7 DAYS</SectionLabel>
              <BarChart bars={dailyBars} />
            </AppCard>
            <AppCard>
              <SectionLabel>WEEKLY FOCUS</SectionLabel>
              <BarChart bars={weeklyBars} />
            </AppCard>
          </div>

          <AppCard>
            <SectionLabel>TIME PER SUBJECT</SectionLabel>
            <div className="flex flex-col gap-3">
              {bySubject.map((item) => (
                <div key={`${item.id ?? 'none'}-${item.name}`} className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-[var(--ff-on-bg)]">{item.name}</span>
                    <span className="font-mono-tabular tabular-nums text-[var(--ff-on-surface-variant)]">
                      {formatMinutes(item.ms)}
                    </span>
                  </div>
                  <ProgressBar progress={item.ms / maxSubjectMs} />
                </div>
              ))}
            </div>
          </AppCard>

          <AppCard>
            <SectionLabel>CHAPTERS</SectionLabel>
            <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
              Time shows how long you spent, not how well you know a chapter. Rate your own
              confidence separately below each one.
            </p>
          </AppCard>

          {chapterStatsList.length === 0 ? (
            <p className="text-sm text-[var(--ff-on-surface-variant)]">
              No chapter activity yet. Pick chapters when you start a session.
            </p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {chapterStatsList.map((st) => {
                const ch = db.chapters.find((c) => c.id === st.chapterId);
                if (!ch) return null;
                const sub = db.subjects.find((s) => s.id === ch.subjectId)?.name ?? '';
                return (
                  <AppCard key={st.chapterId}>
                    <div className="flex flex-col gap-0.5">
                      <h3 className="text-base font-semibold text-[var(--ff-on-bg)]">{ch.name}</h3>
                      <span className="text-xs font-medium text-[var(--ff-on-surface-variant)]">
                        {sub}
                      </span>
                    </div>
                    <ProgressBar progress={st.focusMs / maxChapterMs} />
                    <div className="text-sm text-[var(--ff-on-bg)] tabular-nums">
                      {formatMinutes(st.focusMs)} · {st.sessions} session
                      {st.sessions === 1 ? '' : 's'}
                    </div>
                    <div className="text-sm text-[var(--ff-on-bg)] tabular-nums">
                      {st.completedTasks} done · {st.unfinishedTasks} unfinished
                    </div>
                    <div className="text-xs text-[var(--ff-on-surface-variant)]">
                      Last studied: {st.lastStudiedAt != null ? formatWhen(st.lastStudiedAt) : 'never'}
                    </div>
                    <SectionLabel className="pt-1">YOUR CONFIDENCE (OPTIONAL)</SectionLabel>
                    <div className="flex flex-wrap gap-2">
                      {[1, 2, 3, 4, 5].map((v) => (
                        <ChipButton
                          key={v}
                          selected={ch.selfConfidence === v}
                          onClick={() =>
                            setChapterConfidence(ch.id, ch.selfConfidence === v ? null : v)
                          }
                          className="px-3 py-1.5"
                        >
                          {v}
                        </ChipButton>
                      ))}
                    </div>
                  </AppCard>
                );
              })}
            </div>
          )}
        </>
      )}
    </div>
  );
};
