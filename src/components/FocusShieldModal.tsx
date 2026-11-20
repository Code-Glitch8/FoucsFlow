import React from 'react';
import { Flame, ShieldAlert, Youtube } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { formatClock } from '../domain/Format';

export const FocusShieldModal: React.FC<{
  onOpenRegularYoutube?: () => void;
}> = ({ onOpenRegularYoutube }) => {
  const { activeShield, activeSession, now, closeShieldedApp, dismissShield } = useFocusFlow();

  if (!activeShield) return null;

  let clockText = '';
  let onBreak = false;
  if (activeSession) {
    const clock = sessionToClock(activeSession.session);
    onBreak = SessionClock.isOnBreak(clock, now);
    const ms = onBreak
      ? SessionClock.breakRemainingMs(clock, now)
      : activeSession.session.mode === 'TIMED'
        ? (SessionClock.remainingMs(clock, now) ?? 0)
        : SessionClock.focusElapsedMs(clock, now);
    clockText = formatClock(ms);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="focus-shield-heading"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-sm p-4"
    >
      <div className="w-full max-w-md rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/45 p-6 md:p-7 flex flex-col items-center text-center gap-5 shadow-2xl">
        {/* FocusFlow App Logo */}
        <div className="flex items-center gap-2.5">
          <div className="h-12 w-12 rounded-2xl bg-[var(--ff-primary-container)] text-[var(--ff-on-primary-container)] flex items-center justify-center">
            <Flame className="h-7 w-7" />
          </div>
          <div className="text-left">
            <div className="text-base font-semibold tracking-tight text-[var(--ff-on-bg)]">
              FocusFlow
            </div>
            <div className="text-xs text-[var(--ff-on-surface-variant)]">Focus Shield</div>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="inline-flex items-center justify-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[var(--ff-error)]">
            <ShieldAlert className="h-4 w-4" />
            <span>{activeShield.label} blocked</span>
          </div>
          <h2
            id="focus-shield-heading"
            className="text-2xl font-semibold tracking-tight text-[var(--ff-on-bg)]"
            style={{ textWrap: 'balance' }}
          >
            You should be focusing right now
          </h2>
          <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
            {activeShield.reason}
          </p>
        </div>

        {activeSession && (
          <div className="w-full rounded-xl bg-[var(--ff-bg)] border border-[var(--ff-outline)]/35 px-4 py-3 flex items-center justify-between gap-3">
            <div className="min-w-0 text-left">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-[var(--ff-primary)]">
                {onBreak ? 'On break' : 'Active focus'}
              </div>
              <div className="text-sm font-medium text-[var(--ff-on-bg)] truncate">
                {activeSession.session.subjectName}
              </div>
            </div>
            <div className="text-xl font-semibold font-mono-tabular tabular-nums text-[var(--ff-on-bg)] shrink-0">
              {clockText}
            </div>
          </div>
        )}

        <div className="w-full flex flex-col gap-2.5 pt-1">
          <button
            type="button"
            onClick={closeShieldedApp}
            className="w-full py-3 px-5 rounded-xl text-sm font-semibold bg-[var(--ff-primary)] text-[var(--ff-on-primary)] hover:opacity-90 transition-opacity cursor-pointer"
          >
            Close {activeShield.label}
          </button>

          {activeShield.canOpenRegularYoutube && (
            <button
              type="button"
              onClick={() => {
                dismissShield();
                onOpenRegularYoutube?.();
              }}
              className="w-full py-2.5 px-4 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] inline-flex items-center justify-center gap-2 cursor-pointer"
            >
              <Youtube className="h-4 w-4 text-[var(--ff-primary)]" />
              Open regular YouTube instead
            </button>
          )}

          <button
            type="button"
            onClick={dismissShield}
            className="w-full py-2 rounded-xl text-xs font-medium text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-on-bg)] cursor-pointer"
          >
            Return to FocusFlow
          </button>
        </div>
      </div>
    </div>
  );
};
