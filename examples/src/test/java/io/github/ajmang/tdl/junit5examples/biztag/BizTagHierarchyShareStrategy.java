package io.github.ajmang.tdl.junit5examples.biztag;

import io.github.ajmang.tdl.core.fixture.api.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.strategy.ShareStrategy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class BizTagHierarchyShareStrategy implements ShareStrategy {

    @Override
    public <T> Optional<ManagedFixture<T>> selectCachedFixture(
            FixtureScopeContext context,
            FixtureRequest<T> request,
            List<ManagedFixture<T>> candidates
    ) {
        if (context.injectionPoint() == FixtureScopeContext.InjectionPoint.PARAMETER) {
            return Optional.empty();
        }

        Set<String> consumerTags = readTags(context.attributes());
        if (consumerTags.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(candidate -> canContribute(candidate, consumerTags))
                .findFirst();
    }

    @Override
    public boolean shouldCacheCreatedFixture(FixtureScopeContext context) {
        return context.injectionPoint() != FixtureScopeContext.InjectionPoint.PARAMETER;
    }

    private <T> boolean canContribute(ManagedFixture<T> candidate, Set<String> consumerTags) {
        FixtureScopeContext producerContext = candidate.producerContext();
        if (producerContext == null) {
            return false;
        }
        Set<String> producerTags = readTags(producerContext.attributes());
        if (producerTags.isEmpty()) {
            return false;
        }
        for (String requiredTag : consumerTags) {
            boolean satisfied = producerTags.stream().anyMatch(providedTag -> isContained(providedTag, requiredTag));
            if (!satisfied) {
                return false;
            }
        }
        return true;
    }

    private Set<String> readTags(Map<String, Object> attributes) {
        if (attributes == null) {
            return Set.of();
        }
        Object rawTags = attributes.get(BizTagFixtureContextCollector.BIZ_TAGS_KEY);
        if (!(rawTags instanceof Collection<?> values)) {
            return Set.of();
        }

        Set<String> tags = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof String tag && !tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private boolean isContained(String producerTag, String consumerTag) {
        if (producerTag.equals(consumerTag)) {
            return true;
        }
        return producerTag.startsWith(consumerTag + "/");
    }
}

