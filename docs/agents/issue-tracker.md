# Issue tracker: GitHub

Use `gh` against `scwardbulldog/TCG-Reader-Collector`. Pass `--repo scwardbulldog/TCG-Reader-Collector` to issue commands so a fork's upstream isn't selected by accident.

- Publish specs and tickets with `gh issue create --title ... --body-file ...`.
- Fetch a ticket with `gh issue view <number> --json number,title,body,labels,comments`. List tickets with `gh issue list`, using state and label filters as needed.
- Comment or change labels with `gh issue comment` and `gh issue edit --add-label` / `--remove-label`. To close a ticket, post the reason first, then run `gh issue close`.
- Use native GitHub sub-issues and issue dependencies. On older CLI versions, use `gh api` with the child's database ID for sub-issues and the blocker's database ID for dependencies. If native links are unavailable, put `Part of #<parent>` or `Blocked by: #<blocker>` at the top of the child body. A ticket is unblocked when all blockers are closed.

**PRs as a request surface: no.** When enabled, apply the same triage workflow to external PRs using `gh pr`.

## Wayfinding

Use a `wayfinder:map` issue for Notes, Decisions-so-far, and Fog. Attach child tickets labeled `wayfinder:<type>` as sub-issues or list them in a task list when sub-issues are unavailable. Pick the first open, unassigned, unblocked child in map order and assign it to yourself. Resolve it by commenting with the answer, closing the child, and adding a summary and link to the map's Decisions-so-far.
