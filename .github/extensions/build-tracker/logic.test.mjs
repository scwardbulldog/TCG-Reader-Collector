import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_ROLES, buildModel, classifyGhError, deriveLane, organize, parseCommitRefs, parseLabelRoles, parseRepoSlug, pickNextUp, pickSelection, sessionMatchesIssue,
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

test('deriveLane: a commit on an open ticket means awaiting, but blockers and open PRs still win', () => {
  const commit = { sha: 'abc', pushed: false };
  assert.equal(deriveLane(issue(1, { labels: ['ready-for-agent'] }), roles, commit), 'awaiting');
  assert.equal(deriveLane(issue(1, { labels: ['ready-for-human'] }), roles, commit), 'awaiting');
  assert.equal(deriveLane(issue(1, { prs: [{ number: 5, state: 'OPEN' }] }), roles, commit), 'inProgress');
  assert.equal(deriveLane(issue(1, { blockedBy: [{ number: 9, state: 'OPEN' }] }), roles, commit), 'blocked');
  assert.equal(deriveLane(issue(1, { state: 'CLOSED' }), roles, commit), 'done');
});

test('parseCommitRefs maps (#N) subjects to the newest commit and tracks unpushed ones', () => {
  const log = 'c3\tAdd thing (#12)\nc2\tDocument rules\nc1\tEarlier work (#12) (#13)';
  const refs = parseCommitRefs(log, ['c3']);
  assert.deepEqual(refs[12], { sha: 'c3', pushed: false });
  assert.deepEqual(refs[13], { sha: 'c1', pushed: true });
  assert.equal(Object.keys(refs).length, 2);
  assert.deepEqual(parseCommitRefs(log)[12], { sha: 'c3', pushed: true });
});

test('pickNextUp takes the lowest unassigned ready card, then falls back to human, then null', () => {
  const cards = [card(5, 'readyForAgent'), card(3, 'readyForAgent', ['me']), card(4, 'blocked'), card(9, 'needsYou')];
  assert.deepEqual(pickNextUp(cards), { kind: 'agent', number: 5 });
  assert.deepEqual(pickNextUp([card(9, 'needsYou'), card(7, 'needsYou')]), { kind: 'human', number: 7 });
  assert.equal(pickNextUp([card(1, 'done')]), null);
});

test('organize separates active parents, tidy parents, completed parents (capped at 10) and the inbox', () => {
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

test('an open parent whose children are all closed is tidy, not completed', () => {
  const org = organize([issue(1, { subTotal: 1 }), issue(2, { parent: 1, state: 'CLOSED' })], roles);
  assert.equal(org.overview.length, 0);
  assert.deepEqual(org.tidy.map((s) => s.number), [1]);
  assert.equal(org.completed.length, 0);
});

test('a closed parent with open children stays active, and upcoming skips containers', () => {
  const org = organize([
    issue(1, { subTotal: 2, state: 'CLOSED' }), issue(2, { parent: 1 }), issue(3, { parent: 1 }),
    issue(4, { labels: ['tracker'] }), issue(5),
  ], roles);
  assert.deepEqual(org.overview.map((s) => [s.number, s.state, s.done, s.total]), [[1, 'CLOSED', 0, 2]]);
  assert.deepEqual(org.upcoming.map((i) => i.number), [2, 3, 5]);
});

test('pickSelection honours inbox, upcoming and known parents, else defaults to upcoming', () => {
  const org = organize([issue(1, { subTotal: 1 }), issue(2, { parent: 1 })], roles);
  assert.equal(pickSelection(org, undefined), 'upcoming');
  assert.equal(pickSelection(org, 'inbox'), 'inbox');
  assert.equal(pickSelection(org, 1), 1);
  assert.equal(pickSelection(org, 99), 'upcoming');
  assert.equal(pickSelection(organize([], roles), undefined), 'upcoming');
});

test('buildModel: upcoming spans parents, next up is global, blockers carry titles and parents', () => {
  const issues = [
    issue(1, { subTotal: 2 }), issue(10, { subTotal: 1 }),
    issue(2, { parent: 1, labels: ['ready-for-agent'], blockedBy: [{ number: 11, title: 'Other parent work', state: 'OPEN' }] }),
    issue(3, { parent: 1, labels: ['ready-for-human'], milestone: 'v2' }),
    issue(11, { parent: 10, labels: ['ready-for-agent'] }),
    issue(12, { labels: ['ready-for-agent'] }),
  ];
  const model = buildModel({ roles, issues, requested: 1, commitsByIssue: { 12: { sha: 'abc', pushed: false } } });
  assert.deepEqual(model.selection.cards.map((c) => [c.number, c.lane]), [[2, 'blocked'], [3, 'needsYou']]);
  assert.deepEqual(model.nextUp.card.number, 11);
  assert.equal(model.nextUp.kind, 'agent');

  const upcoming = buildModel({ roles, issues, commitsByIssue: { 12: { sha: 'abc', pushed: false } } });
  assert.equal(upcoming.selectedKey, 'upcoming');
  assert.equal(upcoming.upcomingCount, 4);
  const byNumber = Object.fromEntries(upcoming.selection.cards.map((c) => [c.number, c]));
  assert.deepEqual(byNumber[2].blockers, [{ number: 11, title: 'Other parent work' }]);
  assert.deepEqual(byNumber[2].parent, { number: 1, title: 'Issue 1' });
  assert.equal(byNumber[3].milestone, 'v2');
  assert.equal(byNumber[12].lane, 'awaiting');
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
