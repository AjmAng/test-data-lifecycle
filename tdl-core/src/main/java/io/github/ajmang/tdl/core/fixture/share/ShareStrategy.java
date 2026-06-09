package io.github.ajmang.tdl.core.fixture.share;

import io.github.ajmang.tdl.core.fixture.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;

import java.util.*;


public interface ShareStrategy {

    <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    );

    boolean shouldCacheCreatedFixture(FixtureScopeContext context);

}

