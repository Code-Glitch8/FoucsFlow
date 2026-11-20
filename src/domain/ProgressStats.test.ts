import test from 'node:test';
import assert from 'node:assert/strict';
import { ProgressStats } from './ProgressStats';
import { SessionFact } from './StudyStats';

const DAY_MS = 86_400_000;

test('computes DAYS, WEEKS, and MONTH totals accurately', () => {
  const now = new Date(2026, 10, 20, 12, 0, 0).getTime();
  const facts: SessionFact[] = [
    { id: 1, subjectId: 1, startedAt: now, focusMs: 25 * 60_000, chapterIds: [1] },
    { id: 2, subjectId: 1, startedAt: now - 3 * DAY_MS, focusMs: 30 * 60_000, chapterIds: [1] },
    { id: 3, subjectId: 2, startedAt: now - 10 * DAY_MS, focusMs: 45 * 60_000, chapterIds: [2] },
    { id: 4, subjectId: 2, startedAt: now - 25 * DAY_MS, focusMs: 20 * 60_000, chapterIds: [2] },
  ];

  const days = ProgressStats.compute(facts, now, 'DAYS');
  assert.equal(days.buckets.length, 7);
  assert.equal(days.totalMs, 55 * 60_000);

  const weeks = ProgressStats.compute(facts, now, 'WEEKS');
  assert.equal(weeks.buckets.length, 4);
  assert.equal(weeks.totalMs, 120 * 60_000);

  const month = ProgressStats.compute(facts, now, 'MONTH');
  assert.equal(month.buckets.length, 6);
  assert.equal(month.totalMs, 120 * 60_000);
});
