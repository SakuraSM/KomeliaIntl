# Guard guide

Guards convert stable repository rules into repeatable feedback. They must be deterministic, fast enough for their placement, and scoped to a failure they can explain.

## Active local guards

| Guard | Purpose | Invocation | Enforcement |
|---|---|---|---|
| Harness structure | Manifest shape, required documents, local links, source paths, placeholders, personal paths, and entry-map size | `node scripts/check-harness.mjs` | Local, advisory |
| Diff hygiene | Whitespace errors | `git diff --check` | Required by repository policy |
| Conflict markers | Unresolved merge markers outside vendored code | Command in the [testing guide](testing-guide.md) | Required by repository policy |
| Release policy | SemVer, version-source agreement, bilingual note shape, and templates | `scripts/test-release-policy.sh` | Required before publication |
| Release version | Tag, application version, previous version, and level | `scripts/check-release-version.sh` | Release workflow |
| Release notes | Title and bilingual user-visible content | `scripts/check-release-notes.sh` | Release workflow |

The repository currently has a Release packaging workflow but no general pull-request workflow that runs the full application matrix. Do not describe local checks as protected GitHub checks.

## Adding a guard

Add a guard only after the rule is stable and at least one of these is true:

- The same defect class has repeated.
- A manual omission can publish an invalid artifact or corrupt persisted state.
- The check is substantially cheaper and clearer than another review reminder.

A new guard must:

1. Name the contract it enforces.
2. Operate only on the relevant changed or declared scope when practical.
3. Produce an actionable message with the file or command that failed.
4. Avoid network access unless the contract inherently requires remote state.
5. Have a bounded runtime and a documented escape path for environment failures.
6. Be run locally before it is proposed as a required CI check.

## Rollout levels

1. Document the contract and collect examples.
2. Add a local advisory command.
3. Run it in CI without blocking merges and measure false positives.
4. Make it required only after maintainers explicitly approve the enforcement change.

This refresh stops at level 2 for the harness structure check. It does not change branch protection, pull-request requirements, or CI triggers.
