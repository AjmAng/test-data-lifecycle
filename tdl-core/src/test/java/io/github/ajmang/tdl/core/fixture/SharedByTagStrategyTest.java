package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.api.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.api.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureManager;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureStore;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.strategy.SharedByTagStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class SharedByTagStrategyTest {

    private final FixtureManager fixtureManager = new FixtureManager();
    private final InMemoryFixtureStore fixtureStore = new InMemoryFixtureStore();

    @Test
    void sameTagShouldReuseCachedFixture() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, fieldScope("scope-a", Set.of("integration")), fixtureStore);
        String reused = fixtureManager.getOrCreate(request, fieldScope("scope-b", Set.of("integration")), fixtureStore);

        Assertions.assertSame(first, reused);
        Assertions.assertEquals(1, CountingProvider.creations.get());
    }

    @Test
    void differentTagsShouldNotReuseFixture() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, fieldScope("scope-a", Set.of("billing")), fixtureStore);
        String second = fixtureManager.getOrCreate(request, fieldScope("scope-b", Set.of("inventory")), fixtureStore);

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, CountingProvider.creations.get());
    }

    @Test
    void combinedTagFixtureShouldServeSingleTagConsumers() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String combined = fixtureManager.getOrCreate(request, fieldScope("scope-ab", Set.of("TagA", "TagB")), fixtureStore);
        String tagAOnly = fixtureManager.getOrCreate(request, fieldScope("scope-a", Set.of("TagA")), fixtureStore);
        String tagBOnly = fixtureManager.getOrCreate(request, fieldScope("scope-b", Set.of("TagB")), fixtureStore);

        Assertions.assertSame(combined, tagAOnly);
        Assertions.assertSame(combined, tagBOnly);
        Assertions.assertEquals(1, CountingProvider.creations.get());
    }

    @Test
    void splitTagFixturesShouldNotServeCombinedTagConsumer() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String tagAOnly = fixtureManager.getOrCreate(request, fieldScope("scope-a", Set.of("TagA")), fixtureStore);
        String tagBOnly = fixtureManager.getOrCreate(request, fieldScope("scope-b", Set.of("TagB")), fixtureStore);
        String combined = fixtureManager.getOrCreate(request, fieldScope("scope-ab", Set.of("TagA", "TagB")), fixtureStore);

        Assertions.assertNotSame(tagAOnly, combined);
        Assertions.assertNotSame(tagBOnly, combined);
        Assertions.assertEquals(3, CountingProvider.creations.get());
    }

    @Test
    void shouldFollowRequestedTagSharingRules() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String combinedProducer = fixtureManager.getOrCreate(request, fieldScope("scope-ab", Set.of("TagA", "TagB")), fixtureStore);
        String tagAConsumer = fixtureManager.getOrCreate(request, fieldScope("scope-a", Set.of("TagA")), fixtureStore);
        String tagBConsumer = fixtureManager.getOrCreate(request, fieldScope("scope-b", Set.of("TagB")), fixtureStore);

        Assertions.assertSame(combinedProducer, tagAConsumer);
        Assertions.assertSame(combinedProducer, tagBConsumer);

        InMemoryFixtureStore splitStore = new InMemoryFixtureStore();
        CountingProvider.creations.set(0);

        String onlyTagA = fixtureManager.getOrCreate(request, fieldScope("scope-a-1", Set.of("TagA")), splitStore);
        String onlyTagB = fixtureManager.getOrCreate(request, fieldScope("scope-b-1", Set.of("TagB")), splitStore);
        String combinedConsumer = fixtureManager.getOrCreate(request, fieldScope("scope-ab-1", Set.of("TagA", "TagB")), splitStore);

        Assertions.assertNotSame(onlyTagA, combinedConsumer);
        Assertions.assertNotSame(onlyTagB, combinedConsumer);
        Assertions.assertEquals(3, CountingProvider.creations.get());
    }

    @Test
    void parameterInjectionShouldStayIsolatedEvenWithSameTag() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, parameterScope("scope-a", Set.of("integration")), fixtureStore);
        String second = fixtureManager.getOrCreate(request, parameterScope("scope-b", Set.of("integration")), fixtureStore);

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, CountingProvider.creations.get());
    }

    @Test
    void untaggedFieldShouldNotBeCached() {
        CountingProvider.creations.set(0);

        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, fieldScope("scope-a", Set.of()), fixtureStore);
        String second = fixtureManager.getOrCreate(request, fieldScope("scope-b", Set.of()), fixtureStore);

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, CountingProvider.creations.get());
    }

    private FixtureScopeContext fieldScope(String scopeId, Set<String> tags) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.FIELD,
                "resource",
                null,
                Map.of(FixtureScopeContext.ATTR_TAGS, tags)
        );
    }

    private FixtureScopeContext parameterScope(String scopeId, Set<String> tags) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.PARAMETER,
                "testMethod",
                0,
                Map.of(FixtureScopeContext.ATTR_TAGS, tags)
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

        @Override
        public ManagedFixture<?> remove(String key) {
            return fixtures.remove(key);
        }
    }
}

