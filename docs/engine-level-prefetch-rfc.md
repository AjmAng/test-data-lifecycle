# RFC: Engine-Level Fixture Prefetch

- Status: Draft
- Target versions: `1.2.0`
- Applicable modules: `tdl-core`, `tdl-junit5`, `tdl-testng`

## 1. Background

The class-level eager fetch (a.k.a. prefetch / pre-warming) proposed for `1.0.0` scans `@Fixture` fields during `BeforeAllCallback` and creates them before the first test method of a class runs. This works well for fixtures scoped to a single test class, but it does not solve the cold-start problem for **cross-class shared fixtures**.

When a fixture is shared across multiple test classes—for example, a PostgreSQL container shared by all tests tagged `@Tag("integration")`—every class still pays the creation cost on its first access, because `BeforeAllCallback` runs per-class and each class sees an empty cache on its first invocation.

Engine-level prefetch addresses this by discovering and creating fixtures **once, before the entire test suite starts**, so that all classes hit a warm cache from the very first access.

---

## 2. Design Goals

1. **Pay cold-start cost only once**: Heavy fixtures (containers, DBs, cloud resources) are created before any test runs.
2. **Transparent to existing tests**: No code changes required in test classes; the existing `@Fixture(eagerFetch = EagerFetch.ENABLED)` annotation is reused.
3. **Strategy-aware**: Only strategies that are semantically compatible with engine-level scope participate in prefetch.
4. **Framework-agnostic core**: The orchestration logic lives in `tdl-core`; JUnit 5 and TestNG adapters provide only the discovery and hook bridge.
5. **Safe lifecycle**: Engine-level fixtures are destroyed after the entire test plan / suite finishes.

## 3. Non-Goals

- Changing the `FixtureProvider#create()/destroy()` contract.
- Introducing async / parallel prefetch in the first iteration (parallelism is deferred to a follow-up).
- Cross-process or distributed fixture sharing.
- Auto-discovery of fixtures without `@Fixture` annotation.

---

## 4. Terminology

| Term | Meaning |
|------|---------|
| **Engine-level scope** | A scope that spans the entire test plan (JUnit 5) or the entire TestNG execution/suite. |
| **Class-level eager fetch** | Prefetch triggered in `BeforeAllCallback` / `@BeforeClass`; scoped to a single test class. |
| **Engine-level eager fetch** | Prefetch triggered before the first test class runs; visible to all classes in the test plan. |
| **EngineFixtureStore** | A global, in-JVM store that holds engine-level fixtures and outlives any single `ExtensionContext`. |

---

## 5. Overall Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Framework Adapter                         │
│  ┌─────────────────────┐    ┌──────────────────────────┐   │
│  │ JUnit 5             │    │ TestNG                   │   │
│  │ TestExecutionListener│   │ IExecutionListener       │   │
│  │ testPlanExecutionStarted│ onExecutionStart          │   │
│  └─────────┬───────────┘    └──────────┬───────────────┘   │
│            │ Scan all test classes     │ Scan all classes  │
│            │ + collect @Fixture fields │ in suite XML      │
│            └────────────┬──────────────┘                   │
│                         ▼                                  │
│            ┌─────────────────────────┐                     │
│            │   EagerFetchManager     │                     │
│            │   (tdl-core)            │                     │
│            │                         │                     │
│            │  1. Filter compatible   │                     │
│            │     strategies only     │                     │
│            │  2. Build Engine Scope  │                     │
│            │     FixtureScopeContext │                     │
│            │  3. Call FixtureManager │                     │
│            │     to create fixtures  │                     │
│            └────────────┬────────────┘                     │
│                         ▼                                  │
│            ┌─────────────────────────┐                     │
│            │   EngineFixtureStore    │                     │
│            │   (tdl-core singleton)  │                     │
│            └────────────┬────────────┘                     │
│                         ▼                                  │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Per-class FixtureExtension / FixtureListener      │   │
│  │  beforeEach / beforeInvocation:                    │   │
│  │    1. Query EngineFixtureStore first               │   │
│  │    2. Fallback to lazy getOrCreate                 │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Core Design

### 6.1 Strategy Compatibility

Not every `ShareStrategy` makes sense at the engine level. Prefetching a `PerTestMethodStrategy` fixture engine-wide would create a single instance and share it across methods that expect isolation—semantically wrong.

**Compatibility rule**: A strategy is eligible for engine-level prefetch **iff** its `shouldCacheCreatedFixture()` would return `true` when evaluated with an engine-level `FixtureScopeContext`. In practice this means strategies designed for **cross-class or global sharing**.

Built-in strategies and their engine-level eligibility:

| Strategy | Engine-level eligible | Rationale |
|----------|----------------------|-----------|
| `DefaultShareStrategy` | ⚠️ No (default) | Its default behavior is class-scoped. Changing this to engine-level would silently alter semantics for existing tests. |
| `PerTestMethodStrategy` | ❌ No | Per-method isolation; engine-level sharing violates the contract. |
| `PerTestClassStrategy` | ❌ No | Per-class isolation; engine-level sharing violates the contract. |
| `SharedByTagStrategy` | ✅ Yes | Explicitly designed for cross-class sharing. |
| `GlobalShareStrategy` | ✅ Yes | Explicitly designed for JVM-global sharing. |
| Custom user strategies | ? | Determined by `EagerFetchManager` via capability query. |

**Proposed API addition on `ShareStrategy`**:

```java
public interface ShareStrategy {
    // ... existing methods ...

    /**
     * Returns true if this strategy is willing to cache fixtures
     * at the given scope level.
     */
    default boolean supportsScope(ScopeLevel level) {
        return level == ScopeLevel.CLASS; // conservative default
    }
}
```

Where `ScopeLevel` is:
```java
public enum ScopeLevel {
    METHOD,
    CLASS,
    ENGINE
}
```

### 6.2 Engine Scope Context

When prefetching at the engine level, there is no single `testClassName` or `testMethodName`. The `FixtureScopeContext` is constructed with:

- `engineRunId`: a UUID for this test execution
- `testClassName`: `null`
- `testMethodName`: `null`
- `injectionPoint`: `FIELD` (only field injection is supported for engine-level eager fetch)
- `tags`: the **union of all tags** found anywhere in the test plan (needed for `SharedByTagStrategy` to match)
- All other fields set to sensible defaults or `null`

### 6.3 EngineFixtureStore

A new class in `tdl-core` that provides a JVM-global fixture cache:

```java
public class EngineFixtureStore {
    private final ConcurrentHashMap<String, ManagedFixture<?>> cache = new ConcurrentHashMap<>();

    public void put(String key, ManagedFixture<?> fixture) { ... }
    public Optional<ManagedFixture<?>> get(String key) { ... }
    public List<ManagedFixture<?>> listAll() { ... }
    public void clear() { ... }
}
```

Key format (proposed):
```
{strategyClassName}#{fixtureTypeName}#{scopeQualifier}
```

where `scopeQualifier` is strategy-specific (e.g., tag name for `SharedByTagStrategy`, empty for `GlobalShareStrategy`).

### 6.4 EagerFetchManager (Engine-Level additions)

```java
public class EagerFetchManager {

    /**
     * Discovers all engine-level prefetchable fixtures across the given test classes.
     */
    public Set<FixtureRequest<?>> discoverEngineLevelFixtures(
            Set<Class<?>> testClasses,
            Set<String> allTags);

    /**
     * Prefetches the given requests at engine scope.
     */
    public void prefetch(
            Set<FixtureRequest<?>> requests,
            FixtureScopeContext engineCtx,
            EngineFixtureStore engineStore);
}
```

---

## 7. JUnit 5 Integration

### 7.1 Discovery Hook: `TestExecutionListener`

Implement `org.junit.platform.launcher.TestExecutionListener`:

```java
public class EnginePrefetchTestExecutionListener implements TestExecutionListener {

    private final EagerFetchManager eagerFetchManager = new EagerFetchManager();
    private final EngineFixtureStore engineStore = new EngineFixtureStore();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // 1. Collect all test classes
        Set<Class<?>> classes = testPlan.getDescendants(testPlan.getRoots().iterator().next())
            .stream()
            .filter(TestIdentifier::isContainer)
            .map(id -> id.getSource()
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .map(ClassSource::getJavaClass))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());

        // 2. Collect all tags
        Set<String> allTags = testPlan.getDescendants(...)
            .stream()
            .flatMap(id -> id.getTags().stream())
            .map(TestTag::getName)
            .collect(Collectors.toSet());

        // 3. Discover and prefetch
        Set<FixtureRequest<?>> requests = eagerFetchManager.discoverEngineLevelFixtures(classes, allTags);
        FixtureScopeContext engineCtx = buildEngineScopeContext(allTags);
        eagerFetchManager.prefetch(requests, engineCtx, engineStore);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        // 4. Destroy all engine-level fixtures
        engineStore.listAll().forEach(mf -> {
            try {
                mf.provider().destroy(mf.fixture());
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        });
        engineStore.clear();
    }
}
```

### 7.2 Registration

Registered via ServiceLoader (no user configuration):

```
tdl-junit5/src/main/resources/
└── META-INF/services/
    └── org.junit.platform.launcher.TestExecutionListener
        └── io.github.ajmang.tdl.junit5.fixture.EnginePrefetchTestExecutionListener
```

**Dependency requirement**: `tdl-junit5/pom.xml` must declare `junit-platform-launcher` as a compile-scope dependency (currently absent).

### 7.3 Adapter Bridge: `FixtureExtension`

`FixtureExtension` and `Junit5FixtureManager` are updated to query `EngineFixtureStore` before falling back to lazy `getOrCreate`:

```java
// Inside FixtureExtension.beforeEach or resolveParameter
Optional<Object> engineFixture = fixtureManager.getFromEngineStore(request, metadata);
if (engineFixture.isPresent()) {
    field.set(testInstance, engineFixture.get());
    return;
}
// fallback to existing lazy path
Object fixture = fixtureManager.getOrCreate(request, ctx, store);
```

---

## 8. TestNG Integration

### 8.1 Discovery Hook: `IExecutionListener` or `ISuiteListener`

TestNG does not have a native "test plan" concept like JUnit 5, but it does have execution and suite boundaries.

**Option A: `IExecutionListener`** (preferred for engine-level scope)

```java
public class ExecutionPrefetchListener implements IExecutionListener {
    // onExecutionStart() scans all suites, all test classes,
    // collects @Fixture fields, and prefetches.
    // onExecutionFinish() destroys engine-level fixtures.
}
```

**Option B: `ISuiteListener`**

If the user wants suite-level rather than full execution-level prefetch, `ISuiteListener.onStart(ISuite)` is the right hook. This RFC focuses on execution-level, but the implementation can support both.

### 8.2 Class Discovery in TestNG

TestNG classes come from:
1. XML suite configuration (`ISuite.getXmlSuite().getTests()` → `XmlTest` → `XmlClass`)
2. Runtime additions (listeners, factories)

The listener iterates `ISuite.getAllMethods()` or the XML model to collect all candidate classes.

### 8.3 Adapter Bridge: `FixtureListener`

Same pattern as JUnit 5: `TestngFixtureManager` queries `EngineFixtureStore` first, then falls back to lazy creation.

---

## 9. Interaction with Existing Features

### 9.1 Retry (Feature 1)

Engine-level prefetch internally reuses `FixtureManager.createManagedFixture()`, which already wraps `create()` with retry. If creation fails after all retries, the engine-level prefetch fails fast and the exception propagates before any test runs.

### 9.2 Tag-Based Sharing (Feature 3)

Engine-level prefetch is primarily motivated by tag-based sharing. The union of all tags is computed from the test plan / suite and injected into the engine `FixtureScopeContext` so that `SharedByTagStrategy` can match correctly.

### 9.3 Cleanup Policy (Feature 4)

Engine-level fixtures follow their natural scope lifecycle: destroyed at `testPlanExecutionFinished` (JUnit 5) or `onExecutionFinish` (TestNG). `CleanupPolicy` does not apply to engine-level fixtures because they are inherently shared and their lifetime is deterministic.

---

## 10. Files to Change

### tdl-core

| File | Action | Description |
|------|--------|-------------|
| `ScopeLevel.java` | **Add** | Enum: `METHOD`, `CLASS`, `ENGINE` |
| `ShareStrategy.java` | **Modify** | Add `default boolean supportsScope(ScopeLevel level)` |
| `EngineFixtureStore.java` | **Add** | Global concurrent fixture cache |
| `EagerFetchManager.java` | **Modify** | Add `discoverEngineLevelFixtures` and `prefetch` |
| `FixtureManager.java` | **Modify** | Add `getFromEngineStore` query method |
| `FixtureScopeContext.java` | **Modify** | Add builder / factory for engine scope |

### tdl-junit5

| File | Action | Description |
|------|--------|-------------|
| `pom.xml` | **Modify** | Add `junit-platform-launcher` compile dependency |
| `EnginePrefetchTestExecutionListener.java` | **Add** | `TestExecutionListener` implementation |
| `FixtureExtension.java` | **Modify** | Query engine store before lazy fallback |
| `Junit5FixtureManager.java` | **Modify** | Bridge to `EngineFixtureStore` |
| `META-INF/services/org.junit.platform.launcher.TestExecutionListener` | **Add** | ServiceLoader registration |

### tdl-testng

| File | Action | Description |
|------|--------|-------------|
| `ExecutionPrefetchListener.java` | **Add** | `IExecutionListener` implementation |
| `FixtureListener.java` | **Modify** | Query engine store before lazy fallback |
| `TestngFixtureManager.java` | **Modify** | Bridge to `EngineFixtureStore` |

---

## 11. Open Questions

1. **Should engine-level prefetch be opt-in or opt-out?**
   - If a user adds `@Fixture(eagerFetch = ENABLED)` with `DefaultShareStrategy`, should the engine-level listener silently ignore it, or should it log a warning?

2. **How to handle dynamic tests?**
   - `TestExecutionListener` receives `dynamicTestRegistered` events after `testPlanExecutionStarted`. Dynamic tests may introduce new classes/tags that were not visible during prefetch. Should we support a second-round prefetch for dynamic containers?

3. **What is the interaction with Maven Surefire / Gradle test tasks?**
   - When Surefire forks a new JVM per test class (`reuseForks=false`), engine-level prefetch only covers a single class. Is this acceptable, or do we need documentation calling out this limitation?

4. **Should `LauncherSessionListener` be used instead of `TestExecutionListener`?**
   - `LauncherSessionListener` runs earlier (before test discovery), but it does not have access to `TestPlan`. Using it would require calling `Launcher.discover()` manually, which duplicates work. Is the earlier timing worth the complexity?

5. **Parallel engine-level prefetch?**
   - Should multiple engine-level fixtures be created in parallel via `ExecutorService`? If so, what happens if one fails while others are in-flight?

---

## 12. Milestones

### Phase 1: Foundation (target `1.2.0`)
- [ ] `ScopeLevel` enum and `ShareStrategy.supportsScope()`
- [ ] `EngineFixtureStore` implementation
- [ ] `EagerFetchManager` engine-level discovery and prefetch
- [ ] JUnit 5 `EnginePrefetchTestExecutionListener`
- [ ] TestNG `ExecutionPrefetchListener`
- [ ] Adapter bridges (`FixtureExtension`, `FixtureListener`)
- [ ] ServiceLoader registration for JUnit 5

### Phase 2: Parallel Prefetch (target `1.2.x` or `1.3.0`)
- [ ] Parallel creation of independent fixtures via `ExecutorService`
- [ ] Timeout and cancellation support
- [ ] Progress reporting (how many fixtures prefetched, how many failed)

---

## 13. Related Documents

- `roadmap-rfc.md` — Original prefetch design (class-level scope, `1.0.0`)
- `share-strategy-rfc.md` — Strategy architecture and dual-decision model
