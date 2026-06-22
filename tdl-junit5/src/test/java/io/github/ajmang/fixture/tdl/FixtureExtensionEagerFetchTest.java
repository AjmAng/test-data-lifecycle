package io.github.ajmang.fixture.tdl;

import io.github.ajmang.tdl.core.fixture.EagerFetch;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for class-level eager fetch / prefetch.
 */
@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FixtureExtensionEagerFetchTest {

    private static String prefetchedId;
    private static final AtomicInteger creationCounter = new AtomicInteger();

    @Fixture(provider = PrefetchProvider.class, eagerFetch = EagerFetch.ENABLED)
    String eagerResource;

    @Test
    @Order(1)
    void firstTestUsesPrefetchedFixture() {
        Assertions.assertNotNull(eagerResource);
        prefetchedId = eagerResource;
        Assertions.assertEquals(1, creationCounter.get(), "Eager fetch should create exactly one fixture");
    }

    @Test
    @Order(2)
    void secondTestReusesPrefetchedFixture() {
        Assertions.assertNotNull(eagerResource);
        Assertions.assertEquals(prefetchedId, eagerResource);
        Assertions.assertEquals(1, creationCounter.get(), "Eager fetch should not create a second fixture");
    }

    public static class PrefetchProvider implements FixtureProvider<String> {

        @Override
        public String create() {
            return "prefetch-" + creationCounter.incrementAndGet();
        }

        @Override
        public void destroy(String instance) {
        }
    }
}
