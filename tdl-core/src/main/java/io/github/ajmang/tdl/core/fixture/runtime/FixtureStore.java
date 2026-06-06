package io.github.ajmang.tdl.core.fixture.runtime;

import java.util.List;
import java.util.function.Supplier;

public interface FixtureStore {

    ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier);

    List<ManagedFixture<?>> listAll();

    void put(String key, ManagedFixture<?> fixture);
}


