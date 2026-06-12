package io.github.ajmang.tdl.junit5examples.cleanup;

import io.github.ajmang.fixture.tdl.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.CleanupPolicy;
import io.github.ajmang.tdl.core.fixture.Fixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for CleanupPolicy behavior.
 * Verifies that:
 * - CleanupPolicy.ALWAYS: destroy() is always called
 * - CleanupPolicy.NEVER: destroy() is never called
 * - CleanupPolicy.ON_SUCCESS: destroy() is called only when test passes
 */
@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CleanupPolicyIntegrationTest {

    private static int beforeAllDestroyCount;

    @BeforeAll
    static void setup() {
        TrackingCleanupProvider.resetDestroyCount();
    }

    @Test
    @Order(1)
    void always_cleanup_policy_destroys_after_test(
            @Fixture(provider = TrackingCleanupProviderWithAlways.class)
            String fixture
    ) {
        Assertions.assertNotNull(fixture);
        Assertions.assertTrue(fixture.startsWith("tracked-fixture-"));
    }

    @Test
    @Order(2)
    void always_cleanup_should_have_destroyed() {
        Assertions.assertTrue(TrackingCleanupProvider.getDestroyCount() > 0,
                "ALWAYS policy should have called destroy()");
        beforeAllDestroyCount = TrackingCleanupProvider.getDestroyCount();
    }

    @Test
    @Order(3)
    void never_cleanup_policy_should_not_destroy(
            @Fixture(provider = TrackingCleanupProviderWithNever.class)
            String fixture
    ) {
        Assertions.assertNotNull(fixture);
        Assertions.assertTrue(fixture.startsWith("tracked-fixture-"));
    }

    @Test
    @Order(4)
    void never_cleanup_should_not_have_increased_destroy_count() {
        Assertions.assertEquals(beforeAllDestroyCount, TrackingCleanupProvider.getDestroyCount(),
                "NEVER policy should not have called destroy()");
    }

    @Test
    @Order(5)
    void on_success_cleanup_policy_destroys_on_pass(
            @Fixture(provider = TrackingCleanupProviderWithOnSuccess.class)
            String fixture
    ) {
        Assertions.assertNotNull(fixture);
        Assertions.assertTrue(fixture.startsWith("tracked-fixture-"));
    }

    @Test
    @Order(6)
    void on_success_cleanup_should_have_destroyed_after_pass() {
        Assertions.assertTrue(TrackingCleanupProvider.getDestroyCount() > beforeAllDestroyCount,
                "ON_SUCCESS policy should have called destroy() after passing test");
    }

    public static class TrackingCleanupProviderWithAlways extends TrackingCleanupProvider {
        public TrackingCleanupProviderWithAlways() {
            super(CleanupPolicy.ALWAYS);
        }
    }

    public static class TrackingCleanupProviderWithNever extends TrackingCleanupProvider {
        public TrackingCleanupProviderWithNever() {
            super(CleanupPolicy.NEVER);
        }
    }

    public static class TrackingCleanupProviderWithOnSuccess extends TrackingCleanupProvider {
        public TrackingCleanupProviderWithOnSuccess() {
            super(CleanupPolicy.ON_SUCCESS);
        }
    }
}
