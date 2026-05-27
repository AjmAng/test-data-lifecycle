package io.github.ajmang.tdl.core.fixture;

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

    @Override
    public void close() {
        if (provider != null) {
            provider.destroy(fixture);
        }
    }
}


