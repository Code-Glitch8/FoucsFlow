import React, { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { StudySession } from '../../types/models';
import { SessionClock, sessionToClock } from '../../domain/SessionClock';
import { formatMinutes, formatWhen, statusLabel } from '../../domain/Format';

export const AppCard: React.FC<{
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
}> = ({ children, className = '', onClick }) => {
  const base =
    'rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/35 p-4 md:p-5 flex flex-col gap-3 transition-colors';
  if (onClick) {
    return (
      <div
        role="button"
        tabIndex={0}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        className={`${base} cursor-pointer hover:border-[var(--ff-primary)]/60 focus-visible:outline-2 focus-visible:outline-[var(--ff-primary)] ${className}`}
      >
        {children}
      </div>
    );
  }
  return <div className={`${base} ${className}`}>{children}</div>;
};

export const SectionLabel: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => (
  <h2
    className={`text-[11px] font-semibold tracking-wider text-[var(--ff-on-surface-variant)] uppercase ${className}`}
  >
    {children}
  </h2>
);

export const ScreenTitle: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => (
  <h1
    className={`text-2xl md:text-[28px] font-semibold tracking-tight text-[var(--ff-on-bg)] leading-tight ${className}`}
    style={{ textWrap: 'balance' }}
  >
    {children}
  </h1>
);

export const SubScreenHeader: React.FC<{
  title: string;
  onBack: () => void;
  right?: React.ReactNode;
}> = ({ title, onBack, right }) => (
  <div className="flex items-center gap-3 pb-1">
    <button
      type="button"
      onClick={onBack}
      aria-label="Back"
      className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[var(--ff-outline)]/35 bg-[var(--ff-surface)] text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] transition-colors cursor-pointer"
    >
      <ArrowLeft className="h-5 w-5" />
    </button>
    <ScreenTitle className="flex-1 min-w-0 truncate">{title}</ScreenTitle>
    {right}
  </div>
);

export const StatTile: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="flex flex-col gap-0.5 min-w-0">
    <span className="text-xl md:text-2xl font-semibold tabular-nums font-mono-tabular text-[var(--ff-on-bg)]">
      {value}
    </span>
    <span className="text-xs text-[var(--ff-on-surface-variant)]">{label}</span>
  </div>
);

export const ProgressBar: React.FC<{ progress: number; className?: string }> = ({
  progress,
  className = '',
}) => {
  const clamped = Math.max(0, Math.min(1, progress));
  return (
    <div
      className={`h-2 w-full rounded-full bg-[var(--ff-surface-variant)] overflow-hidden ${className}`}
    >
      <div
        className="h-full rounded-full bg-[var(--ff-primary)] transition-[width] duration-200"
        style={{ width: `${clamped * 100}%` }}
      />
    </div>
  );
};

export interface ChartBarItem {
  label: string;
  value: number;
  valueLabel: string;
}

export const BarChart: React.FC<{ bars: ChartBarItem[] }> = ({ bars }) => {
  const max = Math.max(1, ...bars.map((b) => b.value));
  return (
    <div className="grid gap-3 pt-2" style={{ gridTemplateColumns: `repeat(${bars.length}, minmax(0, 1fr))` }}>
      {bars.map((bar, idx) => {
        const pct = bar.value <= 0 ? 0 : Math.max(6, Math.round((bar.value / max) * 100));
        return (
          <div key={`${bar.label}-${idx}`} className="flex flex-col items-center gap-1.5 min-w-0">
            <span className="text-[11px] font-mono-tabular tabular-nums text-[var(--ff-on-surface-variant)] h-4 truncate">
              {bar.valueLabel}
            </span>
            <div className="w-full max-w-[44px] h-28 flex items-end justify-center rounded-lg bg-[var(--ff-surface-variant)]/50 p-1">
              <div
                className="w-full rounded-md bg-[var(--ff-primary)] transition-[height] duration-200"
                style={{ height: `${pct}%` }}
              />
            </div>
            <span className="text-xs text-[var(--ff-on-surface-variant)] truncate max-w-full">
              {bar.label}
            </span>
          </div>
        );
      })}
    </div>
  );
};

export const ChipButton: React.FC<{
  selected?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
  className?: string;
}> = ({ selected = false, disabled = false, onClick, children, className = '' }) => (
  <button
    type="button"
    disabled={disabled}
    onClick={onClick}
    className={`inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-medium whitespace-nowrap shrink-0 border transition-colors cursor-pointer disabled:opacity-45 disabled:cursor-not-allowed ${
      selected
        ? 'bg-[var(--ff-primary-container)] text-[var(--ff-on-primary-container)] border-[var(--ff-primary)]/50'
        : 'bg-[var(--ff-surface-variant)]/60 text-[var(--ff-on-bg)] border-[var(--ff-outline)]/35 hover:bg-[var(--ff-surface-variant)]'
    } ${className}`}
  >
    {children}
  </button>
);

export const ToggleSwitch: React.FC<{
  checked: boolean;
  onChange: (checked: boolean) => void;
  ariaLabel?: string;
}> = ({ checked, onChange, ariaLabel }) => (
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    aria-label={ariaLabel}
    onClick={() => onChange(!checked)}
    className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full border transition-colors ${
      checked
        ? 'bg-[var(--ff-primary)] border-[var(--ff-primary)]'
        : 'bg-[var(--ff-surface-variant)] border-[var(--ff-outline)]/60'
    }`}
  >
    <span
      className={`inline-block h-5 w-5 rounded-full transition-transform ${
        checked
          ? 'translate-x-6 bg-[var(--ff-on-primary)]'
          : 'translate-x-1 bg-[var(--ff-on-surface-variant)]'
      }`}
    />
  </button>
);

export const ConfirmDialog: React.FC<{
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  destructive?: boolean;
  onDismiss: () => void;
  onConfirm: () => void;
}> = ({ open, title, message, confirmLabel, destructive = false, onDismiss, onConfirm }) => {
  if (!open) return null;
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-md rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/40 p-6 flex flex-col gap-4 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">{title}</h3>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">{message}</p>
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
            onClick={onConfirm}
            className={`px-4 py-2 rounded-xl text-sm font-medium cursor-pointer ${
              destructive
                ? 'bg-[var(--ff-error)] text-white hover:opacity-90'
                : 'bg-[var(--ff-primary)] text-[var(--ff-on-primary)] hover:opacity-90'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};

export const TextInputDialog: React.FC<{
  open: boolean;
  title: string;
  label: string;
  confirmLabel?: string;
  initial?: string;
  onDismiss: () => void;
  onConfirm: (value: string) => void;
}> = ({ open, title, label, confirmLabel = 'Save', initial = '', onDismiss, onConfirm }) => {
  const [text, setText] = useState(initial);
  useEffect(() => {
    if (open) setText(initial);
  }, [open, initial]);

  if (!open) return null;
  const trimmed = text.trim();
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onDismiss}
    >
      <form
        className="w-full max-w-md rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/40 p-6 flex flex-col gap-4 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault();
          if (trimmed) onConfirm(trimmed);
        }}
      >
        <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">{title}</h3>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-[var(--ff-on-surface-variant)]">{label}</span>
          <input
            autoFocus
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            onClick={onDismiss}
            className="px-4 py-2 rounded-xl text-sm font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={!trimmed}
            className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
          >
            {confirmLabel}
          </button>
        </div>
      </form>
    </div>
  );
};

export const SessionRowCard: React.FC<{
  session: StudySession;
  chapterNames: string[];
  completedTasks: number;
  totalTasks: number;
  onClick: () => void;
}> = ({ session, chapterNames, completedTasks, totalTasks, onClick }) => {
  const focusMs = SessionClock.focusElapsedMs(
    sessionToClock(session),
    session.endedAt ?? Date.now(),
  );
  return (
    <AppCard onClick={onClick}>
      <div className="flex flex-col gap-1">
        <div className="text-base font-medium text-[var(--ff-on-bg)]">
          {session.subjectName}
          {chapterNames.length > 0 ? ` · ${chapterNames.join(', ')}` : ''}
        </div>
        <div className="text-sm text-[var(--ff-on-surface-variant)]">
          {formatWhen(session.startedAt)}
        </div>
        <div className="flex flex-wrap items-center gap-3 text-sm text-[var(--ff-on-bg)] pt-1 tabular-nums">
          <span>{formatMinutes(focusMs)} focus</span>
          <span aria-hidden="true" className="text-[var(--ff-on-surface-variant)]">
            ·
          </span>
          <span>{session.mode === 'TIMED' ? 'Timed' : 'Open-ended'}</span>
          {totalTasks > 0 && (
            <>
              <span aria-hidden="true" className="text-[var(--ff-on-surface-variant)]">
                ·
              </span>
              <span>
                {completedTasks}/{totalTasks} tasks
              </span>
            </>
          )}
        </div>
        <div className="text-xs font-medium text-[var(--ff-on-surface-variant)] pt-0.5">
          {statusLabel(session.status)}
        </div>
      </div>
    </AppCard>
  );
};
