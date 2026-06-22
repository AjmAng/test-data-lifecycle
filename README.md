# test-data-lifecycle

`test-data-lifecycle` (TDL) is an experimental project for managing test resource lifecycles.
Its main goal is to turn "whether test data is shared, when it is isolated, and when it is destroyed" into configurable strategies instead of ad-hoc logic scattered across test classes.

## Why TDL? — The Problem

Without a lifecycle framework, test resource management quickly becomes a maintenance burden:

| Pain point | Typical ad-hoc code | With TDL |
|---|---|---|
| Sharing files/tokens across tests | `private static` fields + manual cleanup in `@AfterAll` | Declarative `@Fixture` + automatic lifecycle |
| Isolation | Each test copies identical setup code to prevent side effects | Parameter injection stays isolated by default |
| Cleanup policy | Comments like `// keep this for debugging` or forgotten temp files | `CleanupPolicy.ON_SUCCESS` — retain on failure, destroy on pass |
| Reuse strategy | Copy-pasted "if-first-time-create" checks | Pluggable `ShareStrategy` — tag-based, hierarchy-based, or custom |

TDL captures these cross-cutting concerns into reusable strategies so that teams (and entire organizations) can enforce consistent data governance across all test suites.

### Concrete example: Live-stream e-commerce

Consider testing a live-stream shopping platform. The test data entities include:

| Entity | Creation cost | Ideal lifecycle |
|---|---|---|
| **Seller** | High — requires external team coordination, bank card verification, shop onboarding | Shared across the entire test suite |
| **Buyer** | High — similar external dependencies (verification, payment profile setup) | Shared across the entire test suite |
| **Livestream** | Low — can be created on the fly with an API call | Per-test or per-scope isolation |
| **Product** | Low — simple creation | Per-test or per-scope isolation |

Without TDL, teams often fall back to `static` fields with manual lifecycle comments:

```java
// WARNING: Do not modify! Shared across all tests!!
private static Seller sharedSeller;
```

With TDL, this is declarative and safe:

```java
@Fixture(provider = SellerProvider.class, strategy = SharedByTagStrategy.class)
Seller seller;

@Fixture(provider = ProductProvider.class)   // default: field-level sharing
Product product;
```

The sharing strategy can be decided per entity — expensive resources stay shared, cheap ones stay isolated — without changing how tests are written.

## Quick Start (JUnit 5)

### 1. Add dependency

```xml
<dependency>
    <groupId>io.github.ajmang.tdl</groupId>
    <artifactId>tdl-junit5</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### 2. Define a FixtureProvider

A `FixtureProvider<T>` knows how to create and destroy a resource:

```java
public class MyFileProvider implements FixtureProvider<Path> {
    @Override
    public Path create() {
        try {
            return Files.createTempFile("tdl-", ".tmp");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy(Path file) {
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }
}
```

### 3. Use @Fixture in a test

```java
@ExtendWith(FixtureExtension.class)
class MyFileTest {

    // Field injection — same fixture shared across tests in this class
    @Fixture(provider = MyFileProvider.class)
    Path sharedFile;

    @Test
    void first() {
        assertTrue(Files.exists(sharedFile));
    }

    @Test
    void second() {
        // same sharedFile instance as first()
    }
}
```

### Default behavior

| Injection style | Shared? | Detail |
|---|---|---|
| `@Fixture` on a **field** | ✅ Yes | Reuses the cached instance within the test class |
| `@Fixture` on a **parameter** | ❌ No | Each parameter gets its own isolated instance |

```java
// Parameter injection is always isolated
@Test
void isolatedParams(
        @Fixture(provider = MyFileProvider.class) Path a,
        @Fixture(provider = MyFileProvider.class) Path b
) {
    assertNotEquals(a, b); // always two different files
}
```

### 4. Try a different ShareStrategy

Tag-based sharing groups fixtures by `@FixtureTags`:

```java
@Fixture(provider = MyFileProvider.class, strategy = SharedByTagStrategy.class)
Path resource;

@Test
@FixtureTags("integration")
void integrationTest() { /* resource is shared among "integration" tests */ }

@Test
@FixtureTags("billing")
void billingTest() { /* resource is shared among "billing" tests only */ }
```

For a complete runnable example, see:
- `examples/src/test/java/.../basic/FieldInjectionSharedTest.java`
- `examples/src/test/java/.../tags/SharedByFrameworkTagStrategyTest.java`

## Project Positioning

- Team/company-level test data governance: unified sharing boundaries, reuse rules, and cleanup timing
- Safe by default (isolation first), while still allowing explicit sharing when needed
- Extensible strategy interfaces so users can define their own reuse semantics

## Modules

- `tdl-core`: common contracts and runtime capabilities (`FixtureProvider`, `ShareStrategy`, lifecycle management)
- `tdl-junit5`: JUnit 5 adapter layer (`@Fixture` annotation and `FixtureExtension` injection)
- `tdl-testng`: TestNG adapter layer (`@Fixture` field injection via `FixtureListener`)
- `examples`: JUnit 5 examples and regression tests

## Design Highlights (Current)

- `@Fixture` focuses on two things: `provider` (how to create resources) and `strategy` (how to decide sharing)
- The default strategy is `DefaultShareStrategy` (safe defaults: field injection can reuse cached fixtures, parameter injection stays isolated)
- Users can define custom `ShareStrategy` implementations to bind sharing boundaries to business semantics
- `FixtureScopeContext` keeps a small set of stable fields and an opaque `attributes` map so adapters can pass framework-specific or strategy-specific context without expanding the core API every time
- Adapters can discover user-defined `FixtureContextCollector` implementations from the test runtime classpath, so teams can collect custom annotation/tag metadata into `attributes` without changing core contracts
- Tests can explicitly bind collectors with `@UseFixtureCollectors(...)` on the test class, which serves as a local extension entrypoint and takes precedence over adapter-side `ServiceLoader` discovery
- Cached fixtures retain the `FixtureScopeContext` they were created with, so custom share strategies can compare current demand vs. producer-side context when deciding reuse
- End-to-end custom context example: `examples/src/test/java/io/github/ajmang/tdl/junit5examples/biztag/BizTagHierarchyShareStrategyTest.java` demonstrates `@BizTag` collection via `ServiceLoader` and hierarchy-based sharing decisions
- Lifecycle is centrally managed via JUnit 5 store so resources can be cleaned up when scope ends
- Class-level eager fetch/prefetch is an optional optimization path, not a required baseline behavior

See detailed design:

- `docs/share-strategy-rfc.md`
- `docs/roadmap-rfc.md`

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
.\mvnw.cmd -q test
```

> Note: the project is still in an early iteration stage. Treat the RFC and example tests as the source of truth for API behavior.

