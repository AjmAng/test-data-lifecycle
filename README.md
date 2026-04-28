# test-data-lifecycle

`test-data-lifecycle` (TDL) is an experimental project for managing test resource lifecycles.
Its main goal is to turn "whether test data is shared, when it is isolated, and when it is destroyed" into configurable strategies instead of ad-hoc logic scattered across test classes.

## Project Positioning

- Team/company-level test data governance: unified sharing boundaries, reuse rules, and cleanup timing
- Safe by default (isolation first), while still allowing explicit sharing when needed
- Extensible strategy interfaces so users can define their own reuse semantics

## Modules

- `tdl-core`: common contracts and runtime capabilities (`FixtureProvider`, `FixtureIsolationStrategy`, lifecycle management)
- `tdl-junit5`: JUnit 5 adapter layer (`@Fixture` annotation and `FixtureExtension` injection)
- `examples`: examples and regression tests

## Design Highlights (Current)

- `@Fixture` focuses on two things: `provider` (how to create resources) and `strategy` (how to decide sharing)
- The default strategy is method-level isolation (`DefaultIsolationStrategy`) to avoid cross-test contamination
- Users can define custom `FixtureIsolationStrategy` implementations to bind sharing boundaries to business semantics
- Lifecycle is centrally managed via JUnit 5 store so resources can be cleaned up when scope ends

See detailed design: `docs/shared-strategy-design.md`

## Comparison: Testcontainers vs TDL

They are not replacements for each other; they are complementary.

- `Testcontainers` is better at environment/dependency orchestration: spinning up real DB/Redis/Kafka containers for tests
- `TDL` is better at test data lifecycle governance: defining who shares what, at which scope, and when to destroy it
- In a single project, `Testcontainers` is often enough
- In enterprise, multi-project, multi-team scenarios, `TDL` helps enforce unified strategies and constraints, reducing duplicated custom implementations
- They can be combined in practice: a `provider` can internally use Testcontainers, while TDL handles reuse/isolation strategy and lifecycle closure

## Comparison: Playwright Fixtures vs TDL

TDL is clearly inspired by the Playwright fixture model, but targets different use cases.

- Similarity: both emphasize fixture lifecycle management and dependency injection ergonomics
- Difference 1: Playwright fixtures mainly target frontend E2E ecosystems; TDL targets JVM/JUnit5 testing
- Difference 2: Playwright fixture scopes are built-in framework semantics; TDL abstracts sharing semantics behind extensible strategy interfaces
- Difference 3: TDL emphasizes organization-level governance, enabling team conventions to be reused across repositories

## Global Default Strategy (JUnit 5)

You can configure a default strategy class via `junit-platform.properties` or system properties (effective only when `@Fixture` does not explicitly override `strategy`):

```properties
tdl.fixture.default-strategy-class=io.github.ajmang.tdl.core.fixture.DefaultShareStrategy
```

Compatibility key: `tdl.junit5.fixture.default-strategy-class`

## Quick Validation

```powershell
mvn -q -pl examples test
```

> Note: the project is still in an early iteration stage. Treat the RFC and example tests as the source of truth for API behavior.

