import React, { useState } from 'react';
import { ExternalLink, Lock, ShieldCheck, X, Youtube } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { YOUTUBE_PACKAGE } from '../domain/BlockPolicy';
import { AppCard, SectionLabel } from './ui/Common';

interface AppTarget {
  key: string;
  packageName: string;
  label: string;
  isShorts?: boolean;
}

export const AppGuardBar: React.FC<{ title?: string }> = ({
  title = 'APP GUARD & YOUTUBE TESTER',
}) => {
  const {
    db,
    allCatalogApps,
    closedByShieldPackages,
    isAppCurrentlyBlocked,
    openSimulatedApp,
  } = useFocusFlow();

  const [openedPreview, setOpenedPreview] = useState<{ label: string; note: string } | null>(null);

  const blockedGlobals = db.blockedApps.filter((b) => b.presetId === null);
  const defaultTargets: AppTarget[] = [
    { key: 'yt-app', packageName: YOUTUBE_PACKAGE, label: 'YouTube' },
    { key: 'yt-shorts', packageName: YOUTUBE_PACKAGE, label: 'YouTube Shorts', isShorts: true },
  ];

  // Add up to 4 additional blocked apps (or Instagram/TikTok defaults) so users can test break-blacklist rules directly
  const extraPkgs = blockedGlobals
    .map((b) => ({ packageName: b.packageName, label: b.label }))
    .filter((b) => b.packageName !== YOUTUBE_PACKAGE);

  if (extraPkgs.length === 0) {
    for (const fallbackPkg of ['com.instagram.android', 'com.zhiliaoapp.musically']) {
      const cat = allCatalogApps.find((a) => a.packageName === fallbackPkg);
      if (cat) extraPkgs.push({ packageName: cat.packageName, label: cat.label });
    }
  }

  for (const extra of extraPkgs.slice(0, 4)) {
    defaultTargets.push({
      key: extra.packageName,
      packageName: extra.packageName,
      label: extra.label,
    });
  }

  const tryOpen = (target: AppTarget) => {
    const outcome = openSimulatedApp(target.packageName, target.label, {
      isShorts: target.isShorts,
    });
    if (outcome === 'opened') {
      setOpenedPreview({
        label: target.label,
        note: target.isShorts
          ? 'YouTube Shorts is allowed under your current settings.'
          : `${target.label} is currently allowed and open.`,
      });
    } else {
      setOpenedPreview(null);
    }
  };

  return (
    <AppCard>
      <div className="flex flex-col gap-1">
        <SectionLabel>{title}</SectionLabel>
        <p className="text-xs text-[var(--ff-on-surface-variant)] leading-relaxed">
          Test opening YouTube, YouTube Shorts, or blocked apps here. Blocked items trigger the
          Focus Shield overlay until you start a break or unblock them (break-blacklisted apps
          remain locked on breaks).
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
        {defaultTargets.map((target) => {
          const verdict = isAppCurrentlyBlocked(target.packageName, {
            isShorts: target.isShorts,
          });
          const shieldKey = target.isShorts
            ? `${target.packageName}#shorts`
            : target.packageName;
          const wasClosed = closedByShieldPackages.includes(shieldKey);
          const statusText = !verdict.blocked
            ? 'Allowed now'
            : verdict.blockedDuringBreak
              ? wasClosed
                ? 'Closed · locked on breaks'
                : 'Blocked (even on breaks)'
              : target.isShorts
                ? wasClosed
                  ? 'Shorts closed'
                  : 'Shorts blocked'
                : wasClosed
                  ? 'Closed until break'
                  : 'Blocked until break';

          return (
            <button
              key={target.key}
              type="button"
              onClick={() => tryOpen(target)}
              className={`rounded-xl border p-3 text-left flex items-center justify-between gap-2.5 transition-colors cursor-pointer ${
                verdict.blocked
                  ? 'border-[var(--ff-outline)]/40 bg-[var(--ff-bg)] hover:border-[var(--ff-error)]/60'
                  : 'border-[var(--ff-primary)]/40 bg-[var(--ff-bg)] hover:border-[var(--ff-primary)]'
              }`}
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <div
                  className={`h-8 w-8 shrink-0 rounded-lg flex items-center justify-center ${
                    verdict.blocked
                      ? 'bg-[var(--ff-surface-variant)] text-[var(--ff-on-surface-variant)]'
                      : 'bg-[var(--ff-primary-container)] text-[var(--ff-on-primary-container)]'
                  }`}
                >
                  {target.packageName === YOUTUBE_PACKAGE ? (
                    <Youtube className="h-4 w-4" />
                  ) : verdict.blocked ? (
                    <Lock className="h-4 w-4" />
                  ) : (
                    <ShieldCheck className="h-4 w-4" />
                  )}
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-medium text-[var(--ff-on-bg)] truncate">
                    Open {target.label}
                  </div>
                  <div className="text-[11px] text-[var(--ff-on-surface-variant)] truncate">
                    {statusText}
                  </div>
                </div>
              </div>
              <ExternalLink className="h-4 w-4 shrink-0 text-[var(--ff-on-surface-variant)]" />
            </button>
          );
        })}
      </div>

      {openedPreview && (
        <div className="rounded-xl border border-[var(--ff-primary)]/45 bg-[var(--ff-primary-container)]/25 p-3.5 flex items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="text-sm font-semibold text-[var(--ff-on-bg)]">
              {openedPreview.label} is open
            </div>
            <div className="text-xs text-[var(--ff-on-surface-variant)]">{openedPreview.note}</div>
          </div>
          <button
            type="button"
            onClick={() => setOpenedPreview(null)}
            aria-label={`Close ${openedPreview.label} preview`}
            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl text-xs font-medium border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] shrink-0 cursor-pointer"
          >
            <X className="h-3.5 w-3.5" />
            Close
          </button>
        </div>
      )}
    </AppCard>
  );
};
