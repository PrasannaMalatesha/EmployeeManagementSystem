# Employee Management System

A modular monolith HR system — employees, org hierarchy, leave, attendance, documents — built as a portfolio piece and a learning vehicle for backend architecture judgment.

**Status:** M0 — scaffold complete. Live demo comes at M10.

---

## Why modular monolith, not microservices

This is a deliberate choice, and the answer to the first question a senior interviewer will ask.

Microservices solve *organizational* problems: independent deploys for independent teams, independent scaling for asymmetric load. This system has one developer and no load. The correct heuristic — **transactional consistency boundaries define service boundaries** — applied honestly to this domain (leave status and leave balance must stay consistent, approval is one `@Transactional`) produces one service.

What the monolith preserves: strict module boundaries, enforced mechanically by [ArchUnit](src/test/java/com/malatesha/ems/architecture/ArchitectureTest.java), so extraction remains *possible* if load ever justifies it. The two modules that would extract most cleanly are `notification` and `document` — both stateless, both event-driven, both talk to external systems more than to the rest of the app.

Full reasoning in [docs/EXECUTION-FLOWS.md#dr-001](docs/EXECUTION-FLOWS.md#dr-001--modular-monolith-not-microservices).

---

## What's out of scope (deliberately)

| Excluded | Why |
|---|---|
| Payroll calculation & tax | Regulatory tarpit, jurisdiction-specific, no architectural interest |
| Performance reviews | v2 |
| Recruitment / ATS | Different product |
| Multi-tenancy | v2 |
| SSO / SAML | Hand-rolled auth teaches more here |
| Native mobile | Responsive web only |
| Benefits administration | Jurisdiction-specific |

---

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL 17 · Flyway · Testcontainers · ArchUnit · React 19 + Vite (M4+).

Full version pinning in [docs/PRD.md §1](docs/PRD.md).

---

## Quickstart

Prereqs: **Java 21**, **Docker**, `git`. No local Maven needed — the wrapper handles it.

```bash
git clone https://github.com/PrasannaMalatesha/EmployeeManagementSystem.git
cd EmployeeManagementSystem
cp .env.example .env
docker compose up -d          # Postgres 17 on :5433, MinIO on :9000
./mvnw spring-boot:run        # app on http://localhost:8080
```

> Postgres is exposed on host port **5433** (not the default 5432) so this project coexists with another local Postgres if you're running one. The container itself still speaks 5432; only the host mapping is shifted.

Health check:

```bash
curl -s http://localhost:8080/actuator/health | jq
```

MinIO console: <http://localhost:9001> (default `minioadmin` / `minioadmin`).

---

## Tests

```bash
./mvnw test          # unit + ArchUnit + Testcontainers context load
./mvnw verify        # everything, including reports
```

Integration tests spin up a real Postgres 17 via Testcontainers — **H2 is banned**, see [DR-003](docs/EXECUTION-FLOWS.md#dr-003--testcontainers-never-h2-for-integration-tests).

---

## Project layout

Feature-first packages, not layer-first. See [PRD §6](docs/PRD.md).

```
src/main/java/com/malatesha/ems
├── EmsApplication.java
├── config/            # security, CORS, OpenAPI, Jackson, async, cache, rate limit
├── common/            # BaseAuditableEntity, ProblemDetail handler, shared utils
├── security/          # JWT filter, PermissionEvaluator, AuditorAware
├── employee/          # + department (M2)
├── leave/             # (M3)
├── attendance/        # (M8)
├── document/          # (M8) — candidate for extraction
├── notification/      # candidate for extraction
└── audit/             # append-only HR change log
```

Module boundary rule 3 — a module's `entity` and `repository` packages are private to that module — is the load-bearing rule. Enforced by ArchUnit; build fails on violation.

---

## Documentation

- **[docs/PRD.md](docs/PRD.md)** — the full product & engineering spec (source of truth)
- **[docs/EXECUTION-FLOWS.md](docs/EXECUTION-FLOWS.md)** — traced request lifecycles with ▸ Why annotations; Decision Records for every non-obvious choice
- **[docs/BUG-JOURNAL.md](docs/BUG-JOURNAL.md)** — every non-trivial bug, how it was found, why the fix was chosen

---

## Milestones

| M | Scope | Status |
|---|---|---|
| M0 | Scaffold, Docker Compose, Flyway, Actuator, CI, ArchUnit | ✅ done |
| M1 | Auth: users/roles/permissions, login, refresh rotation, rate limiting | ⧗ next |
| M2 | Employees, departments, hierarchy, directory, data scoping, audit log | |
| M3 | Leave: types, balances, request, approval, concurrency guards, events | |
| M4 | React shell: auth flow, protected routes, design system, directory | |
| M5 | Leave UI: request form, approval queue with bulk actions, calendar | |
| M6 | Dashboard: role-aware landing pages, analytics | |
| M7 | Org chart (500 nodes, smooth, searchable) | |
| M8 | Attendance, timesheets, documents | |
| M9 | Polish: empty states, ⌘K palette, a11y audit | |
| M10 | Deploy + seed demo data + demo login | |

**M0–M7 is the actual product; M8 is optional.** A finished seven-milestone app with a demo beats a ten-milestone skeleton.

---

## License

MIT (add `LICENSE` before making the repo publicly promoted).
