package io.github.ajmang.tdl.core.fixture.runtime;

import java.util.List;
import java.util.function.Supplier;

public interface FixtureStore {

    ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier);

    List<ManagedFixture<?>> listAll();

    void put(String key, ManagedFixture<?> fixture);

    /**
     * Removes a fixture from the store without destroying it.
     * This allows fixtures to be retained when test fails.
     * @param key the fixture cache key
     * @return the removed fixture, or null if not found
     */
    ManagedFixture<?> remove(String key);
}



