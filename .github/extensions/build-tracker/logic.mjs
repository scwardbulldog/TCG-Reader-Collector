// Pure logic for the Build Tracker canvas. No I/O so it can be tested with `node --test`.

export const DEFAULT_ROLES = Object.freeze({
  'needs-triage': 'needs-triage',
  'needs-info': 'needs-info',
  'ready-for-agent': 'ready-for-agent',
  'ready-for-human': 'ready-for-human',
  wontfix: 'wontfix',
});

export const LANES = Object.freeze([
  { key: 'needsYou', title: 'Needs you' },
  { key: 'readyForAgent', title: 'Ready for agent' },
  { key: 'inProgress', title: 'In progress' },
  { key: 'blocked', title: 'Blocked' },
  { key: 'notReady', title: 'Not ready' },
  { key: 'done', title: 'Done' },
]);

const COMPLETED_CAP = 10;
const unquote = (cell) => cell.trim().replace(/^`+|`+$/g, '').trim();

/** Reads the role-to-label table from docs/agents/triage-labels.md; unknown or missing rows keep the defaults. */
export function parseLabelRoles(markdown) {
  const roles = { ...DEFAULT_ROLES };
  for (const line of String(markdown ?? '').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('|')) continue;
    const [role, label] = trimmed.replace(/^\||\|$/g, '').split('|').map(unquote);
    if (role && Object.hasOwn(DEFAULT_ROLES, role) && label && !/^-+$/.test(label)) roles[role] = label;
  }
  return roles;
}

export function deriveLane(issue, roles) {
  if (issue.state === 'CLOSED') return 'done';
  if (issue.blockedBy.some((b) => b.state === 'OPEN')) return 'blocked';
  if (issue.prs.some((p) => p.state === 'OPEN')) return 'inProgress';
  if (issue.labels.includes(roles['ready-for-human'])) return 'needsYou';
  if (issue.labels.includes(roles['ready-for-agent'])) return 'readyForAgent';
  return 'notReady';
}

/** Lowest-numbered unassigned ready-for-agent card, else the lowest-numbered ready-for-human card. */
export function pickNextUp(cards) {
  const lowest = (list) => list.reduce((best, c) => (best && best.number < c.number ? best : c), null);
  const agent = lowest(cards.filter((c) => c.lane === 'readyForAgent' && c.assignees.length === 0));
  if (agent) return { kind: 'agent', number: agent.number };
  const human = lowest(cards.filter((c) => c.lane === 'needsYou'));
  return human ? { kind: 'human', number: human.number } : null;
}

export function toCard(issue, roles, sessionsByIssue = {}) {
  const lane = deriveLane(issue, roles);
  return {
    number: issue.number,
    title: issue.title,
    url: issue.url,
    state: issue.state,
    lane,
    labels: issue.labels,
    chips: issue.labels.filter((l) => l.startsWith('wayfinder:') && l !== 'wayfinder:map'),
    assignees: issue.assignees,
    blockers: issue.blockedBy.filter((b) => b.state === 'OPEN').map((b) => b.number),
    prs: issue.prs.map((p) => ({ number: p.number, state: p.state, url: p.url, isDraft: p.isDraft })),
    sessions: sessionsByIssue[issue.number] ?? [],
    body: issue.body,
    canStart: lane === 'readyForAgent',
  };
}

function summarize(parent, kids) {
  const total = kids.length || parent.subTotal;
  const done = kids.length ? kids.filter((k) => k.state === 'CLOSED').length : parent.subDone;
  const activity = [parent, ...kids].reduce((max, i) => (i.updatedAt > max ? i.updatedAt : max), '');
  return {
    number: parent.number,
    title: parent.title,
    url: parent.url,
    type: parent.labels.includes('wayfinder:map') ? 'map' : 'build',
    state: parent.state,
    done,
    total,
    percent: total ? Math.round((done / total) * 100) : 0,
    activity,
  };
}

/** Splits issues into active parents, completed parents and the Inbox. */
export function organize(issues, roles) {
  const kidsOf = new Map();
  for (const issue of issues) {
    if (issue.parent == null) continue;
    if (!kidsOf.has(issue.parent)) kidsOf.set(issue.parent, []);
    kidsOf.get(issue.parent).push(issue);
  }
  const parents = issues.filter((i) => i.subTotal > 0 || kidsOf.has(i.number));
  const parentNumbers = new Set(parents.map((p) => p.number));
  const byActivity = (a, b) => (a.activity < b.activity ? 1 : a.activity > b.activity ? -1 : a.number - b.number);

  const summaries = parents.map((p) => summarize(p, kidsOf.get(p.number) ?? []));
  const overview = summaries.filter((s) => s.state === 'OPEN' && s.done < s.total).sort(byActivity);
  const completed = summaries.filter((s) => !overview.includes(s)).sort(byActivity).slice(0, COMPLETED_CAP);

  const triageLabels = [roles['needs-triage'], roles['needs-info']];
  const inbox = issues
    .filter((i) => i.state === 'OPEN' && !parentNumbers.has(i.number))
    .filter((i) => i.parent == null || i.labels.some((l) => triageLabels.includes(l)))
    .sort((a, b) => a.number - b.number);

  return { overview, completed, inbox, kidsOf };
}

/** Picks what to show: explicit request, else last used, else the most recently active open parent. */
export function pickSelection(org, requested, lastParent) {
  const valid = (key) => {
    if (key === 'inbox') return true;
    const n = Number(key);
    return [...org.overview, ...org.completed].some((s) => s.number === n);
  };
  for (const key of [requested, lastParent]) {
    if (key != null && key !== '' && valid(key)) return key === 'inbox' ? 'inbox' : Number(key);
  }
  if (org.overview[0]) return org.overview[0].number;
  if (org.completed[0]) return org.completed[0].number;
  return org.inbox.length ? 'inbox' : null;
}

export function buildModel({ issues, roles, requested, lastParent, sessionsByIssue = {}, truncated = false }) {
  const org = organize(issues, roles);
  const selectedKey = pickSelection(org, requested, lastParent);
  let selection = { kind: null, parent: null, cards: [], nextUp: null };

  if (selectedKey === 'inbox') {
    selection = { kind: 'inbox', parent: null, cards: org.inbox.map((i) => toCard(i, roles, sessionsByIssue)), nextUp: null };
  } else if (selectedKey != null) {
    const summary = [...org.overview, ...org.completed].find((s) => s.number === selectedKey);
    const kids = (org.kidsOf.get(selectedKey) ?? []).slice().sort((a, b) => a.number - b.number);
    const cards = kids.map((k) => toCard(k, roles, sessionsByIssue));
    selection = { kind: 'parent', parent: summary, cards, nextUp: pickNextUp(cards) };
  }

  return {
    laneDefs: LANES,
    overview: org.overview,
    completed: org.completed,
    inboxCount: org.inbox.length,
    selectedKey,
    selection,
    truncated,
  };
}

export function parseRepoSlug(remoteUrl) {
  const match = /github\.com[:/]+([^/\s]+)\/([^/\s]+?)(?:\.git)?\/?$/i.exec(String(remoteUrl ?? '').trim());
  return match ? `${match[1]}/${match[2]}` : null;
}

export const ERROR_KINDS = Object.freeze({
  'gh-missing': { message: 'The GitHub CLI (gh) is not installed or not on PATH.', fix: 'Install it from https://cli.github.com, then Refresh.' },
  'not-logged-in': { message: 'The GitHub CLI is not logged in.', fix: 'Run `gh auth login`, then Refresh.' },
  offline: { message: 'Cannot reach GitHub.', fix: 'Check your network connection, then Refresh.' },
  'rate-limited': { message: 'GitHub API rate limit reached.', fix: 'Wait a few minutes, then Refresh.' },
  'no-repo': { message: 'Could not work out which GitHub repository to use.', fix: 'Add a GitHub `origin` remote or pass the `repo` input (owner/name).' },
  unknown: { message: 'GitHub returned an unexpected error.', fix: 'Try Refresh; details are below.' },
});

export function classifyGhError({ code, stderr = '' }) {
  if (code === 'ENOENT') return 'gh-missing';
  if (/rate limit/i.test(stderr)) return 'rate-limited';
  if (/gh auth login|not logged in|authentication|bad credentials|HTTP 401/i.test(stderr)) return 'not-logged-in';
  if (/ENOTFOUND|EAI_AGAIN|ECONNREFUSED|ETIMEDOUT|error connecting|could not resolve|dial tcp|timed out|network/i.test(stderr)) return 'offline';
  return 'unknown';
}

/** Matches a local session to an issue by `#N` in its text or `N` as a segment of its branch name. */
export function sessionMatchesIssue(session, number) {
  const text = `${session.name ?? ''} ${session.summary ?? ''}`;
  if (new RegExp(`(^|[^\\w])#${number}(?!\\d)`).test(text)) return true;
  return new RegExp(`(^|[/_-])${number}(?=[-_/]|$)`).test(session.branch ?? '');
}
