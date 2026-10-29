# Agent guide

## Knowledge graphs

This repository ships two local code graphs. Prefer them over raw `grep`/file reads for codebase questions; use `grep` only when the graph has no match.

### code-review-graph (MCP, primary)

SQLite graph in `.code-review-graph/`, built with tree-sitter AST + Spring resolvers (DI calls, endpoints, events, scheduled tasks). No embeddings: `semantic_search_nodes_tool` runs on FTS5 keyword search.

Use, in order:

1. `query_graph_tool` — callers/callees/relations (~80 tokens).
2. `get_impact_radius_tool` — before any refactor or cross-file change.
3. `list_communities_tool` → `get_community_tool` — architecture overview.
4. `get_review_context_tool` — review a diff.
5. `traverse_graph_tool` — dependency walk; keep `depth <= 3`, `token_budget <= 1500`.
6. `list_graph_stats_tool` — sanity/status.

Rules:

- Never call `get_architecture_overview_tool` (can return ~33k tokens).
- Skip the graph for `.md`, `.json`, `.yml`, `.log`, configs and non-code paths.
- Fall back to `graphify query` / `grep` only when the graph returns no match.
- Refresh manually with `code-review-graph update --skip-flows`.

### graphify (CLI, orientation + visual)

Outputs: `graphify-out/graph.json`, `graphify-out/GRAPH_REPORT.md`, `graphify-out/graph.html`, Obsidian vault `ai-vault/`.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:

- When `graphify-out/graph.json` exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located.
- Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or raw grep output.
- Dirty `graphify-out/` files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed `graphify-out/graph.json` is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.

## TODO(graph): background update during long sessions

The git pre-commit hook keeps both graphs fresh between commits. For long editing sessions where you do not commit, run the watcher in the background instead:

```powershell
crg-daemon start     # watch this repo, incremental updates
crg-daemon status
crg-daemon stop
```

or, for graphify only: `graphify watch .`

Do not run a watcher and the pre-commit hook against the same graph at the same time; they duplicate work.

## Local dev account

A `test` account exists in the local database for logging into the running app.
Do not delete it — recreating it means another manual registration round.

| | |
|---|---|
| login | `test` |
| password | `test-test-1` |
| display name | `Тест` |

The frontend dev server is on `http://localhost:5173` and proxies `/api` to
`APP_PORT` from `.env` (currently 8081, not 8080: 8080 is taken on this machine).

## Docs

`README.md`, `docs/ARCHITECTURE.md`, `docs/DEPLOY.md`, `docs/VERIFY.md` and
`frontend/README.md` are human-facing and can lag the code. For behavior trust the
source and `docs/openapi.json`; for structure trust the graphs above.
`docs/API-SEMANTICS.md` explains what the responses mean — it deliberately does not
repeat the contract, so do not treat it as the source of truth for fields and types.
