import { mkdir, readFile, rename, readdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { sessionMatchesIssue } from './logic.mjs';

const CANVAS_ID = 'build-tracker';
const FORMAT = 'build-tracker-prefs';
const VERSION = 1;
const SESSION_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

export const copilotHome = () => process.env.COPILOT_HOME || path.join(os.homedir(), '.copilot');
const prefsFile = () => path.join(copilotHome(), 'extension-data', CANVAS_ID, 'prefs.json');

/** Reads view preferences. Corrupt or unrecognised files are reported, never overwritten. */
export async function readPrefs() {
  let raw;
  try {
    raw = await readFile(prefsFile(), 'utf8');
  } catch (error) {
    if (error.code === 'ENOENT') return { repos: {}, warning: null };
    return { repos: {}, warning: `Preferences could not be read: ${error.message}`, readOnly: true };
  }
  try {
    const data = JSON.parse(raw);
    if (data?.format !== FORMAT || data?.version !== VERSION || typeof data.repos !== 'object' || data.repos === null) {
      throw new Error('unrecognised format or version');
    }
    return { repos: data.repos, warning: null };
  } catch (error) {
    return { repos: {}, warning: `Preferences file is unrecognised and was left untouched (${error.message}).`, readOnly: true };
  }
}

export async function saveLastParent(repo, lastParent) {
  const prefs = await readPrefs();
  if (prefs.readOnly) return prefs.warning;
  if (prefs.repos[repo]?.lastParent === lastParent) return null;
  prefs.repos[repo] = { ...prefs.repos[repo], lastParent };
  const file = prefsFile();
  await mkdir(path.dirname(file), { recursive: true });
  const temp = `${file}.${randomUUID()}.tmp`;
  await writeFile(temp, JSON.stringify({ format: FORMAT, version: VERSION, repos: prefs.repos }, null, 2));
  await rename(temp, file);
  return null;
}

function readYamlScalar(text, key) {
  const match = new RegExp(`^${key}:\\s*(.*)$`, 'm').exec(text);
  return match ? match[1].trim().replace(/^["']|["']$/g, '') : '';
}

/** Best-effort: finds recent local sessions for the repo and maps issue number -> sessions. */
export async function findSessions(slug, numbers) {
  const root = path.join(copilotHome(), 'session-state');
  const result = {};
  let entries;
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch {
    return result;
  }
  const sessions = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    try {
      const text = await readFile(path.join(root, entry.name, 'workspace.yaml'), 'utf8');
      if (readYamlScalar(text, 'repository').toLowerCase() !== slug.toLowerCase()) continue;
      const updated = Date.parse(readYamlScalar(text, 'updated_at'));
      if (Number.isFinite(updated) && Date.now() - updated > SESSION_WINDOW_MS) continue;
      sessions.push({ id: entry.name, name: readYamlScalar(text, 'name'), summary: readYamlScalar(text, 'summary'), branch: readYamlScalar(text, 'branch') });
    } catch {
      // Sessions without a readable workspace.yaml are ignored.
    }
  }
  for (const number of numbers) {
    const matches = sessions.filter((s) => sessionMatchesIssue(s, number));
    if (matches.length) result[number] = matches.slice(0, 3).map((s) => ({ id: s.id, name: (s.name || s.summary || s.id).slice(0, 80) }));
  }
  return result;
}
