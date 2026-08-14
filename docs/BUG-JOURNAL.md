# EMS — Bug Journal

**Companion to** `docs/PRD.md` and `docs/EXECUTION-FLOWS.md`.
**Purpose.** Record every non-trivial bug — how it was found, why the fix was chosen, and what prevents recurrence. Not the typos; the ones that took real investigation.

---

## ⚠️ Read this first

**This journal is intentionally empty. Do not pre-fill it with bugs you didn't hit.**

An interviewer's second question — *"what did the stack trace say?"* — will expose a fabricated war story, and the credibility loss is far worse than not having one. The wrong hypotheses are the most credible part of a real debugging story; a narrative where you guessed right immediately sounds invented, because it is.

The [awareness list](#bugs-this-architecture-reliably-produces) at the bottom names the failures this specific architecture tends to produce, so you'll recognize them when they hit. Move an entry from awareness to log **only when you actually experience it**, with your real timestamps and your real dead ends.

---

## Why keep this at all

Three reasons, in order of value:

1. **It's the interview material nobody else has.** Everyone can describe their architecture. Almost nobody can say *"here's a race I found, here's the reasoning that isolated it in 40 minutes, here's why I chose the fix I chose, here's the test that makes it impossible to reintroduce."* That's a staff-level conversation.
2. **It converts debugging from luck into method.** Writing down how you *found* something forces you to notice whether you had a method or just changed things until it worked.
3. **You will hit the same class of bug twice.** Searchable notes cost ten minutes and save hours.

---

## Debugging methodology

The discipline that separates senior from mid is **not changing anything until you can explain the mechanism**. *"I added a `sleep` and it went away"* is not a fix — it's a race condition you've hidden and will meet again in production at 3am.

### The loop

**1. Observe — establish the facts before touching anything.**
What is the *exact* symptom? Error text, status code, `traceId`, timestamp. What changed recently — deploy, migration, config, dependency bump? Deterministic or intermittent? Environment-specific? Blast radius: one user, one endpoint, everything? Resist the urge to fix. The first ten minutes spent gathering facts routinely save two hours.

**2. Reproduce — reliably, and as small as possible.**
A bug you cannot reproduce is a bug you cannot verify you fixed. Shrink it: which endpoint? which payload? which role? Non-deterministic bugs still reduce — a race that appears 1-in-20 appears 20-in-20 under a loop with concurrency.

**3. Hypothesize — write candidates down, ranked by prior probability.**
Explicitly. In this file. Then design the *cheapest* experiment that eliminates the most candidates. Binary search the system: is the bad data already wrong in the database, or wrong on the way out? That one question halves the search space.

**4. Isolate — prove which component owns it.**
Use `traceId` to follow a single request end to end. Filter logs by it. Find the span or step where the data first goes wrong. Trace-first debugging is the single biggest productivity difference between having correlation IDs and not.

**5. Root-cause — the Five Whys, honestly.**
*"The welcome email sent twice"* → why? → *"the handler ran twice"* → why? → *"an event handler retried after a transient failure"* → why did that cause damage? → *"the handler wasn't idempotent"* → why not? → *"we assumed handlers run once."* **That last why is the real bug.** The first four are symptoms. Stopping at symptom level produces fixes that don't hold.

**6. Fix — at the right layer.**
Ask: does this fix the mechanism or hide the symptom? Is it the narrowest correct change? What does it break? Is there a *class* of bugs here rather than one instance? If the same mistake is possible in five other places, fix the pattern, not the instance.

**7. Prevent — the part most people skip.**
A fix without a regression test is a fix with a half-life. Ask: what test would have caught this? What alert would have fired? Should this be an ArchUnit rule, a CI check, a database constraint, a type change that makes the bug **unrepresentable**? Making a bug structurally impossible beats testing for it.

### Tools, in the order you should reach for them

| Situation | Tool |
|---|---|
| Which request broke? | `traceId` — search structured logs by the value in `X-Trace-Id` |
| What was the state? | Structured logs filtered by `traceId` + database snapshot at that timestamp |
| Suspect a query? | `hibernate.show_sql` + `generate_statistics`, then `EXPLAIN ANALYZE` |
| Slow but no errors? | Micrometer timers, then a profiler (async-profiler / JFR) |
| Memory or thread issue? | Heap dump + Eclipse MAT; `jstack` for thread dumps |
| Intermittent, suspect concurrency? | Loop it under load — a JUnit test with a `CountDownLatch` |
| "Works locally" | Diff the config. It is almost always the config |
| Auth / cookie weirdness | Browser devtools → Application → Cookies + Network → Request Cookies |

---

## Bug entry template

```markdown
## BUG-XXX — <one-line symptom>

| | |
|---|---|
| **Date** | |
| **Severity** | S1 outage / S2 major / S3 minor / S4 cosmetic |
| **Phase found** | Dev / Code review / Integration test / Staging / Production |
| **Modules** | |
| **Time to detect / diagnose / fix** | |

**Symptom** — verbatim: error text, status code, `traceId`.

**How I noticed** — test failure, log alert, manual click-through, user report.
Be honest. "I noticed by accident" is a finding: it means detection is missing.

**Reproduction** — minimal steps.

**Hypotheses considered**
1. ~~X~~ — eliminated because...
2. ~~Y~~ — eliminated because...
3. Z — confirmed by...

**Investigation** — what you actually did, in order, **including dead ends**.
The dead ends are the most credible part.

**Root cause** — the mechanism, at the level where the fix belongs.
Five Whys until you reach an *assumption*, not a symptom.

**Fix** — what changed, and why this layer.

**Alternatives rejected** — and why.
*This is the most valuable section for interviews.*

**Prevention** — regression test / constraint / lint rule / ArchUnit rule / structural change
that makes recurrence impossible or detectable in CI.

**Lesson** — one generalizable sentence.
```

---

# Log

*Empty. Fill as you go, newest first, using the template above.*

<!--
Example placement — do not populate until real bugs happen:

## BUG-001 — <symptom>
...
-->

---

## Metrics to track over time

Once the log has ≥5 entries, add this table at the top of the Log section and update it monthly. It signals you think of engineering as a system, not just a series of fixes.

| Metric | Why it matters |
|---|---|
| Bugs by phase found | Shifting left is the goal. Lots found in production = weak testing |
| Mean time to detect | Usually worse than MTTR and far less discussed |
| Mean time to resolve | |
| % with a regression test added | Should be near 100% for S1/S2 |
| Repeat root causes | A repeat means the prevention step failed |

**The single most valuable number is bugs-found-in-production ÷ bugs-found-in-CI.** Being able to say *"I tracked this and moved it from 1:3 to 1:12 by adding query-count assertions and a security-matrix test"* is a genuinely senior thing to say in an interview, and almost nobody says it.

---

## Bugs this architecture reliably produces

**Awareness list, not a log.** These are the failures the choices in [PRD §7–8](PRD.md) and [EXECUTION-FLOWS.md](EXECUTION-FLOWS.md) tend to produce. Recognize them fast when they hit; then write them up properly in the Log with your real investigation.

| # | Symptom | Where it comes from |
|---|---|---|
| 1 | Users randomly logged out mid-session | Refresh stampede: N concurrent 401s all fire `/auth/refresh`, reuse detection revokes the family. Fix is client-side single-flight — see [Flow 5](EXECUTION-FLOWS.md#flow-5--refresh-with-rotation) |
| 2 | Directory endpoint fine at 40 seed rows, terrible at 5,000 | N+1 from lazy associations touched in the mapper. Add `@EntityGraph`; guard with query-count assertions |
| 3 | Leave balance went negative under concurrent approval | Lost update. `@Version` + CHECK constraint per [DR-005](EXECUTION-FLOWS.md#dr-005--optimistic-locking-on-leave-approval) |
| 4 | `@Transactional` method silently not transactional | Self-invocation through `this.method()` bypasses the Spring proxy. Call through the bean, or move the annotation. Classic |
| 5 | Leave spanning a DST boundary counted as 4.96 days | Timestamps where the domain concept is a calendar day. Use `DATE`, not `TIMESTAMP` |
| 6 | Connection pool exhaustion under load | A `@Transactional` method makes an HTTP call inside the transaction, holding a DB connection across a network hop. Never do this |
| 7 | Flyway startup fails with "checksum mismatch" | Someone edited an already-applied migration. Add a new migration; never edit an applied one |
| 8 | Rolling deploy takes down half the pods after a migration | `NOT NULL` added in a single step. Use expand–migrate–contract per [PRD §10](PRD.md#10-quality-gates) |
| 9 | Manager can `GET /employees/{ceoId}` and see it | Coarse `@PreAuthorize` present, fine-grained data scoping missing. See [Flow 2](EXECUTION-FLOWS.md#flow-2--authorized-read) |
| 10 | Welcome email sent for an employee that doesn't exist | Handler ran inline before the create transaction committed and then rolled back. Move to `@TransactionalEventListener(AFTER_COMMIT)` — [DR-006](EXECUTION-FLOWS.md#dr-006--after_commit-events-never-inline-side-effects) |
| 11 | CORS works locally, breaks in staging | Credentials-mode requires an explicit origin, never `*` |
| 12 | Recursive CTE hangs forever | Cycle in `manager_id` (A → B → A). Guard at write time and consider a DB constraint |
| 13 | `@Scheduled` fires N times after scaling to N replicas | Single-instance assumption baked in. Would need ShedLock — the reason the app is currently single-instance ([DR-001](EXECUTION-FLOWS.md#dr-001--modular-monolith-not-microservices), [DR-007](EXECUTION-FLOWS.md#dr-007--in-process-rate-limiting-bucket4j-not-redis)) |
| 14 | JSON response mysteriously drops trailing zeros on money | Jackson default numeric serialization on `BigDecimal`. Configure preservation and test it — [DR-008](EXECUTION-FLOWS.md#dr-008--numeric194-and-bigdecimal-for-money) |
| 15 | Refresh cookie not sent to `/auth/refresh` after a domain change | Cookie `Path`/`Domain` mismatch, or `SameSite=Strict` + cross-site link. Check devtools first, not the server |
