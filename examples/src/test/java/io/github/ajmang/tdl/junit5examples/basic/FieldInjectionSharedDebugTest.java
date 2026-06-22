package io.github.ajmang.tdl.junit5examples.basic;

import io.github.ajmang.fixture.tdl.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FieldInjectionSharedDebugTest {

    private static String firstId;

    @Fixture(provider = CountingProvider.class)
    String resource;

    @Test
    @Order(1)
    void first() {
        Assertions.assertNotNull(resource);
        Assertions.assertTrue(resource.startsWith("res-"));
        firstId = resource;
        System.out.println("[DEBUG] first() - resource=" + resource + ", firstId=" + firstId);
    }

    @Test
    @Order(2)
    void second_should_share_same_fixture_in_class() {
        Assertions.assertNotNull(resource);
        System.out.println("[DEBUG] second() - resource=" + resource + ", firstId=" + firstId);
        Assertions.assertEquals(firstId, resource);
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
