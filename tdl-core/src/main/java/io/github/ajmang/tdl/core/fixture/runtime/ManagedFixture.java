package io.github.ajmang.tdl.core.fixture.runtime;

import io.github.ajmang.tdl.core.fixture.CleanupPolicy;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;

public final class ManagedFixture<T> implements AutoCloseable {

    private final T fixture;
    private final FixtureProvider<T> provider;
    private final FixtureScopeContext producerContext;

    public ManagedFixture(T fixture, FixtureProvider<T> provider, FixtureScopeContext producerContext) {
        this.fixture = fixture;
        this.provider = provider;
        this.producerContext = producerContext;
    }

    public T fixture() {
        return fixture;
    }

    public FixtureScopeContext producerContext() {
        return producerContext;
    }

    public FixtureProvider<T> provider() {
        return provider;
    }

    public CleanupPolicy cleanupPolicy() {
        return provider.cleanupPolicy();
    }

    /**
     * Determines whether this fixture should be destroyed based on its cleanup policy
     * and the test outcome.
     *
     * @param testPassed true if the test passed, false if it failed
     * @return true if the fixture should be destroyed, false if it should be retained
     */
    public boolean shouldDestroy(boolean testPassed) {
        return provider.cleanupPolicy().shouldDestroy(testPassed);
    }

    @Override
    public void close() {
        if (provider != null) {
            System.out.println("[TDL] DESTROY fixture: type=" + fixture.getClass().getName());
            provider.destroy(fixture);
        }
    }
}




