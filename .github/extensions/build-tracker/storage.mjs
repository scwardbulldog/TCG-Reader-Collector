import { readFile, readdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { sessionMatchesIssue } from './logic.mjs';

const SESSION_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

export const copilotHome = () => process.env.COPILOT_HOME || path.join(os.homedir(), '.copilot');

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
