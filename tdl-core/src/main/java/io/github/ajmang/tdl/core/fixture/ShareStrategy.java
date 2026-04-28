package io.github.ajmang.tdl.core.fixture;

import java.util.*;


public interface ShareStrategy {

    <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    );

    boolean shouldCacheCreatedFixture(FixtureScopeContext context);

}