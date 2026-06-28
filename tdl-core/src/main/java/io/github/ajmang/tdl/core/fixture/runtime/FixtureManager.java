package io.github.ajmang.tdl.core.fixture.runtime;

import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.RetryPolicy;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.share.ShareStrategy;

import java.util.List;
import java.util.ArrayList;
import java.time.Duration;

public class FixtureManager {

    private static <T> ManagedFixture<T> getTManagedFixture(
            T fixture,
            FixtureProvider<T> provider,
            FixtureScopeContext scopeContext
    ) {
        return new ManagedFixture<>(fixture, provider, scopeContext);
    }

    public <T> T getOrCreate(FixtureRequest<T> request, FixtureScopeContext scopeContext, FixtureStore store) {
        try {
            ShareStrategy strategy = resolveStrategy(request.strategyType());
            List<ManagedFixture<T>> candidates = collectCandidates(request, store);
            var selected = strategy.selectCachedFixture(scopeContext, request, candidates);
            if (selected.isPresent()) {
                System.out.println("[TDL] REUSE fixture: type=" + request.fixtureType().getName()
                        + ", provider=" + request.providerType().getSimpleName()
                        + ", scope=" + cacheScope(scopeContext));
                return castFixture(selected.get().fixture(), request.fixtureType());
            }

            ManagedFixture<T> createdFixture = createManagedFixture(request, scopeContext);
            String cacheKey = buildCacheKey(request, scopeContext);
            store.put(cacheKey, createdFixture);

            System.out.println("[TDL] CREATE fixture: type=" + request.fixtureType().getName()
                    + ", provider=" + request.providerType().getSimpleName()
                    + ", scope=" + cacheScope(scopeContext));
            return createdFixture.fixture();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create fixture for type: " + request.fixtureType().getName(), e);
        }
    }

    private <T> List<ManagedFixture<T>> collectCandidates(FixtureRequest<T> request, FixtureStore store) {
        List<ManagedFixture<T>> candidates = new ArrayList<>();
        for (ManagedFixture<?> managedFixture : store.listAll()) {
            if (request.fixtureType().isInstance(managedFixture.fixture())) {
                candidates.add(uncheckedCastManagedFixture(managedFixture));
            }
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private <T> ManagedFixture<T> uncheckedCastManagedFixture(ManagedFixture<?> managedFixture) {
        return (ManagedFixture<T>) managedFixture;
    }

    private <T> String buildCacheKey(FixtureRequest<T> request, FixtureScopeContext scopeContext) {
        return request.fixtureType().getName()
                + "::"
                + request.providerType().getName()
                + "::"
                + safeScopeId(scopeContext);
    }

    private String cacheScope(FixtureScopeContext scopeContext) {
        String scopeId = safeScopeId(scopeContext);
        // Shorten for readability: keep last 50 chars
        return scopeId.length() > 50 ? "..." + scopeId.substring(scopeId.length() - 50) : scopeId;
    }

    private String  safeScopeId(FixtureScopeContext scopeContext) {
        if (scopeContext == null || scopeContext.scopeId() == null) {
            return "global";
        }
        return scopeContext.scopeId();
    }

    private ShareStrategy resolveStrategy(Class<? extends ShareStrategy> strategyType) {
        try {
            var constructor = strategyType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create strategy: " + strategyType.getName(), e);
        }
    }

    private <T> ManagedFixture<T> createManagedFixture(FixtureRequest<T> request, FixtureScopeContext scopeContext) {
        try {
            var constructor = request.providerType().getDeclaredConstructor();
            constructor.setAccessible(true);
            FixtureProvider<T> provider = constructor.newInstance();
            T fixture = createWithRetry(provider, request.fixtureType());
            return getTManagedFixture(fixture, provider, scopeContext);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create fixture for type: " + request.fixtureType().getName(), e);
        }
    }

    private <T> T createWithRetry(FixtureProvider<T> provider, Class<T> fixtureType) {
        RetryPolicy policy = provider.retryPolicy();
        int attempts = 0;

        while (attempts < policy.maxAttempts()) {
            attempts++;
            try {
                return provider.create();
            } catch (Throwable throwable) {
                boolean canRetry = attempts < policy.maxAttempts() && policy.isRetryable(throwable);
                if (!canRetry) {
                    throw new RuntimeException(
                            "Failed to create fixture for type: "
                                    + fixtureType.getName()
                                    + " after "
                                    + attempts
                                    + " attempt(s)",
                            throwable);
                }
                System.out.println("[TDL] RETRY fixture: type=" + fixtureType.getName()
                        + ", attempt=" + attempts + "/" + policy.maxAttempts()
                        + ", reason=" + throwable.getClass().getSimpleName());
                sleepBackoff(policy.backoff());
            }
        }

        throw new RuntimeException("Failed to create fixture for type: " + fixtureType.getName());
    }

    private void sleepBackoff(Duration backoff) {
        if (backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during fixture retry backoff", e);
        }
    }

    private <T> T castFixture(Object fixture, Class<T> fixtureType) {
        return fixtureType.cast(fixture);
    }
}



