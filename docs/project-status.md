# 项目进展与下一步计划

> 更新时间：2026-06-11
> 基于当前 HEAD（含 `FixtureScopeContext` deprecated 方法清理）

---

## 1. 项目概况

`test-data-lifecycle` (TDL) 是一个管理测试资源生命周期的实验性 Java 框架。核心目标是让**"测试数据是否共享、何时隔离、何时销毁"**变成可配置的策略，而不是散落在各个测试类中的临时逻辑。

当前处于 **早期迭代阶段**，主版本目标为 `1.0.0-SNAPSHOT`。核心架构已落地，主要链路可运行，全项目测试通过。

---

## 2. 已完成的功能

### 2.1 核心架构（`tdl-core`）

- **策略优先架构**：`FixtureProvider` + `ShareStrategy` + `FixtureManager` 已落地。
- **可扩展上下文模型**：`FixtureScopeContext` 采用精简的固定字段 + opaque `attributes` map 设计，使框架适配器可以传递框架特定的元数据，而无需频繁改动 core API。
- **生命周期管理**：`ManagedFixture` 实现了 `AutoCloseable`，通过 JUnit 5 Store 的 `CloseableResource` 机制在 scope 结束时自动销毁。
- **生产者上下文感知**：缓存的 fixture 保留创建时的 `producerContext`，自定义策略可以据此对比"当前需求上下文"与"生产时上下文"来做更精细的重用决策。

### 2.2 Retry 重试机制（Feature 1）

- `RetryPolicy` value object 已定义，支持 `maxAttempts`、`backoff`、`retryOn` 配置。
- `FixtureManager.createWithRetry()` 已实现重试循环和退避等待。
- 有对应单测 `FixtureManagerRetryTest` 验证重试行为。

### 2.3 Tag-Based Sharing（Feature 3）

- `FixtureScopeContext` 已通过 attributes 支持 `tags`、`annotations`、`packageName`。
- **内置策略**：
  - `DefaultShareStrategy`：安全默认值，字段注入可复用缓存，参数注入保持隔离。
  - `SharedByTagStrategy`：基于 tag 超集匹配共享 fixture（生产者 tag {A,B} 可服务消费者 {A} 或 {B}）。
  - `IsolatedByLabelStrategy`：基于 label 做隔离匹配。
- JUnit 5 adapter 已集成框架 tag 收集（`@Tag` 和 `@FixtureTags`）。
- 完整端到端示例：`BizTagHierarchyShareStrategyTest` 演示了通过 `ServiceLoader` 收集自定义 `@BizTag` 注解并实现层级共享策略。

### 2.4 Context Collector 扩展机制

- `FixtureContextCollector` 接口 + `FixtureContextCollectorRegistry` 已落地。
- `@UseFixtureCollectors` 支持在测试类上显式绑定自定义 collector。
- 支持 `ServiceLoader` 自动发现 classpath 中的 collector 实现。

### 2.5 Class-Level Prefetch / Eager Fetch（Feature 2，可选能力）

- `@Fixture(eagerFetch = EagerFetch.ENABLED)` 已可用。
- `FixtureExtension` 实现了 `BeforeAllCallback`，可在类级别扫描并预取标记了 eager fetch 的字段。
- `beforeEach` 中会优先从 prefetch 缓存中获取，避免重复创建。
- ⚠️ `EagerFetchManager` 目前为空壳类（仅注释，无实际逻辑），预取逻辑仍散落在 JUnit 5 adapter 中。
- 该能力定位为**可选优化**（opt-in），不作为 1.0 主线阻塞项；保持单类范围即可。

### 2.6 框架适配器

- **JUnit 5（`tdl-junit5`）**：`FixtureExtension` 支持字段注入、参数注入、class-level prefetch（可选）、默认策略外部配置（`junit-platform.properties` 或 system property）。
- **TestNG（`tdl-testng`）**：`FixtureListener` + `TestngFixtureManager` 基础实现已存在。

### 2.7 测试与 CI

- 全项目测试通过（core 层 6 个单测 + examples 层 15 个集成/示例测试）。
- GitHub Actions CI 已配置（Maven + JDK 17）。

### 2.8 近期清理（2026-06-11）

- **删除 `FixtureScopeContext` deprecated 方法**：`engineRunId()`、`testClassName()`、`testMethodName()`、`junitUniqueId()`、`threadId()`、`tags()` 已移除。框架主张通过 `attributes()` 直接访问上下文数据，减少 core API 表面积。
- **同步更新测试**：`FixtureScopeContextTest`、`FixtureManagerRetryTest`、`ProducerContextAwareReuseTest` 已改用新的 5 参数 record 构造函数，将旧参数放入 `attributes` map。
- **清理 dead code**：移除了 `mergeLegacyAttributes`、`asString`、`readStringSet` 等仅被 deprecated 方法使用的私有辅助方法。

---

## 3. 未完成 / 待完善的功能

| 功能 | 状态 | 说明 |
|------|------|------|
| **CleanupPolicy（Feature 4）** | ⚠️ 部分落地 | `CleanupPolicy` enum（`ALWAYS` / `ON_SUCCESS` / `NEVER`）已定义，`FixtureProvider` 接口已增加 `cleanup()` 默认方法，但**实际销毁/保留逻辑尚未实现**。`FixtureStore` 缺少 `remove`/`destroy` 方法，`FixtureExtension` 也未跟踪测试成败。 |
| **CompositeStrategy** | ⛔ 取消 | 不再作为 roadmap 目标，避免在早期阶段引入额外策略编排复杂度。 |
| **内置策略库** | ✅ 收敛 | 内置策略以 `DefaultShareStrategy` + `SharedByTagStrategy` 为主，不再推进大而全策略集合。 |
| **Debug Event Listener** | ❌ 未开始 | cache hit / miss / create / destroy 生命周期事件监听与报告未实现。 |
| **Engine-Level Prefetch** | ⛔ 取消 | 不再推进跨类/跨测试计划预取；保留当前 class-level prefetch（可选）即可。 |

---

## 4. 已知问题与注意事项

### 4.1 `buildCacheKey()` 使用 `UUID.randomUUID()`

`FixtureManager.buildCacheKey()` 当前实现为：

```java
private <T> String buildCacheKey(FixtureRequest<T> request, FixtureScopeContext scopeContext) {
    return request.fixtureType().getName()
            + "::"
            + request.providerType().getName()
            + "::"
            + safeScopeId(scopeContext)
            + "::"
            + UUID.randomUUID();  // <-- 问题在这里
}
```

每次调用都会生成新的 UUID，这意味着 `store.put(cacheKey, ...)` 写入的 key 永远不会与后续读取匹配。虽然 `selectCachedFixture` 仍能按策略过滤候选，但**缓存层本身可能在重复创建 fixture**，造成内存泄漏或资源浪费。需要确认这是有意为之（例如为了强制每个请求独立）还是实现缺陷。

### 4.2 `FixtureStore` 接口缺少 `remove` / `destroy`

当前 `FixtureStore` 只有：

```java
ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier);
List<ManagedFixture<?>> listAll();
void put(String key, ManagedFixture<?> fixture);
```

若要实现 `CleanupPolicy.ON_SUCCESS`（测试失败时保留 fixture），需要能**将 fixture 从 store 中移除但不销毁**。当前接口不支持此操作。

### 4.3 `EagerFetchManager` 空壳

该类仅有类级 Javadoc，无任何字段或方法。`FixtureExtension` 中的 prefetch 逻辑应逐步迁移至此，以便 TestNG 等其他适配器复用。

### 4.4 AI 生成代码的审查债务

项目前期代码大量由 AI 辅助生成，存在以下潜在风险：
- **设计一致性**：部分类/方法命名、异常消息风格不统一。
- **边界条件**：重试、并发、异常传播路径的测试覆盖可能不足。
- **文档同步**：代码变动后 Javadoc 和 RFC 文档偶有滞后（如 `CleanupPolicy` enum 已落地，但 roadmap 中状态未同步更新）。
- **API 兼容性**：`FixtureScopeContext` 从多字段 record 重构为精简 record + attributes 的过程中，旧构造函数和 accessor 方法已逐步废弃并删除，但需确认外部使用者是否受影响。

---

## 5. 下一步建议

以下按主题分组，不严格排序。建议根据实际使用场景选择切入点。

### 🔴 主题 A：代码审查与技术债清理

> 适合想先稳底子、再往前走的阶段。

1. **人工 Review AI 生成的核心代码**
   - 重点看 `FixtureManager`（缓存、重试、并发路径）、`FixtureExtension`（JUnit 5 回调顺序）、`ShareStrategy` 各实现（边界条件）。
   - 检查异常消息是否统一、日志是否足够排查问题。

2. **修复 `buildCacheKey()` 的缓存稳定性**
   - 评估 `UUID.randomUUID()` 是否必要。
   - 如果目标是让相同 scope + type + provider 的请求命中同一缓存项，应使用稳定的 scope key 替代 UUID。
   - 同步调整相关测试，确保缓存命中行为可被验证。

3. **填实 `EagerFetchManager`**
   - 将 `FixtureExtension` 中的字段扫描、预取执行、缓存回查逻辑下放到 `EagerFetchManager`。
   - 目标仅限于提升 class-level prefetch 的可维护性，不扩展到引擎级预取。

4. **统一代码风格与命名**
   - 例如 `tdl-junit5` 包名是 `io.github.ajmang.fixture`（缺少 `tdl`），而 `tdl-core` 和 `tdl-testng` 都含 `tdl`，可评估是否统一。

---

### 🔴 主题 B：CleanupPolicy 落地（Feature 4）

> 这是从"演示框架"到"可用框架"的关键一步。

当前 `CleanupPolicy` enum 和 `FixtureProvider.cleanup()` 方法已存在，但逻辑未打通：

1. **Store 层**：扩展 `FixtureStore` 接口，增加 `remove(String key)`（从 store 移除但不销毁）。
2. **JUnit 5 Adapter**：
   - 让 `FixtureExtension` 实现 `TestExecutionExceptionHandler`，跟踪当前测试是否抛异常。
   - 在 `AfterEach` / `AfterAll` 中根据 `CleanupPolicy` 决定：
     - `ALWAYS`：正常走 `CloseableResource` 销毁。
     - `ON_SUCCESS`：测试失败时调用 `store.remove()` 保留 fixture；成功时正常销毁。
     - `NEVER`：创建后始终不销毁（调试用）。
3. **Reporter**：提供一个默认的 `RetainedFixtureReporter`，在控制台输出被保留的 fixture 信息，方便开发者事后排查。
4. **边界情况**：`CleanupPolicy` 仅作用于**非共享 fixture**（per-test-method / per-parameter）。共享 fixture 仍按自然 scope 生命周期销毁，避免资源耗尽。

---

### 🟡 主题 C：补充示例（Examples）

> 适合想验证框架在实际场景中好不好用的阶段。示例写得越多，越容易暴露 API 设计上的摩擦点。

建议补充的场景：

| 示例 | 目的 |
|------|------|
| **Retry 示例** | 演示 `RetryPolicy` 在 flaky provider（如模拟网络抖动）下的行为 |
| **CleanupPolicy 示例** | 演示 `ALWAYS` / `ON_SUCCESS` / `NEVER` 对临时目录/容器的影响 |
| **多线程并发示例** | 验证 `DefaultShareStrategy` 在线程隔离下的正确性 |
| **TestNG 完整示例** | 当前 TestNG 示例比 JUnit 5 少，可补一个字段注入 + group sharing 的完整 case |
| **自定义 Collector 示例** | 演示如何通过 `FixtureContextCollector` 注入业务自定义上下文（如租户 ID、环境标识） |
| **性能基准示例** | 对比"无缓存每次新建" vs "tag-based 共享"在大量测试下的耗时差异 |

---

### 🟡 主题 D：TestNG 适配层补齐

- 当前 TestNG 只有字段注入，缺少参数注入。
- `TestngFixtureManager` 的 scope 收集逻辑（tag / group / annotation）可能不如 JUnit 5 完整，需对齐。
- 若团队有 TestNG 存量测试，这个方向的 ROI 较高。

---

### 🟢 主题 E：调试与可观测性

- **Debug Event Listener**：添加 `FixtureEventListener` 接口（或基于 SLF4J 的日志），在 create、destroy、cache hit、cache miss、retry 时触发事件。
- **运行报告**：在测试运行结束时输出 fixture 创建次数、缓存命中率、重试次数、被保留的 fixture 列表。

---

### 🟢 主题 F：文档与规范

- **补 `AGENTS.md`**：为后续 AI 辅助开发提供项目专属规范（包结构、命名约定、测试写法）。
- **补 `share-strategy-rfc.md`**：`README` 引用了该文件，但当前目录下未找到。
- **更新 `README`**：移除关于 `engine-level-prefetch` 的已取消内容，补充 CleanupPolicy 和最新 API 用法。

---

## 6. 相关文档

- `README.md` — 项目定位、模块说明、快速开始
- `docs/roadmap-rfc.md` — 1.0.0 / 1.1.0 / 1.2.0 里程碑与功能设计（Retry、Prefetch、Tag Sharing、Cleanup Policy）
- `docs/share-strategy-rfc.md` — 被 README 引用，但当前目录下未找到（需确认是否存在或已合并到 roadmap）

---

## 7. 测试速查

快速验证：

```powershell
.\mvnw.cmd -q test
```

当前全项目测试通过，总耗时约 4-5 秒。
