import React from 'react';
import {
  AlarmClock,
  BookOpen,
  Bookmark,
  Brain,
  Calculator,
  Code2,
  FlaskConical,
  Globe,
  GraduationCap,
  Microscope,
  Pencil,
  Play,
  Star,
  Timer,
  Trophy,
  Zap,
} from 'lucide-react';
import { DashboardWidget, WidgetAction, WidgetType } from '../../types/models';

export const WIDGET_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  timer: Timer,
  play: Play,
  book: BookOpen,
  calculate: Calculator,
  science: FlaskConical,
  biotech: Microscope,
  bolt: Zap,
  edit: Pencil,
  star: Star,
  school: GraduationCap,
  alarm: AlarmClock,
  bookmark: Bookmark,
  psychology: Brain,
  code: Code2,
  language: Globe,
  trophy: Trophy,
};

export const WIDGET_COLORS: Record<string, string> = {
  indigo: '#5C6BC0',
  teal: '#26A69A',
  amber: '#FFB300',
  rose: '#EC407A',
  green: '#66BB6A',
  slate: '#78909C',
};

export const WidgetStyle = {
  icon(key: string): React.ComponentType<{ className?: string }> {
    return WIDGET_ICONS[key] ?? Timer;
  },

  color(key: string): string {
    return WIDGET_COLORS[key] ?? WIDGET_COLORS.indigo;
  },

  typeLabel(t: WidgetType): string {
    switch (t) {
      case 'TIMER':
        return 'Timer';
      case 'OPEN_ENDED':
        return 'Open-ended';
      case 'HOMEWORK':
        return 'Homework';
      case 'SUBJECT':
        return 'Subject';
      case 'PRESET':
        return 'Saved setup';
      case 'CUSTOM':
        return 'Custom';
      case 'PROGRESS':
        return 'Progress';
    }
  },

  actionLabel(a: WidgetAction): string {
    switch (a) {
      case 'START_SESSION':
        return 'Start a session';
      case 'OPEN_PREPARE':
        return 'Open session setup';
      case 'OPEN_TASKS':
        return 'Open tasks';
      case 'OPEN_HISTORY':
        return 'Open history';
      case 'OPEN_STATISTICS':
        return 'Open statistics';
    }
  },

  subtitle(
    w: DashboardWidget,
    subjectName?: string | null,
    presetName?: string | null,
    allSubjectNames?: string[],
  ): string {
    const resolvedSubjects =
      allSubjectNames && allSubjectNames.length > 0
        ? allSubjectNames.join(' + ')
        : subjectName;
    const modeFlags = [
      w.voiceAutoStart ? 'Voice start' : null,
      w.launchMinimized ? 'Circle only' : null,
    ].filter(Boolean);

    switch (w.type) {
      case 'PROGRESS': {
        const rangeText =
          w.progressRange === 'MONTH'
            ? 'Past month'
            : w.progressRange === 'WEEKS'
              ? 'Past weeks'
              : 'Past days';
        return `${rangeText} · Editable chart`;
      }
      case 'TIMER':
        return [`${w.durationMinutes} min`, ...modeFlags].join(' · ');
      case 'OPEN_ENDED':
        return ['Open-ended', ...modeFlags].join(' · ');
      case 'HOMEWORK':
      case 'SUBJECT':
        return [
          resolvedSubjects,
          w.mode === 'TIMED' ? `${w.durationMinutes} min` : 'Open-ended',
          ...modeFlags,
        ]
          .filter(Boolean)
          .join(' · ');
      case 'PRESET':
        return [presetName ?? 'Preset missing', ...modeFlags].filter(Boolean).join(' · ');
      case 'CUSTOM':
        if (w.action === 'START_SESSION') {
          return [
            resolvedSubjects,
            w.mode === 'TIMED' ? `${w.durationMinutes} min` : 'Open-ended',
            ...modeFlags,
          ]
            .filter(Boolean)
            .join(' · ');
        }
        return WidgetStyle.actionLabel(w.action);
    }
  },

  rows(widgets: DashboardWidget[]): DashboardWidget[][] {
    const out: DashboardWidget[][] = [];
    let pending: DashboardWidget | null = null;
    for (const w of widgets) {
      if (w.size === 'LARGE') {
        if (pending) {
          out.push([pending]);
          pending = null;
        }
        out.push([w]);
      } else if (!pending) {
        pending = w;
      } else {
        out.push([pending, w]);
        pending = null;
      }
    }
    if (pending) out.push([pending]);
    return out;
  },
};

export const WidgetTile: React.FC<{
  widget: DashboardWidget;
  subtitle: string;
  onClick: () => void;
  dimmed?: boolean;
}> = ({ widget, subtitle, onClick, dimmed = false }) => {
  const IconComp = WidgetStyle.icon(widget.icon);
  const accent = WidgetStyle.color(widget.colorKey);
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full text-left rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/35 p-4 flex items-center gap-3.5 hover:border-[var(--ff-primary)]/60 transition-all cursor-pointer ${
        dimmed ? 'opacity-65' : ''
      }`}
    >
      <div
        className="h-11 w-11 shrink-0 rounded-xl flex items-center justify-center"
        style={{ backgroundColor: `${accent}24`, color: accent }}
      >
        <IconComp className="h-5 w-5" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-base font-medium text-[var(--ff-on-bg)] truncate">{widget.title}</div>
        <div className="text-xs text-[var(--ff-on-surface-variant)] truncate">{subtitle}</div>
      </div>
    </button>
  );
};
