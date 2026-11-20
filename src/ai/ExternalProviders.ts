import { ProviderConfig, ProviderType } from '../types/models';

export const PROVIDER_DEFAULTS: Record<
  ProviderType,
  { label: string; defaultEndpoint: string; defaultModel: string }
> = {
  OPENAI_COMPATIBLE: {
    label: 'OpenAI-compatible',
    defaultEndpoint: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o-mini',
  },
  OPENROUTER: {
    label: 'OpenRouter',
    defaultEndpoint: 'https://openrouter.ai/api/v1',
    defaultModel: 'openai/gpt-4o-mini',
  },
  GEMINI: {
    label: 'Google Gemini',
    defaultEndpoint: 'https://generativelanguage.googleapis.com/v1beta',
    defaultModel: 'gemini-2.5-flash',
  },
};

export function defaultProviderConfig(type: ProviderType): ProviderConfig {
  const d = PROVIDER_DEFAULTS[type];
  return {
    id: crypto.randomUUID(),
    type,
    name: d.label,
    endpoint: d.defaultEndpoint,
    model: d.defaultModel,
    enabled: true,
  };
}

export const ProviderJson = {
  VERSION: 1,

  toArray(list: ProviderConfig[]): Array<Record<string, unknown>> {
    return list.map((p) => ({
      id: p.id,
      type: p.type,
      name: p.name,
      endpoint: p.endpoint,
      model: p.model,
      enabled: p.enabled,
    }));
  },

  fromArray(arr: unknown): ProviderConfig[] {
    if (!Array.isArray(arr)) return [];
    const seen = new Set<string>();
    const out: ProviderConfig[] = [];
    for (const item of arr) {
      if (!item || typeof item !== 'object') continue;
      const o = item as Record<string, unknown>;
      const typeStr = String(o.type ?? '');
      if (
        typeStr !== 'OPENAI_COMPATIBLE' &&
        typeStr !== 'OPENROUTER' &&
        typeStr !== 'GEMINI'
      ) {
        continue;
      }
      const id = String(o.id ?? '').trim();
      if (!id || seen.has(id)) continue;
      seen.add(id);
      const d = PROVIDER_DEFAULTS[typeStr];
      out.push({
        id,
        type: typeStr,
        name: typeof o.name === 'string' && o.name.trim() ? o.name : d.label,
        endpoint:
          typeof o.endpoint === 'string' && o.endpoint.trim() ? o.endpoint : d.defaultEndpoint,
        model: typeof o.model === 'string' && o.model.trim() ? o.model : d.defaultModel,
        enabled: typeof o.enabled === 'boolean' ? o.enabled : true,
      });
    }
    return out;
  },
};

export interface BuiltRequest {
  url: string;
  headers: Record<string, string>;
  body: string;
}

export function buildProviderRequest(
  config: ProviderConfig,
  apiKey: string,
  system: string,
  user: string,
): BuiltRequest {
  const base = config.endpoint.trim().replace(/\/+$/, '');
  if (config.type === 'GEMINI') {
    return {
      url: `${base}/models/${config.model.trim()}:generateContent`,
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': apiKey,
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: system }] },
        contents: [{ role: 'user', parts: [{ text: user }] }],
        generationConfig: { temperature: 0.2 },
      }),
    };
  }

  return {
    url: `${base}/chat/completions`,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model: config.model.trim(),
      temperature: 0.2,
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user },
      ],
    }),
  };
}

export function parseProviderReply(type: ProviderType, body: string): string | null {
  try {
    const root = JSON.parse(body) as Record<string, unknown>;
    let text: unknown;
    if (type === 'GEMINI') {
      const candidates = root.candidates as Array<Record<string, unknown>> | undefined;
      const content = candidates?.[0]?.content as Record<string, unknown> | undefined;
      const parts = content?.parts as Array<Record<string, unknown>> | undefined;
      text = parts?.[0]?.text;
    } else {
      const choices = root.choices as Array<Record<string, unknown>> | undefined;
      const message = choices?.[0]?.message as Record<string, unknown> | undefined;
      text = message?.content;
    }
    if (typeof text !== 'string') return null;
    const trimmed = text.trim();
    return trimmed.length > 0 ? trimmed : null;
  } catch {
    return null;
  }
}

export type ExternalResult =
  | { ok: true; text: string }
  | { ok: false; message: string };

export const ExternalAiClient = {
  async complete(
    config: ProviderConfig,
    apiKey: string,
    system: string,
    user: string,
    timeoutMs = 30_000,
  ): Promise<ExternalResult> {
    if (!apiKey.trim()) {
      return { ok: false, message: 'No API key is saved for this provider.' };
    }
    const req = buildProviderRequest(config, apiKey, system, user);
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(req.url, {
        method: 'POST',
        headers: req.headers,
        body: req.body,
        signal: controller.signal,
      });
      const text = (await res.text()).slice(0, 200_000);
      if (res.ok) {
        const parsed = parseProviderReply(config.type, text);
        return parsed
          ? { ok: true, text: parsed }
          : { ok: false, message: "The provider's answer could not be understood." };
      }
      if (res.status === 401 || res.status === 403) {
        return { ok: false, message: 'The provider rejected the API key.' };
      }
      return { ok: false, message: `The provider returned HTTP ${res.status}.` };
    } catch (err) {
      const name = err instanceof Error ? err.name : 'NetworkError';
      if (name === 'AbortError') {
        return { ok: false, message: 'The request timed out.' };
      }
      return {
        ok: false,
        message: `Could not reach the provider (${name}). Browser CORS or network connectivity may block direct API calls.`,
      };
    } finally {
      window.clearTimeout(timer);
    }
  },

  async test(config: ProviderConfig, apiKey: string): Promise<ExternalResult> {
    return ExternalAiClient.complete(
      config,
      apiKey,
      'Reply with the single word OK.',
      'ping',
      15_000,
    );
  },
};
