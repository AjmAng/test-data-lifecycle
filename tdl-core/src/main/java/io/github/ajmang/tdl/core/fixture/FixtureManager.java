package io.github.ajmang.tdl.core.fixture;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.time.Duration;

public class FixtureManager {

    private static <T> ManagedFixture<T> getTManagedFixture(T fixture, FixtureProvider<T> provider) {
        return new ManagedFixture<>(fixture, provider);
    }

    public <T> T getOrCreate(FixtureRequest<T> request, FixtureScopeContext scopeContext, FixtureStore store) {
        try {
            ShareStrategy strategy = resolveStrategy(request.strategyType());
            List<ManagedFixture<T>> candidates = collectCandidates(request, store);
            var selected = strategy.selectCachedFixture(scopeContext, request, candidates);
            if (selected.isPresent()) {
                return castFixture(selected.get().fixture(), request.fixtureType());
            }

            ManagedFixture<T> createdFixture = createManagedFixture(request);
            if (strategy.shouldCacheCreatedFixture(scopeContext)) {
                String cacheKey = buildCacheKey(request, scopeContext);
                store.put(cacheKey, createdFixture);
            }

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
                + safeScopeId(scopeContext)
                + "::"
                + UUID.randomUUID();
    }

    private String safeScopeId(FixtureScopeContext scopeContext) {
        if (scopeContext == null || scopeContext.junitUniqueId() == null) {
            return "global";
        }
        return scopeContext.junitUniqueId();
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

    private <T> ManagedFixture<T> createManagedFixture(FixtureRequest<T> request) {
        try {
            FixtureProvider<T> provider = request.providerType().getDeclaredConstructor().newInstance();
            T fixture = createWithRetry(provider, request.fixtureType());
            return getTManagedFixture(fixture, provider);

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

