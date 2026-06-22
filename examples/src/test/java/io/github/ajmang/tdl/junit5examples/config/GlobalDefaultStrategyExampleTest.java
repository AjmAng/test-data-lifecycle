package io.github.ajmang.tdl.junit5examples.config;

import io.github.ajmang.fixture.tdl.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.FixtureTags;
import io.github.ajmang.tdl.core.fixture.share.SharedByTagStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example demonstrating that a global default strategy can be configured
 * via {@code junit-platform.properties}.
 *
 * <p>This test relies on {@code src/test/resources/junit-platform.properties}
 * setting {@code tdl.fixture.default-strategy-class} to
 * {@link SharedByTagStrategy}. Both methods use the same tag so they should
 * share the fixture even though the annotation does not explicitly set a
 * strategy.</p>
 */
@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalDefaultStrategyExampleTest {

    private static final String STRATEGY_KEY = "tdl.fixture.default-strategy-class";
    private static String firstId;

    @BeforeAll
    static void setGlobalStrategy() {
        System.setProperty(STRATEGY_KEY, SharedByTagStrategy.class.getName());
    }

    @AfterAll
    static void clearGlobalStrategy() {
        System.clearProperty(STRATEGY_KEY);
    }

    @Fixture(provider = SharedResourceProvider.class)
    SharedResource resource;

    @Test
    @Order(1)
    @FixtureTags("global-config")
    void firstTestCreatesFixture() {
        Assertions.assertNotNull(resource);
        firstId = resource.id();
    }

    @Test
    @Order(2)
    @FixtureTags("global-config")
    void secondTestReusesFixture() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(firstId, resource.id());
    }

    public record SharedResource(String id) {
    }

    public static class SharedResourceProvider implements FixtureProvider<SharedResource> {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        public SharedResource create() {
            return new SharedResource("shared-" + counter.incrementAndGet());
        }

        @Override
        public void destroy(SharedResource instance) {
        }
    }
}
