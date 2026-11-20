import { ProviderConfig } from '../types/models';
import { ProviderJson } from '../ai/ExternalProviders';

export interface SettingsSnapshot {
  theme?: string;
  defaultMode?: string;
  defaultDurationMin?: number;
  shortsMode?: string;
  homeRedirect?: boolean;
  blockAllExceptAllowed?: boolean;
  floatingEnabled?: boolean;
  snapGptEnabled?: boolean;
  snapGptPackage?: string;
  providers: ProviderConfig[];
  selectedProvider?: string | null;
}

export type SettingsParseResult =
  | { ok: true; snapshot: SettingsSnapshot }
  | { ok: false; reason: string };

export const SettingsBackup = {
  FORMAT: 'focusflow-settings',
  VERSION: 1,

  toJson(s: SettingsSnapshot): string {
    const out: Record<string, unknown> = {
      format: SettingsBackup.FORMAT,
      version: SettingsBackup.VERSION,
    };
    if (s.theme !== undefined) out.theme = s.theme;
    if (s.defaultMode !== undefined) out.defaultMode = s.defaultMode;
    if (s.defaultDurationMin !== undefined) out.defaultDurationMin = s.defaultDurationMin;
    if (s.shortsMode !== undefined) out.shortsMode = s.shortsMode;
    if (s.homeRedirect !== undefined) out.homeRedirect = s.homeRedirect;
    if (s.blockAllExceptAllowed !== undefined) out.blockAllExceptAllowed = s.blockAllExceptAllowed;
    if (s.floatingEnabled !== undefined) out.floatingEnabled = s.floatingEnabled;
    if (s.snapGptEnabled !== undefined) out.snapGptEnabled = s.snapGptEnabled;
    if (s.snapGptPackage !== undefined) out.snapGptPackage = s.snapGptPackage;
    out.providers = ProviderJson.toArray(s.providers);
    if (s.selectedProvider !== undefined && s.selectedProvider !== null) {
      out.selectedProvider = s.selectedProvider;
    }
    return JSON.stringify(out, null, 2);
  },

  parse(json: string): SettingsParseResult {
    let o: Record<string, unknown>;
    try {
      const parsed = JSON.parse(json);
      if (!parsed || typeof parsed !== 'object') {
        return { ok: false, reason: 'That is not a FocusFlow settings file.' };
      }
      o = parsed as Record<string, unknown>;
    } catch {
      return { ok: false, reason: 'That is not a FocusFlow settings file.' };
    }

    if (o.format !== SettingsBackup.FORMAT) {
      return { ok: false, reason: 'That is not a FocusFlow settings file.' };
    }
    const v = typeof o.version === 'number' ? o.version : -1;
    if (v < 1) return { ok: false, reason: 'Unreadable settings file version.' };
    if (v > SettingsBackup.VERSION) {
      return { ok: false, reason: 'This settings file comes from a newer version of FocusFlow.' };
    }

    return {
      ok: true,
      snapshot: {
        theme: typeof o.theme === 'string' ? o.theme : undefined,
        defaultMode: typeof o.defaultMode === 'string' ? o.defaultMode : undefined,
        defaultDurationMin:
          typeof o.defaultDurationMin === 'number' ? o.defaultDurationMin : undefined,
        shortsMode: typeof o.shortsMode === 'string' ? o.shortsMode : undefined,
        homeRedirect: typeof o.homeRedirect === 'boolean' ? o.homeRedirect : undefined,
        blockAllExceptAllowed:
          typeof o.blockAllExceptAllowed === 'boolean' ? o.blockAllExceptAllowed : undefined,
        floatingEnabled: typeof o.floatingEnabled === 'boolean' ? o.floatingEnabled : undefined,
        snapGptEnabled: typeof o.snapGptEnabled === 'boolean' ? o.snapGptEnabled : undefined,
        snapGptPackage: typeof o.snapGptPackage === 'string' ? o.snapGptPackage : undefined,
        providers: ProviderJson.fromArray(o.providers),
        selectedProvider: typeof o.selectedProvider === 'string' ? o.selectedProvider : null,
      },
    };
  },

  mergeProviders(existing: ProviderConfig[], imported: ProviderConfig[]): ProviderConfig[] {
    const byId = new Map<string, ProviderConfig>();
    for (const p of existing) byId.set(p.id, p);
    for (const p of imported) byId.set(p.id, p);
    return Array.from(byId.values());
  },
};
