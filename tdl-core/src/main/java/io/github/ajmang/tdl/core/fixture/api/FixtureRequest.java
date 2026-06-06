package io.github.ajmang.tdl.core.fixture.api;

import io.github.ajmang.tdl.core.fixture.strategy.ShareStrategy;

public record FixtureRequest<T>(
        Class<T> fixtureType,
        Class<? extends FixtureProvider<T>> providerType,
        Class<? extends ShareStrategy> strategyType) {

    @SuppressWarnings("unchecked")
    public static <T> FixtureRequest<T> of(
            Class<T> fixtureType,
            Class<? extends FixtureProvider<?>> providerType,
            Class<? extends ShareStrategy> strategyType) {
        return new FixtureRequest<>(
                fixtureType,
                (Class<? extends FixtureProvider<T>>) providerType,
                strategyType
        );
    }
}


