package io.github.ajmang.tdl.core.fixture.support;

import io.github.ajmang.tdl.core.fixture.CleanupPolicy;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.RetryPolicy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test provider that counts creations and optionally tracks destructions.
 */
public class CountingProvider implements FixtureProvider<String> {

    public static final AtomicInteger CREATIONS = new AtomicInteger();
    public static final AtomicInteger DESTRUCTIONS = new AtomicInteger();

    private final String prefix;
    private final CleanupPolicy cleanupPolicy;
    private final RetryPolicy retryPolicy;

    public CountingProvider() {
        this("fixture", CleanupPolicy.ALWAYS, RetryPolicy.none());
    }

    public CountingProvider(String prefix) {
        this(prefix, CleanupPolicy.ALWAYS, RetryPolicy.none());
    }

    public CountingProvider(CleanupPolicy cleanupPolicy) {
        this("fixture", cleanupPolicy, RetryPolicy.none());
    }

    public CountingProvider(RetryPolicy retryPolicy) {
        this("fixture", CleanupPolicy.ALWAYS, retryPolicy);
    }

    public CountingProvider(String prefix, CleanupPolicy cleanupPolicy, RetryPolicy retryPolicy) {
        this.prefix = prefix;
        this.cleanupPolicy = cleanupPolicy;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public String create() {
        return prefix + "-" + CREATIONS.incrementAndGet();
    }

    @Override
    public void destroy(String instance) {
        DESTRUCTIONS.incrementAndGet();
    }

    @Override
    public CleanupPolicy cleanupPolicy() {
        return cleanupPolicy;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public static void reset() {
        CREATIONS.set(0);
        DESTRUCTIONS.set(0);
    }
}
