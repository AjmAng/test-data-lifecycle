package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for CleanupPolicy functionality.
 * Verifies that ALWAYS, ON_SUCCESS, and NEVER policies work as expected.
 */
class CleanupPolicyTest {

    @Test
    void testAlwaysCleanupPolicy() {
        DestructionTrackingProvider provider = new DestructionTrackingProvider(CleanupPolicy.ALWAYS);
        String fixture = provider.create();
        Assertions.assertNotNull(fixture);
        Assertions.assertEquals(CleanupPolicy.ALWAYS, provider.cleanupPolicy());

        // Clean up
        provider.destroy(fixture);
        Assertions.assertTrue(provider.wasDestroyed.get());
    }

    @Test
    void testNeverCleanupPolicy() {
        NeverDestroyProvider provider = new NeverDestroyProvider();
        String fixture = provider.create();
        Assertions.assertNotNull(fixture);
        Assertions.assertEquals(CleanupPolicy.NEVER, provider.cleanupPolicy());
    }

    @Test
    void testOnSuccessCleanupPolicy() {
        OnSuccessDestroyProvider provider = new OnSuccessDestroyProvider();
        String fixture = provider.create();
        Assertions.assertNotNull(fixture);
        Assertions.assertEquals(CleanupPolicy.ON_SUCCESS, provider.cleanupPolicy());
    }

    @Test
    void testDefaultCleanupPolicy() {
        // Test that default CleanupPolicy is ALWAYS
        Assertions.assertEquals(CleanupPolicy.ALWAYS, CleanupPolicy.defaultPolicy());
    }


    @Test
    void testCleanupPolicyInManagedFixture() {
        DestructionTrackingProvider provider = new DestructionTrackingProvider(CleanupPolicy.ON_SUCCESS);
        String fixture = provider.create();

        // Create a ManagedFixture with the provider
        ManagedFixture<String> managed = new ManagedFixture<>(fixture, provider, null);

        // Verify we can access the provider's cleanup policy through ManagedFixture
        Assertions.assertNotNull(managed.provider());
        Assertions.assertEquals(CleanupPolicy.ON_SUCCESS, managed.provider().cleanupPolicy());

        // Verify close() calls destroy
        managed.close();
        Assertions.assertTrue(provider.wasDestroyed.get());
    }

    @Test
    void testProviderDefaultCleanupPolicy() {
        // Test that provider's default cleanup policy method returns ALWAYS
        DefaultCleanupProvider provider = new DefaultCleanupProvider();
        String fixture = provider.create();
        Assertions.assertNotNull(fixture);

        // Default should be ALWAYS
        Assertions.assertEquals(CleanupPolicy.ALWAYS, provider.cleanupPolicy());
    }

    // Test fixtures and providers

    static class DestructionTrackingProvider implements FixtureProvider<String> {
        final AtomicBoolean wasDestroyed = new AtomicBoolean(false);
        private final CleanupPolicy policy;



        DestructionTrackingProvider(CleanupPolicy policy) {
            this.policy = policy;
        }

        @Override
        public String create() {
            return "fixture-" + UUID.randomUUID().toString().substring(0, 8);
        }

        @Override
        public void destroy(String instance) {
            wasDestroyed.set(true);
        }

        @Override
        public CleanupPolicy cleanupPolicy() {
            return policy;
        }
    }

    static class NeverDestroyProvider extends DestructionTrackingProvider {
        NeverDestroyProvider() {
            super(CleanupPolicy.NEVER);
        }
    }

    static class OnSuccessDestroyProvider extends DestructionTrackingProvider {
        OnSuccessDestroyProvider() {
            super(CleanupPolicy.ON_SUCCESS);
        }
    }

    static class DefaultCleanupProvider implements FixtureProvider<String> {
        final AtomicBoolean wasDestroyed = new AtomicBoolean(false);

        @Override
        public String create() {
            return "fixture-" + UUID.randomUUID().toString().substring(0, 8);
        }

        @Override
        public void destroy(String instance) {
            wasDestroyed.set(true);
        }
        // Uses default cleanupPolicy() which returns ALWAYS
    }
}


