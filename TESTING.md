# Testing Strategy

This document describes how the test suite is organized, how to run each tier, and how coverage
is measured and enforced.

## Test tiers

| Tier | Examples | What it covers | Speed |
|---|---|---|---|
| Domain unit tests | `WarehouseValidationTest`, `ArchiveWarehouseUseCaseTest`, `ReplaceWarehouseUseCaseTest` | Business-rule validation: capacity/stock limits, invalid locations, duplicate codes, archive/replace preconditions. Positive and negative cases via `@ParameterizedTest` where scenarios repeat with different inputs | Fast (no HTTP, in-memory H2) |
| REST-layer tests | `ProductEndpointTest`, `StoreEndpointTest`, `WarehouseEndpointTest`, `WarehouseSearchEndpointTest`, `RestExceptionMapperTest` | HTTP status codes and response shape for every CRUD path, including the error paths: 404 (not found), 422 (invalid request), 400 (business-rule rejection) | Fast |
| Transaction/event tests | `StoreEventObserverTest`, `StoreTransactionIntegrationTest` | The legacy-system sync only fires after a successful commit, never after a rollback (see `BRIEFING.md`) | Fast |
| Concurrency tests | `WarehouseOptimisticLockingTest`, `ArchiveWarehouseUseCaseTest#testConcurrentArchiveAndStockUpdateCausesOptimisticLockException`, `ReplaceWarehouseUseCaseTest#testConcurrentReplaceCausesLostUpdates`, `WarehouseConcurrencyIT` | Optimistic locking under concurrent writers; race conditions on duplicate-code creation; concurrent reads | Slower (spins up real threads) |
| Database-integration tests | `WarehouseTestcontainersIT` | Behavior only the real ORM/DB enforces: unique constraints, transaction rollback, complex JPQL queries | Slower |
| Collaborator unit tests | `LegacyStoreManagerGatewayTest` | Exercises the real file-write path directly, not through a mock (everywhere else in the suite this collaborator is mocked) | Fast |
| Framework wiring | `HealthCheckTest` | `/q/health`, `/q/health/live`, `/q/health/ready` respond `UP` | Fast |
| Packaged smoke test | `WarehouseEndpointIT` (`@QuarkusIntegrationTest`) | Black-box check against the packaged artifact; needs `mvn package` first | Slowest |

## Running the tests

```bash
# Default suite (everything except *IT classes)
./mvnw clean test

# Include the two integration test classes that aren't run by default
./mvnw test -Dtest="*Test,WarehouseConcurrencyIT,WarehouseTestcontainersIT"

# Full verify: tests + coverage report + coverage threshold check
./mvnw clean verify
```

## Coverage

Coverage is measured with JaCoCo, via the `io.quarkus:quarkus-jacoco` extension — plain
`jacoco-maven-plugin` alone under-reports `@QuarkusTest` coverage, since Quarkus's own CDI/Panache
bytecode transformation at test boot runs after JaCoCo's agent and discards its probes; this
extension hooks coverage collection into Quarkus's own classloading instead.

- HTML report: `target/site/jacoco/index.html` (open in a browser after running `./mvnw clean test`)
- Raw data: `target/site/jacoco/jacoco.csv` / `jacoco.xml`
- **Enforced minimum: 80% instruction coverage.** `./mvnw clean verify` runs `jacoco:check` at the
  `verify` phase and fails the build if the bundle drops below that threshold (configured in
  `pom.xml` under the `jacoco-maven-plugin` `check` execution)

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: checks out the repo, sets up JDK 17,
runs `./mvnw clean verify` with the two required IT classes included, and uploads the JaCoCo
report and surefire results as build artifacts.
