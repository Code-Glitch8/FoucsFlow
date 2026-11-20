import { ProviderConfig, SessionStatus, SummarySource, TaskOrigin } from '../types/models';
import { AiTaskParser, ExtractedTask } from './AiTaskParser';
import { ExternalAiClient } from './ExternalProviders';

export interface SummaryFacts {
  subject: string;
  chapters: string[];
  planned: string;
  status: SessionStatus;
  focusMinutes: number;
  breakMinutes: number;
  completedTasks: string[];
  unfinishedTasks: string[];
}

export interface TaskExtraction {
  tasks: ExtractedTask[];
  source: TaskOrigin;
  note: string | null;
  provider?: string;
}

export interface SummaryResult {
  text: string;
  source: SummarySource;
  note: string | null;
  provider?: string;
}

const EXTRACT_SYSTEM =
  "Turn a student's study description into a short ordered checklist. Use only the topics and work " +
  'the student mentioned. Never invent chapters, activities or achievements. Split larger activities ' +
  'into smaller concrete tasks only when implied. Each title must be an imperative phrase of at most ' +
  '12 words. minutes is an optional integer estimate; subtasks are optional. Never mark anything done. ' +
  'Reply only with JSON: {"tasks":[{"title":"...","minutes":15,"subtasks":["..."]}]}';

const SUMMARY_SYSTEM =
  'Write a brief, friendly study-session summary of 3 to 5 sentences using only the supplied facts. ' +
  'Do not invent numbers, topics or achievements, and do not claim that time spent means mastery. ' +
  'End with one practical next step.';

function isFaithfulSummary(text: string, facts: string): boolean {
  if (text.length < 20 || text.length > 1800) return false;
  const allowedNumbers = new Set(facts.match(/\d+/g) ?? []);
  const textNumbers = text.match(/\d+/g) ?? [];
  return textNumbers.every((n) => allowedNumbers.has(n));
}

export const AiService = {
  async extractTasks(
    readyProvider: { config: ProviderConfig; apiKey: string } | null,
    description: string,
    subject: string | null,
    chapters: string[],
  ): Promise<TaskExtraction> {
    if (!readyProvider) {
      return {
        tasks: [],
        source: 'MANUAL',
        note: 'Add and select an API provider in Settings → AI providers. You can still add tasks manually.',
      };
    }

    const lines: string[] = [];
    if (subject && subject.trim()) lines.push(`Subject: ${subject.trim()}`);
    if (chapters.length > 0) lines.push(`Chapters: ${chapters.join(', ')}`);
    lines.push(`Student's plan:\n${description.trim()}`);
    const prompt = lines.join('\n');

    const res = await ExternalAiClient.complete(
      readyProvider.config,
      readyProvider.apiKey,
      EXTRACT_SYSTEM,
      prompt,
    );
    if (!res.ok) {
      return {
        tasks: [],
        source: 'MANUAL',
        note: `API request failed: ${res.message} Check your connection and API settings.`,
        provider: readyProvider.config.name,
      };
    }

    const parsed = AiTaskParser.parse(res.text);
    if (!parsed) {
      return {
        tasks: [],
        source: 'MANUAL',
        note: `${readyProvider.config.name} returned an unreadable response. Try again or add tasks manually.`,
        provider: readyProvider.config.name,
      };
    }

    const contextWords = [...(subject ? [subject] : []), ...chapters];
    const { kept, dropped } = AiTaskParser.keepGrounded(parsed, description, contextWords);
    if (kept.length === 0) {
      return {
        tasks: [],
        source: 'MANUAL',
        note: 'The API returned no tasks that matched your plan. Add tasks manually or try rephrasing it.',
        provider: readyProvider.config.name,
      };
    }

    const note =
      dropped > 0
        ? `${dropped} suggestion(s) were removed because they did not match your plan.`
        : null;
    return {
      tasks: kept,
      source: 'EXTERNAL_AI',
      note,
      provider: readyProvider.config.name,
    };
  },

  async summarize(
    readyProvider: { config: ProviderConfig; apiKey: string } | null,
    facts: SummaryFacts,
  ): Promise<SummaryResult> {
    if (!readyProvider) {
      return {
        text: '',
        source: 'EXTERNAL_AI',
        note: 'Add and select an API provider to generate a session summary.',
      };
    }

    const factText = [
      `Subject: ${facts.subject}`,
      `Chapters: ${facts.chapters.length > 0 ? facts.chapters.join(', ') : 'None selected'}`,
      `Plan: ${facts.planned.trim() ? facts.planned.trim() : 'No plan text supplied'}`,
      `Session status: ${facts.status}`,
      `Focused minutes: ${facts.focusMinutes}`,
      `Break minutes: ${facts.breakMinutes}`,
      `Completed tasks: ${facts.completedTasks.length > 0 ? facts.completedTasks.join('; ') : 'None'}`,
      `Unfinished tasks: ${facts.unfinishedTasks.length > 0 ? facts.unfinishedTasks.join('; ') : 'None'}`,
    ].join('\n');

    const res = await ExternalAiClient.complete(
      readyProvider.config,
      readyProvider.apiKey,
      SUMMARY_SYSTEM,
      factText,
    );
    if (!res.ok) {
      return {
        text: '',
        source: 'EXTERNAL_AI',
        note: `API summary request failed: ${res.message} Check your connection and API settings.`,
        provider: readyProvider.config.name,
      };
    }

    const clean = res.text.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
    if (isFaithfulSummary(clean, factText)) {
      return {
        text: clean,
        source: 'EXTERNAL_AI',
        note: null,
        provider: readyProvider.config.name,
      };
    }

    return {
      text: '',
      source: 'EXTERNAL_AI',
      note: 'The API response could not be matched to the session facts. Regenerate it or check another provider.',
      provider: readyProvider.config.name,
    };
  },
};
