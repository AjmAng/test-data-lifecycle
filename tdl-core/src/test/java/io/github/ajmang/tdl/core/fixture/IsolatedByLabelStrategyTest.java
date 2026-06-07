package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.api.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.api.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureManager;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureStore;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.strategy.IsolatedByLabelStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class IsolatedByLabelStrategyTest {

    private final FixtureManager fixtureManager = new FixtureManager();
    private final InMemoryFixtureStore fixtureStore = new InMemoryFixtureStore();

    @Test
    void sameLabelShouldNotReuseFixture() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                IsolatedByLabelStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, fieldScope("scope-a", "label-A"), fixtureStore);
        String second = fixtureManager.getOrCreate(request, fieldScope("scope-b", "label-A"), fixtureStore);

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, CountingProvider.creations.get());
    }

    @Test
    void differentLabelCanReuseFixture() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                IsolatedByLabelStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, fieldScope("scope-a", "label-A"), fixtureStore);
        String second = fixtureManager.getOrCreate(request, fieldScope("scope-b", "label-B"), fixtureStore);

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, CountingProvider.creations.get());
    }

    @Test
    void parameterInjectionShouldAlwaysBeIsolated() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                IsolatedByLabelStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, parameterScope("scope-a", "label-A"), fixtureStore);
        String second = fixtureManager.getOrCreate(request, parameterScope("scope-b", "label-B"), fixtureStore);

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, CountingProvider.creations.get());
    }

    private FixtureScopeContext fieldScope(String scopeId, String label) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.FIELD,
                "resource",
                null,
                Map.of("tdl.label", label)
        );
    }

    private FixtureScopeContext parameterScope(String scopeId, String label) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.PARAMETER,
                "testMethod",
                0,
                Map.of("tdl.label", label)
        );
    }

    static class CountingProvider implements FixtureProvider<String> {
        static final AtomicInteger creations = new AtomicInteger();

        @Override
        public String create() {
            return "fixture-" + creations.incrementAndGet();
        }

        @Override
        public void destroy(String instance) {
        }
    }

    static class InMemoryFixtureStore implements FixtureStore {
        private final Map<String, ManagedFixture<?>> fixtures = new LinkedHashMap<>();

        @Override
        public ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier) {
            return fixtures.computeIfAbsent(key, ignored -> supplier.get());
        }

        @Override
        public List<ManagedFixture<?>> listAll() {
            return new ArrayList<>(fixtures.values());
        }

        @Override
        public void put(String key, ManagedFixture<?> fixture) {
            fixtures.put(key, fixture);
        }
    }
}

