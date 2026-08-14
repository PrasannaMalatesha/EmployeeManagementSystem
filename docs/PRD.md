# Employee Management System — Product & Engineering Requirements

**Version:** 2.0 — supersedes all earlier EMS docs
**Architecture:** Modular monolith (not microservices)
**Audience:** This document is the complete build spec.

---

## 0. How to use this document

This is a single, self-contained specification. It contains:

| Part | Contents |
|---|---|
| **Part I** | Product — problem, users, scope, functional requirements |
| **Part II** | Architecture — modules, schema, cross-cutting concerns |
| **Part III** | Execution flows — traced request lifecycles with rationale |
| **Part IV** | Engineering standards — testing, quality gates, definition of done |
| **Part V** | Living documentation the build must produce and maintain |
| **Part VI** | Milestones, risks, open decisions |

**Instruction to the implementing agent:** Part V is a hard requirement, not documentation nicety. Two documents (`docs/EXECUTION-FLOWS.md` and `docs/BUG-JOURNAL.md`) must be created in the repository and updated as part of the same commit as the code they describe. A milestone is not complete if those documents are stale.

---

## 1. Technology stack — pinned

| Layer | Technology | Version | Notes |
|---|---|---|---|
| Language | Java | **21 (LTS)** | Records, pattern matching, virtual threads |
| Framework | Spring Boot | **4.1.x** | Current stable. 3.5 EOL'd 2026-06-30 — do not use 3.x |
| Core | Spring Framework | 7.0.x | Transitive |
| Security | Spring Security | 7.0.x | Transitive |
| ORM | Hibernate / Spring Data JPA | 7.x | Transitive |
| Database | PostgreSQL | **17** | |
| Migrations | Flyway | 11.x | `ddl-auto: validate`, never `update` |
| Mapping | MapStruct | 1.6+ | Compile-time, no reflection |
| Cache | Caffeine | 3.x | Local. Redis only if genuinely needed |
| Rate limiting | Bucket4j | 8.x | In-process is correct for a monolith |
| Object storage | MinIO (local) / S3 (prod) | — | Files never in Postgres |
| Testing | JUnit 5, Mockito, Testcontainers, ArchUnit | current | Testcontainers, **not H2** |
| API docs | springdoc-openapi | 2.x | Swagger UI at `/swagger-ui.html` |
| Observability | Actuator + Micrometer | transitive | Prometheus endpoint enabled |
| Build | Maven | 3.9+ | Multi-module |
| Frontend | React | **19** | TypeScript, strict mode |
| Bundler | Vite | 6.x | |
| Server state | TanStack Query | v5 | |
| Client state | Zustand | 5.x | Auth + UI only |
| Routing | React Router | 7.x | |
| Forms | React Hook Form + Zod | current | |
| Tables | TanStack Table | v8 | |
| Charts | Recharts | 2.x | |
| Styling | Tailwind CSS + shadcn/ui | 4.x / current | Theme must be overridden — see §12 |
| Node | Node.js | 22 LTS | |

**Version policy:** no dependency upgrades mid-milestone. Renovate/Dependabot PRs are triaged weekly, merged only with CI green.

---

# PART I — PRODUCT

## 2. Problem & goals

### 2.1 Problem

Companies of 50–500 employees run HR on spreadsheets, email threads, and shared drives. Leave requests live in inboxes, org structure lives in someone's head, and there is no reliable answer to "who approved this change, and when?" — which becomes a compliance problem the first time someone asks.

### 2.2 Product goal

A single system of record for employees, org structure, leave, attendance, and employment history — with role-appropriate access and a complete audit trail.

### 2.3 Project goals

This is a portfolio piece and a learning vehicle. Both matter, and they inform requirements that a pure product spec wouldn't contain:

1. **Demonstrate backend architecture judgment** — layering, module boundaries, transactional design, rate limiting, caching, auditing, observability
2. **Demonstrate polished frontend craft** — this must not look like a Bootstrap admin template
3. **Demonstrate engineering process** — documented decisions, a real bug journal, meaningful tests

### 2.4 Why modular monolith, not microservices

**This is a deliberate architectural decision and must be stated in the README.**

Microservices solve *organizational* problems: independent deploys for independent teams, independent scaling for asymmetric load. This system has one developer and no load. Splitting it would mean approving a leave request spans two services — turning one `@Transactional` into a saga with compensating actions, solving a hard distributed-systems problem that doesn't exist here.

The correct heuristic: **transactional consistency boundaries define service boundaries.** Leave status and leave balance must be consistent, so they live together. Applied honestly to this domain, that heuristic produces one service.

What the monolith preserves: strict module boundaries, enforced mechanically by ArchUnit, so that extraction remains *possible* if it ever becomes *necessary*. The README should name the two modules that would extract most cleanly (`notification`, `document`) and say why.

**Interview framing:** "I built this as a modular monolith because at this scale microservices would add distributed-systems failure modes without solving any problem I had. The module boundaries are enforced in tests, so extraction is a folder move if load ever justifies it."

### 2.5 Success criteria

**Product**
- A manager approves a leave request in under 15 seconds from landing on the dashboard
- Zero authorization escapes — verified by the role × endpoint × resource test matrix
- Every employment change attributable to an actor with a timestamp

**Portfolio**
- Deployed, publicly reachable, read-only demo login, ~40 seeded employees with realistic history
- `docker compose up` gives a fully working local stack in one command
- Swagger UI live and linked from the README
- Lighthouse accessibility ≥ 95
- `docs/EXECUTION-FLOWS.md` and `docs/BUG-JOURNAL.md` present and current

---

## 3. Users & permissions

| Persona | Frequency | Primary jobs |
|---|---|---|
| **Priya — HR Manager** | Daily | Onboard hires, maintain records, configure leave policy, pull reports |
| **Marcus — Engineering Manager** | 2–3×/week | Approve leave for his team, see who's out, review his org branch |
| **Sam — Employee** | 2–3×/month | Request leave, check balance, find a colleague, upload a document |
| **Dana — Admin/IT** | Rarely | Manage accounts and roles, investigate audit trail |

**Design consequence:** Marcus and Sam are the volume users but the least engaged — they open the app to do exactly one thing and leave. Their flows get the polish budget. Priya tolerates information density; they do not.

### Permission matrix

| Capability | ADMIN | HR | MANAGER | EMPLOYEE |
|---|:-:|:-:|:-:|:-:|
| View public directory | ✓ | ✓ | ✓ | ✓ |
| View full employee record | ✓ | ✓ | own subtree | own only |
| Create / edit employee | ✓ | ✓ | — | limited own fields |
| Terminate employee | ✓ | ✓ | — | — |
| View compensation | ✓ | ✓ | — | own only |
| Approve leave | ✓ | ✓ | own subtree | — |
| Configure leave policy | ✓ | ✓ | — | — |
| Approve timesheets | ✓ | ✓ | own subtree | — |
| Manage users & roles | ✓ | — | — | — |
| View audit log | ✓ | ✓ | — | — |

"Own subtree" means the **recursive** reporting hierarchy, not direct reports only.

---

## 4. Scope

### In scope — v1

Auth & sessions · roles/permissions · employee records & directory · departments, positions, hierarchy · temporal employment history · leave management with approval workflow · attendance & timesheets · document storage · audit log · notifications (in-app + email) · analytics dashboard · interactive org chart.

### Out of scope — v1

| Excluded | Why |
|---|---|
| **Payroll calculation & tax** | Regulatory tarpit, jurisdiction-specific, no architectural interest. Store compensation *records*; never compute payslips |
| **Performance reviews** | v2. Adds surface area, teaches nothing new |
| **Recruitment / ATS** | Different product |
| **Multi-tenancy** | v2 |
| **SSO / SAML** | Hand-rolled auth teaches more here |
| **Native mobile** | Responsive web only |
| **Benefits administration** | Jurisdiction-specific |

Keep this table in the README. Naming what you deliberately didn't build is a requirements-maturity signal.

---

## 5. Functional requirements

Format: **FR-x.y** · story · acceptance criteria written to be directly translatable into tests.

### 5.1 Authentication

**FR-1.1 — Login**
- Valid credentials → access token (JWT, 15 min) + refresh token (opaque, 7 days) in an HttpOnly, Secure, SameSite=Strict cookie scoped to `Path=/api/v1/auth`
- Invalid credentials → generic "invalid email or password". Never reveal which was wrong
- **Response time statistically indistinguishable** between unknown-email and wrong-password (run a dummy BCrypt hash on the miss)
- 5 failed attempts → 15-minute lockout, user notified
- Rate limited: 5 attempts / 15 min, per IP **and** per account

**FR-1.2 — Refresh with rotation**
- Refresh issues a new access token and rotates the refresh token
- Old token revoked, linked via `replaced_by` (builds the family chain)
- Presenting an **already-revoked** token → revoke the entire family, force re-login, log a security event
- Frontend single-flights refresh: N concurrent 401s produce exactly one server call

**FR-1.3 — Password reset** — single-use token, 1h TTL, identical response whether or not the email exists. Successful reset revokes all refresh tokens.

**FR-1.4 — Logout** — revokes current refresh token and clears the cookie. "Sign out everywhere" revokes all families.

### 5.2 Employee records

**FR-2.1 — Directory**
- Paginated (default 20, **hard server cap 100**), sortable, filterable by department/status/location
- Search across name, email, employee number, title. Debounced. p95 ≤ 300ms
- Results scoped to caller permissions; an EMPLOYEE sees public fields only
- Empty state offers "Add your first employee" / "Import from CSV"

**FR-2.2 — Employee profile**
- Tabs: Overview · Employment · Leave · Attendance · Documents
- Field-level visibility by role — compensation hidden from all but HR/ADMIN/self
- Tabs load their own data; a failure in one degrades that tab only

**FR-2.3 — Create employee**
- Required: first name, last name, work email, hire date, department, position, employment type
- Work email and employee number unique — violation returns a **field-level 400**, never a 500
- Manager assignment must reject any change creating a **cycle** in the hierarchy
- Publishes `EmployeeHired` → seeds leave balances, sends welcome email (both after commit)
- Optionally provisions a user account with a set-password invite

**FR-2.4 — Update employee**
- Changes to position / department / manager / salary **insert a new `employment_history` row** and close the previous with `effective_to`. Never mutate history in place
- Every change writes an audit entry: actor, field, before, after, timestamp, IP, traceId

**FR-2.5 — Terminate employee**
- Typed-confirmation dialog stating exactly what happens to access and records
- Sets status TERMINATED + termination date. **Never deletes the row** — retention obligations
- Publishes `EmployeeTerminated` → disable user account, cancel pending leave, close open timesheets

**FR-2.6 — Departments & positions** — departments nest; positions belong to a department. A department with active employees cannot be deleted; offer reassign-then-delete.

### 5.3 Org chart

**FR-3.1**
- Full hierarchy from a **recursive CTE** (`WITH RECURSIVE`)
- Pan, zoom, collapse/expand, search-and-focus
- Node cards: photo, name, title, department, direct-report count
- 500 nodes without jank — virtualize or lazily expand
- **Cycle guard**: a cycle in data must be detected and reported, never rendered into an infinite loop

**This is the signature UI element and receives disproportionate polish.**

### 5.4 Leave management

**FR-4.1 — Leave types & policy** (HR/ADMIN) — name, days/year, accrual method (annual grant / monthly accrual), carry-over limit, approval required, paid/unpaid. Editing policy does **not** retroactively alter already-granted balances.

**FR-4.2 — Request leave**
- Date range picker showing remaining balance live; excludes weekends and configured holidays
- Rejects: overlap with existing pending/approved request; insufficient balance; closed period
- Half-day support (AM/PM)
- Publishes `LeaveRequested` → notifies approving manager

**FR-4.3 — Approve / reject**
- Manager sees a queue from their subtree with coverage context ("2 others out that week")
- **Bulk select and approve**
- Status change and balance decrement in **one transaction** — no state where one applied and the other didn't
- Concurrent approvals: exactly one succeeds; the other gets a clear "already decided" message
- `used_days <= entitled_days` enforced by a **database CHECK constraint**, not application logic alone
- Rejection requires a reason
- Publishes `LeaveApproved` / `LeaveRejected` after commit

**FR-4.4 — Team calendar** — month view of who's out, filterable, highlighting coverage gaps (>30% of a team out same day).

**FR-4.5 — Balances** — entitled / used / pending / remaining per type. Year-end job carries over up to the configured limit.

### 5.5 Attendance & timesheets

**FR-5.1 — Attendance** — clock in/out or manual entry. One record per employee per day (unique constraint). Blocked on approved-leave days. Flags <4h and >12h for review.

**FR-5.2 — Timesheets** — weekly period, submit for approval, manager approves/rejects with comment. Approved timesheets immutable without an explicit, audited reopen.

### 5.6 Documents

**FR-6.1 — Upload**
- **Presigned PUT direct to object storage — bytes never transit the API**
- Type validated by **magic bytes**, not extension. 25MB cap
- Server re-verifies actual size and content type from storage metadata after upload
- Filename sanitized against path traversal
- Visibility by document type — HR-only categories invisible to the employee

**FR-6.2 — Download** — presigned GET, 5-minute TTL. Every access audited.

### 5.7 Notifications

**FR-7.1** — In-app centre with unread count. Triggered by domain events, never by direct calls from the producing service.

**FR-7.2** — Email for: welcome, leave requested/decided, timesheet submitted, password reset. Per-user preferences. Failed sends retried with backoff, then logged **with an alert**.

### 5.8 Audit log

**FR-8.1**
- Covers every create/update/delete on employee, employment history, leave decisions, role changes, document access
- Records: actor, entity type, entity id, action, changed fields (before/after as JSONB), IP, timestamp, traceId
- Searchable by actor, entity, date range. **Append-only — no UI path to edit or delete**
- CSV export

### 5.9 Dashboard

**FR-9.1 — Role-aware landing page leading with actionable items, not ambient metrics.**

- *Manager:* "3 leave requests awaiting you" · "2 timesheets to approve" · who's out this week
- *HR:* pending onboarding · headcount · new hires this month · attrition trend
- *Employee:* leave balance · pending requests · team out this week
- *Admin:* system health · recent audit activity · failed logins

**FR-9.2 — Analytics** (HR/ADMIN) — headcount trend, attrition rate, department distribution, leave utilization, average tenure. Date-range filterable. CSV export.

---

# PART II — ARCHITECTURE

## 6. Module structure

One Spring Boot application, one deployable JAR, one PostgreSQL database. **Feature-first packages, not layer-first.**

```
com.malatesha.ems
├── EmsApplication.java
├── config/            # security, cors, openapi, jackson, async, cache, ratelimit
├── common/            # BaseAuditableEntity, exceptions, ProblemDetail handler,
│                      # pagination utils, domain event base types
├── security/          # JWT filter, UserDetailsService, PermissionEvaluator,
│                      # rate limit filter, AuditorAware
├── employee/          # controller, service, repository, entity, dto/, mapper/, event/
├── department/
├── leave/
├── attendance/
├── document/
├── notification/
└── audit/
```

Layer-first packages (`controllers/`, `services/`, `repositories/`) stop being navigable around fifteen entities and make every change touch three distant folders. Feature-first keeps related code together and makes extraction to a service — should it ever be needed — a folder move.

### Module boundary rules (ArchUnit-enforced, build fails on violation)

1. Controllers must not import entities. Entities never leave the service layer; controllers speak DTOs.
2. Controllers must not import repositories. Always go through a service.
3. A module's internal packages (`entity`, `repository`) are not importable by other modules. Cross-module access goes through the module's public service interface or via events.
4. `@Transactional` appears on services, never on repositories or controllers.
5. No cyclic dependencies between modules.

**Rule 3 is the one that matters.** It's what makes this a modular monolith rather than a big ball of mud with feature-shaped folders.

### Cross-module communication

- **Read another module's data** → call its public service interface. Direct, synchronous, in-process. No network, no serialization, no failure mode.
- **React to something happening** → Spring `ApplicationEvent` + `@TransactionalEventListener(phase = AFTER_COMMIT)`.

The `AFTER_COMMIT` phase is important: it guarantees a welcome email is never sent for a transaction that rolled back. This is the in-process equivalent of the outbox pattern's guarantee, and it costs one annotation instead of a table, a relay, and a message broker.

---

## 7. Database schema

```sql
-- identity
users               (id, email, password_hash, status, employee_id, failed_attempts,
                     locked_until, created_at, updated_at)
roles               (id, name, description)
permissions         (id, name)                    -- LEAVE_APPROVE, EMPLOYEE_WRITE, ...
user_roles          (user_id, role_id)
role_permissions    (role_id, permission_id)
refresh_tokens      (id, user_id, token_hash, expires_at, revoked_at, replaced_by)

-- org
employees           (id, employee_number, first_name, last_name, work_email, personal_email,
                     phone, hire_date, termination_date, status, department_id, position_id,
                     manager_id, location, employment_type, created_at, updated_at,
                     created_by, updated_by)
departments         (id, name, code, parent_department_id, head_employee_id)
positions           (id, title, level, department_id)
employment_history  (id, employee_id, position_id, department_id, manager_id,
                     salary_amount NUMERIC(19,4), salary_currency CHAR(3),
                     effective_from, effective_to, change_reason)

-- leave
leave_types         (id, name, days_per_year, accrual_method, carry_over_limit,
                     requires_approval, is_paid)
leave_balances      (id, employee_id, leave_type_id, year, entitled_days, used_days,
                     carried_over,
                     CONSTRAINT chk_balance CHECK (used_days <= entitled_days + carried_over))
leave_requests      (id, employee_id, leave_type_id, start_date, end_date, days_count,
                     half_day_start, half_day_end, reason, status, approver_id,
                     decided_at, decision_note, version)

-- time
attendance_records  (id, employee_id, work_date, clock_in, clock_out, hours_worked,
                     source, note,
                     UNIQUE (employee_id, work_date))
timesheets          (id, employee_id, period_start, period_end, status, submitted_at,
                     approved_by, approved_at)

-- documents
documents           (id, employee_id, doc_type, file_name, storage_key, content_type,
                     size_bytes, visibility, uploaded_by, uploaded_at)

-- system
notifications       (id, user_id, type, payload JSONB, read_at, created_at)
audit_log           (id, actor_user_id, entity_type, entity_id, action,
                     changed_fields JSONB, ip_address, trace_id, occurred_at)
holidays            (id, calendar_year, holiday_date, name)
```

### Schema decisions to be able to defend

**`users` and `employees` are separate tables.** Not every employee has a login (contractors, pre-start hires), and a login can be disabled without touching the employment record. Conflating them is the most common mistake in EMS designs.

**Soft delete via `status`, never row deletion.** HR records carry retention obligations. `ACTIVE / ON_LEAVE / SUSPENDED / TERMINATED`.

**`employment_history` is temporal.** `employees.position_id` is a denormalized *current* pointer for fast reads; `employment_history` is the truth. Both updated in one transaction.

**Money as `NUMERIC(19,4)` → `BigDecimal`.** Binary floating point cannot represent 0.1. Salary arithmetic in `double` produces cents-level drift that compounds and cannot be reconciled. Hard rule, no exceptions.

**Dates as `DATE`, not `TIMESTAMP`, where the domain concept is a calendar day.** Leave spanning a DST boundary computed from timestamps yields 4.96 days.

**Org hierarchy via self-referencing `manager_id`,** queried with `WITH RECURSIVE`. Same mechanism serves manager data-scoping. Requires a cycle guard in the application *and* ideally a constraint.

**`version` column on `leave_requests`** for optimistic locking (see Flow 4).

### Required indexes

`employees(department_id)` · `employees(manager_id)` · `employees(status)` · `leave_requests(employee_id, status)` · `leave_balances(employee_id, leave_type_id, year)` unique · `attendance_records(employee_id, work_date)` unique · `audit_log(entity_type, entity_id)` · `audit_log(occurred_at DESC)` · a `pg_trgm` GIN index on employee name/email for directory search.

---

## 8. Cross-cutting concerns

**Validation.** Jakarta Bean Validation on request DTOs (`@NotBlank`, `@Email`, `@Past`), plus custom constraints for domain rules (`@ValidLeaveDateRange`). Validate at the boundary; services assume valid input.

**Error handling.** One `@RestControllerAdvice` returning **RFC 9457 `ProblemDetail`** — built into Spring, don't hand-roll an envelope. Every error carries `traceId`. A `DataIntegrityViolationException` must be translated to a field-level 400, never surface as a 500. Stack traces never reach the client.

**Rate limiting.** Bucket4j, **in-process** — correct for a single-instance monolith, and being able to say *"Redis would be required the moment I run a second instance, and here's why"* is the interview answer. Auth endpoints: 5 / 15 min per IP and per account. General API: 100 / min per user. Returns 429 with `Retry-After` and `X-RateLimit-Remaining`.

**Caching.** Caffeine via `@Cacheable` on genuinely hot, rarely-changing reads: department tree, leave type config, permission sets. `@CacheEvict` on the corresponding write paths. Never cache user-scoped data without the user in the cache key.

**Auditing.** Two layers:
- Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedBy`) on `BaseAuditableEntity`, with an `AuditorAware` bean reading the `SecurityContext`
- A domain `audit_log` for HR-meaningful changes, written via entity listeners or domain events

**Async & events.** Domain events published in-process; handled with `@TransactionalEventListener(AFTER_COMMIT)`. Email dispatch on `@Async` with an **explicitly configured, bounded** thread pool — never the default.

**Scheduling.** `@Scheduled` for leave accrual, carry-over at year end, anniversary notifications. Note in the README that multi-instance deployment would require ShedLock.

**Pagination.** `Pageable` everywhere with a hard max page size. `?size=1000000` must be impossible. Any unbounded collection endpoint is a latent outage.

**N+1 prevention.** Associations `LAZY` by default; `@EntityGraph` or `join fetch` on known access paths. `hibernate.generate_statistics` enabled in dev. **Query-count assertions in integration tests** so a regression fails CI rather than production.

**Security.** BCrypt cost 12 · `@EnableMethodSecurity` with a custom `PermissionEvaluator` for subtree checks · explicit per-origin CORS, never `*` with credentials · CSP, HSTS, X-Content-Type-Options.

**Observability.** Actuator (`/health/liveness`, `/health/readiness`, `/metrics`, `/prometheus`) · Micrometer · structured JSON logging with an MDC `traceId` set by a filter and echoed in every response header.

**Configuration.** Profiles (`dev`, `test`, `prod`) · secrets from environment variables only · `@ConfigurationProperties` records instead of scattered `@Value`.

---

# PART III — EXECUTION FLOWS

These belong in `docs/EXECUTION-FLOWS.md` and must be extended as features land. The **▸ Why** annotations are the interview answers — they explain the mechanism, not the syntax.

## Flow 1 — Login

```
POST /api/v1/auth/login { email, password }
  ▼
RateLimitFilter — Bucket4j, key = "login:" + clientIp
  └─ bucket empty → 429 + Retry-After, request never reaches the controller
  ▼
AuthController.login()  — @Valid runs Bean Validation BEFORE any logic
  ▼
AuthService.login()
  1. Load user by email
  2. If absent → run a dummy BCrypt hash anyway, then fail generically
  3. Check locked_until
  4. passwordEncoder.matches(raw, hash)     ← BCrypt cost 12
  5. Failure → increment failed_attempts, lock at 5, publish event, throw
  6. Success → reset counter
  7. Load roles + permissions
  8. Mint access JWT (15 min; claims: sub, roles, permissions, employeeId)
  9. Mint refresh token (opaque, 256-bit random, 7 days)
 10. Persist SHA-256 hash of the refresh token
  ▼
200 { accessToken, expiresIn: 900, user }
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth
  ▼
Frontend: access token → memory (Zustand). Refresh token → cookie JS cannot read.
```

**▸ Why the dummy hash (step 2).** BCrypt costs ~200ms. Skipping it on a missing user returns in 5ms while a wrong password takes 205ms — a **timing oracle** that lets an attacker enumerate valid company emails. Equalizing the cost closes it. Small detail; reliably impresses, because it shows you think about side channels.

**▸ Why lockout at 5, not 3.** 3 generates support load from ordinary typos. Note that lockout is itself a **DoS vector** — an attacker can deliberately lock a known account. Mitigated by time-boxing it and putting per-IP rate limiting in front.

**▸ Why a JWT access token but an opaque refresh token.** Opposite requirements. The access token is checked on every request and must be verifiable **without a database call** — that's the entire value of stateless auth. The refresh token is used once per 15 minutes and *must* be revocable, so a DB lookup is affordable and desirable. Using a JWT for both gives you a long-lived credential you cannot revoke.

**▸ Why hash the stored refresh token.** Same reasoning as passwords: if the database leaks, the tokens in it are inert.

**▸ Why `Path=/api/v1/auth`.** The refresh token is needed by exactly one endpoint. Scoping it keeps it off the other ~200 requests the app makes, shrinking exposure.

**▸ Why the access token lives in memory, not localStorage.** localStorage is readable by any JavaScript on the page — including XSS payloads and compromised npm dependencies. In-memory means the token dies on page refresh (the cookie silently re-mints it) and cannot be persistently harvested.

## Flow 2 — Authorized read

```
GET /api/v1/employees?page=0&size=20&department=eng
Authorization: Bearer <access token>
  ▼
TraceIdFilter        → generate traceId, put in MDC + response header
JwtAuthenticationFilter → validate signature/exp/iss, build Authentication, set SecurityContext
RateLimitFilter      → key = "user:" + sub, 100/min
  ▼
EmployeeController.list()
  └─ @PreAuthorize("hasAuthority('EMPLOYEE_READ')")   ← coarse: may you call this at all?
  ▼
EmployeeService.list(pageable, filter, currentUser)
  ├─ Page size capped server-side at 100
  ├─ Data scoping:                                     ← fine: WHICH rows?
  │    ADMIN/HR  → unrestricted
  │    MANAGER   → recursive CTE for reporting subtree, restrict to those ids
  │    EMPLOYEE  → own record + public directory fields only
  └─ JPA Specification = filter predicates AND scope predicate
  ▼
Repository → PostgreSQL — one query, @EntityGraph fetches department + position
  ▼
MapStruct → EmployeeSummaryDto        ← entity never crosses the controller boundary
  ▼
200 Page<EmployeeSummaryDto>, X-Trace-Id echoed
```

**▸ Why two authorization mechanisms.** They answer different questions. `@PreAuthorize` answers *"may this user invoke this operation?"* — static, role-shaped. Data scoping answers *"which rows may this specific user see?"* — dynamic, data-shaped. Conflating them is exactly how a manager ends up able to `GET /employees/{ceoId}`. Most tutorial projects implement only the first, which is why this is worth calling out.

**▸ Why scoping lives in the service layer, never the frontend.** The frontend is an untrusted client. Anything filtered in React is one `curl` away.

**▸ Why cap page size server-side.** Without it, `?size=1000000` is a free denial of service — and it *will* eventually be requested, by a script or a misconfigured export button.

**▸ Why `@EntityGraph`.** Lazy associations plus a mapper looping over rows = one query per row. 20 employees → 41 queries. The pathology is invisible at the call site; nothing in the code looks like a query. Detect with `generate_statistics`; prevent with query-count assertions in tests.

## Flow 3 — Create employee (transaction + events)

```
POST /api/v1/employees { ... }
  ▼
EmployeeService.create()          — ONE TRANSACTION
  1. Bean Validation already passed; run domain checks
  2. Unique employee_number, unique work_email
  3. Manager exists AND assignment creates no cycle in the hierarchy
  4. INSERT employees
  5. INSERT employment_history (effective_from = hire_date, effective_to = NULL)
  6. INSERT audit_log
  7. publisher.publishEvent(new EmployeeHiredEvent(...))
  8. COMMIT
  ▼
@TransactionalEventListener(phase = AFTER_COMMIT)
  ├─ LeaveBalanceInitializer  → seed balances for the current year from leave_types
  ├─ NotificationHandler      → in-app notification
  └─ EmailHandler (@Async)    → welcome email
  ▼
201 Created
```

**▸ Why `AFTER_COMMIT` and not an inline call.** If the notification were sent inline and the transaction subsequently rolled back, you'd have emailed a welcome to an employee who does not exist. `AFTER_COMMIT` guarantees handlers run only against committed state.

**▸ Why this is where microservices would have cost you.** Across services, this same guarantee requires the **transactional outbox pattern** — you cannot atomically commit to Postgres and publish to Kafka, so you write the event to an outbox table in the same transaction and relay it separately. That's a table, a relay, a broker, idempotent consumers, and a dedup table. In a monolith, one annotation buys the same guarantee. **Be able to explain the outbox pattern and why you didn't need it** — knowing the expensive solution and why it's unnecessary here is a stronger answer than having implemented it.

**▸ Why the cycle check.** `WITH RECURSIVE` against a cycle in `manager_id` (A → B → A) runs forever. Guard at write time; ideally also constrain in the database.

**▸ Why audit is written inside the transaction.** An audit entry for a change that rolled back is a lie. Same transaction, same fate.

## Flow 4 — Leave approval (concurrency)

```
POST /api/v1/leave-requests/{id}/approve
  ▼
@PreAuthorize("hasAuthority('LEAVE_APPROVE')")
Scope check: is request.employeeId within the approver's subtree?
  ▼
LeaveApprovalService.approve()    — ONE TRANSACTION
  1. Load request (optimistic lock via @Version)
  2. State machine guard: only PENDING → APPROVED is legal
  3. Re-check balance — it may have changed since the request was filed
  4. UPDATE leave_requests SET status = APPROVED, approver_id, decided_at
  5. UPDATE leave_balances SET used_days = used_days + n
  6. INSERT audit_log
  7. publishEvent(LeaveApprovedEvent)
  8. COMMIT — @Version mismatch → OptimisticLockException → 409 "already decided"
  ▼
AFTER_COMMIT → notify employee · block attendance entry on those dates
```

**▸ Why status and balance share one transaction.** They must be consistent — an approved request whose balance never decremented is a correctness bug users notice immediately. In a monolith this is one `@Transactional`, full ACID, no thought required.

**▸ Why this drove the architecture decision.** Split balances into a separate service and this becomes a **saga**: approve → emit → decrement → emit, plus a compensating action to revert the approval if the decrement fails, plus a window where the system is visibly inconsistent, plus compensations that can themselves fail. The heuristic — *transactional consistency boundaries define service boundaries* — says keep them together. Applied honestly, it says keep the whole thing together.

**▸ Why optimistic locking over pessimistic.** Two managers clicking approve simultaneously would both read PENDING, both pass the guard, both decrement — a lost update. `@Version` makes the second commit fail cleanly with a 409. Pessimistic (`PESSIMISTIC_WRITE`) also works and is simpler to reason about, but holds a row lock for the transaction duration and scales worse. Approvals are low-frequency either way. **Be able to argue both sides — this is a very common interview question.**

**▸ Why a database CHECK constraint on top of application logic.** Application checks can be bypassed by any future code path, a migration script, or a manual fix. `CHECK (used_days <= entitled_days + carried_over)` is enforced for everyone, forever. **Making an invalid state unrepresentable beats testing for it.**

**▸ Why an explicit state machine.** Legal transitions are PENDING→APPROVED, PENDING→REJECTED, PENDING→CANCELLED, APPROVED→CANCELLED. Nothing else. Encoding this as a guarded transition rather than a free `setStatus()` eliminates a whole category of bug where an API call moves an entity into a nonsensical state.

## Flow 5 — Refresh with rotation

```
Access token expires → API returns 401
  ▼
Frontend interceptor
  └─ single-flight: first 401 starts the refresh and stores the promise;
     concurrent 401s await the SAME promise, then replay
  ▼
POST /api/v1/auth/refresh   (cookie sent automatically)
  ▼
1. Hash presented token, look up
2. Absent / expired            → 401
3. Already revoked             → ⚠ REUSE DETECTED
   └─ revoke the ENTIRE family, force re-login, log security event
4. Valid → issue new access + new refresh; revoke old, set replaced_by
```

**▸ Why rotation, and why reuse detection is the point.** A refresh token valid for 7 days of repeated use is a 7-day credential; if stolen, you cannot tell. With rotation each token is single-use, so when the legitimate user refreshes after an attacker has, the attacker's token is already revoked — and the reuse attempt is the detection signal. Revoking the family costs the real user one re-login and denies the attacker everything.

**▸ Why single-flight is mandatory, not an optimization.** Without it, six parallel requests 401 simultaneously, all fire refresh, five present an already-rotated token, reuse detection fires, and the user is logged out **during entirely normal use**. This bug is guaranteed if you don't design for it. When a security control fires during normal operation, suspect the client — never weaken the control.

## Flow 6 — Document upload

```
1. POST /api/v1/documents/upload-url { fileName, contentType, sizeBytes }
   → validate type + size → presigned PUT (5 min TTL) → { uploadUrl, storageKey }
2. Browser PUTs the file DIRECTLY to MinIO/S3. Never through the API.
3. POST /api/v1/documents/confirm { storageKey, ... }
   → verify the object exists; re-read ACTUAL size and content type from storage
   → validate magic bytes → INSERT documents → audit
```

**▸ Why bytes never stream through the API.** A 50MB upload through your service occupies a thread and heap for the entire transfer. Twenty concurrent uploads exhausts the pool — a trivially cheap DoS. Presigned URLs offload bytes to storage built for it; your service handles metadata in milliseconds.

**▸ Why confirm separately and re-verify.** The client is untrusted. It can claim a 1KB PDF and upload a 2GB executable. Re-reading actual size and type from storage is the only trustworthy check. Validate by **magic bytes**, not extension — extensions are user input.

**▸ Why files never live in Postgres.** Bloats the database, destroys backup/restore times, holds connections open. Databases store facts about files; object storage stores files.

---

# PART IV — ENGINEERING STANDARDS

## 9. Testing

| Level | Tooling | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito (`@MockitoBean`) | Service logic, pure functions, state machines |
| Integration | **Testcontainers + real PostgreSQL** | Repositories, transactions, constraints, recursive CTEs |
| Web | `@WebMvcTest` + MockMvc | Controller contracts, validation, status codes |
| Architecture | ArchUnit | Module boundary rules from §6 |
| Concurrency | JUnit + `CountDownLatch` | Every shared-counter mutation |
| Security | Parameterized matrix | role × endpoint × own/other/out-of-scope |
| Frontend | Vitest + React Testing Library | Components, hooks |
| E2E | Playwright | One happy path. Keep it to one — E2E is slow and flaky |

**Testcontainers, never H2.** H2 has a different SQL dialect, doesn't handle `WITH RECURSIVE` the way Postgres does, treats JSONB differently, and has different locking semantics. A suite green on H2 and broken on Postgres is worse than no suite — it manufactures false confidence.

**Mandatory adversarial tests:**
- Concurrent leave approval → exactly one succeeds
- IDOR walk: as MANAGER, `GET /employees/{id}` for someone outside the subtree → 403
- Page size `?size=1000000` → capped, not honored
- Cycle creation in `manager_id` → rejected
- Query-count assertion on the directory endpoint

## 10. Quality gates

Every PR must pass, or the build fails:

- All tests green
- ArchUnit boundary rules satisfied
- No new critical/high CVEs (OWASP dependency check)
- Flyway migration backward-compatible (see below)
- No `System.out.println`, no committed secrets

**Migration rule (expand–migrate–contract).** During a rolling deploy, version N and N−1 run simultaneously against the same schema. A migration adding `NOT NULL` in one step breaks the old instances. Three steps instead: (1) add nullable with default, deploy; (2) backfill, deploy code that writes it; (3) add `NOT NULL`, deploy. CI should reject migrations containing `NOT NULL` without a default, `DROP COLUMN`, or `RENAME` on a populated table.

## 11. Definition of done

A feature is done when: acceptance criteria pass · unit + integration tests written · security matrix extended if a new endpoint · OpenAPI annotations present · frontend handles loading/error/empty states · a11y checked (keyboard, focus, contrast) · **`docs/EXECUTION-FLOWS.md` updated if the flow is non-obvious** · **`docs/BUG-JOURNAL.md` updated if anything non-trivial broke on the way.**

---

## 12. Frontend & UI/UX requirements

Portfolio quality is a **requirement**, not a nice-to-have. The most common failure mode for this project category is looking like a 2016 admin template.

**Visual identity.** Choose a deliberate palette and a display/body type pairing. Do not ship default shadcn-on-white with a blue accent — override the theme. Two or three considered colors, a real type scale, generous whitespace, and **one signature element**. Here that's the **org chart**: smooth pan/zoom, collapsible branches, hover cards, search-and-focus. Everything else stays quiet and disciplined.

**Lead with the "so what."** A manager landing on the dashboard sees *"3 leave requests awaiting you"* before any chart. Actionable items above ambient metrics, always.

**Empty states are onboarding.** A fresh install shows "Add your first employee" or "Import from CSV" — never a blank table or zeroed charts.

**Command palette (⌘K).** Jump to any employee, any action. Small feature, disproportionate perceived-quality payoff.

**Bulk actions.** Multi-select and bulk approve on the leave queue. Shows you thought about the actual daily user, not just the demo path.

**Optimistic UI with honest rollback.** Approving leave feels instant; a server rejection reverts visibly and explains why.

**Destructive actions need friction.** Termination requires typed confirmation and states exactly what will happen to access and records.

**Partial failure degrades one panel, not the page.** If documents fail to load, the profile renders everything else with a scoped inline error.

**Accessibility as a quality floor.** WCAG 2.1 AA — keyboard navigable, visible focus rings, ARIA on data tables, AA contrast, `prefers-reduced-motion` honored. Charts need a table-view equivalent; a chart alone is not accessible. Target Lighthouse a11y ≥ 95 and say so in the README.

**Copy matters.** "Save changes," not "Submit." Errors say what went wrong and what to do next. No apologizing microcopy.

**Seed a demo tenant.** ~40 employees across 5 departments with realistic history, plus a read-only demo login on the deployed instance. **The single highest-ROI item in this document** — a recruiter will click a demo link; they will not clone your repo.

---

# PART V — LIVING DOCUMENTATION

Two documents must exist in the repository and be maintained as code lands. This is a hard requirement of every milestone.

## 13.1 `docs/EXECUTION-FLOWS.md`

Seeded from Part III. Extended whenever a non-obvious flow is implemented. Each entry: the traced sequence, then **▸ Why** annotations explaining mechanism and rejected alternatives.

Also contains **Decision Records** — short ADRs, one per non-obvious choice, written the same day the choice is made (not at the end of the milestone, when you'll have forgotten the alternatives you rejected — and the rejected alternatives are the interesting part).

```markdown
## DR-XXX — <decision>
**Context:** <what forced a choice>
**Decision:** <what was chosen>
**Why:** <mechanism, not preference>
**Alternatives rejected:** <and why> ← the most valuable line
**Consequences:** <including what got worse>
```

Seed with: modular monolith over microservices · feature-first packages · Testcontainers over H2 · Flyway over `ddl-auto: update` · optimistic over pessimistic locking · `AFTER_COMMIT` events over inline calls · in-process rate limiting over Redis · `NUMERIC` over `double` · separate `users`/`employees` · temporal `employment_history` · presigned uploads.

## 13.2 `docs/BUG-JOURNAL.md`

**Every non-trivial bug gets an entry.** Not the typos — the ones that took real investigation.

The discipline: **do not change anything until you can explain the mechanism.** "I added a sleep and it went away" is not a fix; it's a race condition you've hidden and will meet again in production at 3am.

Method: Observe (facts first, no fixing) → Reproduce (minimally, reliably) → Hypothesize (write candidates down, ranked) → Isolate (binary-search the system) → Root-cause (Five Whys until you reach an *assumption*, not a symptom) → Fix (right layer, narrowest correct change) → Prevent (test, constraint, or structural change).

```markdown
## BUG-XXX — <one-line symptom>
| Date | Severity | Phase found | Time to detect / diagnose / fix |

**Symptom** — verbatim: error text, status, traceId
**How I noticed** — be honest. "By accident" means detection is missing
**Reproduction** — minimal steps
**Hypotheses** — 1. ~~X~~ eliminated because... 2. Y confirmed by...
**Investigation** — what you did, in order, **including dead ends**
**Root cause** — the mechanism, at the level where the fix belongs
**Fix** — what changed and why this layer
**Alternatives rejected** — ← most valuable section for interviews
**Prevention** — regression test / constraint / lint rule
**Lesson** — one generalizable sentence
```

**Do not pre-fill this with bugs you didn't hit.** A fabricated war story collapses on the interviewer's second question ("what did the stack trace say?"). The wrong hypotheses are the most credible part of a real debugging story — a narrative where you guessed right immediately sounds invented, because it is.

Bugs this architecture reliably produces, so you'll recognize them: refresh stampede logging users out · N+1 on the directory endpoint · lost update on concurrent approval · `@Transactional` silently inert on a self-invoked method (Spring proxy limitation) · DST making leave 4.96 days · connection pool exhaustion from an HTTP call inside a transaction · Flyway checksum mismatch after editing an applied migration · CORS working locally and failing in staging because credentials-mode requires an explicit origin.

## 13.3 `README.md`

Must contain: one-paragraph pitch · **live demo link + read-only credentials** · architecture diagram · ERD · the "why modular monolith" paragraph from §2.4 · the out-of-scope table · `docker compose up` quickstart · Swagger link · testing strategy · links to the two docs above.

---

# PART VI — DELIVERY

## 14. Milestones

Each ends with something demonstrable and both docs current.

| M | Scope | Exit criteria |
|---|---|---|
| **M0** | Maven scaffold, Docker Compose (Postgres + MinIO), Flyway baseline, Actuator, CI, ArchUnit skeleton | `docker compose up` works; CI green |
| **M1** | Auth: users/roles/permissions, login, refresh rotation, `/me`, method security, rate limiting | Login → protected endpoint end to end |
| **M2** | Employees, departments, positions, hierarchy, directory search, data scoping, audit log | Security matrix green |
| **M3** | Leave: types, balances, request, approval, concurrency guards, events | Concurrent-approval test passes |
| **M4** | React shell: auth flow, protected routes, design system, directory table, profile | Usable without an API client |
| **M5** | Leave UI: request form, approval queue with bulk actions, team calendar | Manager approves in the UI |
| **M6** | Dashboard: role-aware landing pages, analytics, charts | Answers "what needs me?" instantly |
| **M7** | **Org chart** — the signature piece | 500 nodes, smooth, searchable |
| **M8** | Attendance, timesheets, documents | Full v1 feature set |
| **M9** | Polish: empty states, skeletons, ⌘K palette, a11y audit, copy pass | Lighthouse a11y ≥ 95 |
| **M10** | Deploy, seed demo data, README | Public demo link live |

**M0–M7 is the actual product. M8 is optional.** A finished seven-milestone app with a working demo beats a ten-milestone skeleton — especially with a second project competing for the same evenings.

## 15. Non-functional requirements

| Category | Requirement |
|---|---|
| Performance | Directory p95 < 300ms · profile < 1s · org chart (500 nodes) < 2s · search < 300ms |
| Scale | 5,000 employees, 200 concurrent users; load-tested at 2× |
| Security | OWASP Top 10 · BCrypt 12 · no secrets in source · CSP/HSTS · security matrix green |
| Privacy | Compensation and personal contact role-restricted; all access audited |
| Accessibility | WCAG 2.1 AA, Lighthouse ≥ 95 |
| Browser | Last 2 versions of Chrome/Firefox/Safari/Edge; responsive ≥ 375px |
| Observability | Every request carries a traceId, echoed in logs and response headers |
| Retention | Employment records survive termination; deletion is an explicit, audited admin action |

## 16. Risks

| Risk | L | I | Mitigation |
|---|:-:|:-:|---|
| **Scope death — 10 milestones never finish** | **H** | **H** | M0–M7 is the product. M8+ optional. Finished beats complete |
| Two projects competing for evenings | H | M | Sequence them. Don't interleave |
| Spring Boot 4.x tutorial gap (most content targets 3.x) | M | M | Read the 4.x release notes once, up front |
| UI ends up looking templated | M | H | §12 is a requirement. Budget a full milestone (M9) for polish |
| Docs go stale, defeating their purpose | M | M | Definition of done ties doc updates to the same commit |

## 17. Open decisions

1. **Employee self-service scope** — which fields can an employee edit without HR approval? *(Proposal: phone, personal email, emergency contact, address. Everything else is a request.)*
2. **Holiday calendars** — per-location holidays affect leave day counts and add real complexity. *(Proposal: single company calendar v1; per-location v2.)*
3. **Leave year boundary** — calendar year or per-employee anniversary? *(Proposal: calendar year; anniversary is materially harder.)*
4. **Manager visibility of compensation** — real companies differ. *(Proposal: no in v1 — HR/ADMIN only. Fewer ways to leak.)*
5. **Notification delivery** — immediate email per event, or daily digest? *(Proposal: immediate v1, preferences v2.)*
