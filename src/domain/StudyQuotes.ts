export interface StudyQuote {
  id: string;
  text: string;
  author: string;
  topic: string;
}

export const STUDY_QUOTES: StudyQuote[] = [
  {
    id: 'deep-work-focus',
    text: 'Starve your distractions and feed your focus — one uninterrupted block at a time.',
    author: 'FocusFlow Study Principle',
    topic: 'Deep Focus',
  },
  {
    id: 'aristotle-habit',
    text: 'We are what we repeatedly do. Excellence, then, is not an act, but a habit.',
    author: 'Will Durant (on Aristotle)',
    topic: 'Consistency',
  },
  {
    id: 'seneca-time',
    text: 'It is not that we have a short time to live, but that we waste much of it.',
    author: 'Seneca',
    topic: 'Time Management',
  },
  {
    id: 'einstein-problem',
    text: 'It is not that I am so smart, it is just that I stay with problems longer.',
    author: 'Albert Einstein',
    topic: 'Persistence',
  },
  {
    id: 'feynman-understanding',
    text: 'Study hard what interests you the most in the most undisciplined, irreverent and original manner possible.',
    author: 'Richard Feynman',
    topic: 'Curiosity',
  },
  {
    id: 'confucius-pace',
    text: 'It does not matter how slowly you go as long as you do not stop.',
    author: 'Confucius',
    topic: 'Momentum',
  },
  {
    id: 'franklin-prep',
    text: 'An investment in knowledge pays the best interest.',
    author: 'Benjamin Franklin',
    topic: 'Learning',
  },
  {
    id: 'curie-progress',
    text: 'Nothing in life is to be feared, it is only to be understood. Now is the time to understand more.',
    author: 'Marie Curie',
    topic: 'Mastery',
  },
  {
    id: 'clear-systems',
    text: 'You do not rise to the level of your goals. You fall to the level of your systems.',
    author: 'James Clear',
    topic: 'Study Systems',
  },
  {
    id: 'drucker-priority',
    text: 'Until we can manage time, we can manage nothing else.',
    author: 'Peter Drucker',
    topic: 'Discipline',
  },
  {
    id: 'newport-attention',
    text: 'Clarity about what matters provides clarity about what does not.',
    author: 'Cal Newport',
    topic: 'Single-Tasking',
  },
  {
    id: 'small-chapters',
    text: 'One finished chapter today beats ten chapters postponed until tomorrow.',
    author: 'FocusFlow Study Principle',
    topic: 'Execution',
  },
];

export function pickRandomQuote(
  excludeId?: string,
  rng: () => number = Math.random,
): StudyQuote {
  const pool =
    excludeId && STUDY_QUOTES.length > 1
      ? STUDY_QUOTES.filter((q) => q.id !== excludeId)
      : STUDY_QUOTES;
  const idx = Math.min(pool.length - 1, Math.max(0, Math.floor(rng() * pool.length)));
  return pool[idx];
}
