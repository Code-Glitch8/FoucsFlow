import test from 'node:test';
import assert from 'node:assert/strict';
import { BlockPolicy, YOUTUBE_PACKAGE } from './BlockPolicy';

test('blocks full YouTube during focus when blockYoutubeApp is true and unlocks on break unless blacklisted for break', () => {
  const emptyBlocked = new Map();
  assert.equal(
    BlockPolicy.shouldBlockApp(YOUTUBE_PACKAGE, {
      sessionActive: true,
      onBreak: false,
      blocked: emptyBlocked,
      allowed: new Set(),
      essentials: new Set(),
      blockYoutubeApp: true,
      blockAllExceptAllowed: false,
    }),
    true,
  );

  // During break when not blacklisted during break -> allowed
  assert.equal(
    BlockPolicy.shouldBlockApp(YOUTUBE_PACKAGE, {
      sessionActive: true,
      onBreak: true,
      blocked: emptyBlocked,
      allowed: new Set(),
      essentials: new Set(),
      blockYoutubeApp: true,
      blockAllExceptAllowed: false,
    }),
    false,
  );

  // Blacklisted even during break -> still blocked on break
  const breakBlocked = new Map([[YOUTUBE_PACKAGE, { packageName: YOUTUBE_PACKAGE, blockedDuringBreak: true }]]);
  assert.equal(
    BlockPolicy.shouldBlockApp(YOUTUBE_PACKAGE, {
      sessionActive: true,
      onBreak: true,
      blocked: breakBlocked,
      allowed: new Set(),
      essentials: new Set(),
      blockYoutubeApp: true,
      blockAllExceptAllowed: false,
    }),
    true,
  );
});

test('allows regular YouTube while blocking Shorts separately when blockYoutubeApp is false', () => {
  const blocked = new Map();
  assert.equal(
    BlockPolicy.shouldBlockApp(YOUTUBE_PACKAGE, {
      sessionActive: true,
      onBreak: false,
      blocked,
      allowed: new Set(),
      essentials: new Set(),
      blockYoutubeApp: false,
      blockAllExceptAllowed: false,
    }),
    false,
  );
  assert.equal(BlockPolicy.shortsEnabled('FOCUS_ONLY', true, false), true);
  assert.equal(BlockPolicy.shortsEnabled('FOCUS_ONLY', true, true), false);
  assert.equal(BlockPolicy.shortsEnabled('ALWAYS', false, false), true);
});
