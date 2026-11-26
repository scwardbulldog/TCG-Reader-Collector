import { execFile } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { classifyGhError, ERROR_KINDS, parseLabelRoles, parseRepoSlug } from './logic.mjs';

const PAGE_SIZE = 100;
const MAX_PAGES = 5;
const BODY_LIMIT = 1500;

const QUERY = `query($owner:String!,$name:String!,$cursor:String){
  repository(owner:$owner,name:$name){
    issues(first:${PAGE_SIZE},after:$cursor,orderBy:{field:UPDATED_AT,direction:DESC}){
      pageInfo{hasNextPage endCursor}
      nodes{
        number title state url updatedAt bodyText
        labels(first:20){nodes{name}}
        assignees(first:5){nodes{login}}
        parent{number}
        subIssuesSummary{total completed}
        blockedBy(first:20){nodes{number state}}
        closedByPullRequestsReferences(first:5,includeClosedPrs:false){nodes{number state url isDraft}}
      }
    }
  }
}`;

export class TrackerError extends Error {
  constructor(kind, detail = '') {
    super(ERROR_KINDS[kind].message);
    this.kind = kind;
    this.fix = ERROR_KINDS[kind].fix;
    this.detail = detail;
  }
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    execFile(command, args, { timeout: 30_000, maxBuffer: 32 * 1024 * 1024, windowsHide: true, ...options }, (error, stdout, stderr) => {
      if (!error) return resolve(stdout);
      if (command === 'gh') {
        const kind = classifyGhError({ code: error.code, stderr: `${stderr} ${error.message}` });
        return reject(new TrackerError(kind, String(stderr || error.message).trim().slice(0, 500)));
      }
      reject(error);
    });
  });
}

export async function detectRepo(root) {
  try {
    return parseRepoSlug(await run('git', ['-C', root, 'remote', 'get-url', 'origin']));
  } catch {
    return null;
  }
}

export async function loadLabelRoles(root) {
  try {
    return parseLabelRoles(await readFile(path.join(root, 'docs', 'agents', 'triage-labels.md'), 'utf8'));
  } catch {
    return parseLabelRoles('');
  }
}

function normalize(node) {
  return {
    number: node.number,
    title: node.title,
    state: node.state,
    url: node.url,
    updatedAt: node.updatedAt,
    body: String(node.bodyText ?? '').slice(0, BODY_LIMIT),
    labels: node.labels.nodes.map((l) => l.name),
    assignees: node.assignees.nodes.map((a) => a.login),
    parent: node.parent?.number ?? null,
    subTotal: node.subIssuesSummary?.total ?? 0,
    subDone: node.subIssuesSummary?.completed ?? 0,
    blockedBy: node.blockedBy.nodes.map((b) => ({ number: b.number, state: b.state })),
    prs: node.closedByPullRequestsReferences.nodes.map((p) => ({ number: p.number, state: p.state, url: p.url, isDraft: p.isDraft })),
  };
}

/** Fetches issues newest-first, paginating up to MAX_PAGES; `truncated` is true when older issues were left out. */
export async function fetchIssues(slug) {
  const [owner, name] = slug.split('/');
  const issues = [];
  let cursor = null;
  for (let page = 0; page < MAX_PAGES; page++) {
    const args = ['api', 'graphql', '-f', `query=${QUERY}`, '-f', `owner=${owner}`, '-f', `name=${name}`];
    if (cursor) args.push('-f', `cursor=${cursor}`);
    let parsed;
    try {
      parsed = JSON.parse(await run('gh', args));
    } catch (error) {
      if (error instanceof TrackerError) throw error;
      throw new TrackerError('unknown', String(error.message));
    }
    if (parsed.errors?.length) throw new TrackerError('unknown', parsed.errors.map((e) => e.message).join('; ').slice(0, 500));
    const connection = parsed.data?.repository?.issues;
    if (!connection) throw new TrackerError('unknown', `Repository ${slug} not found or not accessible.`);
    issues.push(...connection.nodes.map(normalize));
    if (!connection.pageInfo.hasNextPage) return { issues, truncated: false };
    cursor = connection.pageInfo.endCursor;
  }
  return { issues, truncated: true };
}
