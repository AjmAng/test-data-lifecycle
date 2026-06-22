package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.share.ShareStrategy;

import java.util.List;
import java.util.Optional;

/**
 * Strategy with a private constructor, used to test error handling.
 */
public class NonInstantiableStrategy implements ShareStrategy {

    public NonInstantiableStrategy() {
        throw new UnsupportedOperationException("Cannot instantiate this strategy");
    }

    @Override
    public <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    ) {
        return Optional.empty();
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
        return true;
    }
}
