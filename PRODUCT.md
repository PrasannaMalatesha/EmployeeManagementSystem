# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Java 21 (LTS) · Spring Boot 4.1 · PostgreSQL 17 · Flyway 11 · Testcontainers · ArchUnit · React 19 + Vite (M4+) · TypeScript strict · TanStack Query/Table · React Hook Form + Zod · Tailwind CSS 4 with shadcn/ui as a base to override. Full pinning in [docs/PRD.md §1](docs/PRD.md).

## Users

- **Priya (HR Manager)** — daily driver; onboards hires, maintains records, configures leave policy, pulls reports. Tolerates information density.
- **Marcus (Engineering Manager)** — 2–3×/week; approves leave for his team, checks who's out, reviews his org branch. Low engagement per session — opens the app to do one thing and leaves.
- **Sam (Employee)** — 2–3×/month; requests leave, checks balance, finds a colleague, uploads a document. Same low-engagement profile as Marcus.
- **Dana (Admin/IT)** — rarely; manages accounts and roles, investigates audit trail.

Marcus and Sam are the volume users but the least engaged; their flows get the polish budget.

## Product Purpose

A single system of record for employees, org structure, leave, attendance, and employment history — with role-appropriate access and a complete audit trail. Solves the SMB-HR problem where leave requests live in inboxes, org structure lives in someone's head, and there is no reliable answer to "who approved this change, and when?" — which becomes a compliance problem the first time it's asked.

## Positioning

A modular monolith HR system with strict, mechanically-enforced module boundaries — extractable to services if load ever justifies it, without paying the distributed-systems cost in v1. Explicit portfolio piece: also serves as a demonstration of backend architecture judgment (transactional design, module boundaries, observability, security posture) and polished frontend craft — see [docs/EXECUTION-FLOWS.md#dr-001](docs/EXECUTION-FLOWS.md#dr-001--modular-monolith-not-microservices) for the honest framing.

## Operating Context

Target companies: 50–500 employees currently running HR on spreadsheets, email threads, and shared drives. Replaces those tools rather than integrating with a full HRIS. Workflows the app owns end-to-end: onboarding a new hire, requesting/approving leave, submitting timesheets, uploading personnel documents, exporting audit-log CSVs for compliance requests.

## Capabilities and Constraints

Full functional spec in [docs/PRD.md §5](docs/PRD.md). Headline capabilities:

- **Authentication** — email/password with BCrypt cost 12, JWT access (15 min) + rotating opaque refresh (7 days, HttpOnly cookie), reuse detection with family revocation, per-IP and per-account rate limiting, timing-safe error responses. See Flow 1 and Flow 5.
- **Employee records** — directory (paginated hard-cap 100 per page), profile with tab-scoped panel failure isolation, temporal `employment_history` (never mutated in place), cycle guard on `manager_id`, role-scoped data visibility.
- **Org hierarchy** — recursive CTE from `manager_id`; also the query mechanism for MANAGER data scoping.
- **Leave** — configurable types/policies, balances, requests with half-day support, approval workflow with optimistic locking + DB `CHECK (used_days <= entitled_days + carried_over)`. Status change + balance decrement in ONE transaction. See Flow 4.
- **Attendance & timesheets** — one attendance record per employee per day (unique constraint), approved timesheets immutable without audited reopen.
- **Documents** — presigned PUT/GET direct to MinIO/S3; bytes never transit the API. Magic-byte validation.
- **Audit log** — append-only, covers create/update/delete on employee, employment history, leave decisions, role changes, document access. Written inside originating transaction.
- **Notifications** — in-app + email, triggered by domain events with `@TransactionalEventListener(AFTER_COMMIT)`.

Hard constraints:

- Money stored as `NUMERIC(19,4)` and modeled as `BigDecimal`; never `double`.
- Calendar-day dates stored as `DATE`, never `TIMESTAMP`, to survive DST correctly.
- Flyway migrations with `spring.jpa.hibernate.ddl-auto: validate` — Hibernate never modifies the schema.
- Testcontainers with real PostgreSQL for integration tests; H2 is banned.
- Module boundary rules enforced by ArchUnit — build fails on violation.
- SOLID principles applied throughout the backend (single responsibility, open/closed, Liskov, interface segregation, dependency inversion).

## Brand Commitments

- **Name**: Employee Management System (working title). No trademark or logo assets exist yet.
- **Visual identity** — a neo-brutalist design system called "Dossier": manila paper ground, ink typography, oxblood as the brand hue. Full token set in [DESIGN.md](DESIGN.md). Live design tile: <https://claude.ai/code/artifact/8d49f7fb-6edf-4d6a-8ba7-9f06c37e2560>.
- **Voice** — direct, factual, no apologizing microcopy. "Save changes," not "Submit." Errors say what went wrong and what to do next.
- **Aesthetic bans** (universal to all UI work in this project): no warm-cream + terracotta + Space Grotesk cliché, no purple-to-blue gradient heroes, no rounded-lg on cards, no emoji as section markers, no default shadcn-white-with-blue-accent.

## Evidence on Hand

- **PRD (source of truth)**: [docs/PRD.md](docs/PRD.md) — v2.0
- **Execution flows + Decision Records**: [docs/EXECUTION-FLOWS.md](docs/EXECUTION-FLOWS.md) — 6 traced flows, 11 seeded DRs
- **Bug journal** (methodology + template; log intentionally empty): [docs/BUG-JOURNAL.md](docs/BUG-JOURNAL.md)
- **M0 scaffold**: Spring Boot 4.1 app booting cleanly against Postgres 17 via Testcontainers; CI green. See [README.md](README.md).
- **Design system tile**: <https://claude.ai/code/artifact/8d49f7fb-6edf-4d6a-8ba7-9f06c37e2560>
- **Public repo**: <https://github.com/PrasannaMalatesha/EmployeeManagementSystem>

Absent: no real customer data, no live demo yet (comes at M10), no seed data yet (M10 will ship ~40 seeded employees across 5 departments with realistic history), no telemetry.

## Product Principles

1. **Transactional consistency boundaries define service boundaries.** Leave approval + balance decrement is one transaction; therefore one service. Applied honestly to this domain, the heuristic produces a modular monolith, not microservices.
2. **Lead with the "so what."** A manager landing on the dashboard sees "3 leave requests awaiting you" before any chart. Actionable items above ambient metrics, always.
3. **Every remote/side-effect call must be safe under partial failure.** Timeouts, bulkheads, circuit breakers, `AFTER_COMMIT` event handlers, presigned uploads that bypass the API — the app degrades one panel, never the page.
4. **Make invalid states unrepresentable.** Database CHECK constraints, `@Version` optimistic locking, ArchUnit boundary rules, `ddl-auto: validate` — enforcement in the layer where it cannot be bypassed beats testing for it.
5. **The doc is done when the code is done.** [docs/EXECUTION-FLOWS.md](docs/EXECUTION-FLOWS.md) and [docs/BUG-JOURNAL.md](docs/BUG-JOURNAL.md) are updated in the same commit as the code they describe.

## Accessibility & Inclusion

WCAG 2.1 AA across the app. Keyboard navigable, visible focus rings, ARIA on all data tables, AA contrast throughout, `prefers-reduced-motion` honored. Charts must ship with a table-view equivalent — a chart alone is not accessible. Target Lighthouse a11y ≥ 95 on the deployed demo.
