package io.github.ajmang.tdl.junit5examples.cleanup;

import io.github.ajmang.tdl.core.fixture.CleanupPolicy;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A provider that tracks how many times destroy() is called.
 * Used to verify CleanupPolicy behavior in integration tests.
 */
public class TrackingCleanupProvider implements FixtureProvider<String> {

    static final AtomicInteger destroyCount = new AtomicInteger(0);
    private final CleanupPolicy policy;


    public TrackingCleanupProvider(CleanupPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String create() {
        return "tracked-fixture-" + System.nanoTime();
    }

    @Override
    public void destroy(String instance) {
        destroyCount.incrementAndGet();
    }

    @Override
    public CleanupPolicy cleanupPolicy() {
        return policy;
    }

    static void resetDestroyCount() {
        destroyCount.set(0);
    }

    static int getDestroyCount() {
        return destroyCount.get();
    }
}
