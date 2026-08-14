# EMS — Execution Flows & Decision Records

**Companion to** `docs/PRD.md`.
**Purpose.** Two things live here:

1. **Execution flows** — the request lifecycle for anything non-obvious, traced end to end, with **▸ Why** annotations that explain the *mechanism* (not the syntax). The ▸ Why lines are the interview answers.
2. **Decision records (DRs)** — short ADRs written the day a non-obvious choice is made, not at the end of the milestone when the alternatives you rejected will have blurred.

**Update rule.** A milestone is not complete if this document is stale. When you implement a flow whose sequence isn't obvious from the code, add it here in the same commit. When you make an architecturally load-bearing choice, add a DR in the same commit.

---

## Contents

**Flows**
- [Flow 1 — Login](#flow-1--login)
- [Flow 2 — Authorized read](#flow-2--authorized-read)
- [Flow 3 — Create employee (transaction + events)](#flow-3--create-employee-transaction--events)
- [Flow 4 — Leave approval (concurrency)](#flow-4--leave-approval-concurrency)
- [Flow 5 — Refresh with rotation](#flow-5--refresh-with-rotation)
- [Flow 6 — Document upload](#flow-6--document-upload)

**Decision records**
- [DR template](#dr-template)
- [DR-001 — Modular monolith, not microservices](#dr-001--modular-monolith-not-microservices)
- [DR-002 — Feature-first packages, not layer-first](#dr-002--feature-first-packages-not-layer-first)
- [DR-003 — Testcontainers, never H2, for integration tests](#dr-003--testcontainers-never-h2-for-integration-tests)
- [DR-004 — Flyway migrations; `ddl-auto: validate`](#dr-004--flyway-migrations-ddl-auto-validate)
- [DR-005 — Optimistic locking on leave approval](#dr-005--optimistic-locking-on-leave-approval)
- [DR-006 — `AFTER_COMMIT` events, never inline side effects](#dr-006--after_commit-events-never-inline-side-effects)
- [DR-007 — In-process rate limiting (Bucket4j), not Redis](#dr-007--in-process-rate-limiting-bucket4j-not-redis)
- [DR-008 — `NUMERIC(19,4)` and `BigDecimal` for money](#dr-008--numeric194-and-bigdecimal-for-money)
- [DR-009 — Separate `users` and `employees` tables](#dr-009--separate-users-and-employees-tables)
- [DR-010 — Temporal `employment_history`, denormalized current pointer](#dr-010--temporal-employment_history-denormalized-current-pointer)
- [DR-011 — Presigned URLs for document upload/download](#dr-011--presigned-urls-for-document-uploaddownload)

---

# Flows

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

**▸ Why the dummy hash (step 2).** BCrypt costs ~200ms. Skipping it on a missing user returns in 5ms while a wrong password takes 205ms — a **timing oracle** that lets an attacker enumerate valid company emails. Equalizing the cost closes it.

**▸ Why lockout at 5, not 3.** 3 generates support load from ordinary typos. Note that lockout is itself a **DoS vector** — an attacker can deliberately lock a known account. Mitigated by time-boxing it and putting per-IP rate limiting in front.

**▸ Why a JWT access token but an opaque refresh token.** Opposite requirements. The access token is checked on every request and must be verifiable **without a database call** — that's the entire value of stateless auth. The refresh token is used once per 15 minutes and *must* be revocable, so a DB lookup is affordable and desirable. Using a JWT for both gives you a long-lived credential you cannot revoke.

**▸ Why hash the stored refresh token.** Same reasoning as passwords: if the database leaks, the tokens in it are inert.

**▸ Why `Path=/api/v1/auth`.** The refresh token is needed by exactly one endpoint. Scoping it keeps it off the other ~200 requests the app makes, shrinking exposure.

**▸ Why the access token lives in memory, not localStorage.** localStorage is readable by any JavaScript on the page — including XSS payloads and compromised npm dependencies. In-memory means the token dies on page refresh (the cookie silently re-mints it) and cannot be persistently harvested.

---

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

---

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

---

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

**▸ Why optimistic locking over pessimistic.** Two managers clicking approve simultaneously would both read PENDING, both pass the guard, both decrement — a lost update. `@Version` makes the second commit fail cleanly with a 409. Pessimistic (`PESSIMISTIC_WRITE`) also works and is simpler to reason about, but holds a row lock for the transaction duration and scales worse. Approvals are low-frequency either way. **Be able to argue both sides — this is a very common interview question.** See [DR-005](#dr-005--optimistic-locking-on-leave-approval).

**▸ Why a database CHECK constraint on top of application logic.** Application checks can be bypassed by any future code path, a migration script, or a manual fix. `CHECK (used_days <= entitled_days + carried_over)` is enforced for everyone, forever. **Making an invalid state unrepresentable beats testing for it.**

**▸ Why an explicit state machine.** Legal transitions are PENDING→APPROVED, PENDING→REJECTED, PENDING→CANCELLED, APPROVED→CANCELLED. Nothing else. Encoding this as a guarded transition rather than a free `setStatus()` eliminates a whole category of bug where an API call moves an entity into a nonsensical state.

---

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

---

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

# Decision records

## DR template

```markdown
## DR-XXX — <decision>
**Date:** YYYY-MM-DD
**Status:** Accepted | Superseded by DR-YYY | Deprecated
**Context:** <what forced a choice>
**Decision:** <what was chosen>
**Why:** <mechanism, not preference>
**Alternatives rejected:** <and why>   ← the most valuable line
**Consequences:** <including what got worse>
```

---

## DR-001 — Modular monolith, not microservices

**Status:** Accepted
**Context.** Portfolio project with one developer, no load, and a domain (employees ↔ leave ↔ balances) whose invariants must stay consistent across module boundaries. The temptation to build microservices for learning conflicts with what the domain actually wants.

**Decision.** One Spring Boot application, one PostgreSQL database, one deployable JAR. Strict module boundaries enforced by ArchUnit so extraction remains possible if it ever becomes necessary. The README will name `notification` and `document` as the two modules that would extract most cleanly.

**Why.** Microservices solve *organizational* problems — independent deploys for independent teams, independent scaling for asymmetric load. Neither applies. The correct heuristic is *transactional consistency boundaries define service boundaries*; applied honestly to leave approval (Flow 4), that heuristic produces one service. Splitting anyway would turn one `@Transactional` into a saga with compensating actions, a distributed-systems problem that doesn't exist here.

**Alternatives rejected.**
- *Microservices per bounded context* (identity / employee / leave / notification). Rejected because leave approval + balance decrement must be atomic — splitting them demands the saga pattern to solve a problem the domain never had, and the resulting system is visibly inconsistent in the middle of every approval.
- *Ball-of-mud monolith with feature-shaped folders.* Rejected as the failure mode this DR is written to prevent: it looks like a modular monolith and is not one. See [DR-002](#dr-002--feature-first-packages-not-layer-first) for the packaging discipline; the ArchUnit rules in §6 are what make the boundaries real.

**Consequences.**
- Scaling is vertical until it isn't. Single-instance rate limiting ([DR-007](#dr-007--in-process-rate-limiting-bucket4j-not-redis)) and single-instance scheduling (`@Scheduled` without ShedLock) are consequences of this and must be noted where they appear.
- The interview framing must lead with the reasoning, not apologise for the choice: *"I built this as a modular monolith because at this scale microservices would add distributed-systems failure modes without solving any problem I had."*

---

## DR-002 — Feature-first packages, not layer-first

**Status:** Accepted
**Context.** Given [DR-001](#dr-001--modular-monolith-not-microservices), one module structure has to serve the whole app. Layer-first packages (`controllers/`, `services/`, `repositories/`) stop being navigable around fifteen entities.

**Decision.** Top-level packages are features (`employee`, `leave`, `notification`, …). Inside each: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `event`. ArchUnit rules from PRD §6 enforce that a module's internal `entity` and `repository` packages are not importable by other modules — cross-module access is through the public service interface or a domain event.

**Why.** Feature-first co-locates code that changes together and makes extraction to a service — should it ever be needed — a folder move, not a rewrite. The boundary-enforcement rules (rule 3 in particular) are what make this a modular monolith rather than a big ball of mud with feature-shaped folders.

**Alternatives rejected.**
- *Layer-first.* Rejected: every feature change touches three distant folders, and there is no mechanical way to enforce "the leave module doesn't reach into employee's private entities."
- *Package-per-entity with no rules.* Rejected: the folders would suggest modularity without providing it. Without enforcement the layout is aspirational; ArchUnit turns it into a build failure.

**Consequences.**
- ArchUnit must be wired in M0 and treated as a first-class test. Rules go in at empty-scaffold stage, before there's any code to violate them, so the first violation is caught immediately.
- Cross-module reads via public service interfaces are direct in-process calls; cross-module reactions go through `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` ([DR-006](#dr-006--after_commit-events-never-inline-side-effects)).

---

## DR-003 — Testcontainers, never H2, for integration tests

**Status:** Accepted
**Context.** Integration tests need a database. H2 is fast and requires zero infrastructure; Testcontainers spins up a real PostgreSQL per suite and costs seconds of startup plus Docker on the developer machine.

**Decision.** Testcontainers + PostgreSQL 17. H2 is banned outright, including as an optional dev dependency.

**Why.** H2's SQL dialect, JSONB handling, `WITH RECURSIVE` behaviour, and locking semantics all differ from Postgres. This app relies on all four — recursive CTEs for the org hierarchy, JSONB for `audit_log.changed_fields`, `@Version` optimistic locking for leave approval, and CHECK constraints for balance invariants. A test suite green on H2 and broken on Postgres is worse than no suite — it manufactures false confidence.

**Alternatives rejected.**
- *H2 in Postgres compatibility mode.* Rejected: partial. It handles surface syntax; it does not reproduce Postgres's isolation semantics or its JSONB engine.
- *Shared local Postgres for the whole team.* Rejected: state leaks between tests, tests can't run in parallel, and CI needs its own instance anyway. Testcontainers gives every suite a clean database at the cost of a few seconds of startup — cheap given what it prevents.

**Consequences.**
- CI needs Docker available. GitHub Actions supports this out of the box.
- Test startup time is measurably slower than H2. This is the cost of tests that catch real bugs.

---

## DR-004 — Flyway migrations; `ddl-auto: validate`

**Status:** Accepted
**Context.** Schema evolution needs to be reproducible across dev, CI, staging, and prod. Hibernate can generate the schema at startup (`ddl-auto: update`), which appears convenient.

**Decision.** All schema changes are Flyway migrations under `db/migration`. `spring.jpa.hibernate.ddl-auto: validate` — Hibernate must confirm the schema matches its entities and never modifies it.

**Why.** `ddl-auto: update` is nondeterministic. It never drops columns, never renames, silently ignores mismatches, and produces different schemas in different environments depending on entity load order and history. Version-controlled migrations give a single, ordered, replayable history that is the same everywhere. `validate` at startup catches drift between entities and schema immediately, rather than in a stale-JSONB query at 3am.

**Alternatives rejected.**
- *`ddl-auto: update`.* Rejected: not reproducible, no migration history, no rollback path, and impossible to review.
- *Liquibase.* Reasonable equivalent; Flyway chosen for its simpler mental model (ordered SQL files, checksum-verified) and lighter XML footprint.

**Consequences.**
- CI must enforce the migration rule from PRD §10 — reject migrations containing `NOT NULL` without a default, `DROP COLUMN`, or `RENAME` on a populated table. Rolling deploys run N and N−1 simultaneously; breaking N−1 is the anti-goal.
- Editing an applied migration causes a Flyway checksum mismatch. This is a feature, not a bug — see BUG-JOURNAL awareness list.

---

## DR-005 — Optimistic locking on leave approval

**Status:** Accepted
**Context.** Two managers can click approve on the same request at the same time. Both transactions read `status = PENDING`, both pass the state-machine guard, both decrement the balance. Classic lost-update race — silent data corruption.

**Decision.** `@Version` on `leave_requests`. Second commit fails with `OptimisticLockException`, translated to HTTP 409 with a "this request was already decided" message. A database `CHECK (used_days <= entitled_days + carried_over)` sits underneath as an unbypassable backstop.

**Why.** Optimistic locking imposes zero cost when there is no contention and detects the collision cleanly when there is. Approvals are low-frequency and rarely contended, so the optimistic path is almost always the taken path. The CHECK constraint guards against any future code path — a migration, a manual fix, a new endpoint — that bypasses the service layer.

**Alternatives rejected.**
- *Pessimistic `PESSIMISTIC_WRITE`.* Correct and arguably simpler to reason about — the second approver blocks until the first commits, then sees APPROVED and fails the state check. Rejected here because it holds a row lock for the transaction duration for a case that almost never contends. **This is a very common interview question — be able to argue either side.** If contention were high, pessimistic would win.
- *`SERIALIZABLE` transaction isolation.* Rejected: correct but heavy-handed, pushes serialization failures onto unrelated transactions, and hides the intent (this specific row is being updated by more than one actor) inside global isolation semantics.
- *Application-level double-check only.* Rejected: two reads still race between them. And any code path that bypasses the service reintroduces the bug — the CHECK constraint is the only enforcement that cannot be bypassed.

**Consequences.**
- Callers must handle 409 as a normal outcome, not an error to retry blindly. Retry would just re-fail; the correct UX is "someone else already decided this — refresh."
- A concurrency test with `CountDownLatch` firing N simultaneous approvals is mandatory and lives in the integration suite. See PRD §9 "Mandatory adversarial tests."

---

## DR-006 — `AFTER_COMMIT` events, never inline side effects

**Status:** Accepted
**Context.** Creating an employee triggers a welcome email, seeds leave balances, writes a notification, publishes to internal listeners. Doing these inline in the create transaction couples all of them to the transaction's success. Doing them after the response ships them without commit guarantees.

**Decision.** Publish an in-process `ApplicationEvent` from the service inside the transaction; handle with `@TransactionalEventListener(phase = AFTER_COMMIT)`. Email dispatch is `@Async` on an explicitly-configured bounded thread pool.

**Why.** `AFTER_COMMIT` guarantees the handler runs only if the transaction actually committed. If the create rolls back, no welcome email is sent to a nonexistent employee. This is the in-process equivalent of the transactional outbox pattern — same guarantee, one annotation instead of a table, a relay, and a broker.

**Alternatives rejected.**
- *Inline call from the service before commit.* Rejected: sends email for rolled-back state. Guaranteed inconsistency the first time a downstream validation fails after the email dispatch.
- *`@EventListener` (no phase).* Rejected: same failure mode — the listener runs while the transaction is still open.
- *Transactional outbox with Kafka.* The right answer *if* the handler lived in a different service. In-process, the annotation buys the same guarantee for a fraction of the moving parts. Being able to explain the outbox pattern and why it's unnecessary here is a stronger interview answer than having implemented it.

**Consequences.**
- The `@Async` thread pool must be explicitly configured and bounded. The default is a `SimpleAsyncTaskExecutor` that creates a new thread per submission — a DoS waiting to happen under load.
- If an event handler throws, it does not roll back the original transaction (the transaction is already committed). Handlers must therefore be idempotent enough that a retry — either via `@Retryable` or a scheduled reconciliation — is safe.

---

## DR-007 — In-process rate limiting (Bucket4j), not Redis

**Status:** Accepted
**Context.** Auth endpoints need per-IP and per-account rate limits; general API needs per-user limits. A distributed system would use Redis with atomic scripts; a single-instance monolith can hold buckets in memory.

**Decision.** Bucket4j with in-memory storage. Filter runs before the controller. Returns 429 with `Retry-After` and `X-RateLimit-Remaining`.

**Why.** The app runs as a single instance ([DR-001](#dr-001--modular-monolith-not-microservices)). In-process buckets are exact, allocation-free per request, and add no infrastructure. Redis would add a network hop and a dependency to solve a problem this deployment doesn't have.

**Alternatives rejected.**
- *Redis-backed distributed buckets.* Rejected today, required the moment a second instance runs. Being able to say *"Redis would be required the moment I run a second instance, and here's why"* is the correct interview answer — knowing when this choice would flip is the point.
- *Gateway-only rate limiting (e.g., Nginx `limit_req`).* Rejected: works for coarse IP limits but cannot key per authenticated user without duplicating auth into the gateway. Application-layer limiting is the correct home for per-user quotas.
- *No rate limiting.* Rejected on principle: login endpoint without brute-force protection is a security bug, and unbounded API is a latent outage.

**Consequences.**
- Scaling to a second instance requires migrating to distributed buckets. This is noted in the README as an intentional single-instance choice.
- Buckets do not survive restart. For auth abuse this is fine — a restart is not a bypass path a real attacker would exploit given the DB-backed lockout also exists.

---

## DR-008 — `NUMERIC(19,4)` and `BigDecimal` for money

**Status:** Accepted
**Context.** Salaries are stored and computed. Binary floating point cannot represent 0.1 exactly. Cents-level drift from `double` arithmetic compounds and cannot be reconciled against source records.

**Decision.** All monetary values are `NUMERIC(19,4)` in Postgres, `BigDecimal` in Java. Explicit `RoundingMode` on every division. Currency is stored as a `CHAR(3)` alongside the amount, never inferred.

**Why.** `NUMERIC` is arbitrary-precision decimal; `BigDecimal` is its Java equivalent. Both represent 0.10 exactly. Together they eliminate an entire class of bug that is impossible to retrofit — the wrong-type decision spreads through every calculation and every stored row.

**Alternatives rejected.**
- *`double` / `float`.* Rejected. Not a debate. The failure mode is silent drift, not a crash, so the bug is invisible until reconciliation.
- *Store cents as `BIGINT`.* Works and is used in payment systems, but chosen against here because salaries are entered as decimal amounts and the human-visible representation matches the storage — fewer conversions, fewer opportunities to shift the decimal by mistake. The scale of 4 (not 2) leaves room for per-unit rates without another migration.
- *A framework money type (Joda-Money, JavaMoney).* Deferred — `BigDecimal` + explicit currency covers v1's needs; adopting a money type is a straightforward migration if a real need appears.

**Consequences.**
- Every JSON serializer touching money must be configured to preserve precision — Jackson's default numeric handling can strip trailing zeros. Explicit test coverage.
- Currency mixing must be a domain error, not a silent conversion. If a compensation record in USD is added to one in EUR, the code should refuse.

---

## DR-009 — Separate `users` and `employees` tables

**Status:** Accepted
**Context.** Common instinct is to put login credentials on the employee record — one table, one join saved. Applied to this domain it produces bugs.

**Decision.** Two tables. `employees` is the HR record (may exist without a login: contractors, pre-start hires, terminated but retained for retention obligations). `users` is the identity record (may exist without an employee: admins, service accounts). `users.employee_id` is a nullable FK.

**Why.** The lifecycles are different. An employee's login can be disabled without touching their employment record; an employee can exist for months before their login is provisioned; a terminated employee's record must survive their login being deleted. Conflating the tables ties these lifecycles together and produces impossible states — "we terminated Priya, so we deleted her employee row, and now her audit trail is orphaned."

**Alternatives rejected.**
- *Single `users` table with employment fields.* Rejected: cannot represent an employee without a login, or a login without an employee. Both cases exist.
- *Single `employees` table with credential fields.* Same problem, symmetric.
- *STI/JOINED inheritance across a `Person` supertype.* Rejected: adds JPA complexity to solve a problem the two-table design already solves cleanly.

**Consequences.**
- Every "who did this?" audit must resolve `actor_user_id` to a display name via the user → employee join. Cached at the API boundary for hot paths.
- Provisioning a login for an existing employee is an explicit operation, not a side effect. This is the correct behaviour — HR should decide when access is granted.

---

## DR-010 — Temporal `employment_history`, denormalized current pointer

**Status:** Accepted
**Context.** "What was Marcus's title on 2024-06-01?" is a legitimate HR question. Mutating `employees.position_id` in place loses the history. Storing only history and computing "current" on every read is expensive on the directory endpoint.

**Decision.** `employment_history` is the source of truth — one row per period, `effective_from` and `effective_to`, with `effective_to = NULL` meaning "current." `employees.position_id / department_id / manager_id` are denormalized *current* pointers, updated in the same transaction as the history insert. Never mutated independently.

**Why.** Reads dominate: the directory page joins the current position and department on every list. Reading requires zero history logic. Writes are rare and can afford both operations in one transaction, protected by the ACID guarantee. Optimizing for the common path without sacrificing the truth.

**Alternatives rejected.**
- *History-only, compute current at read time.* Rejected: every directory query becomes a subquery per row asking "which history row has `effective_to IS NULL`?" On 5,000 employees this is measurable, and the "current" concept is used everywhere.
- *Denormalized current only, no history.* Rejected: the point of an HR system is to answer historical questions, and this design cannot.
- *A `bitemporal` model (transaction time + valid time).* Correct for regulated finance systems; overkill here. If regulatory pressure later demands it, migration is possible.

**Consequences.**
- Any write that changes position, department, manager, or salary must go through a single service method that updates both places atomically. Directly setting `employees.position_id` from anywhere else is a bug and should be prevented by making the setter package-private and reviewed.
- Reporting queries over history use `employment_history` directly; day-to-day UI uses the denormalized pointers. Both are correct at the same time.

---

## DR-011 — Presigned URLs for document upload/download

**Status:** Accepted
**Context.** Employees upload documents up to 25MB (contracts, IDs, photos). Two obvious designs: stream bytes through the API to storage, or hand the browser a URL that lets it upload directly.

**Decision.** Presigned PUT for uploads (5-minute TTL), presigned GET for downloads (5-minute TTL). The API only ever sees the metadata — file name, content type, size claim, storage key. The confirm step re-reads actual size and content type from storage after the upload; type is validated by magic bytes, not extension.

**Why.** Bytes streaming through the API occupy a thread and heap for the entire transfer. Twenty concurrent 25MB uploads exhausts a default thread pool — a trivially cheap DoS. Object storage exists to handle this; the API doesn't have to. Presigned URLs are time-limited capabilities: they cannot be replayed after expiry, and they cannot access anything outside the specific key they were issued for.

**Alternatives rejected.**
- *Multipart upload streamed through the API.* Rejected: the DoS above, plus the API becomes a bandwidth bottleneck.
- *Store files in Postgres as `BYTEA`.* Rejected: bloats the database, destroys backup/restore times, holds connections open per download. Databases store facts about files; object storage stores files.
- *Trust the client's declared content type.* Rejected — extensions and Content-Type headers are user input. Magic-byte validation on the server after upload is the only trustworthy check.

**Consequences.**
- Deployment requires object storage (MinIO for local dev, S3 for prod). Docker Compose handles this in M0.
- The upload flow is three requests (URL → PUT → confirm) instead of one. Frontend must handle the split; the UX cost is offset by not blocking a user's browser on a large synchronous upload.
- The bucket must not be publicly readable — every download URL must be presigned and audit-logged. A misconfigured public bucket here is a data-leak incident.
