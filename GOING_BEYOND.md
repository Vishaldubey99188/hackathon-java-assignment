# Going Beyond

CODE_ASSIGNMENT.md's "Going Beyond" section asks four open-ended questions. Answered directly
below, grounded in what was actually found in this codebase — not generic engineering advice.

## Edge cases or failure modes not covered by the existing tests

**The most significant one: `Location.maxNumberOfWarehouses` and the aggregate per-location
capacity rule are stated in `BRIEFING.md` but never implemented.**

`BRIEFING.md` states two location-level business rules:
- "Each location has constraints on how many warehouses it can support"
- "Total warehouse capacity at a location cannot exceed the location's max capacity"

`CreateWarehouseUseCase` and `ReplaceWarehouseUseCase` only check a single warehouse's own
capacity against `location.maxCapacity()` — never the *sum* of all active warehouses' capacity at
that location, and `location.maxNumberOfWarehouses()` is never read anywhere in the codebase. In
practice this means, for example, five separate warehouses of capacity 90 could all be created at
`ZWOLLE-001` (max capacity 40, max 1 warehouse) one at a time, each individually under the 40
threshold... except capacity 90 > 40 so that specific example would fail the existing single-item
check — but two warehouses of capacity 30 each at `ZWOLLE-001` (max 1 warehouse) would both
succeed today, silently violating both stated rules at once.

**I did not implement this**, and want to be explicit about why, since it would be the obvious
next fix: `WarehouseConcurrencyIT` (one of the two integration test classes CODE_ASSIGNMENT.md
requires to keep passing) creates 10 concurrent warehouses of capacity 50 each at
`AMSTERDAM-001` and asserts *all 10 succeed* — a scenario that only makes sense if aggregate
capacity is *not* enforced (`AMSTERDAM-001`'s max capacity is 100; 10×50 = 500). The seeded fixture
data in `import.sql` has the same issue — `MWH.001` alone is already inserted at capacity 100
against `ZWOLLE-001`'s max of 40, bypassing use-case validation entirely since it's raw SQL.
Enforcing the aggregate rule as a hard constraint would make both of those required, currently-passing
artifacts fail. Retrofitting it safely would mean either changing the required IT test's own
scenario (out of scope — it's specified, not mine to rewrite) or fixing the seed data and treating
this as a breaking behavior change that needs sign-off first, not something to land silently. Flagging
it here as the real, correctly-diagnosed gap it is, rather than "fixing" it in a way that quietly
breaks a required test.

**What I did fix, safely**: `capacity <= 0` and `stock < 0` were previously accepted silently by
both `CreateWarehouseUseCase` and `ReplaceWarehouseUseCase` — every existing check only bounds
capacity/stock from *above*, never rejects non-positive values. Verified no existing test relied on
zero/negative values succeeding, then added the missing lower-bound validation with test coverage
in `WarehouseValidationTest` and `ReplaceWarehouseUseCaseTest`.

## Architecture, API design, or error handling I'd do differently

- **REST layer bypasses the port abstraction for reads.** `WarehouseResourceImpl` injects the
  concrete `WarehouseRepository` directly for `listAllWarehousesUnits()`, `getAWarehouseUnitByID()`,
  and `searchWarehouseUnits()`, rather than depending on the `WarehouseStore` port the write paths
  use. It works today because `WarehouseRepository` is the only implementation, but it means the
  hexagonal boundary the codebase otherwise follows isn't actually enforced by the compiler for
  reads — nothing stops a future change from making the REST layer depend on Hibernate-specific
  behavior. I'd add read methods to `WarehouseStore` (or a separate query-side port) rather than
  reach past it.
- **Two REST conventions with no shared validation.** Already covered in `QUESTIONS.md` (Q1) — worth
  repeating here because it's also an error-handling problem, not just a style one:
  `ProductResource`, `StoreResource`, and `WarehouseResourceImpl` each hand-roll their own
  null/duplicate/not-found checks with independently-worded messages. A shared validation helper
  (or bean-validation annotations backed by the OpenAPI schema, for the spec-first side) would keep
  error responses consistent across all three resources instead of consistent within each one.
- **`WarehouseStore.remove()` had no caller.** It's implemented now (this session), but nothing in
  the REST layer exposes a hard-delete — only archive (soft-delete). That's arguably correct for
  warehouses specifically (archival preserves history), but it means the port method existed for a
  year with an `UnsupportedOperationException` body and no test ever exercised it, which is exactly
  the kind of dead/half-finished code that's easy to miss in review.

## Observability, resilience, or operational concerns for a real system

- **The legacy-system sync has no retry or dead-letter path.** `LegacyStoreManagerGateway` writes a
  temp file and, on failure, only logs the exception (`RestExceptionMapper`/its own `LOGGER.errorf`)
  — there's no retry, no queue, no alerting. In a real system, a transient failure (disk full,
  permissions) silently and permanently loses that sync with no recovery mechanism, and nothing
  downstream would ever know it happened beyond a log line.
- **No metrics.** There's no `quarkus-micrometer`/Prometheus integration — no request-rate,
  latency, or error-rate metrics per endpoint, and no gauge on things that matter operationally here
  specifically, like optimistic-lock conflict rate on `WarehouseRepository.update()` (a rising
  conflict rate would be an early signal of a hot warehouse under real contention).
- **No distributed tracing / correlation IDs.** Logs across `WarehouseResourceImpl` →
  `CreateWarehouseUseCase` → `WarehouseRepository` have no shared request ID, so correlating a
  single request's log lines requires timestamp-matching by hand.
- **No rate limiting or request size limits** on `POST /warehouse` or `GET /warehouse/search` — the
  search endpoint's `pageSize` is capped at 100 (good), but nothing stops unbounded concurrent load
  on either endpoint.

## Anything else in the codebase that looked off

- **`WarehouseEndpointIT.testSimpleCheckingArchivingWarehouses`** is a test method whose entire body
  is commented out — it asserts nothing. Left as-is rather than silently deleting or completing
  someone else's clearly-unfinished test without being asked, but it's worth a maintainer's
  attention.
- **Inconsistent `@BeforeEach` cleanup across warehouse test classes.** `ArchiveWarehouseUseCaseTest`,
  `ReplaceWarehouseUseCaseTest`, and `WarehouseTestcontainersIT` all clear `DbWarehouse` before each
  test; `WarehouseValidationTest` and `WarehouseConcurrencyIT` don't. Since `@QuarkusTest` classes in
  the same run can share one H2 instance, this creates implicit test-ordering dependencies — it
  happens not to bite today, but it's fragile, and it's the same underlying issue that makes the
  aggregate-capacity rule above unsafe to add without also auditing fixture/test data consistency.
