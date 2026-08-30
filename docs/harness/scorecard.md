# Harness scorecard

This scorecard measures repository guidance and feedback loops, not application quality. Each area scores 0 for missing, 1 for partial, or 2 for explicit and repeatable.

| Area | Score | Evidence |
|---|---:|---|
| Entry map | 2 | `AGENTS.md` is short and routes readers to canonical documents. |
| Project and boundary map | 2 | `docs/project-knowledge/` records modules, contracts, and risks. |
| Task routing | 2 | Task families map to files, checks, and expected output. |
| Test selection | 2 | Focused and consuming checks are separated by impact. |
| Delivery consistency | 2 | Pull request, Release, and issue formats point to repository templates. |
| Evidence discipline | 2 | Observed, declared, inferred, and unknown are defined; execution status is separate. |
| Executable harness validation | 1 | Local structural check exists but is not exercised by CI. |
| Pull-request feedback | 0 | No general PR workflow validates the application or harness. |
| Freshness automation | 1 | Source commit and refresh triggers are recorded, but staleness is reviewed manually. |
| Toolchain consistency | 1 | JDK runtime/target distinction is explicit; Node 20 local and Node 24 CI remain split. |
| **Total** | **15/20** | Standard-tier contract is usable; automation remains intentionally advisory. |

## Highest-value next steps

1. Decide one supported Node version and align `.node-version`, contributor docs, and workflows.
2. Trial `node scripts/check-harness.mjs` as a non-blocking pull-request job before considering enforcement.
3. Add focused CI only where runtime and dependency cost are acceptable; do not imply full cross-platform coverage from one host.
4. Refresh the project map when upstream synchronization changes module ownership or build aliases.
