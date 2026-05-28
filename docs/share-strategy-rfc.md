# RFC: Share Strategy for Fixture Reuse and Caching (Implementation-Aligned)

- Status: Draft (aligned with current mainline behavior)
- Aligned version: `1.0-SNAPSHOT`
- Applicable modules: `tdl-core`, `tdl-junit5`, `examples`

## 1. Background

The project has moved from legacy mode-based ideas to a strategy-driven model.
In the current implementation, a strategy controls two decisions:

1. Which existing fixture (if any) should be reused.
2. Whether a newly created fixture should be cached.

This RFC documents the current implementation and replaces outdated references to `scopeKey`-only design and `FixtureIsolationStrategy`.

## 2. Design Goals (Current)

- Safe by default: avoid accidental sharing when injection semantics require isolation
- Explicit control: sharing/caching behavior is defined by `ShareStrategy`
- Extensible behavior: users can plug in custom strategy implementations
- Clear module boundary: orchestration in `tdl-core`, framework bridge in `tdl-junit5`

## 3. Non-Goals

- Cross-JVM/process fixture sharing
- Distributed locking/coordination
- Changing `FixtureProvider#create()/destroy()` contract in this iteration
- Introducing hierarchical store placement in this iteration (currently root store in JUnit 5 adapter)

## 4. Terminology

- Share Strategy: policy object that selects reusable fixtures and controls cache writes
- Candidate Fixtures: typed cached fixtures visible to the strategy during selection
- Managed Fixture: runtime wrapper that owns fixture lifecycle and close behavior
- Scope Context: request-time metadata used by strategy (`FixtureScopeContext`)
- Producer Context: the `FixtureScopeContext` snapshot captured when a cached fixture was first created

## 5. Layering and Responsibilities

### 5.1 `tdl-core`

- Defines common contracts: `FixtureProvider`, `ShareStrategy`, `FixtureScopeContext`, `FixtureRequest`, `FixtureStore`
- Owns orchestration in `FixtureManager`: strategy resolution, candidate collection, reuse decision, fixture creation, conditional caching
- Does not depend on JUnit APIs
- `FixtureScopeContext` keeps stable core fields, plus an opaque `attributes` map for framework- or strategy-specific context data

### 5.2 `tdl-junit5`

- Provides `FixtureExtension` and integrates `@Fixture`
- Builds `FixtureScopeContext` from `ExtensionContext` + `InjectionMetadata`
- Adapts JUnit store to `FixtureStore` via `Junit5FixtureManager`
- Uses `ExtensionContext.Store.CloseableResource` for lifecycle cleanup
- Supports explicit collector binding via `@UseFixtureCollectors(...)` on the test class
- Falls back to `ServiceLoader` discovery from the test runtime classpath when no class-level collector annotation is present

## 6. Core API (Current Implementation)

### 6.1 Strategy contract (`tdl-core`)

```java
public interface ShareStrategy {
    <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    );

    boolean shouldCacheCreatedFixture(FixtureScopeContext context);
}
```

### 6.2 Scope context (`tdl-core`)

`FixtureScopeContext` currently includes:

- `engineRunId`
- `testClassName`
- `testMethodName`
- `junitUniqueId`
- `injectionPoint` (`FIELD` or `PARAMETER`)
- `injectionTarget`
- `parameterIndex`
- `threadId`
- `tags`
- `annotations`
- `packageName`
- `attributes` (opaque `Map<String, Object>` for extra context)

Current adapters populate `attributes` with framework-specific keys such as `framework`, `junit.tags`, `junit.annotations`, `junit.packageName`, `testng.groups`, `testng.annotations`, and `testng.packageName`.

In addition, users can contribute extra namespaced attributes by implementing `FixtureContextCollector` and either binding it explicitly with `@UseFixtureCollectors(...)` or exposing it through `ServiceLoader`. Collectors are framework-aware, ordered, and rejected if they contribute duplicate attribute keys in the same request.

### 6.3 Store contract (`tdl-core`)

```java
public interface FixtureStore {
    ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier);
    List<ManagedFixture<?>> listAll();
    void put(String key, ManagedFixture<?> fixture);
}
```

`FixtureManager` currently uses `listAll()` to build typed candidates and `put(...)` for conditional cache writes.

### 6.4 Annotation contract

`@Fixture` defines:

- required `provider`
- optional `strategy`, defaulting to `DefaultShareStrategy`

## 7. Built-in Behavior (Current)

The built-in `DefaultShareStrategy` behavior is:

- `PARAMETER` injection: never reuse candidates, and do not cache newly created fixtures
- non-`PARAMETER` injection (currently `FIELD`): reuse first available candidate and cache newly created fixtures

Note: this is current behavior, not a long-term final policy contract.

Cached `ManagedFixture` instances now retain their producer context, which allows custom strategies to compare current consumer attributes with the attributes that were present when a candidate fixture was created.

## 8. JUnit 5 Effective Strategy Resolution

Current `Junit5FixtureManager` behavior:

1. If `@Fixture(strategy = ...)` is explicitly set to a non-default strategy, use it.
2. Otherwise resolve configured default strategy class from configuration/system keys.
3. Fallback to `DefaultShareStrategy`.

Configuration keys:

- `tdl.fixture.default-strategy-class`
- compatibility key: `tdl.junit5.fixture.default-strategy-class`

Important: configured defaults are only applied when the annotation keeps the default strategy.

## 9. Execution Model (Current)

1. `FixtureExtension` discovers `@Fixture` on fields/parameters
2. Extension passes `InjectionMetadata` (`injectionPoint`, `injectionTarget`, `parameterIndex`) to `Junit5FixtureManager`
3. `Junit5FixtureManager` builds `FixtureRequest` and `FixtureScopeContext`
4. Context is delegated to `FixtureManager#getOrCreate(...)`
5. `FixtureManager` resolves strategy, selects candidates, creates fixture if needed, and conditionally caches
6. `Junit5FixtureStore` stores `ManagedFixture` wrappers in JUnit root store
7. JUnit closes `CloseableResource` at scope end, triggering fixture cleanup

Current store placement is root-context-based; hierarchical store selection is future work.

## 10. Risks and Mitigations

- Risk: unintended reuse due to broad candidate selection logic
  - Mitigation: context-aware strategy and focused example tests for field/parameter behavior
- Risk: strategy misconfiguration (missing class or invalid type)
  - Mitigation: fail fast with clear runtime error in strategy parsing
- Risk: stale cache entries from custom strategy behavior
  - Mitigation: keep cache-write control explicit in `shouldCacheCreatedFixture(...)`
- Risk: lifecycle mismatch due to root-store placement
  - Mitigation: document current behavior clearly and treat scoped store selection as planned enhancement

## 11. Milestones

### M1 (Completed)

- Introduced `ShareStrategy` dual-decision model (reuse + cache-write)
- Extended `FixtureStore` for candidate listing and direct writes
- Added richer `FixtureScopeContext` and injection metadata bridge
- Aligned JUnit 5 adapter with `CloseableResource` cleanup

### M2 (In Progress)

- Expand regression coverage for field sharing vs parameter isolation
- Improve naming consistency and documentation across modules
- Add debug logging for strategy decision tracing

### M3 (Planned)

- Evaluate store scoping options beyond root store
- Evaluate `FixtureProvider#destroy(T instance)` evolution path
- Evaluate strategy discovery/loading conventions (for example via `ServiceLoader`)

## 12. Open Questions

- Should candidate selection be narrowed by stronger built-in filtering (for example provider identity), not only fixture type?
- Should compatibility key `tdl.junit5.fixture.default-strategy-class` remain beyond `1.1.x`?

