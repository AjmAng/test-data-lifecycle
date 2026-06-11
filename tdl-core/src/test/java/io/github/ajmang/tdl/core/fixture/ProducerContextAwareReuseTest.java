package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureManager;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureStore;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.share.ShareStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class ProducerContextAwareReuseTest {

    private final FixtureManager fixtureManager = new FixtureManager();
    private final FixtureStore fixtureStore = new InMemoryFixtureStore();

    @Test
    void strategyCanInspectProducerContextOnCachedFixture() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                ProducerTagReuseStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, scopeWithTag("team/shared/orders"), fixtureStore);
        String reused = fixtureManager.getOrCreate(request, scopeWithTag("team/shared"), fixtureStore);
        String isolated = fixtureManager.getOrCreate(request, scopeWithTag("team/payments"), fixtureStore);

        Assertions.assertSame(first, reused);
        Assertions.assertNotSame(first, isolated);
        Assertions.assertEquals(2, CountingProvider.creations.get());
    }

    private FixtureScopeContext scopeWithTag(String tag) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FixtureScopeContext.ATTR_TEST_CLASS_NAME, "example.TestClass");
        attributes.put(FixtureScopeContext.ATTR_TEST_METHOD_NAME, "testMethod");
        attributes.put(FixtureScopeContext.ATTR_THREAD_ID, 1L);
        attributes.put(FixtureScopeContext.ATTR_TAGS, Set.of());
        attributes.put(FixtureScopeContext.ATTR_ANNOTATIONS, Set.of());
        attributes.put(FixtureScopeContext.ATTR_PACKAGE_NAME, "example");
        attributes.put("biz.tags", Set.of(tag));
        return new FixtureScopeContext(
                "uid-" + tag,
                FixtureScopeContext.InjectionPoint.FIELD,
                "fixtureField",
                null,
                attributes
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

    static class ProducerTagReuseStrategy implements ShareStrategy {
        @Override
        public <T> Optional<ManagedFixture<T>> selectCachedFixture(
                FixtureScopeContext context,
                FixtureRequest<T> request,
                List<ManagedFixture<T>> candidates
        ) {
            Set<String> requestedTags = tagsOf(context);
            return candidates.stream()
                    .filter(candidate -> candidate.producerContext() != null)
                    .filter(candidate -> supportsRequestedTags(tagsOf(candidate.producerContext()), requestedTags))
                    .findFirst();
        }

        @Override
        public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
            return true;
        }

        private boolean supportsRequestedTags(Set<String> producerTags, Set<String> requestedTags) {
            for (String requested : requestedTags) {
                boolean matched = producerTags.stream().anyMatch(produced -> produced.equals(requested) || produced.startsWith(requested + "/"));
                if (!matched) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        private Set<String> tagsOf(FixtureScopeContext context) {
            Object raw = context.attributes().get("biz.tags");
            if (raw instanceof Set<?> values) {
                return (Set<String>) values;
            }
            return Set.of();
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

