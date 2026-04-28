package io.github.ajmang.tdl.core.fixture;

public interface FixtureProvider<T> {
    T create();

    void destroy(T instance);
}

