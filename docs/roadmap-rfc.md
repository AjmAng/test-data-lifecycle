# RFC: TDL Roadmap — Retry, Prefetch, and Tag-Based Sharing

- Status: Draft
- Target versions: `1.0.0` – `1.2.0`
- Applicable modules: `tdl-core`, `tdl-junit5`

## 1. Background

The strategy-first architecture (`FixtureProvider` + `ShareStrategy` + `FixtureManager`) provides the foundation for fixture lifecycle governance. The core abstraction is solid, but three real-world concerns are not yet addressed:

1. **Flaky fixture creation**: External resources (DBs, containers, cloud services) fail intermittently on startup. Tests fail not because of bugs, but because `FixtureProvider.create()` threw on the first attempt.
2. **Test runtime bloat**: Every test method waits for its fixture to be created inline. For heavy resources (DB schema migration, container start), this adds seconds or minutes to the test suite.
3. **Coarse sharing boundaries**: `ShareStrategy` today sees only the test class, method, and thread. It cannot express rules like "share this DB across all tests tagged `integration`" or "isolate this fixture for tests in TestNG group `billing`".

This RFC proposes concrete designs for each area.

---

## 2. Feature 1: Provider Retry on Failure

### 2.1 Motivation

`FixtureProvider.create()` is user code that talks to the outside world. Networks flake, ports collide, and rate limits hit. Today a single failure bubbles up as a test failure. Users wrap providers in ad-hoc try/catch loops. We should make retry a first-class concern.

### 2.2 Proposed Design

#### Option A: Retry Policy on `FixtureProvider`

Add an optional method to `FixtureProvider`:

```java
public interface FixtureProvider<T> {
    T create();
    void destroy(T instance);

    default RetryPolicy retryPolicy() {
        return RetryPolicy.none(); // backward compatible
    }
}
```

`RetryPolicy` is a simple value object:

```java
public record RetryPolicy(int maxAttempts, Duration backoff, Class<? extends Throwable>[] retryOn) {
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, new Class[]{Exception.class});
    }
    public static RetryPolicy fixed(int maxAttempts, Duration backoff) {
        return new RetryPolicy(maxAttempts, backoff, new Class[]{Exception.class});
    }
}
```

`FixtureManager` wraps `create()` invocation:

```java
int attempts = 0;
while (true) {
    try {
        return provider.create();
    } catch (Exception e) {
        attempts++;
        if (attempts >= policy.maxAttempts() || !isRetryable(e, policy)) {
            throw new FixtureCreateException("Failed after " + attempts + " attempts", e);
        }
        Thread.sleep(policy.backoff().toMillis());
    }
}
```

#### Option B: Retry Strategy (pluggable)

If retry logic should be customizable beyond fixed backoff:

```java
public interface RetryStrategy {
    <T> T execute(FixtureProvider<T> provider, FixtureScopeContext ctx) throws FixtureCreateException;
}
```

`FixtureManager` delegates `create()` to the `RetryStrategy`. Built-in implementations:
- `NoRetryStrategy` (default)
- `FixedIntervalRetryStrategy`
- `ExponentialBackoffRetryStrategy`

**Recommendation**: Start with **Option A** (`RetryPolicy`). It covers 95% of use cases with minimal API surface. If users need custom backoff algorithms, we can graduate to Option B later.

### 2.3 Open Questions

- Should `destroy()` also have retry semantics, or is a best-effort destroy sufficient?
- Should retry events be emitted to the event listener (see Feature 3) so users can see flakiness in reports?
- Should we add a global `tdl.fixture.default-retry-policy` configuration property?

---

## 3. Feature 2: Prefetch / Pre-warming

### 3.1 Motivation

Fixture creation is often the bottleneck in test suites. If we know which fixtures a test class needs *before* the first test method runs, we can create them eagerly during a "setup phase". This is especially valuable for:
- Docker containers that take 10-30s to start
- Database schemas that require migration
- Cloud resources with cold-start latency

### 3.2 Proposed Design

#### 3.2.1 Declaration: Prefetch Hint

Extend `@Fixture` with a prefetch hint:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Fixture {
    Class<? extends FixtureProvider<?>> provider();
    Class<? extends ShareStrategy> strategy() default DefaultShareStrategy.class;
    Prefetch prefetch() default Prefetch.AUTO; // NEW
}

public enum Prefetch {
    AUTO,      // adapter decides (default: prefetch for field injection, not for parameters)
    ENABLED,   // always prefetch if possible
    DISABLED   // never prefetch, create on demand
}
```

#### 3.2.2 Prefetch Orchestrator (`tdl-core`)

Introduce `PrefetchOrchestrator` in `tdl-core`:

```java
public class PrefetchOrchestrator {
    private final FixtureManager fixtureManager;
    private final ExecutorService executor;

    public List<Future<ManagedFixture<?>>> submitAll(Set<FixtureRequest<?>> requests, FixtureScopeContext classScope) {
        // Submit creation tasks in parallel
    }
}
```

#### 3.2.3 JUnit 5 Integration

`FixtureExtension` implements `BeforeAllCallback`. During `beforeAll`:

1. Scan the test class for all `@Fixture` fields with `prefetch != DISABLED`.
2. Build `FixtureRequest` + `FixtureScopeContext` for each.
3. Submit to `PrefetchOrchestrator` (parallel creation).
4. Store `Future<ManagedFixture<?>>` in a per-class cache.

During `beforeEach` / `resolveParameter`:
- If the fixture was prefetched, await the `Future` (non-blocking if already done).
- If not prefetched, fall back to normal `getOrCreate`.

#### 3.2.4 Scope Considerations

Prefetch only makes sense when the target scope is known ahead of time. For field injection, the scope is the test class (or whatever the `ShareStrategy` decides). For parameter injection, the scope is the method, so prefetch is usually not applicable.

```java
// This field-injected fixture will be created during @BeforeAll
@Fixture(provider = PostgresContainerProvider.class, prefetch = Prefetch.ENABLED)
static PostgreSQLContainer<?> postgres;

// This parameter-injected fixture is created per-method, no prefetch benefit
void test(@Fixture(provider = TempDirProvider.class) Path temp) { }
```

### 3.3 Open Questions

- Should prefetch be limited to `static` fields only, or also instance fields (with class-level scope)?
- What happens if prefetch fails? Fail the entire test class upfront, or fallback to lazy creation?
- Should there be a timeout for prefetch operations?
- How does prefetch interact with retry (Feature 1)? Prefetch + retry means the `Future` may internally retry before completing.

---

## 4. Feature 3: Tag-Based and Group-Based Isolation/Sharing

### 4.1 Motivation

Current `ShareStrategy` receives `FixtureScopeContext` with `testClassName`, `testMethodName`, `threadId`. This is enough for class-level or thread-level sharing, but not for higher-level test organization:

- **JUnit 5 Tags**: `@Tag("integration")`, `@Tag("slow")`. We want to share an expensive container across all `@Tag("integration")` tests, regardless of which class they are in.
- **TestNG Groups**: `@Test(groups = {"smoke"})`. A fixture should be shared within the `smoke` group but isolated from `regression`.
- **Custom taxonomies**: "share across all tests in package `com.example.billing.*`", "share across tests annotated with `@UsesRedis`".

### 4.2 Proposed Design

#### 4.2.1 Enriched Scope Context

Extend `FixtureScopeContext` to carry tags/groups/annotations:

```java
public record FixtureScopeContext(
    String engineRunId,
    String testClassName,
    String testMethodName,
    String junitUniqueId,
    InjectionPoint injectionPoint,
    String injectionTarget,
    Integer parameterIndex,
    long threadId,
    // NEW FIELDS
    Set<String> tags,           // JUnit 5 @Tag values, TestNG groups, etc.
    Set<String> annotations,    // Fully-qualified names of annotations on class/method
    String packageName
) {}
```

Each framework adapter is responsible for extracting tags/groups from the native framework context:
- JUnit 5: `ExtensionContext.getTags()`
- TestNG: `ITestNGMethod.getGroups()`
- JUnit 4: Could support a custom `@FixtureTag` annotation

#### 4.2.2 Tag-Aware Share Strategies

Built-in strategies that leverage the new fields:

```java
public class SharedByTagStrategy implements ShareStrategy {
    private final String targetTag;

    public SharedByTagStrategy(String targetTag) {
        this.targetTag = targetTag;
    }

    @Override
    public <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext ctx, FixtureRequest<T> request, List<ManagedFixture<T>> candidates) {

        if (!ctx.tags().contains(targetTag)) {
            return Optional.empty(); // not applicable, let fallback strategy handle it
        }

        // Match by: tag + fixture type
        String scopeKey = targetTag + "#" + request.fixtureType().getName();
        return candidates.stream()
            .filter(f -> scopeKey.equals(f.scopeKey()))
            .findFirst();
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext ctx) {
        return ctx.tags().contains(targetTag);
    }
}
```

Usage:

```java
@Tag("integration")
@ExtendWith(FixtureExtension.class)
class BillingIntegrationTest {
    @Fixture(provider = PostgresContainerProvider.class, strategy = SharedByTagStrategy.class)
    static PostgreSQLContainer<?> postgres; // shared across ALL @Tag("integration") tests
}
```

#### 4.2.3 Composite Strategy Builder

A fluent API for composing rules:

```java
public class CompositeStrategy implements ShareStrategy {
    private final List<ShareRule> rules;

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        public Builder whenTag(String tag, ShareStrategy then) { ... }
        public Builder whenClass(Class<?> clazz, ShareStrategy then) { ... }
        public Builder defaultStrategy(ShareStrategy fallback) { ... }
    }
}

// Usage in code or configuration
CompositeStrategy.builder()
    .whenTag("integration", new SharedByTagStrategy("integration"))
    .whenTag("unit", new PerTestMethodStrategy())
    .defaultStrategy(new PerTestClassStrategy())
    .build();
```

### 4.3 Open Questions

- How do we ensure consistent `scopeKey` computation when the same tag is used across multiple strategy instances? Should `scopeKey` be a first-class concept again, or is type + tag string sufficient?
- What happens when a test has multiple tags and each tag wants a different sharing boundary?
- Should tag-based sharing work across the entire JVM (engine-level), or should it be scoped to a test plan / execution?
- For TestNG, groups can be defined at suite level (XML). How do we propagate suite-level groups into `FixtureScopeContext`?

---

## 5. Feature 4: Failure-Aware Fixture Cleanup (Retain on Failure)

### 5.1 Motivation

Today, fixtures are destroyed when the JUnit store closes. There is no distinction between "test passed, clean up" and "test failed, preserve for investigation". In practice:
- A failed integration test leaves behind a DB schema that the developer wants to inspect.
- A container-based test fails, and the logs/volume need to be captured before destruction.
- A temp directory with generated files should be kept for post-mortem analysis.

Users currently work around this by not using TDL and writing manual setup/teardown with try/catch. We should make this a first-class policy.

### 5.2 Proposed Design

#### 5.2.1 Cleanup Policy

Introduce `CleanupPolicy` enum and a way to configure it:

```java
public enum CleanupPolicy {
    ALWAYS,      // destroy fixture regardless of test result (current behavior)
    ON_SUCCESS,  // destroy only if the test passed; retain on failure
    NEVER        // never destroy (useful for debugging sessions)
}
```

Extend `@Fixture`:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Fixture {
    Class<? extends FixtureProvider<?>> provider();
    Class<? extends ShareStrategy> strategy() default DefaultShareStrategy.class;
    CleanupPolicy cleanup() default CleanupPolicy.ALWAYS; // NEW
}
```

#### 5.2.2 Test Result Awareness in Adapters

The core (`tdl-core`) is framework-agnostic and does not know about "test pass/fail". The framework adapter must bridge this:

**JUnit 5 approach**:
- `FixtureExtension` implements `TestExecutionExceptionHandler`.
- Track whether the current test threw an exception.
- At `AfterEachCallback` / `AfterAllCallback`, decide whether to register the fixture for destruction or "leak" it.

However, JUnit 5's `CloseableResource` in the root store is closed at engine shutdown. We need a more granular mechanism:

```java
public interface FixtureStore {
    ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier);
    List<ManagedFixture<?>> listAll();
    void put(String key, ManagedFixture<?> fixture);
    void remove(String key);           // NEW: remove from store without destroying
    void destroy(String key);          // NEW: destroy immediately
}
```

`JunitFixtureStore` can then:
- On test failure: `remove(key)` → fixture is detached from store but not destroyed.
- On test success: let `CloseableResource` handle destruction as usual.

**Problem**: If we `remove()` on failure, the fixture is leaked. We need a way to report or track retained fixtures.

#### 5.2.3 Retained Fixture Reporting

When fixtures are retained on failure, the user needs to know where they are:

```java
public interface RetainedFixtureReporter {
    void onRetained(FixtureScopeContext ctx, ManagedFixture<?> fixture, Throwable failure);
}

// Default console reporter:
// [TDL] RETAINED fixture=DirectoryResource path=/tmp/fixture-123 reason=Test failed with NullPointerException
```

This can be combined with the event listener from §3.2.

#### 5.2.4 Shared Fixtures and Failure

**Critical edge case**: If a fixture is shared (e.g. `SharedByTagStrategy`) and *one* test fails, do we retain or destroy?

Options:
- **Conservative (default)**: If the fixture is shared and any dependent test failed, retain it until the end of the run, then destroy. This prevents destroying a fixture that another failing test might need.
- **Reference counting**: Track which tests used the fixture. Only destroy if *all* using tests passed.
- **Scope-based**: Retain decision is made at fixture scope end (e.g. class-level fixtures are checked at `@AfterAll`, engine-level fixtures at engine shutdown).

**Recommendation**: For `1.0.0`, keep it simple:
- `CleanupPolicy` only affects **non-shared** fixtures (per-test-method, per-parameter).
- Shared fixtures always follow their natural scope lifecycle (destroyed when the scope ends). The rationale: shared fixtures are expensive, and retaining them for every failure in a large suite would exhaust resources.
- A future version can add `SharedCleanupPolicy` for more nuanced rules.

### 5.3 Open Questions

- Should `CleanupPolicy` be overridable globally via `junit-platform.properties`?
- Should retained fixtures have a TTL (e.g. auto-destroy after 1 hour) to prevent disk leaks?
- How does this interact with CI environments where the workspace is ephemeral anyway?

---

## 6. Interaction Matrix

These four features are not independent. Here is how they interact:

| Interaction | Behavior |
|-------------|----------|
| Retry + Prefetch | Prefetch `Future` internally uses retry. If all retries exhaust, the `Future` completes exceptionally. |
| Retry + Tag Sharing | Retry applies to creation. Once created, the cached fixture is shared per tag rules. |
| Prefetch + Tag Sharing | Prefetch scans all test classes in the plan to discover tag-based fixtures. This may require engine-level hooks (not just `BeforeAllCallback`). |
| Cleanup + Tag Sharing | Shared fixtures ignore `CleanupPolicy` in `1.0.0` (see §5.2.4). Only isolated fixtures are retained on failure. |
| All four | The ideal state: flaky heavy fixtures are created eagerly with retry, shared intelligently by tags, and preserved for inspection when tests fail. |

### 6.1 Engine-Level Prefetch (Advanced)

If Prefetch needs to discover fixtures across the entire test plan (e.g. to share a `@Tag("integration")` fixture across classes), JUnit 5's `BeforeAllCallback` is insufficient because it runs per-class. We may need:

```java
public interface EnginePrefetchExtension {
    Set<FixtureRequest<?>> discoverFixtures(TestPlan testPlan);
}
```

This would be a `TestExecutionListener` or a custom JUnit 5 `Engine` extension. This is complex and should be a `1.2.0` item.

**Short-term compromise** (`1.0.0`): Prefetch only works within a single test class (via `BeforeAllCallback`). Cross-class tag sharing still works, but the first class to need the fixture pays the creation cost; subsequent classes hit the cache.

---

## 7. Milestones

### `1.0.0` — Foundation
- [ ] Provider retry with `RetryPolicy` (Feature 1, Option A)
- [ ] Class-level prefetch with `BeforeAllCallback` (Feature 2, single-class scope)
- [ ] `FixtureScopeContext` enriched with `tags`, `annotations`, `packageName` (Feature 3, data model)
- [ ] `SharedByTagStrategy` and `CompositeStrategy` (Feature 3, strategies)
- [ ] Built-in strategy library: `PerTestMethod`, `PerTestClass`, `PerThread`, `Global`
- [ ] Debug event listener (cache hit/miss/create/destroy)
- [ ] `CleanupPolicy` (`ALWAYS` / `ON_SUCCESS` / `NEVER`) for non-shared fixtures (Feature 4)

### `1.1.0` — Ecosystem
- [ ] `ServiceLoader` strategy discovery + short names
- [ ] `ParameterizedFixtureProvider` (`@FixtureArg` support)
- [ ] `tdl-junit4` adapter (tag support via custom annotations)
- [ ] `tdl-testng` adapter (group support)

### `1.2.0` — Advanced Orchestration
- [ ] Engine-level prefetch (cross-class fixture discovery)
- [ ] Lifecycle reporting (fixture creation time, cache hit rate, retry count, retained fixtures)
- [ ] External configuration (`tdl.yml`, profiles)
- [ ] Async `destroy()` / graceful shutdown hooks
- [ ] Shared fixture failure-aware cleanup (reference counting or scope-based retain)

---

## 8. Open Questions (Global)

1. Is `FixtureProvider` the right place for `retryPolicy()`, or should it be on `@Fixture` annotation?
2. Should `ShareStrategy` be redesigned to expose a `String scopeKey()` method again, to make tag-based caching more deterministic?
3. How do we test flaky provider behavior? Introduce a `FlakyFixtureProvider` test double?
4. Should we stay on Java 17, or drop to 11 for broader adoption?
5. For `CleanupPolicy.ON_SUCCESS`, who is responsible for eventually cleaning up retained fixtures? Should TDL emit a shell script / summary listing retained paths?
6. Should `CleanupPolicy` apply to `destroy()` only, or also affect whether the fixture is removed from the `FixtureStore`?

---

## 9. Related Documents

- `share-strategy-rfc.md` — Core strategy architecture (needs alignment with current `ShareStrategy` API)
