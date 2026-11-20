import React, { useMemo, useState } from 'react';
import { SessionMode } from '../types/models';
import { useFocusFlow } from '../store/FocusFlowStore';
import { minusDays, startOfLocalDay } from '../domain/StudyStats';
import {
  AppCard,
  ChipButton,
  SectionLabel,
  SessionRowCard,
  SubScreenHeader,
} from '../components/ui/Common';

type DateRange = 'ALL' | 'WEEK' | 'MONTH';

const DATE_RANGES: Array<{ key: DateRange; label: string; days: number | null }> = [
  { key: 'ALL', label: 'All time', days: null },
  { key: 'WEEK', label: 'Last 7 days', days: 7 },
  { key: 'MONTH', label: 'Last 30 days', days: 30 },
];

export const HistoryScreen: React.FC<{
  onBack: () => void;
  onOpenSession: (sessionId: number) => void;
}> = ({ onBack, onOpenSession }) => {
  const { db, now } = useFocusFlow();
  const [range, setRange] = useState<DateRange>('ALL');
  const [mode, setMode] = useState<SessionMode | null>(null);
  const [subjectId, setSubjectId] = useState<number | null>(null);
  const [chapterId, setChapterId] = useState<number | null>(null);

  const finishedSessions = useMemo(
    () =>
      db.sessions
        .filter((s) => s.status !== 'ACTIVE')
        .sort((a, b) => b.startedAt - a.startedAt),
    [db.sessions],
  );

  const sessionChapterMap = useMemo(() => {
    const m = new Map<number, number[]>();
    for (const sc of db.sessionChapters) {
      const list = m.get(sc.sessionId) ?? [];
      list.push(sc.chapterId);
      m.set(sc.sessionId, list);
    }
    return m;
  }, [db.sessionChapters]);

  const chaptersForSelectedSubject = useMemo(
    () => (subjectId == null ? [] : db.chapters.filter((c) => c.subjectId === subjectId)),
    [db.chapters, subjectId],
  );

  const visible = useMemo(() => {
    const selectedRange = DATE_RANGES.find((r) => r.key === range);
    const cutoff =
      selectedRange?.days != null
        ? minusDays(startOfLocalDay(new Date(now)), selectedRange.days - 1).getTime()
        : null;

    return finishedSessions.filter((s) => {
      if (cutoff != null && s.startedAt < cutoff) return false;
      if (subjectId != null && s.subjectId !== subjectId) return false;
      if (chapterId != null) {
        const chIds = sessionChapterMap.get(s.id) ?? [];
        if (!chIds.includes(chapterId)) return false;
      }
      if (mode != null && s.mode !== mode) return false;
      return true;
    });
  }, [chapterId, finishedSessions, mode, now, range, sessionChapterMap, subjectId]);

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader title="Session history" onBack={onBack} />

      <AppCard>
        <SectionLabel>DATE</SectionLabel>
        <div className="flex flex-wrap gap-2">
          {DATE_RANGES.map((r) => (
            <ChipButton key={r.key} selected={range === r.key} onClick={() => setRange(r.key)}>
              {r.label}
            </ChipButton>
          ))}
        </div>

        <SectionLabel className="pt-1">TYPE</SectionLabel>
        <div className="flex flex-wrap gap-2">
          <ChipButton selected={mode === null} onClick={() => setMode(null)}>
            Any
          </ChipButton>
          <ChipButton selected={mode === 'TIMED'} onClick={() => setMode('TIMED')}>
            Timed
          </ChipButton>
          <ChipButton selected={mode === 'OPEN_ENDED'} onClick={() => setMode('OPEN_ENDED')}>
            Open-ended
          </ChipButton>
        </div>

        {db.subjects.length > 0 && (
          <>
            <SectionLabel className="pt-1">SUBJECT</SectionLabel>
            <div className="flex flex-wrap gap-2">
              <ChipButton
                selected={subjectId === null}
                onClick={() => {
                  setSubjectId(null);
                  setChapterId(null);
                }}
              >
                All
              </ChipButton>
              {db.subjects.map((s) => (
                <ChipButton
                  key={s.id}
                  selected={subjectId === s.id}
                  onClick={() => {
                    setSubjectId(s.id);
                    setChapterId(null);
                  }}
                >
                  {s.name}
                </ChipButton>
              ))}
            </div>
          </>
        )}

        {chaptersForSelectedSubject.length > 0 && (
          <>
            <SectionLabel className="pt-1">CHAPTER</SectionLabel>
            <div className="flex flex-wrap gap-2">
              <ChipButton selected={chapterId === null} onClick={() => setChapterId(null)}>
                All
              </ChipButton>
              {chaptersForSelectedSubject.map((c) => (
                <ChipButton
                  key={c.id}
                  selected={chapterId === c.id}
                  onClick={() => setChapterId(c.id)}
                >
                  {c.name}
                </ChipButton>
              ))}
            </div>
          </>
        )}
      </AppCard>

      {finishedSessions.length === 0 ? (
        <p className="text-sm text-[var(--ff-on-surface-variant)]">
          No finished sessions yet. Start one from the Focus tab.
        </p>
      ) : visible.length === 0 ? (
        <p className="text-sm text-[var(--ff-on-surface-variant)]">
          No sessions match these filters.
        </p>
      ) : (
        <>
          <div className="text-sm text-[var(--ff-on-surface-variant)]">
            {visible.length} session{visible.length === 1 ? '' : 's'}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {visible.map((s) => {
              const chIds = sessionChapterMap.get(s.id) ?? [];
              const chapterNames = chIds
                .map((id) => db.chapters.find((c) => c.id === id)?.name)
                .filter((name): name is string => Boolean(name));
              const links = db.sessionTasks.filter((st) => st.sessionId === s.id);
              const done = links.filter((l) => l.completed).length;
              return (
                <SessionRowCard
                  key={s.id}
                  session={s}
                  chapterNames={chapterNames}
                  completedTasks={done}
                  totalTasks={links.length}
                  onClick={() => onOpenSession(s.id)}
                />
              );
            })}
          </div>
        </>
      )}
    </div>
  );
};
