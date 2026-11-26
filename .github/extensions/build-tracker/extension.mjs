import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CanvasError, createCanvas, joinSession } from '@github/copilot-sdk/extension';
import declaration from './extension.json' with { type: 'json' };
import { TrackerError, detectRepo, fetchIssues, loadCommitRefs, loadLabelRoles } from './github.mjs';
import { buildModel, deriveLane } from './logic.mjs';
import { findSessions } from './storage.mjs';

const EXTENSION_DIR = path.dirname(fileURLToPath(import.meta.url));
// Workspace scope: <repo>/.github/extensions/build-tracker
const REPO_ROOT = path.resolve(EXTENSION_DIR, '..', '..', '..');
const CACHE_MS = 60_000;
const ASSETS = {
  '/': ['index.html', 'text/html; charset=utf-8'],
  '/client.js': ['client.js', 'text/javascript; charset=utf-8'],
  '/styles.css': ['styles.css', 'text/css; charset=utf-8'],
};

const instances = new Map();
let cache = null;
let detectedRepo;
let sessionRef = null;

const errorBody = (error) => ({ kind: error.kind ?? 'unknown', message: error.message, fix: error.fix ?? '', detail: error.detail ?? '' });

async function resolveRepo(override) {
  if (override) {
    if (!/^[\w.-]+\/[\w.-]+$/.test(override)) throw new CanvasError('invalid_repo', 'repo must look like owner/name.');
    return override;
  }
  if (detectedRepo === undefined) detectedRepo = await detectRepo(REPO_ROOT);
  if (!detectedRepo) throw new TrackerError('no-repo');
  return detectedRepo;
}

async function loadData(repo, force) {
  if (!force && cache?.repo === repo && Date.now() - cache.fetchedAt < CACHE_MS) return { data: cache, error: null };
  try {
    const [{ issues, truncated }, roles] = await Promise.all([fetchIssues(repo), loadLabelRoles(REPO_ROOT)]);
    cache = { repo, issues, truncated, roles, fetchedAt: Date.now(), generatedAt: new Date().toISOString() };
    return { data: cache, error: null };
  } catch (error) {
    const known = error instanceof TrackerError ? error : new TrackerError('unknown', String(error?.message ?? error));
    return { data: cache?.repo === repo ? cache : null, error: known };
  }
}

async function getState(entry, { force = false, parent } = {}) {
  if (parent !== undefined) entry.requestedParent = parent;
  let repo;
  try {
    repo = await resolveRepo(entry.repoOverride);
  } catch (error) {
    if (error instanceof CanvasError) throw error;
    return { repo: null, generatedAt: null, model: null, stale: false, error: errorBody(error) };
  }
  const { data, error } = await loadData(repo, force);
  if (!data) return { repo, generatedAt: null, model: null, stale: false, error: errorBody(error) };

  const numbers = data.issues.filter((i) => i.state === 'OPEN').map((i) => i.number);
  const [sessionsByIssue, commitsByIssue] = await Promise.all([findSessions(repo, numbers), loadCommitRefs(REPO_ROOT)]);
  const model = buildModel({
    issues: data.issues,
    roles: data.roles,
    requested: entry.requestedParent,
    sessionsByIssue,
    commitsByIssue,
    truncated: data.truncated,
  });
  return { repo, generatedAt: data.generatedAt, model, stale: Boolean(error), error: error ? errorBody(error) : null };
}

const clean = (value) => String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, 160).replace(/["\\]/g, '\\$&');

async function startSession(entry, { number, mode = 'local' }) {
  if (!Number.isInteger(number) || number < 1) throw new CanvasError('invalid_input', 'number must be a positive integer.');
  if (!['local', 'cloud'].includes(mode)) throw new CanvasError('invalid_input', 'mode must be local or cloud.');
  if (!sessionRef) throw new CanvasError('unavailable', 'The session bridge is not available.');
  const repo = await resolveRepo(entry.repoOverride);
  const found = cache?.repo === repo ? cache.issues.find((i) => i.number === number) : null;
  if (!found) throw new CanvasError('not_found', `Issue #${number} is not in the loaded data; Refresh first.`);
  if (deriveLane(found, cache.roles, (await loadCommitRefs(REPO_ROOT))[number]) !== 'readyForAgent') {
    throw new CanvasError('not_startable', `#${number} is not an unblocked ready-for-agent ticket.`);
  }
  const title = clean(found.title);
  const prompt = mode === 'cloud'
    ? `Create a cloud coding session for issue #${number} in "${repo}" ("${title}"). Use create_session with execution_location "cloud". Tell the cloud agent to read the issue, GLOSSARY.md and relevant ADRs, implement it per the acceptance criteria, run the existing validation, and report the resulting PR. Do not edit labels or assignees.`
    : `Start a new local Copilot session in this repository to implement issue #${number} in "${repo}" ("${title}"). Have it read the issue with gh issue view ${number} --repo ${repo}, then GLOSSARY.md and relevant ADRs, and implement it per the acceptance criteria. Do not edit labels or assignees.`;
  await sessionRef.send(prompt);
  return { ok: true, message: `Requested a ${mode} session for #${number}.` };
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 16 * 1024) {
        reject(new CanvasError('too_large', 'Request body is too large.'));
        req.destroy();
      }
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new CanvasError('invalid_json', 'Request body is not valid JSON.'));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, value, status = 200) {
  const body = JSON.stringify(value);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body), 'Cache-Control': 'no-store' });
  res.end(body);
}

const parentParam = (value) => {
  if (value === null || value === undefined || value === '') return undefined;
  if (value === 'inbox' || value === 'upcoming') return value;
  return Number.isInteger(Number(value)) ? Number(value) : undefined;
};

async function handle(entry, port, req, res) {
  // Reject requests whose Host is not the loopback address we bound to (DNS rebinding).
  if (req.headers.host !== `127.0.0.1:${port}`) return sendJson(res, { error: 'Forbidden' }, 403);
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${port}`);
  try {
    if (req.method === 'GET' && Object.hasOwn(ASSETS, url.pathname)) {
      const [file, type] = ASSETS[url.pathname];
      const body = await readFile(path.join(EXTENSION_DIR, file));
      res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      return res.end(body);
    }
    if (req.method === 'GET' && url.pathname === '/api/state') {
      return sendJson(res, await getState(entry, { force: url.searchParams.get('force') === '1', parent: parentParam(url.searchParams.get('parent')) }));
    }
    if (req.method === 'POST' && url.pathname === '/api/start') {
      if (!String(req.headers['content-type'] ?? '').startsWith('application/json')) return sendJson(res, { error: 'JSON required' }, 415);
      return sendJson(res, await startSession(entry, await readBody(req)));
    }
    return sendJson(res, { error: 'Not found' }, 404);
  } catch (error) {
    const status = error instanceof CanvasError ? 400 : 500;
    return sendJson(res, { error: error.message, code: error.code }, status);
  }
}

function listen(entry) {
  return new Promise((resolve, reject) => {
    const server = createServer((req, res) => handle(entry, server.address().port, req, res));
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      entry.server = server;
      entry.url = `http://127.0.0.1:${server.address().port}/`;
      resolve();
    });
  });
}

const entryFor = (instanceId) => instances.get(instanceId) ?? { repoOverride: undefined, requestedParent: undefined };

// Agent-facing results omit ticket bodies (the page loads them) to keep payloads small.
const slim = (state) => {
  if (!state.model) return state;
  const strip = ({ body, ...card }) => card;
  const { nextUp } = state.model;
  return {
    ...state,
    model: {
      ...state.model,
      nextUp: nextUp ? { ...nextUp, card: strip(nextUp.card) } : null,
      selection: { ...state.model.selection, cards: state.model.selection.cards.map(strip) },
    },
  };
};

const actionHandlers = {
  refresh: async ({ instanceId }) => slim(await getState(entryFor(instanceId), { force: true })),
  get_state: async ({ instanceId }) => slim(await getState(entryFor(instanceId))),
  select_parent: async ({ instanceId, input }) => {
    const parent = parentParam(input?.parent);
    if (parent === undefined) throw new CanvasError('invalid_input', 'parent must be an issue number, "inbox" or "upcoming".');
    return slim(await getState(entryFor(instanceId), { parent }));
  },
  start_session: ({ instanceId, input }) => startSession(entryFor(instanceId), input ?? {}),
};

const session = await joinSession({
  canvases: declaration.canvases.map((canvas) => createCanvas({
    ...canvas,
    actions: canvas.actions.map((action) => ({ ...action, handler: actionHandlers[action.name] })),
    open: async ({ instanceId, input }) => {
      const repo = input?.repo;
      if (repo !== undefined && !/^[\w.-]+\/[\w.-]+$/.test(String(repo))) throw new CanvasError('invalid_repo', 'repo must look like owner/name.');
      let entry = instances.get(instanceId);
      if (!entry) {
        entry = { server: null, url: '' };
        await listen(entry);
        instances.set(instanceId, entry);
      }
      entry.repoOverride = repo;
      if (input?.parent !== undefined) entry.requestedParent = parentParam(input.parent);
      return { url: entry.url, title: 'Build Tracker' };
    },
    onClose: async ({ instanceId }) => {
      const entry = instances.get(instanceId);
      instances.delete(instanceId);
      await new Promise((resolve) => (entry?.server ? entry.server.close(() => resolve()) : resolve()));
    },
  })),
});
sessionRef = session;
