# roadmap.md — FitNesse Modernization to Vert.x + Agentic Requirements + Native Git Storage + MCP

This plan is written to be executed against a **fork of https://github.com/unclebob/fitnesse** and is intentionally structured for **Codex** (clear tasks, interfaces, acceptance criteria, and deliverables).

---

## 0) Outcomes

### What “done” looks like
1. **FitNesse rewritten on Vert.x** (HTTP server, routing, async IO, modular services).
2. **Native Git-backed wiki storage + Git-based history** (no “git plugin” required; Git is a first-class doc store).
3. **Can run existing FitNesseRoot doc trees unchanged** (migration + compatibility modes).
4. **CI-friendly headless test runner** that runs Fit/Slim suites in **GitHub Actions** and **GitLab CI**, producing **JUnit XML + HTML artifacts**.
5. **Side-panel AI assistant** for agentic requirements writing + Java test generation + immutable history + summarization + Q&A + eval authoring.
6. **MCP server** (multi-protocol “views”) exposing hosted content + search and safe actions, with plugin architecture.
7. **OAuth2/OIDC auth plugin** with **Azure AD** support, secured for UI + APIs + MCP.
8. Strong **unit, integration, acceptance** testing across all components.

---

## 1) High-Level Architecture

### 1.1 Modules (suggested Gradle multi-module layout)
- `fitnesse-core`
  - Page model, parsing, rendering, directives, properties, suites, Fit/Slim integration hooks, results model.
- `fitnesse-docstore`
  - `DocStore` SPI + implementations: `FileSystemDocStore`, `GitDocStore`.
- `fitnesse-web-vertx`
  - Vert.x HTTP server, routing, handlers, static assets, websocket/SSE (optional for live results), API endpoints.
- `fitnesse-search`
  - Indexer + query engine (keyword first; optional embeddings later).
- `fitnesse-mcp`
  - MCP server + protocol adapters (HTTP+JSON first; add gRPC/websocket view).
- `fitnesse-ai`
  - OpenAI provider, prompt templates, LangGraph orchestration, eval framework, immutable AI history.
- `fitnesse-auth`
  - OAuth2/OIDC, Azure AD integration, RBAC policy hooks.
- `fitnesse-cli`
  - Headless server + test runner CLI, migration tools, CI outputs.
- `fitnesse-plugins`
  - Plugin host + built-in plugins (auth, optional protocol adapters, optional search backends).
- `fitnesse-compat-tests`
  - Golden tests comparing legacy FitNesse behavior and outputs.

### 1.2 Core service boundaries
- `PageService` (CRUD, history, metadata, attachments)
- `TestExecutionService` (run suite/page, Fit/Slim runner orchestration, results)
- `SearchService` (index + query, return snippets + references)
- `AuditService` (append-only event log)
- `AuthService` (identity, sessions, token validation, RBAC)
- `AiAssistantService` (agent graph execution, summaries, Q&A, test generation, eval authoring)
- `McpService` (resources, tools, query/search, safe actions)

---

## 2) Native Git-backed Pages (First-Class Feature)

### 2.1 Requirements
- Wiki pages live in a **Git working tree**, committed on save.
- Git becomes the **authoritative history** (no bespoke zip history).
- Support:
  - Initialize new repo
  - Clone existing repo and use it as wiki store
  - Commit on save with deterministic, configurable commit messages
  - Optional author mapping (from logged-in user)
  - Branching strategies:
    - `main` for canonical wiki
    - optional feature branches per change-request
- Must support concurrency and conflict handling:
  - optimistic concurrency with `ETag` / page version hash
  - detect conflicts; surface merge UI or auto-merge when safe
  - merge strategy is configurable per environment (fast-forward, merge commit, rebase, squash, ours, theirs)
  - allow manual conflict resolution path and auto-resolution when safe
  - unresolved conflicts are stored as explicit conflict artifacts (no inline conflict markers in `content.txt`)
- Must be compatible with existing `FitNesseRoot` layout:
  - `PageName/content.txt`
  - `PageName/properties.xml`
  - `PageName/files/**`

### 2.2 DocStore SPI
Define in `fitnesse-docstore`:

```java
interface DocStore {
  PageRef resolve(String wikiPath);
  Page readPage(PageRef ref);
  void writePage(PageRef ref, PageWriteRequest req); // includes expectedVersion/etag
  List<PageRef> listChildren(PageRef ref);
  PageProperties readProperties(PageRef ref);
  void writeProperties(PageRef ref, PageProperties props);
  List<AttachmentRef> listAttachments(PageRef ref);
  InputStream readAttachment(AttachmentRef ref);
  void writeAttachment(AttachmentRef ref, InputStream data, Metadata meta);
  PageHistory history(PageRef ref, HistoryQuery q); // may delegate to Git
}
```

### 2.3 `GitDocStore` implementation
- Use **JGit** (or shell git behind an adapter) to:
  - Maintain a working tree directory (configurable)
  - Auto-commit changes on write operations
  - Provide history via `git log`
  - Provide diffs for UI (git diff)
- Transaction model:
  - Acquire a short-lived lock per page write (file lock or JVM lock)
  - Validate `expectedVersion`
  - Apply changes to working tree
  - `git add` affected files
  - `git commit -m "<template>" --author "<name <email>>"`
  - On conflict: apply selected merge strategy (fast-forward, merge commit, rebase, squash, ours, theirs); if unresolved, return conflict payload for UI merge
  - Strategy must be pluggable/configurable via `docstore.git.mergeStrategy`
- Commit message template:
  - Default: `wiki: update <PagePath> (content/properties/attachments)`
  - Include optional “reason” from UI
  - Include correlation ID for audit log
- Identity mapping:
  - If OAuth/OIDC/basic auth user present: author name/email derived from claims (preferred_username/name/email/upn) or headers (`X-FitNesse-User` / `X-FitNesse-Email`)
  - Commits use a fixed service committer (`docstore.git.committer.name/email` or `FITNESSE_GIT_COMMITTER_*`); author is set per request for per-user history
  - Fallback to system user if no identity available

### 2.4 UI: Git History Views
- “History” tab uses Git commits:
  - list commits affecting the page path
  - show diffs (content and properties)
  - allow revert (creates new commit)

### 2.5 Acceptance Criteria
- Saving a page results in a new git commit.
- History view shows commits; diff matches `git diff`.
- Concurrency conflict is detected and resolved (manual merge or rebase flow).
- Works with repo containing existing FitNesseRoot.

---

## 3) Migrating / Using Existing FitNesse Doc Folders

### 3.1 Compatibility modes
Config `docstore.mode`:
- `filesystem-legacy` — operate directly on existing `FitNesseRoot` folder tree.
- `git-native` — operate on a Git working tree; content stored in standard FitNesse layout inside repo.
- `import-to-git` — one-time importer from filesystem to a git repo with initial commit.

### 3.2 Migration tooling (`fitnesse-cli`)
Commands:
- `fitnesse migrate --from <FitNesseRoot> --to <gitDir> [--initRepo] [--dryRun] [--diff]`
- `fitnesse validate --root <FitNesseRoot> --strict`
- `fitnesse repair --root <FitNesseRoot>` (optional) fix common issues (invalid xml, missing properties)

Migration outputs:
- `migration-report.json` (counts, warnings, invalid pages)
- Optional: `migration-report.md`

### 3.3 Acceptance Criteria
- Can point new server at an existing FitNesseRoot and run suites unchanged.
- `import-to-git` produces a git repo where running the same suites yields the same results.

---

## 4) Vert.x Rewrite (Web + APIs)

### 4.1 Server
- `Vertx` main entrypoint:
  - HTTP server
  - router with handlers:
    - Page render
    - Page edit/save
    - Attachments
    - Test run
    - Search
    - AI side panel endpoints
    - MCP endpoints (or separate verticle)
- Non-blocking:
  - File IO via Vert.x filesystem API or worker pool
  - Git operations via worker pool
  - Test execution via worker pool / managed processes

### 4.2 Routing mapping
Replace ResponderFactory pattern with explicit routes:
- `GET /wiki/:path*` view page
- `GET /wiki/:path*/edit` edit page
- `POST /wiki/:path*` save page
- `POST /wiki/:path*/attachments` upload
- `POST /run` run suite/page
- `GET /results/:id` get results
- `GET /api/search?q=...`
- `POST /api/ai/assist`
- `POST /api/ai/evals`
- `GET /api/history/:path*`
- `GET /mcp/...`

### 4.3 Security hooks
Attach auth handler to:
- writes, test execution, AI calls, MCP calls (configurable)

### 4.4 Acceptance Criteria
- Core browsing/editing works.
- Test runs work from UI and API.
- Stable under concurrent requests (load test baseline).

### 4.5 Performance defaults and SLAs (suggested)
- Configure Vert.x timeouts and TTLs:
  - Page render SLA: p95 < 200ms on moderate repos
  - Page save SLA: p95 < 300ms (excluding merge conflicts)
  - Search SLA: p95 < 200ms for keyword search
  - Test run SLA: start within 2s; execution time reported separately
  - API request timeout default: 30s (configurable)
  - WebSocket/SSE idle timeout: 60s (configurable)
- Worker pool sizing and blocking calls documented in `docs/architecture.md`.

---

## 5) Running FitNesse Tests in GitHub/GitLab Pipelines

### 5.1 CLI runner
In `fitnesse-cli`:
- `fitnesse test --root <dir> --suite <SuitePage> --format junit,html,json --out <dir> [--parallel N] [--timeout ...]`

Outputs:
- `junit.xml` (or per-suite XML)
- `report.html` + assets
- `summary.json` (counts, timings, failures)

### 5.2 Container-first distribution
- Publish Docker image `ghcr.io/<org>/fitnesse-modern:<version>`
  - Contains CLI and runtime
- Provide example pipeline files:
  - `.github/workflows/fitnesse.yml`
  - `.gitlab-ci.yml` include snippet

### 5.3 CI integration requirements
- Works in headless mode
- Returns non-zero exit code on failures
- Exposes artifacts and JUnit reports for pipeline UI

### 5.4 Acceptance Criteria
- Sample pipeline runs on PR and reports pass/fail in GitHub and GitLab.
- JUnit XML is correctly parsed by both CI systems.

---

## 6) Search Capability (for UI + AI + MCP)

### 6.1 Baseline (Phase 1)
- Keyword index across:
  - `content.txt`
  - key properties
  - tags/labels if present
- Provide snippets with highlights
- Update index incrementally on page writes

### 6.2 Optional (Phase 2)
- Embedding index for semantic search (pluggable)
- Hybrid ranking (keyword + semantic)

### 6.3 APIs
- `GET /api/search?q=...&scope=...&limit=...`

### 6.4 Acceptance Criteria
- Search returns relevant pages and snippets quickly (<200ms for moderate repos).
- AI uses search tool in agent graph to ground answers.

---

## 7) Agentic Requirements Side Panel (OpenAI + LangGraph)

### 7.1 UX requirements
Side panel on edit/view pages:
- Immutable conversation history per page / thread
- Summarization (“What changed? What’s decided? What’s open?”)
- Q&A over page + related pages + history
- AI test generation (Java unit/integration/acceptance, FitNesse tables/fixtures)
- Evals authoring (prompt sets + expected criteria + scoring)

### 7.2 Data model (append-only)
- `ConversationThread`
- `Message` (user/agent/tool, timestamp, references)
- `Artifact` (generated requirements, tests, evals)
- `AuditEvent` (page write, git commit id, test run id)

Store as:
- Append-only log (e.g., event store file, sqlite, or git-backed “/ai-history/” folder committed)
- Prefer: store AI history in repo under `/.fitnesse/ai/` and commit via GitDocStore for full traceability.

### 7.3 LangGraph orchestration (embedded)
Graph includes agents:
- ProductOwnerAgent
- TesterAgent
- DeveloperAgent
- ArchitectAgent
- Tool nodes:
  - SearchTool (SearchService)
  - PageContextTool (reads current + linked pages)
  - TestScaffoldTool (templates Java/fixtures)
  - EvalsTool (create/validate eval definitions)

Outputs:
- Proposed requirement update (markdown/wiki)
- Acceptance criteria
- Suggested test cases
- Risks/assumptions
- Trace links to pages & commits

### 7.4 Prompting + Guardrails
- System prompts per role
- Grounding rules: cite page refs used; do not invent APIs/fixtures
- Safety: redact secrets; don’t store OAuth tokens; never log API keys

### 7.5 Acceptance Criteria
- Panel can draft a requirement from conversation and insert into page.
- Generates Java test skeletons compilable by Maven/Gradle sample project.
- Summarization and Q&A reference real pages via search grounding.

---

## 8) MCP Server (Multi-protocol “views”)

### 8.1 MCP capabilities
Expose:
- Resources:
  - Pages (content + properties + attachments metadata)
  - History (git commits)
  - Search results
  - Test run results
  - AI artifacts (optional, permissioned)
- Tools:
  - `search(query)`
  - `get_page(path)`
  - `get_history(path)`
  - `run_suite(suitePath)` (optional; gated)
  - `summarize_page(path)`
  - `create_eval(...)`

### 8.2 Protocol adapters (“views”)
- Required: HTTP+JSON
- Add-on adapters:
  - WebSocket for streaming results
  - gRPC (optional)
Design: one core MCP service, multiple adapters.

### 8.3 Auth for MCP
- OAuth2 bearer tokens
- API keys (optional) with scopes
- RBAC enforcement

### 8.4 Acceptance Criteria
- External agent can connect, search, fetch content, and read history.
- Tool calls are audited (append-only log, correlated to auth identity).

## 8.5 Policy inheritance
- UI/API/MCP policies inherit from a master policy.
- Allow override by folder/path hierarchy for local wiki trees (e.g., `/Team/*`).
- Precedence: most-specific path rule wins; deny rules override allow rules unless explicitly marked as `allowOverride`.
- Expose policy evaluation decision (allow/deny + rule) for auditability.

---

## 9) Plugin Architecture (including Auth)

### 9.1 Plugin model
- Use Java ServiceLoader (SPI) or a simple plugin registry.
- Plugins can register:
  - Vert.x routes
  - DocStore implementations
  - Search backends
  - MCP protocol adapters
  - AI provider adapters

### 9.2 Built-in plugin: OAuth2/OIDC with Azure AD
- Configure tenant/client/app
- Login for UI
- Token validation for API/MCP
- Role mapping from claims/groups
- CSRF protection for browser flows

### 9.3 Acceptance Criteria
- Azure login works end-to-end.
- Unauthorized writes blocked.
- Plugin can be disabled/enabled via config.

### 9.4 Auth optionality (local dev)
- Auth plugin can be disabled for local/dev by config flag.
- Provide `auth.mode=none` or `auth.enabled=false` with clear warnings and a default local-only binding.


---

## 10) Testing Strategy

### 10.1 Unit tests
- DocStore implementations (filesystem + git)
- Git commit creation + history retrieval
- Parser/rendering/directives
- Search indexing + query
- AI orchestration (mock OpenAI; deterministic fixtures)
- MCP resources/tools schema validation

### 10.2 Integration tests
- Spin up Vert.x server in test harness
- End-to-end page CRUD + git commits
- Test execution flow (Fit/Slim) with a tiny sample suite
- Auth flows (mock OIDC provider or local test provider)
- MCP calls over HTTP

### 10.3 Acceptance tests
- Golden compatibility suite:
  - Run sample FitNesseRoot with legacy FitNesse and new system
  - Compare key outputs (counts, pass/fail, critical HTML sections)
- CI pipeline smoke tests:
  - GitHub Actions workflow runs on PR
  - GitLab CI job runs and uploads JUnit

### 10.4 Non-functional
- Load test baseline (concurrent reads + edits + test runs)
- Security: XSS/CSRF regression tests for wiki rendering and editor
- Use latest Vert.x unit testing support for reactive tests in the Vert.x modules.

## 10.5 Logging
- Use Vert.x logging patterns (SLF4J + JUL bridge if needed).
- Logs are configurable by module and environment (console, JSON, file).
- Default log levels documented; debug traces are opt-in.

## 11) Security and compliance defaults
- XSS/CSRF protections enabled by default (content sanitization, CSP headers, CSRF tokens for state-changing requests).
- Follow latest NIST recommendations for AI systems: data minimization, prompt/response logging controls, redaction, and model output labeling.
- Secrets never logged; tokens redacted at ingress and persistence.

## 12) Dependency licensing
- Prefer MIT or Apache-2.0 licensed dependencies.
- Add a dependency license report and fail build if non-compliant licenses appear.


---

## 13) Delivery Milestones (suggested)

### Milestone A — Core Vert.x + filesystem-legacy (completed)
- [x] Vert.x server with view/edit/save
- [x] FileSystemDocStore
- [x] Basic test runner
- [x] Minimal UI

### Milestone B — Native GitDocStore + history UI (completed)
- [x] GitDocStore
- [x] History/diff/revert UI
- [x] Migration CLI import-to-git
- [x] Replace FitNesse internal class-to-class calls with Vert.x EventBus messaging (request/reply) for all core services.
- [x] Replace legacy HTTP/socket handling with Vert.x Web + EventBus (retire FitNesseExpediter/FitNesseServer/SocketService where covered).
- [x] Replace legacy static file serving with Vert.x StaticHandler for `/files/*` and asset delivery.
- [x] EventBus tests using Vert.x unit test library.

### Milestone C — CLI runner + CI templates (completed)
- [x] `fitnesse test` CLI
- [x] JUnit + HTML outputs
- [x] Docker image
- [x] GitHub/GitLab examples

### Milestone D — Search + AI assistant v1 (completed)
- [x] 1) Ship search endpoints + UI with grounding output (done in current implementation).
- [x] 2) Move search execution to EventBus + shared-data caching and async worker pool.
- [x] 3) Add search filters (tags, suite/test) and paging with UI controls.
- [x] 4) Add navigation links in the main menu for Search and Git History.
- [x] 5) Add a React-based workflow UI for chaining AI prompts (LangGraph-style nodes/edges) integrated into FitNesse UI.
- [x] 6) Provide workflow test execution from the UI, with stored runs and assertions in FitNesse pages.

#### React workflow UI — acceptance criteria
- [x] Visual editor for nodes/edges, with prompt chaining and parameter mapping.
- [x] Save/load workflows to FitNesse pages (JSON + metadata).
- [x] Execute workflows from UI and capture per-node outputs.
- [x] FitNesse assertions against workflow outputs (expected text, contains, regex).
- [x] Export workflow runs and results in JSON for CI artifacts.

#### React workflow UI — tasks
- [x] Add React app under `src/fitnesse/resources/agent-ui/` with minimal build pipeline.
- [x] Implement workflow editor (sequence nodes, inputs/outputs, assertions).
- [x] Add workflow persistence API (`/api/ai/workflows`) backed by local store.
- [x] Add workflow execution API (`/api/ai/workflows/run`) using LangGraph adapter.
- [x] Add UI for test assertions + run history.

### Milestone E — LangGraph multi-agent + evals (completed)
- [x] Multi-role orchestration (role-aware workflow nodes).
- [x] Reflection nodes (second-pass reasoning prompts).
- [x] Graph execution order with edges + draggable node layout.
- [x] Evals authoring + execution UI and API (mockable).

### Milestone F — MCP server + multi-protocol adapters (completed)
- [x] HTTP+JSON MCP
- [x] Optional websocket/gRPC view
- [x] RBAC + audit logs

---

## 14) Concrete Task List (Codex-friendly)

### Next up (current focus)
- Test execution integration and reporting (Slim/Fit runners, async execution, HTML + JUnit outputs).
- CI/CLI follow-through (Docker image + GitHub/GitLab pipelines + smoke tests).

### Repo & build
- [ ] Create new Gradle multi-module structure and wire CI build.
- [ ] Define shared config model (yaml + env overrides).
- [ ] Add formatter, static analysis, and baseline test scaffolding.
- [x] Replace org.json usage with Vert.x JSON types and drop legacy dependency.

### DocStore + Git
- [x] Implement `DocStore` SPI and `FileSystemDocStore`.
- [x] Implement `GitDocStore` using shell git adapter (commit on save).
- [x] Implement git history/diff/revert service.
- [x] Add conflict detection (ETag/version hash) + merge UX (ours/theirs + conflict error for other strategies).
- [x] Add configurable merge strategies (fast-forward, merge commit, rebase, squash).
- [x] Add migration CLI `migrate` (import-to-git).
- [x] Add migration CLI `validate`.

### Vert.x web
- [x] Implement Vert.x router and handlers for wiki CRUD.
- [x] Implement attachments API + static serving.
- [x] Implement results storage and retrieval endpoints.
- [x] Implement auth middleware integration points.
- [x] Document/implement Vert.x default timeouts and SLA targets.
- [x] Refactor responders to EventBus-backed handlers (no direct responder invocation).
- [x] Replace legacy static file responders with Vert.x StaticHandler.
- [x] Replace legacy HTTP/socket classes with Vert.x Web equivalents where applicable.
- [x] Wire async Slim/Fit execution on worker pool with timeouts and backpressure.
- [x] Emit JUnit + HTML result artifacts from Vert.x test execution.
- [x] Add run monitor UI/API to observe queue/running/completed and throttle when saturated.

### Test execution
- [x] Integrate Slim and Fit runners.
- [x] Implement async execution with worker pool.
- [x] Implement reporting model and HTML rendering.
- [x] Add JUnit exporter from results.

### CI / CLI
- [x] Implement `fitnesse test` runner CLI.
- [x] Produce JUnit XML + HTML report artifacts.
- [ ] Build/publish Docker image.
- [ ] Add GitHub Actions workflow example.
- [ ] Add GitLab CI job example.
- [ ] Add pipeline smoke tests for Docker + CI templates.

### Search
- [x] Implement SearchService + indexer.
- [x] Add `/api/search` endpoint + UI search page.
- [x] Hook into AI tools for grounding.

### AI + LangGraph
- [x] Implement AI provider interface + OpenAI adapter (placeholder echo provider).
- [x] Implement immutable AI history store (git-backed folder recommended).
- [x] Build side panel UI with conversation, summaries, Q&A (minimal /ai UI + /api/ai/assist).
- [x] Implement test generation tools (Java scaffolds, fixtures).
- [x] Implement LangGraph multi-agent workflows (sequential placeholder).
- [x] Implement eval authoring + eval execution harness.

### MCP + protocols
- [x] Implement MCP core resources + tools.
- [x] Implement HTTP+JSON adapter.
- [x] Add optional websocket streaming adapter.
- [x] Replace io.grpc server with Vert.x gRPC adapter (auth + audit preserved).
- [x] Add RBAC enforcement + audit log.

### Plugins + Auth
- [x] Implement plugin registry / SPI for Vert.x adapters.
- [x] Implement OAuth2/OIDC plugin with Azure AD.
- [x] Secure UI + API + MCP routes.
- [x] Add shared master policy (UI/API/MCP) with folder hierarchy overrides.

### Tests
- [ ] Unit tests for all services.
- [x] Integration tests spinning up Vert.x.
- [x] Golden compatibility suite vs legacy FitNesse (keep legacy tests to confirm functional equivalence).
- [x] Pipeline smoke tests.
- [ ] Coverage target and Javadoc on new/changed APIs.

### Further Vert.x replacements
- [ ] Migrate remaining file I/O to Vert.x async FS (docstore, attachments, indexing).
- [x] Move Slim/Fit execution fully onto Vert.x worker executors with backpressure + metrics + run monitor API.
- [x] Standardize config on `vertx-config` (file/env/JSON merge + reload) feeding router/handlers.
- [x] Use Vert.x OAuth2/OIDC for UI/API/MCP auth (shared guard + session/JWT).
- [x] Use Vert.x WebClient/WebSocket/EventBus bridge for outbound HTTP/WS and UI live updates.
- [x] Adopt Vert.x logging + Micrometer (`vertx-micrometer-metrics`) for metrics/Prometheus export.
- [x] Retire legacy `FitNesseServer`/`SocketService` stack once Vert.x routes cover all endpoints.
- [ ] Replace legacy HTTP socket calls (`ResponseParser`, `WikiImporter`, `FitServer`) with Vert.x WebClient + timeouts.
- [ ] Consolidate ad-hoc thread/Timer usage into Vert.x worker executors and periodic timers (centralized lifecycle + metrics).
- [ ] Move remaining blocking file writes in responders/tests to Vert.x FileSystem APIs with worker offload where needed.
- [ ] Consolidate health/metrics endpoints behind Vert.x routes (single metrics registry + standardized response).
### Additional Vert.x replacements (proposed)
- [x] Replace direct `java.net` URL/HTTP usage with Vert.x WebClient (timeouts, retries, tracing).
- [x] Replace direct `java.io`/`java.nio` file access with Vert.x FileSystem + `executeBlocking` where needed.
- [x] Replace `Timer`/`ScheduledExecutorService` usage with Vert.x timers and periodic schedules.
- [x] Replace raw `Thread`/`ThreadPoolExecutor` usage with Vert.x worker pools and `Context` propagation.
- [x] Replace `Socket`/`ServerSocket` usage with Vert.x NetClient/NetServer (if any remain).
- [ ] Replace `WatchService` file watchers with Vert.x fileSystem watch or scheduled scans (configurable).
- [x] Replace ad-hoc JSON parsing (Jackson/Gson direct usage) with Vert.x `JsonObject`/`JsonArray` where appropriate.
- [x] Replace direct `CompletableFuture` chains in request paths with Vert.x `Future`/`Promise` for uniform error handling.
#### Concrete targets (from scan)
- [ ] Retire legacy socket-based HTTP responders (`fitnesse.http.*`, `fitnesse.FitNesseExpediter`, `fitnesse.FitNesseServer`) in favor of Vert.x Router + WebClient for any remaining internal calls.
- [ ] Replace `fitnesse.http.ResponseParser` socket usage with Vert.x WebClient (timeouts + failure handling).
- [ ] Replace `fitnesse.http.RequestBuilder`/`Request` IO buffering with Vert.x `Buffer` and `HttpServerRequest` handling.
- [ ] Replace `fitnesse.slim.SlimStreamReader` socket IO with Vert.x NetClient (worker offload where blocking).
- [ ] Replace file utilities (`util.FileUtil`, `fitnesse.components.ContentBuffer`, update helpers) with Vert.x FileSystem + worker executor where needed.
- [ ] Replace `fitnesse.responders.ShutdownResponder` thread usage with Vert.x timer/executeBlocking shutdown flow.
- [ ] Replace `fitnesse.responders.WikiImporter*` direct HTTP/IO with Vert.x WebClient + Buffer.
- [ ] Replace `fitnesse.responders.RssResponder` host lookup/network calls with Vert.x DNS/client APIs (or cached async lookup).
- [ ] Replace `fitnesse.components.PluginsClassLoaderFactory` URLClassLoader path handling with Vert.x FileSystem where IO is required (keep URLClassLoader only for class loading).

### Feature enhancements (proposed)
- [x] Native inline PlantUML + Mermaid rendering (replace outdated PlantUML plugin; secure server-side rendering + cached assets).
- [x] One-click table commentary (hover action to wrap selected table with `-|comment|` in-page editor).
- [x] Template management UX (browse/create/edit templates with validation and preview).

---

## 15) Repo Artifacts to Add

- `roadmap.md` (this file)
- `docs/architecture.md`
- `docs/security.md`
- `docs/ci.md`
- `docs/migration.md`
- `docs/logging.md`
- `.github/workflows/fitnesse.yml`
- `.gitlab-ci.yml` (or `ci/gitlab-fitnesse.yml` include)
- `docker/Dockerfile`
- `examples/FitNesseRoot/` (small sample)
- `examples/ci/` (sample pipelines)

---

## 16) Key Decisions (defaults)
- Default DocStore: `git-native` (recommended)
- Store AI history in repo: `/.fitnesse/ai/` (append-only, committmied)
- Default CI outputs: `JUnit XML + HTML + summary.json`
- Default MCP: HTTP+JSON, with optional websocket streaming
- Default UI theme: modern CSS utility (Tailwind) while preserving functional equivalence

---

## 17) Non-goals (for first release)
- Perfect HTML byte-for-byte parity with legacy (aim for functional equivalence + comparable results).
- Full semantic search / embeddings (optional phase 2).
- Full write access from MCP by default (start read-only + gated safe actions).

## 18) Model versioning + audit schemas

### 16.1 Audit log schema (append-only)
Fields:
- `eventId` (uuid)
- `timestamp` (iso8601)
- `actor` (user/service)
- `action` (page.write, page.read, test.run, ai.call, mcp.tool)
- `resource` (path/id)
- `correlationId` (trace across systems)
- `version` (schema version)
- `payload` (json object)

### 16.2 API response versioning
- Version via URL prefix (`/api/v1/...`) and response header `X-Api-Version`.
- Include `schemaVersion` in response body for evolvable clients.
- Deprecation policy documented with sunset headers.

### 16.3 Model versioning strategy
- Each persisted model includes `schemaVersion`.
- Provide migrators for AI history, audit log, and DocStore metadata.
