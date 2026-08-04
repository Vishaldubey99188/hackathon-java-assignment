# Questions

Here are 2 questions related to the codebase. There's no right or wrong answer - we want to understand your reasoning.

## Question 1: API Specification Approaches

When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded everything directly. 

What are your thoughts on the pros and cons of each approach? Which would you choose and why?

**Answer:**

**Spec-first (Warehouse, generated from `warehouse-openapi.yaml`)**

Pros:
- The YAML is the contract. Consumer teams can integrate against it, and mocking/codegen tools work, before the implementation exists.
- The DTO shape and the documented shape can't drift apart — `WarehouseResourceImpl` implements a generated interface, so a mismatch fails compilation instead of surfacing as a runtime surprise for a consumer months later.
- Swagger UI and client SDKs are free byproducts of the same source of truth.

Cons:
- Indirection tax: understanding what `/warehouse` actually returns means reading the YAML, then the generated code, then the implementation.
- Generated code is a black box until you go looking for it — not obvious to a newcomer that `id` in the generated bean isn't populated, because nothing forces the codegen output and the domain model to agree on which fields matter.
- Codegen/tooling version quirks become your bugs to debug, not just business logic.
- Slower to evolve for a field still being designed — every shape change is edit-YAML, regenerate, then edit the implementation to match.

**Code-first (Product, Store, coded directly)**

Pros:
- Fast iteration — add a field, it's live, no generation step, no parallel YAML to keep in sync.
- The whole request/response path lives in one file per resource, no jumping between a spec and generated sources.
- Full control — `StoreResource` fires domain events per operation and layers in transactional semantics that would be awkward to express purely as an OpenAPI annotation.

Cons:
- No contract until someone writes one by hand — documentation is optional and easy to let rot relative to the actual code.
- Nothing stops the hand-written DTO shape from silently diverging from whatever's documented.
- Every endpoint hand-rolls its own request validation rather than getting it from schema constraints, which invites inconsistency across resources.

**Which I'd choose:** it depends on who the API's consumers are, not on one approach being objectively better. For a public or cross-team API where the contract needs to be a stable, reviewable artifact, spec-first is worth the indirection — the contract can't drift from the implementation, which is the failure mode that hurts most once external consumers exist. For an internal CRUD resource with a small, co-located team — which is what Product and Store actually are here — code-first is the pragmatic choice, since the overhead of a parallel YAML doesn't pay for itself when the same engineer owns both the client and the endpoint. What I wouldn't do is what this repo does today: one spec-first resource and two ad hoc hand-coded ones with different validation-error conventions. I'd standardize on one approach per API surface, or at minimum extract the repeated null/duplicate-id validation into a shared piece and layer smallrye-openapi annotations onto the hand-coded resources so all three endpoints produce one discoverable, consistent Swagger document.

---

## Question 2: Testing Strategy

Given the need to balance thorough testing with time and resource constraints, how would you prioritize tests for this project? 

Which types of tests (unit, integration, parameterized, etc.) would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**

Priority order, and why:

1. **Concurrency/transaction-boundary tests on anything touching shared mutable state or an external system.** Highest priority, despite being the most expensive to write and run. A correctness bug that only manifests under concurrent access or a rolled-back transaction is invisible in code review and in demos, and only shows up in production under real load — so it has to be caught by a test, or it won't be caught at all. In this codebase, `WarehouseRepository.update()` using a bulk JPQL `UPDATE` that silently bypassed the `@Version` optimistic-lock column is a good example: every ordinary unit and integration test passed with that in place, because none of them exercised two writers touching the same row at once. Only the concurrent archive+stock-update and concurrent-replace tests, asserting on the combination of final state *or* a thrown exception, ever surfaced it.

2. **Domain/use-case unit tests (fast, no DB).** Second priority because they're nearly free to run and pin down business rules precisely — validation ordering, error messages, boundary cases like capacity/stock limits. `@ParameterizedTest` scenarios are a good fit here: one method, many boundary cases, instead of near-duplicate test methods. Push pure validation logic into something parameterizable and unit-testable in isolation; it's the cheapest test money spent, milliseconds per run, on every commit.

3. **Integration tests against a real database for anything the ORM/DB itself enforces** — unique constraints, NOT NULL, cascade behavior, actual query correctness. H2-vs-Postgres behavioral differences (constraint error types, SQL dialect quirks) are exactly the kind of thing that passes against an in-memory mock and then breaks in prod. Keep this tier deliberately small — a handful of tests confirming the schema does what the entity annotations claim — rather than re-testing business rules already covered by the unit-test tier against a slower, more expensive fixture.

4. **HTTP-layer tests** — thin, "does the wiring work" tests: right status code, right shape, error mapper produces the expected JSON. One full CRUD-ish round-trip per resource is the right scope here, not a combinatorial re-test of validation rules the unit tests already own.

What I would *not* heavily invest in, given constrained time: exhaustive parameterized coverage of every field/value combination once the boundary cases are covered (diminishing returns fast — a handful of well-chosen boundary cases catch what dozens of exhaustive ones would, at a fraction of the maintenance cost), and UI/end-to-end tests, since there isn't a UI here.

Keeping it effective over time:
- Treat a production bug as a missing test first — a regression test written against a real bug becomes permanent protection against it recurring.
- Enforce the concurrency-test tier specifically for any method that mutates shared state without an obvious single owner. That's usually a narrow, identifiable category, so it's cheap to keep a checklist of "does this method need a concurrent-writer test" rather than trying to blanket-test everything.
- Keep the fast unit tier fast. The moment full-context-boot tests creep into what should be plain unit tests, the whole suite slows down, people start skipping local test runs, and that's when regressions start landing unnoticed.
