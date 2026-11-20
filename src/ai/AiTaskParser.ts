export interface ExtractedTask {
  title: string;
  minutes: number | null;
  subtasks: string[];
}

const THINK_BLOCK = /<think>[\s\S]*?<\/think>/gi;
const WORD_REGEX = /[a-z0-9]+/g;

const GENERIC_STUDY_WORDS = new Set([
  'answers', 'answer', 'questions', 'question', 'theory', 'practice', 'notes', 'note', 'mistakes', 'mistake',
  'errors', 'error', 'incorrect', 'wrong', 'problems', 'problem', 'exercises', 'exercise', 'summary',
  'revision', 'formulas', 'formula', 'concepts', 'concept', 'test', 'quiz', 'flashcards', 'mock', 'paper',
  'papers', 'homework', 'chapter', 'topic', 'topics', 'examples', 'example', 'solutions', 'solution',
  'weak', 'areas', 'key', 'points', 'definitions', 'past', 'previous', 'year', 'review', 'revise',
]);

const STOP_WORDS = new Set([
  'the', 'and', 'for', 'with', 'that', 'this', 'from', 'into', 'your', 'about', 'all', 'any', 'some',
  'my', 'our', 'its', 'them', 'then', 'than', 'are', 'was', 'were', 'got', 'get', 'have', 'has', 'had',
  'you', 'his', 'her', 'their', 'out', 'off', 'over', 'one', 'two',
]);

const ACTION_VERBS = new Set([
  'revise', 'solve', 'review', 'read', 'practice', 'practise', 'learn', 'watch', 'write', 'complete', 'do',
  'finish', 'memorize', 'memorise', 'attempt', 'summarize', 'summarise', 'prepare', 'study', 'redo',
  'analyse', 'analyze', 'make', 'take', 'test', 'check', 'correct', 'fix', 'create', 'draw', 'derive',
  'prove', 'understand', 'recap', 'rewrite', 'highlight', 'work', 'try', 'list', 'note', 'go', 'cover',
]);

function stem(w: string): string {
  if (w.length > 5 && w.endsWith('ing')) return w.slice(0, -3);
  if (w.length > 4 && w.endsWith('es')) return w.slice(0, -2);
  if (w.length > 4 && w.endsWith('ed')) return w.slice(0, -2);
  if (w.length > 3 && w.endsWith('s')) return w.slice(0, -1);
  return w;
}

const STEMMED_GENERIC = new Set(Array.from(GENERIC_STUDY_WORDS, stem));
const STEMMED_ACTION_VERBS = new Set(Array.from(ACTION_VERBS, stem));

function tokens(text: string): string[] {
  const matches = text.toLowerCase().match(WORD_REGEX) ?? [];
  return matches
    .filter((w) => w.length >= 3 && !STOP_WORDS.has(w))
    .map(stem);
}

function cleanTask(title: string, minutes: number | null, subtasks: string[]): ExtractedTask | null {
  const t = title
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[.,;]+$/, '')
    .slice(0, 120);
  if (t.length < 2) return null;
  const capitalized = t.charAt(0).toUpperCase() + t.slice(1);
  const validMinutes =
    minutes != null && Number.isFinite(minutes) && minutes >= 1 && minutes <= 240
      ? Math.round(minutes)
      : null;
  const cleanSubs = subtasks
    .map((s) => s.trim().slice(0, 100))
    .filter((s) => s.length >= 2)
    .slice(0, 6);
  return {
    title: capitalized,
    minutes: validMinutes,
    subtasks: cleanSubs,
  };
}

export const AiTaskParser = {
  parse(raw: string, maxTasks = 12): ExtractedTask[] | null {
    const text = raw.replace(THINK_BLOCK, '').trim();
    let arr: unknown[] | null = null;
    try {
      const objStart = text.indexOf('{');
      const arrStart = text.indexOf('[');
      if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
        const parsed = JSON.parse(text.slice(objStart, text.lastIndexOf('}') + 1)) as Record<string, unknown>;
        if (Array.isArray(parsed.tasks)) arr = parsed.tasks;
      } else if (arrStart >= 0) {
        const parsed = JSON.parse(text.slice(arrStart, text.lastIndexOf(']') + 1));
        if (Array.isArray(parsed)) arr = parsed;
      }
    } catch {
      arr = null;
    }
    if (!arr) return null;

    const out = new Map<string, ExtractedTask>();
    for (const item of arr) {
      let task: ExtractedTask | null = null;
      if (typeof item === 'string') {
        task = cleanTask(item, null, []);
      } else if (item && typeof item === 'object') {
        const o = item as Record<string, unknown>;
        const title = typeof o.title === 'string' ? o.title : '';
        const minsRaw = o.minutes;
        const mins =
          typeof minsRaw === 'number'
            ? minsRaw
            : typeof minsRaw === 'string' && /^\d+$/.test(minsRaw.trim())
              ? Number(minsRaw.trim())
              : null;
        const subs = Array.isArray(o.subtasks)
          ? o.subtasks.filter((s): s is string => typeof s === 'string')
          : [];
        task = cleanTask(title, mins, subs);
      }
      if (!task) continue;
      const key = task.title.toLowerCase();
      if (!out.has(key)) out.set(key, task);
      if (out.size >= maxTasks) break;
    }
    return Array.from(out.values());
  },

  keepGrounded(
    tasks: ExtractedTask[],
    description: string,
    contextWords: string[],
  ): { kept: ExtractedTask[]; dropped: number } {
    const source = new Set<string>([
      ...tokens(`${description} ${contextWords.join(' ')}`),
      ...STEMMED_GENERIC,
    ]);
    const kept = tasks.filter((task) => {
      const titleTokens = tokens(task.title);
      const content = titleTokens.filter((t) => !STEMMED_ACTION_VERBS.has(t));
      const toCheck = content.length > 0 ? content : titleTokens;
      return toCheck.length === 0 || toCheck.some((t) => source.has(t));
    });
    return { kept, dropped: tasks.length - kept.length };
  },
};
