import { ProgressRange } from '../types/models';
import { SessionFact, StudyStats, minusDays, startOfLocalDay } from './StudyStats';

export interface ProgressBucket {
  label: string;
  valueMs: number;
  valueLabel: string;
}

export interface ProgressSummary {
  range: ProgressRange;
  title: string;
  totalMs: number;
  buckets: ProgressBucket[];
}

export const ProgressStats = {
  compute(facts: SessionFact[], now: number, range: ProgressRange): ProgressSummary {
    const today = startOfLocalDay(new Date(now));

    if (range === 'DAYS') {
      const days = StudyStats.dailyFocus(facts, today, 7);
      const totalMs = days.reduce((acc, d) => acc + d.focusMs, 0);
      return {
        range,
        title: 'Past 7 days',
        totalMs,
        buckets: days.map((d) => ({
          label: d.date.toLocaleDateString(undefined, { weekday: 'short' }),
          valueMs: d.focusMs,
          valueLabel: d.focusMs > 0 ? `${Math.floor(d.focusMs / 60_000)}m` : '',
        })),
      };
    }

    if (range === 'WEEKS') {
      const buckets: ProgressBucket[] = [];
      let totalMs = 0;
      for (let w = 3; w >= 0; w--) {
        const end = minusDays(today, w * 7);
        const days = StudyStats.dailyFocus(facts, end, 7);
        const sum = days.reduce((acc, d) => acc + d.focusMs, 0);
        totalMs += sum;
        buckets.push({
          label: w === 0 ? 'This wk' : `${w}w ago`,
          valueMs: sum,
          valueLabel: sum > 0 ? `${Math.floor(sum / 60_000)}m` : '',
        });
      }
      return {
        range,
        title: 'Past 4 weeks',
        totalMs,
        buckets,
      };
    }

    // MONTH: past 30 days grouped into 6 five-day spans so the chart remains clean and scannable
    const days30 = StudyStats.dailyFocus(facts, today, 30);
    const totalMs = days30.reduce((acc, d) => acc + d.focusMs, 0);
    const buckets: ProgressBucket[] = [];
    for (let i = 0; i < days30.length; i += 5) {
      const slice = days30.slice(i, i + 5);
      const sum = slice.reduce((acc, d) => acc + d.focusMs, 0);
      const firstDate = slice[0].date;
      const label = firstDate.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
      buckets.push({
        label,
        valueMs: sum,
        valueLabel: sum > 0 ? `${Math.floor(sum / 60_000)}m` : '',
      });
    }
    return {
      range,
      title: 'Past 30 days',
      totalMs,
      buckets,
    };
  },
};
