## Build and test

Build, test and install commands are in [README.md](README.md). The `domain` module must stay free of Android dependencies.

For the definition of done, device handling (`adb` is not on PATH) and handing over to the owner, read [Verification and device testing](docs/agents/verification.md).

## Working rules

- Never stage `local.properties` or print `catalog.token`. Run `git status` before every commit.
- Work on `main`: one commit per ticket, with `(#NN)` in the message. No branches or PRs unless asked.
- Commit locally, then wait for the owner's go-ahead before `git push` and `gh issue close`.
- On `ready-for-human` tickets, prepare what you can, hand over a "your turn" checklist, and don't close until the owner reports results.

## Agent skills

### Issue tracker

For issue operations, read [Issue tracker](docs/agents/issue-tracker.md). This repo uses GitHub Issues.

### Triage labels

For issue triage, read [Triage labels](docs/agents/triage-labels.md) for the role-to-label mapping.

### Domain docs

Before exploring the codebase, read [Domain docs](docs/agents/domain.md). This repo uses a single-context layout.
