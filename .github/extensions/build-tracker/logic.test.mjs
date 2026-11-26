import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_ROLES, buildModel, classifyGhError, deriveLane, organize, parseLabelRoles, parseRepoSlug, pickNextUp, pickSelection, sessionMatchesIssue,
} from './logic.mjs';

const roles = DEFAULT_ROLES;
let counter = 0;
const issue = (number, over = {}) => ({
  number, title: `Issue ${number}`, state: 'OPEN', url: `https://github.com/o/r/issues/${number}`, updatedAt: `2026-01-${String(10 + (counter++ % 18)).padStart(2, '0')}T00:00:00Z`,
  body: '', labels: [], assignees: [], parent: null, subTotal: 0, subDone: 0, blockedBy: [], prs: [], ...over,
});
const card = (number, lane, assignees = []) => ({ number, lane, assignees });

test('parseLabelRoles reads renamed labels and falls back to defaults', () => {
  const md = '| Role | GitHub label | When |\n| --- | --- | --- |\n| `ready-for-agent` | `agent-ready` | x |\n| `bogus` | `nope` | x |';
  const parsed = parseLabelRoles(md);
  assert.equal(parsed['ready-for-agent'], 'agent-ready');
  assert.equal(parsed['ready-for-human'], 'ready-for-human');
  assert.equal(Object.hasOwn(parsed, 'bogus'), false);
  assert.deepEqual(parseLabelRoles(undefined), DEFAULT_ROLES);
});

test('deriveLane: open blockers override labels, closed blockers do not', () => {
  const labelled = { labels: ['ready-for-agent'] };
  assert.equal(deriveLane(issue(1, { ...labelled, blockedBy: [{ number: 9, state: 'OPEN' }] }), roles), 'blocked');
  assert.equal(deriveLane(issue(1, { ...labelled, blockedBy: [{ number: 9, state: 'CLOSED' }] }), roles), 'readyForAgent');
});

test('deriveLane: closed, open PR, human, and unlabelled', () => {
  assert.equal(deriveLane(issue(1, { state: 'CLOSED', labels: ['ready-for-agent'] }), roles), 'done');
  assert.equal(deriveLane(issue(1, { labels: ['ready-for-agent'], prs: [{ number: 5, state: 'OPEN' }] }), roles), 'inProgress');
  assert.equal(deriveLane(issue(1, { labels: ['ready-for-human'] }), roles), 'needsYou');
  assert.equal(deriveLane(issue(1, { labels: ['wayfinder:grilling'] }), roles), 'notReady');
});

test('pickNextUp takes the lowest unassigned ready card, then falls back to human, then null', () => {
  const cards = [card(5, 'readyForAgent'), card(3, 'readyForAgent', ['me']), card(4, 'blocked'), card(9, 'needsYou')];
  assert.deepEqual(pickNextUp(cards), { kind: 'agent', number: 5 });
  assert.deepEqual(pickNextUp([card(9, 'needsYou'), card(7, 'needsYou')]), { kind: 'human', number: 7 });
  assert.equal(pickNextUp([card(1, 'done')]), null);
});

test('organize separates active parents, completed parents (capped at 10) and the inbox', () => {
  const issues = [
    issue(1, { subTotal: 2, labels: ['wayfinder:map'] }), issue(2, { parent: 1, state: 'CLOSED' }), issue(3, { parent: 1 }),
    issue(10, { subTotal: 1, state: 'CLOSED' }), issue(11, { parent: 10, state: 'CLOSED' }),
    issue(20), issue(21, { parent: 1, labels: ['needs-triage'] }), issue(22, { parent: 1 }),
  ];
  const org = organize(issues, roles);
  assert.deepEqual(org.overview.map((s) => [s.number, s.type, s.done, s.total]), [[1, 'map', 1, 4]]);
  assert.deepEqual(org.completed.map((s) => s.number), [10]);
  assert.deepEqual(org.inbox.map((i) => i.number), [20, 21]);

  const many = [];
  for (let n = 100; n < 115; n++) many.push(issue(n, { subTotal: 1, state: 'CLOSED' }), issue(n + 1000, { parent: n, state: 'CLOSED' }));
  assert.equal(organize(many, roles).completed.length, 10);
});

test('a parent whose children are all closed is completed even while open', () => {
  const org = organize([issue(1, { subTotal: 1 }), issue(2, { parent: 1, state: 'CLOSED' })], roles);
  assert.equal(org.overview.length, 0);
  assert.equal(org.completed[0].percent, 100);
});

test('pickSelection prefers request, then last used, then most recently active', () => {
  const org = organize([
    issue(1, { subTotal: 1, updatedAt: '2026-01-01T00:00:00Z' }), issue(2, { parent: 1, updatedAt: '2026-01-01T00:00:00Z' }),
    issue(3, { subTotal: 1, updatedAt: '2026-02-01T00:00:00Z' }), issue(4, { parent: 3, updatedAt: '2026-02-01T00:00:00Z' }),
  ], roles);
  assert.equal(pickSelection(org, undefined, undefined), 3);
  assert.equal(pickSelection(org, undefined, 1), 1);
  assert.equal(pickSelection(org, 'inbox', 1), 'inbox');
  assert.equal(pickSelection(org, 99, 1), 1);
  assert.equal(pickSelection(organize([], roles), undefined, undefined), null);
});

test('buildModel returns the board for the selected parent with next up', () => {
  const model = buildModel({
    roles, requested: 1,
    issues: [
      issue(1, { subTotal: 3 }),
      issue(2, { parent: 1, labels: ['ready-for-agent'], blockedBy: [{ number: 9, state: 'OPEN' }] }),
      issue(3, { parent: 1, labels: ['ready-for-agent'] }),
      issue(4, { parent: 1, labels: ['ready-for-human'] }),
    ],
  });
  assert.deepEqual(model.selection.cards.map((c) => [c.number, c.lane]), [[2, 'blocked'], [3, 'readyForAgent'], [4, 'needsYou']]);
  assert.deepEqual(model.selection.cards[0].blockers, [9]);
  assert.deepEqual(model.selection.nextUp, { kind: 'agent', number: 3 });
});

test('parseRepoSlug handles https and ssh remotes', () => {
  assert.equal(parseRepoSlug('https://github.com/scwardbulldog/TCG-Reader-Collector.git'), 'scwardbulldog/TCG-Reader-Collector');
  assert.equal(parseRepoSlug('git@github.com:owner/repo.git'), 'owner/repo');
  assert.equal(parseRepoSlug('https://example.com/x/y'), null);
});

test('classifyGhError maps failures to distinct kinds', () => {
  assert.equal(classifyGhError({ code: 'ENOENT' }), 'gh-missing');
  assert.equal(classifyGhError({ stderr: 'To get started with GitHub CLI, please run:  gh auth login' }), 'not-logged-in');
  assert.equal(classifyGhError({ stderr: 'dial tcp: lookup api.github.com: no such host' }), 'offline');
  assert.equal(classifyGhError({ stderr: 'API rate limit exceeded' }), 'rate-limited');
  assert.equal(classifyGhError({ stderr: 'boom' }), 'unknown');
});

test('sessionMatchesIssue matches #N in text or N in the branch, not substrings', () => {
  assert.equal(sessionMatchesIssue({ name: 'Implement #26 scan session' }, 26), true);
  assert.equal(sessionMatchesIssue({ name: 'Implement #260' }, 26), false);
  assert.equal(sessionMatchesIssue({ branch: 'issue-26-scan' }, 26), true);
  assert.equal(sessionMatchesIssue({ branch: 'feature/26-scan' }, 26), true);
  assert.equal(sessionMatchesIssue({ branch: 'main' }, 26), false);
});
