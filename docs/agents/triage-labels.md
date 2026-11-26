# Triage labels

| Role | GitHub label | When to use |
| --- | --- | --- |
| `needs-triage` | `needs-triage` | Awaiting maintainer evaluation |
| `needs-info` | `needs-info` | Awaiting more information from the reporter |
| `ready-for-agent` | `ready-for-agent` | Fully specified and ready for an agent |
| `ready-for-human` | `ready-for-human` | Requires human implementation |
| `wontfix` | `wontfix` | Will not be worked on |

Apply the GitHub label mapped to the role named by the skill.

## Other labels and milestones

- `tracker`: a parent issue that groups child tickets. It carries this instead of a state role, and closes when its children are done. Discovery skips it when looking for work to triage.
- Milestone `v2`: post-v1 work. These tickets are not bound by `docs/spec/v1.md`; they may supersede it, and the ticket should say which part. Keep solutions simple.
