import test from 'node:test';
import assert from 'node:assert/strict';
import { PlanAnalyzer } from './PlanAnalyzer';
import { Chapter, Subject } from '../types/models';

const subjects: Subject[] = [
  { id: 1, name: 'Mathematics', builtIn: true, createdAt: 0 },
  { id: 2, name: 'Chemistry', builtIn: true, createdAt: 0 },
  { id: 3, name: 'Physics', builtIn: true, createdAt: 0 },
];

const chapters: Chapter[] = [
  { id: 101, subjectId: 1, name: 'Quadratic Equations', selfConfidence: null, createdAt: 0 },
  { id: 102, subjectId: 1, name: 'Trigonometric Functions', selfConfidence: null, createdAt: 0 },
  { id: 201, subjectId: 2, name: 'Structure of Atom', selfConfidence: null, createdAt: 0 },
  { id: 202, subjectId: 2, name: 'Thermodynamics', selfConfidence: null, createdAt: 0 },
  { id: 301, subjectId: 3, name: 'Motion in a Straight Line', selfConfidence: null, createdAt: 0 },
];

test('detects multiple subjects, their chapters, and estimates rough duration', () => {
  const res = PlanAnalyzer.analyze(
    'I am doing quadratic equations and solving 20 questions for math, and revising thermodynamics for chemistry',
    subjects,
    chapters,
  );

  assert.equal(res.detectedSubjects.length, 2);
  const math = res.detectedSubjects.find((s) => s.subjectId === 1);
  const chem = res.detectedSubjects.find((s) => s.subjectId === 2);
  assert.ok(math, 'Mathematics should be detected');
  assert.ok(chem, 'Chemistry should be detected');
  assert.deepEqual(math.chapterIds, [101]);
  assert.deepEqual(chem.chapterIds, [202]);
  assert.ok(res.estimatedMinutes >= 45, `Expected >= 45 min, got ${res.estimatedMinutes}`);
  assert.ok(res.suggestedTasks.length >= 2);
});

test('promotes parent subject when chapter is spoken without naming subject', () => {
  const res = PlanAnalyzer.analyze('Revise motion in a straight line', subjects, chapters);
  assert.equal(res.detectedSubjects.length, 1);
  assert.equal(res.detectedSubjects[0].subjectId, 3);
  assert.deepEqual(res.detectedSubjects[0].chapterIds, [301]);
});

test('honors explicit duration when specified by user', () => {
  const res = PlanAnalyzer.analyze('Solve trigonometric functions for math for 45 minutes', subjects, chapters);
  assert.equal(res.detectedSubjects.length, 1);
  assert.equal(res.estimatedMinutes, 45);
});
