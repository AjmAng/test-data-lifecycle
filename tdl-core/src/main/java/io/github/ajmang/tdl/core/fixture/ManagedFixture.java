package io.github.ajmang.tdl.core.fixture;

public final class ManagedFixture<T> implements AutoCloseable {

    private final T fixture;
    private final FixtureProvider<T> provider;

    public ManagedFixture(T fixture, FixtureProvider<T> provider) {
        this.fixture = fixture;
        this.provider = provider;
    }

    public T fixture() {
        return fixture;
    }

    @Override
    public void close() {
        if (provider != null) {
            provider.destroy(fixture);
        }
    }
}


