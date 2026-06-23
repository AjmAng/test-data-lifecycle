package io.github.ajmang.fixture.tdl;

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
 * Integration tests for {@link FixtureExtension} field and parameter injection.
 */
@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FixtureExtensionIntegrationTest {

    private static String fieldFixtureId;

    @Fixture(provider = CountingProvider.class)
    String fieldResource;

    @Test
    @Order(1)
    void fieldInjectionCreatesFixture() {
        Assertions.assertNotNull(fieldResource);
        Assertions.assertTrue(fieldResource.startsWith("res-"));
        fieldFixtureId = fieldResource;
    }

    @Test
    @Order(2)
    void fieldInjectionReusesSameFixtureWithinClass() {
        Assertions.assertNotNull(fieldResource);
        Assertions.assertEquals(fieldFixtureId, fieldResource);
    }

    @Test
    @Order(3)
    void parameterInjectionIsIsolated(
            @Fixture(provider = CountingProvider.class) String a,
            @Fixture(provider = CountingProvider.class) String b
    ) {
        Assertions.assertNotNull(a);
        Assertions.assertNotNull(b);
        Assertions.assertNotEquals(a, b);
    }

    public static class CountingProvider implements FixtureProvider<String> {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        public String create() {
            return "res-" + counter.incrementAndGet();
        }

        @Override
        public void destroy(String instance) {
        }
    }
}
