package io.github.ajmang.tdl.core.fixture.support;

import io.github.ajmang.tdl.core.fixture.runtime.FixtureStore;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Simple in-memory fixture store for unit tests.
 */
public class InMemoryFixtureStore implements FixtureStore {

    private final Map<String, ManagedFixture<?>> fixtures = new LinkedHashMap<>();

    @Override
    public ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier) {
        return fixtures.computeIfAbsent(key, ignored -> supplier.get());
    }

    @Override
    public List<ManagedFixture<?>> listAll() {
        return new ArrayList<>(fixtures.values());
    }

    @Override
    public void put(String key, ManagedFixture<?> fixture) {
        fixtures.put(key, fixture);
    }

    @Override
    public ManagedFixture<?> remove(String key) {
        return fixtures.remove(key);
    }

    public int size() {
        return fixtures.size();
    }

    public boolean containsKey(String key) {
        return fixtures.containsKey(key);
    }
}
