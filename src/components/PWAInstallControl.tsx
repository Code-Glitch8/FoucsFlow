import React, { useState } from 'react';
import { Download, Smartphone, WifiOff } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { useOnlineStatus } from '../hooks/useOnlineStatus';

export const PWAInstallButton: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  if (isInstalled) {
    return null;
  }

  if (isInstallable) {
    return (
      <button
        type="button"
        onClick={install}
        className={`inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--ff-primary)] text-[var(--ff-on-primary)] font-medium hover:opacity-90 transition-opacity whitespace-nowrap shrink-0 cursor-pointer ${
          compact ? 'px-3 py-1.5 text-xs' : 'px-4 py-2 text-sm'
        }`}
      >
        <Download className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span>Install app</span>
      </button>
    );
  }

  if (isIOS) {
    return (
      <>
        <button
          type="button"
          onClick={() => setShowIOSGuide(true)}
          className={`inline-flex items-center justify-center gap-2 rounded-xl border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] font-medium hover:bg-[var(--ff-surface-variant)] transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
            compact ? 'px-3 py-1.5 text-xs' : 'px-3.5 py-2 text-sm'
          }`}
        >
          <Smartphone className="h-4 w-4 shrink-0 text-[var(--ff-primary)]" aria-hidden="true" />
          <span>Install on iOS</span>
        </button>

        {showIOSGuide && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4"
            onClick={() => setShowIOSGuide(false)}
          >
            <div
              className="w-full max-w-sm rounded-2xl border border-[var(--ff-outline)]/45 bg-[var(--ff-surface)] p-6 shadow-2xl flex flex-col gap-3"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">
                Install on iPhone / iPad
              </h3>
              <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
                1. Tap the <strong className="text-[var(--ff-on-bg)]">Share</strong> button in
                Safari&apos;s toolbar.
                <br />
                2. Scroll down and tap{' '}
                <strong className="text-[var(--ff-on-bg)]">Add to Home Screen</strong>.
              </p>
              <button
                type="button"
                onClick={() => setShowIOSGuide(false)}
                className="mt-1 w-full rounded-xl bg-[var(--ff-primary)] py-2.5 text-sm font-medium text-[var(--ff-on-primary)] cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        )}
      </>
    );
  }

  return null;
};

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();
  if (isOnline) return null;

  return (
    <div
      role="status"
      className="fixed bottom-20 md:bottom-4 left-4 z-40 flex items-center gap-2 rounded-xl border border-[var(--ff-outline)]/40 bg-[var(--ff-surface)] px-3.5 py-2 text-xs font-medium text-[var(--ff-on-bg)] shadow-lg"
    >
      <WifiOff className="h-3.5 w-3.5 text-[#D97706] shrink-0" aria-hidden="true" />
      <span>Offline mode · Cached workspace active</span>
    </div>
  );
};
