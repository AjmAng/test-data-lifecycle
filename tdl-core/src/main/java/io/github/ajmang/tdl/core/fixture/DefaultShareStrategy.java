package io.github.ajmang.tdl.core.fixture;

import java.util.List;
import java.util.Optional;

public class DefaultShareStrategy implements ShareStrategy {

    @Override
    public <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    ) {
        if (context.injectionPoint() == FixtureScopeContext.InjectionPoint.PARAMETER) {
            return Optional.empty();
        }
        return candidates.stream().findFirst();
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
        return context.injectionPoint() != FixtureScopeContext.InjectionPoint.PARAMETER;
    }
}