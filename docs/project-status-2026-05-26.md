# Project Status Audit (2026-05-26)

This document captures the current implementation gap analysis and the next execution plan.

## Scope

- Reviewed: `README.md`, `docs/roadmap-rfc.md`, `docs/share-strategy-rfc.md`, `docs/engine-level-prefetch-rfc.md`
- Scanned modules: `tdl-core`, `tdl-junit5`, `tdl-testng`, `examples`
- Baseline check: `./mvnw.cmd -q test` (passed in current workspace snapshot)

## Priority Checklist

### P0 (Start Here, Non Engine-Level)

- [x] Align documentation baseline with current naming (`ShareStrategy`, `DefaultShareStrategy`)
- [ ] Implement strategy milestone items from roadmap
  - [ ] `SharedByTagStrategy`
  - [ ] `CompositeStrategy`
  - [ ] Built-in strategy library (`PerTestMethod`, `PerTestClass`, `PerThread`, `Global`)
- [x] Enrich `FixtureScopeContext` with roadmap fields
  - [x] `tags`
  - [x] `annotations`
  - [x] `packageName`
- [x] Bridge new scope fields from adapters
  - [x] `Junit5FixtureManager`
  - [x] `TestngFixtureManager`

### P1


- [ ] Broaden regression coverage for adapter behavior and strategy precedence
- [ ] Add examples for retry, eager fetch, tag/group sharing, cleanup behavior
- [ ] Add module-level usage docs for JUnit5/TestNG integration details

### P2

- [ ] Lifecycle observability/reporting (cache hit/miss/create/destroy)
- [ ] Parallel prefetch, timeout/cancel controls
- [ ] Clarify Java compatibility policy and compatibility-key deprecation timeline
- [ ] Engine-level prefetch package (`ScopeLevel`, `EngineFixtureStore`, listeners, ServiceLoader)

## Current Findings (Condensed)

- `README.md` had outdated terminology and link references (now aligned).
- `tdl-core` still contains `EagerFetchManager` as an empty placeholder (de-prioritized for now).
- Engine-level prefetch classes listed in RFC are not yet implemented (moved out of current P0).
- `FixtureScopeContext` enrichment (`tags`/`annotations`/`packageName`) is implemented and wired in JUnit5/TestNG adapters.
- Retry foundation already exists (`RetryPolicy`, `FixtureProvider#retryPolicy`, retry tests).

## Next P0 Execution Order (Updated)

1. Implement `SharedByTagStrategy` minimal usable behavior
2. Add `CompositeStrategy` with fallback chain
3. Add built-in strategies (`PerTestMethod`, `PerTestClass`, `PerThread`, `Global`)
4. Add regression tests for strategy selection and cache-write decisions
5. Sync README/RFC examples with new built-in strategy names and precedence

## Validation Commands

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q -pl tdl-core test
.\mvnw.cmd -q -pl tdl-junit5 test
.\mvnw.cmd -q -pl tdl-testng test
```

