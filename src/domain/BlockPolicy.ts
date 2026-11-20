import { ShortsMode } from '../types/models';

export interface BlockRule {
  packageName: string;
  blockedDuringBreak: boolean;
}

export interface PolicyInput {
  sessionActive: boolean;
  onBreak: boolean;
  blocked: Map<string, BlockRule>;
  allowed: Set<string>;
  essentials: Set<string>;
  blockYoutubeApp: boolean;
  blockAllExceptAllowed: boolean;
}

export const YOUTUBE_PACKAGE = 'com.google.android.youtube';

export const DEFAULT_ESSENTIAL_PACKAGES = new Set<string>([
  'com.focusflow.app',
  'com.android.phone',
  'com.android.dialer',
  'com.google.android.dialer',
  'com.android.settings',
  'com.android.systemui',
  'com.android.contacts',
  'com.google.android.contacts',
]);

export const BlockPolicy = {
  shouldBlockApp(packageName: string | null | undefined, input: PolicyInput): boolean {
    if (!packageName) return false;
    if (!input.sessionActive) return false;
    if (input.essentials.has(packageName)) return false;

    const rule = input.blocked.get(packageName);
    if (packageName === YOUTUBE_PACKAGE && input.blockYoutubeApp) {
      return !input.onBreak || rule?.blockedDuringBreak === true;
    }
    if (input.allowed.has(packageName)) return false;
    if (input.blockAllExceptAllowed && !input.onBreak) return true;
    if (!rule) return false;
    return !input.onBreak || rule.blockedDuringBreak;
  },

  shortsEnabled(mode: ShortsMode, sessionActive: boolean, onBreak: boolean): boolean {
    switch (mode) {
      case 'OFF':
        return false;
      case 'ALWAYS':
        return true;
      case 'FOCUS_ONLY':
        return sessionActive && !onBreak;
    }
  },

  homeRedirectEnabled(
    enabled: boolean,
    mode: ShortsMode,
    sessionActive: boolean,
    onBreak: boolean,
  ): boolean {
    return enabled && BlockPolicy.shortsEnabled(mode, sessionActive, onBreak);
  },
};
