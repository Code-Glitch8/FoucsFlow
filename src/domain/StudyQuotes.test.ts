import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { STUDY_QUOTES, pickRandomQuote } from './StudyQuotes';

describe('StudyQuotes', () => {
  it('provides curated non-empty study quotes with unique ids', () => {
    assert.ok(STUDY_QUOTES.length >= 8);
    const ids = new Set(STUDY_QUOTES.map((q) => q.id));
    assert.equal(ids.size, STUDY_QUOTES.length);
    for (const q of STUDY_QUOTES) {
      assert.ok(q.text.trim().length > 10);
      assert.ok(q.author.trim().length > 2);
      assert.ok(q.topic.trim().length > 2);
    }
  });

  it('never returns the excluded quote id when picking next random quote', () => {
    for (const q of STUDY_QUOTES) {
      const next = pickRandomQuote(q.id, () => 0);
      assert.notEqual(next.id, q.id);
      const nextEnd = pickRandomQuote(q.id, () => 0.9999);
      assert.notEqual(nextEnd.id, q.id);
    }
  });
});
