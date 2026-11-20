import React, { useState } from 'react';
import { BookOpen, RefreshCw } from 'lucide-react';
import { StudyQuote, pickRandomQuote } from '../domain/StudyQuotes';
import { AppCard, SectionLabel } from './ui/Common';

export const StudyQuoteCard: React.FC = () => {
  const [quote, setQuote] = useState<StudyQuote>(() => pickRandomQuote());

  const handleNextQuote = () => {
    setQuote((prev) => pickRandomQuote(prev.id));
  };

  return (
    <AppCard>
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <BookOpen className="h-3.5 w-3.5 text-[var(--ff-primary)] shrink-0" aria-hidden="true" />
          <SectionLabel>STUDY MOTIVATION</SectionLabel>
        </div>
        <button
          type="button"
          onClick={handleNextQuote}
          aria-label="Show another study motivational quote"
          className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--ff-outline)]/40 px-3 py-1.5 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] transition-colors whitespace-nowrap shrink-0 cursor-pointer"
        >
          <RefreshCw className="h-3.5 w-3.5 text-[var(--ff-primary)]" aria-hidden="true" />
          <span>New quote</span>
        </button>
      </div>

      <blockquote
        className="text-[15px] md:text-base font-medium leading-relaxed text-[var(--ff-on-bg)]"
        style={{ textWrap: 'balance' }}
      >
        “{quote.text}”
      </blockquote>

      <div className="text-xs font-medium text-[var(--ff-on-surface-variant)]">
        — {quote.author} · {quote.topic}
      </div>
    </AppCard>
  );
};
