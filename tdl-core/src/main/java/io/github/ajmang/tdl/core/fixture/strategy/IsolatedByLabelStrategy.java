package io.github.ajmang.tdl.core.fixture.strategy;

import io.github.ajmang.tdl.core.fixture.api.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;

import java.util.List;
import java.util.Optional;

public class IsolatedByLabelStrategy implements ShareStrategy {

    private static final String[] LABEL_KEYS = new String[]{
            "tdl.label",
            "junit.label",
            "testng.label",
            "label"
    };

    @Override
    public <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    ) {
        if (context.injectionPoint() == FixtureScopeContext.InjectionPoint.PARAMETER) {
            return Optional.empty();
        }

        Optional<String> consumerLabel = labelOf(context);
        if (consumerLabel.isEmpty()) {
            return candidates.stream().findFirst();
        }

        return candidates.stream()
                .filter(candidate -> candidate.producerContext() != null)
                .filter(candidate -> labelOf(candidate.producerContext())
                        .map(candidateLabel -> !candidateLabel.equals(consumerLabel.get()))
                        .orElse(false))
                .findFirst();
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
        return context.injectionPoint() != FixtureScopeContext.InjectionPoint.PARAMETER;
    }

    private Optional<String> labelOf(FixtureScopeContext context) {
        for (String key : LABEL_KEYS) {
            Object value = context.attributes().get(key);
            if (value == null) {
                continue;
            }
            String normalized = String.valueOf(value).trim();
            if (!normalized.isEmpty()) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }
}
