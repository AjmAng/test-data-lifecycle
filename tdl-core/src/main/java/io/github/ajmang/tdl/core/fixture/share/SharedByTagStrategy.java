package io.github.ajmang.tdl.core.fixture.share;

import io.github.ajmang.tdl.core.fixture.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Shares fixtures by framework tags/groups using superset matching.
 * Rule 1: A producer with tags {A,B} can serve consumers needing {A} or {B}.
 * Rule 2: Producers {A} and {B} cannot serve a consumer needing {A,B}; a new fixture is created.
 */
public class SharedByTagStrategy implements ShareStrategy {

    private static final String[] TAG_KEYS = new String[] {
            FixtureScopeContext.ATTR_TAGS,
            "junit.tags",
            "testng.groups"
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

        Set<String> consumerTags = tagsOf(context);
        if (consumerTags.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(candidate -> candidate.producerContext() != null)
                .filter(candidate -> isProducerSuperset(tagsOf(candidate.producerContext()), consumerTags))
                .min(Comparator.comparingInt(candidate -> tagsOf(candidate.producerContext()).size()));
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
        return context.injectionPoint() != FixtureScopeContext.InjectionPoint.PARAMETER
                && !tagsOf(context).isEmpty();
    }

    private boolean isProducerSuperset(Set<String> producerTags, Set<String> consumerTags) {
        if (producerTags.isEmpty() || consumerTags.isEmpty()) {
            return false;
        }
        return producerTags.containsAll(consumerTags);
    }

    private Set<String> tagsOf(FixtureScopeContext context) {
        for (String key : TAG_KEYS) {
            Set<String> tags = readStringSet(context.attributes().get(key));
            if (!tags.isEmpty()) {
                return tags;
            }
        }
        return Set.of();
    }

    private Set<String> readStringSet(Object source) {
        if (!(source instanceof Collection<?> values)) {
            return Set.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String normalized = String.valueOf(value).trim();
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
        return tags;
    }
}

