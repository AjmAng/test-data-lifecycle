package io.github.ajmang.tdl.core.fixture;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
                .filter(candidate -> hasCommonTag(tagsOf(candidate.producerContext()), consumerTags))
                .findFirst();
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
        return context.injectionPoint() != FixtureScopeContext.InjectionPoint.PARAMETER
                && !tagsOf(context).isEmpty();
    }

    private boolean hasCommonTag(Set<String> producerTags, Set<String> consumerTags) {
        if (producerTags.isEmpty() || consumerTags.isEmpty()) {
            return false;
        }
        for (String consumerTag : consumerTags) {
            if (producerTags.contains(consumerTag)) {
                return true;
            }
        }
        return false;
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

