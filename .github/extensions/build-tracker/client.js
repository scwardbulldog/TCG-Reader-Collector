(function () {
  'use strict';

  const app = document.getElementById('app');
  const statusEl = document.getElementById('status');
  const openKeys = new Set();
  let state = null;
  let loading = false;
  let lastFocusId = null;

  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
      if (value === null || value === undefined || value === false) continue;
      if (key === 'class') el.className = value;
      else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
      else el.setAttribute(key, value === true ? '' : String(value));
    }
    for (const kid of kids.flat()) {
      if (kid === null || kid === undefined || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }

  const safeUrl = (url) => (/^https:\/\/github\.com\//.test(url || '') ? url : null);
  const link = (url, text) => (safeUrl(url) ? h('a', { href: url, target: '_blank', rel: 'noopener noreferrer' }, text) : text);

  function say(message) { statusEl.textContent = message; }

  function ago(iso) {
    if (!iso) return 'never';
    const minutes = Math.floor((Date.now() - Date.parse(iso)) / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return minutes + 'm ago';
    return Math.floor(minutes / 60) + 'h ago';
  }

  async function api(path, options) {
    const response = await fetch(path, options);
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || 'Request failed');
    return body;
  }

  async function load(query) {
    if (loading) return;
    loading = true;
    render();
    try {
      state = await api('/api/state' + (query || ''));
      say(state.error ? 'Refresh failed: ' + state.error.message : 'Updated');
    } catch (error) {
      state = { repo: null, generatedAt: null, model: null, stale: false, error: { kind: 'unknown', message: error.message, fix: 'Try Refresh.', detail: '' }, warning: null };
    }
    loading = false;
    render();
  }

  async function start(number, mode) {
    try {
      const result = await api('/api/start', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ number, mode }) });
      say(result.message);
    } catch (error) {
      say('Could not start session: ' + error.message);
    }
  }

  function renderHeader() {
    const model = state && state.model;
    return h('header', { class: 'top' },
      h('h1', null, 'Build Tracker'),
      state && state.repo ? h('span', { class: 'muted' }, state.repo) : null,
      h('span', { class: 'spacer' }),
      h('span', { class: 'muted' }, loading ? 'Refreshing…' : 'updated ' + ago(state && state.generatedAt)),
      model ? h('button', { type: 'button', id: 'inbox-btn', 'aria-pressed': String(model.selectedKey === 'inbox'), onclick: () => select('inbox') },
        'Inbox ', h('span', { class: 'badge', 'aria-label': model.inboxCount + ' issues' }, model.inboxCount)) : null,
      h('button', { type: 'button', id: 'refresh-btn', disabled: loading, onclick: () => { lastFocusId = 'refresh-btn'; load('?force=1'); } }, 'Refresh'));
  }

  function renderBanners() {
    const out = [];
    if (state && state.error) {
      out.push(h('div', { class: 'banner ' + (state.model ? 'stale' : 'error'), role: 'alert' },
        h('strong', null, state.model ? 'Showing stale data. ' : ''), state.error.message, ' ', state.error.fix,
        state.error.detail ? h('pre', null, state.error.detail) : null));
    }
    if (state && state.warning) out.push(h('div', { class: 'banner warn', role: 'status' }, state.warning));
    if (state && state.model && state.model.truncated) out.push(h('div', { class: 'banner warn', role: 'status' }, 'Only the 500 most recently updated issues were loaded; older ones are not shown.'));
    return out;
  }

  function parentButton(summary, selectedKey) {
    return h('button', { type: 'button', class: 'parent-btn', 'aria-pressed': String(summary.number === selectedKey), onclick: () => select(summary.number) },
      h('span', { class: 'name' }, h('strong', null, '#' + summary.number), h('span', null, summary.title)),
      h('span', { class: 'muted' }, summary.type + ' · ' + summary.done + ' / ' + summary.total + ' done'),
      h('span', { class: 'bar', role: 'progressbar', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': summary.percent, 'aria-label': summary.title + ' progress' },
        h('span', { style: 'width:' + summary.percent + '%' })));
  }

  function renderOverview(model) {
    const nodes = [];
    if (model.overview.length) nodes.push(h('nav', { class: 'overview', 'aria-label': 'Open parents' }, model.overview.map((s) => parentButton(s, model.selectedKey))));
    else if (model.completed.length) nodes.push(h('div', { class: 'banner' }, 'All clear: every parent is complete.'));
    if (model.completed.length) {
      nodes.push(h('details', { class: 'completed', 'data-key': 'completed', open: openKeys.has('completed') ? true : null, ontoggle: toggleKey },
        h('summary', null, 'Completed (' + model.completed.length + ')'),
        h('div', { class: 'overview' }, model.completed.map((s) => parentButton(s, model.selectedKey)))));
    }
    return nodes;
  }

  function chipRow(card) {
    const chips = [];
    if (card.lane !== 'done' && card.lane !== 'blocked') chips.push(h('span', { class: 'chip' + (card.lane === 'needsYou' ? ' human' : '') }, laneTitle(card.lane)));
    for (const chip of card.chips) chips.push(h('span', { class: 'chip' }, chip.replace('wayfinder:', '')));
    for (const n of card.blockers) chips.push(h('span', { class: 'chip blocked' }, 'blocked by #' + n));
    for (const pr of card.prs) chips.push(h('span', { class: 'chip pr' }, link(pr.url, 'PR #' + pr.number + (pr.isDraft ? ' (draft)' : ''))));
    for (const s of card.sessions) chips.push(h('span', { class: 'chip session', title: s.name }, 'session'));
    for (const a of card.assignees) chips.push(h('span', { class: 'chip' }, '@' + a));
    return h('div', { class: 'chips' }, chips);
  }

  function laneTitle(key) {
    const def = state.model.laneDefs.find((l) => l.key === key);
    return def ? def.title : key;
  }

  function cardActions(card) {
    if (card.state !== 'OPEN') return null;
    if (card.lane === 'needsYou') return h('div', { class: 'actions' }, h('button', { type: 'button', disabled: true, title: 'Needs a human; not handed to an agent' }, 'Start session'));
    if (!card.canStart) return null;
    return h('div', { class: 'actions' },
      h('button', { type: 'button', class: 'primary', onclick: () => start(card.number, 'local') }, 'Start session'),
      h('details', { class: 'menu' }, h('summary', null, 'More'),
        h('button', { type: 'button', onclick: () => start(card.number, 'cloud') }, 'Start cloud session')));
  }

  function renderCard(card, isNext) {
    const key = 'body-' + card.number;
    return h('article', { class: 'card' + (isNext ? ' next' : ''), 'aria-label': 'Issue ' + card.number },
      h('div', { class: 'head' }, h('span', { class: 'num' }, link(card.url, '#' + card.number)), h('span', { class: 'title' }, card.title)),
      chipRow(card),
      card.body ? h('details', { 'data-key': key, open: openKeys.has(key) ? true : null, ontoggle: toggleKey }, h('summary', null, 'Details'), h('pre', null, card.body)) : null,
      cardActions(card));
  }

  function renderNextUp(selection) {
    if (selection.kind !== 'parent') return null;
    const next = selection.nextUp;
    if (!next) return h('div', { class: 'banner' }, 'Next up: nothing is ready. Check Blocked and Not ready.');
    const card = selection.cards.find((c) => c.number === next.number);
    return h('section', { class: 'nextup ' + (next.kind === 'human' ? 'human' : ''), 'aria-label': 'Next up' },
      h('span', { class: 'label' }, next.kind === 'human' ? 'Needs you' : 'Next up'),
      h('span', { class: 'title' }, link(card.url, '#' + card.number), ' ', card.title),
      next.kind === 'agent' ? cardActions(card) : null);
  }

  function renderBoard(model) {
    const selection = model.selection;
    if (!selection.kind) return h('div', { class: 'empty' }, 'Nothing to show yet. No parent issues with sub-issues were found and the Inbox is empty.');
    const heading = selection.kind === 'inbox'
      ? h('h2', null, 'Inbox')
      : h('h2', null, link(selection.parent.url, '#' + selection.parent.number), ' ', selection.parent.title, ' ',
        h('span', { class: 'muted' }, selection.parent.done + ' / ' + selection.parent.total + ' done'));
    const nextNumber = selection.nextUp ? selection.nextUp.number : null;
    const lanes = model.laneDefs.map((lane) => {
      const cards = selection.cards.filter((c) => c.lane === lane.key);
      if (!cards.length && lane.key !== 'done') return null;
      const key = 'lane-' + lane.key;
      const body = h('div', { class: 'cards' }, cards.map((c) => renderCard(c, c.number === nextNumber)));
      const title = [lane.title, h('span', { class: 'count' }, '(' + cards.length + ')')];
      if (lane.key === 'done') {
        return h('details', { class: 'lane done', 'data-key': key, open: openKeys.has(key) ? true : null, ontoggle: toggleKey }, h('summary', null, title), body);
      }
      return h('section', { class: 'lane ' + lane.key, 'aria-label': lane.title }, h('h2', null, title), body);
    });
    return h('main', { style: 'display:flex;flex-direction:column;gap:16px' }, heading, selection.cards.length ? lanes : h('div', { class: 'empty' }, 'No issues here.'));
  }

  function toggleKey(event) {
    const key = event.currentTarget.getAttribute('data-key');
    if (event.currentTarget.open) openKeys.add(key); else openKeys.delete(key);
  }

  function select(key) { load('?parent=' + encodeURIComponent(key)); }

  function render() {
    const nodes = [renderHeader(), ...renderBanners()];
    if (state && state.model) {
      nodes.push(...renderOverview(state.model));
      nodes.push(renderNextUp(state.model.selection));
      nodes.push(renderBoard(state.model));
    } else if (!state || loading) {
      nodes.push(h('div', { class: 'empty', role: 'status' }, 'Loading from GitHub…'));
    }
    app.replaceChildren(...nodes.filter(Boolean));
    if (lastFocusId && !loading) {
      const el = document.getElementById(lastFocusId);
      if (el) el.focus();
      lastFocusId = null;
    }
  }

  // Refresh the relative "updated … ago" label locally; this never calls GitHub.
  setInterval(() => { if (state && !loading) render(); }, 60000);

  load('');
})();
