import { Chapter, DraftTask, Subject } from '../types/models';
import { TaskNormalizer } from './TaskNormalizer';

export interface DetectedSubjectPlan {
  subjectId: number;
  subjectName: string;
  chapterIds: number[];
  estimatedMinutes: number;
  suggestedTasks: DraftTask[];
}

export interface PlanAnalysisResult {
  detectedSubjects: DetectedSubjectPlan[];
  estimatedMinutes: number;
  suggestedTasks: DraftTask[];
  reason: string;
}

const SUBJECT_ALIASES: Record<string, string[]> = {
  mathematics: ['math', 'maths', 'mathematics', 'calculus', 'algebra', 'trigonometry', 'geometry', 'coordinate'],
  physics: ['physics', 'phys', 'mechanics', 'kinematics', 'optics', 'electrostatics', 'gravitation'],
  chemistry: ['chemistry', 'chem', 'organic', 'inorganic', 'physical chemistry', 'stoichiometry', 'mole'],
  biology: ['biology', 'bio', 'botany', 'zoology', 'anatomy', 'cell', 'photosynthesis'],
  'computer science': ['cs', 'computer science', 'computer', 'python', 'coding', 'programming', 'algorithms'],
  english: ['english', 'grammar', 'essay', 'literature', 'writing', 'reading comprehension'],
  history: ['history', 'historical', 'modern history', 'ancient history'],
};

const STOP_WORDS = new Set([
  'a', 'an', 'the', 'and', 'or', 'of', 'in', 'on', 'for', 'to', 'with', 'by', 'from',
  'is', 'are', 'i', 'im', "i'm", 'doing', 'this', 'that', 'these', 'those', 'my', 'some',
  'study', 'studying', 'work', 'working', 'complete', 'finish', 'chapter', 'chapters',
  'unit', 'topic', 'topics', 'subject', 'minutes', 'minute', 'min', 'mins', 'hour', 'hours', 'hr', 'hrs',
]);

function normalizeTokens(text: string): string[] {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 0 && !STOP_WORDS.has(w));
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function containsWholePhrase(haystack: string, phrase: string): boolean {
  const clean = phrase.trim().toLowerCase();
  if (!clean) return false;
  const re = new RegExp(`\\b${escapeRegex(clean)}\\b`, 'i');
  return re.test(haystack);
}

function parseExplicitMinutes(text: string): number | null {
  const lower = text.toLowerCase();
  if (/\bhalf\s+(?:an\s+)?hour\b/.test(lower)) return 30;
  let total = 0;
  let matched = false;

  const hrMatches = lower.matchAll(/(\d+(?:\.\d+)?)\s*(?:hours?|hrs?)\b/g);
  for (const m of hrMatches) {
    const val = Number(m[1]);
    if (Number.isFinite(val) && val > 0) {
      total += Math.round(val * 60);
      matched = true;
    }
  }

  const minMatches = lower.matchAll(/(\d+)\s*(?:minutes?|mins?|min)\b/g);
  for (const m of minMatches) {
    const val = Number(m[1]);
    if (Number.isFinite(val) && val > 0) {
      total += Math.round(val);
      matched = true;
    }
  }

  if (!matched) return null;
  return Math.max(5, Math.min(300, total));
}

function estimateClauseMinutes(clause: string, matchedChaptersCount: number): number {
  const explicit = parseExplicitMinutes(clause);
  if (explicit != null) return explicit;

  const lower = clause.toLowerCase();
  let mins = 0;

  // Question / problem / numerical count
  const qMatch = lower.match(/(\d+)\s*(?:questions?|problems?|numericals?|mcqs?|exercises?|sums?)\b/);
  if (qMatch) {
    const count = Math.min(120, Number(qMatch[1]) || 0);
    mins += Math.max(10, Math.round(count * 2));
  }

  // Page count
  const pMatch = lower.match(/(\d+)\s*(?:pages?|pgs?)\b/);
  if (pMatch) {
    const count = Math.min(100, Number(pMatch[1]) || 0);
    mins += Math.max(10, Math.round(count * 3));
  }

  // Activity multipliers
  if (/\b(mock\s*test|full\s*paper|test\s*paper|exam)\b/.test(lower)) {
    mins += 60;
  } else {
    const hasSolve = /\b(solve|practice|homework|assignment|worksheet|derive|derivation)\b/.test(lower);
    const hasRevise = /\b(revise|revision|review|read|notes|memorize|learn|prepare)\b/.test(lower);
    if (hasSolve && mins === 0) mins += 25;
    if (hasRevise) mins += 20;
  }

  if (matchedChaptersCount > 0) {
    const chapterFloor = matchedChaptersCount * 20;
    mins = Math.max(mins, chapterFloor);
  }

  if (mins === 0) mins = 25;
  // Round to nearest 5 minutes cleanly
  return Math.max(10, Math.min(240, Math.round(mins / 5) * 5));
}

function matchSubjectIdsInText(text: string, subjects: Subject[]): Subject[] {
  const lower = text.toLowerCase();
  const matched: Subject[] = [];

  for (const sub of subjects) {
    const subLower = sub.name.toLowerCase();
    const aliases = SUBJECT_ALIASES[subLower] ?? [subLower];
    const directHit = containsWholePhrase(lower, subLower);
    const aliasHit = aliases.some((alias) => containsWholePhrase(lower, alias));
    if (directHit || aliasHit) {
      matched.push(sub);
    }
  }
  return matched;
}

function matchChaptersInText(text: string, candidateChapters: Chapter[]): Chapter[] {
  const lower = text.toLowerCase();
  const inputTokens = new Set(normalizeTokens(text));
  const hits: Chapter[] = [];

  for (const ch of candidateChapters) {
    const chLower = ch.name.toLowerCase();
    // 1. Direct full chapter name match
    if (containsWholePhrase(lower, chLower)) {
      hits.push(ch);
      continue;
    }

    // 2. Partial phrase after stripping common prefixes like "some basic concepts of"
    const cleanName = chLower
      .replace(/^some basic concepts of\s+/i, '')
      .replace(/^introduction to\s+/i, '')
      .replace(/\s+and\s+/g, ' ');
    const chTokens = normalizeTokens(cleanName);
    if (chTokens.length === 0) continue;

    // 3. Stem / prefix matching on significant tokens (min length >= 4)
    let matchedCount = 0;
    for (const ct of chTokens) {
      if (inputTokens.has(ct)) {
        matchedCount++;
      } else if (ct.length >= 5) {
        const stem = ct.slice(0, 5);
        for (const it of inputTokens) {
          if (it.length >= 4 && (it.startsWith(stem) || ct.startsWith(it.slice(0, 4)))) {
            matchedCount++;
            break;
          }
        }
      }
    }

    const ratio = matchedCount / chTokens.length;
    if (
      (chTokens.length === 1 && matchedCount === 1) ||
      (chTokens.length === 2 && matchedCount >= 1 && ratio >= 0.5) ||
      (chTokens.length >= 3 && matchedCount >= 2 && ratio >= 0.5)
    ) {
      hits.push(ch);
    }
  }

  return hits;
}

export const PlanAnalyzer = {
  analyze(text: string, subjects: Subject[], chapters: Chapter[]): PlanAnalysisResult {
    const cleaned = text.trim();
    if (!cleaned) {
      return {
        detectedSubjects: [],
        estimatedMinutes: 25,
        suggestedTasks: [],
        reason: 'Enter or speak a study plan to auto-detect subjects, chapters, and time.',
      };
    }

    // Split text into clauses on punctuation or conjunctions introducing new work
    const clauses = cleaned
      .split(/(?:[.;\n]+|\s+and\s+(?=(?:then|also|for|in|doing|revise|solve|read|study))|\s*,\s*)/i)
      .map((c) => c.trim())
      .filter((c) => c.length > 0);

    const planBySubjectId = new Map<number, DetectedSubjectPlan>();

    const ensureSubjectPlan = (sub: Subject): DetectedSubjectPlan => {
      let existing = planBySubjectId.get(sub.id);
      if (!existing) {
        existing = {
          subjectId: sub.id,
          subjectName: sub.name,
          chapterIds: [],
          estimatedMinutes: 0,
          suggestedTasks: [],
        };
        planBySubjectId.set(sub.id, existing);
      }
      return existing;
    };

    const unassignedClauses: string[] = [];

    for (const clause of clauses) {
      const matchedSubjects = matchSubjectIdsInText(clause, subjects);
      const candidateChapters =
        matchedSubjects.length > 0
          ? chapters.filter((c) => matchedSubjects.some((s) => s.id === c.subjectId))
          : chapters;

      const matchedChapters = matchChaptersInText(clause, candidateChapters);

      // Promote chapter parent subjects if chapter matched without explicit subject name
      for (const ch of matchedChapters) {
        if (!matchedSubjects.some((s) => s.id === ch.subjectId)) {
          const parent = subjects.find((s) => s.id === ch.subjectId);
          if (parent) matchedSubjects.push(parent);
        }
      }

      if (matchedSubjects.length === 0) {
        unassignedClauses.push(clause);
        continue;
      }

      const clauseMins = estimateClauseMinutes(clause, matchedChapters.length);
      const perSubjectMins = Math.max(
        10,
        Math.round(clauseMins / matchedSubjects.length / 5) * 5,
      );

      for (const sub of matchedSubjects) {
        const plan = ensureSubjectPlan(sub);
        const subChapters = matchedChapters.filter((c) => c.subjectId === sub.id);
        for (const ch of subChapters) {
          if (!plan.chapterIds.includes(ch.id)) {
            plan.chapterIds.push(ch.id);
          }
        }
        plan.estimatedMinutes += perSubjectMins;

        const taskTitle = TaskNormalizer.clean(clause);
        if (taskTitle) {
          plan.suggestedTasks.push({
            title: taskTitle.charAt(0).toUpperCase() + taskTitle.slice(1),
            estimatedMinutes: perSubjectMins,
            origin: 'MANUAL',
            chapterId: subChapters[0]?.id ?? null,
            subtasks: [],
          });
        }
      }
    }

    const detectedSubjects = Array.from(planBySubjectId.values());
    const explicitTotal = parseExplicitMinutes(cleaned);

    let estimatedMinutes: number;
    if (explicitTotal != null && detectedSubjects.length <= 1) {
      estimatedMinutes = explicitTotal;
    } else if (detectedSubjects.length > 0) {
      const sum = detectedSubjects.reduce((acc, s) => acc + s.estimatedMinutes, 0);
      const unassignedMins = unassignedClauses.reduce(
        (acc, c) => acc + estimateClauseMinutes(c, 0),
        0,
      );
      estimatedMinutes = explicitTotal ?? Math.max(15, Math.min(300, sum + (sum === 0 ? unassignedMins : 0)));
    } else {
      estimatedMinutes = explicitTotal ?? estimateClauseMinutes(cleaned, 0);
    }

    // Round total cleanly to nearest 5 min
    estimatedMinutes = Math.max(10, Math.min(300, Math.round(estimatedMinutes / 5) * 5));

    const suggestedTasks: DraftTask[] = [];
    const seenKeys = new Set<string>();
    for (const sp of detectedSubjects) {
      for (const t of sp.suggestedTasks) {
        const key = TaskNormalizer.dedupeKey(t.title, t.chapterId);
        if (!seenKeys.has(key)) {
          seenKeys.add(key);
          suggestedTasks.push(t);
        }
      }
    }
    if (suggestedTasks.length === 0 && unassignedClauses.length > 0) {
      for (const clause of unassignedClauses) {
        const clean = TaskNormalizer.clean(clause);
        if (!clean) continue;
        const mins = estimateClauseMinutes(clean, 0);
        const key = TaskNormalizer.dedupeKey(clean, null);
        if (!seenKeys.has(key)) {
          seenKeys.add(key);
          suggestedTasks.push({
            title: clean.charAt(0).toUpperCase() + clean.slice(1),
            estimatedMinutes: mins,
            origin: 'MANUAL',
            chapterId: null,
            subtasks: [],
          });
        }
      }
    }

    const chapterCount = detectedSubjects.reduce((acc, s) => acc + s.chapterIds.length, 0);
    const reason =
      detectedSubjects.length > 0
        ? `Detected ${detectedSubjects.map((s) => s.subjectName).join(' + ')}${
            chapterCount > 0 ? ` (${chapterCount} chapter${chapterCount === 1 ? '' : 's'})` : ''
          } · Estimated ~${estimatedMinutes} min`
        : `Estimated ~${estimatedMinutes} min from your plan`;

    return {
      detectedSubjects,
      estimatedMinutes,
      suggestedTasks,
      reason,
    };
  },
};
