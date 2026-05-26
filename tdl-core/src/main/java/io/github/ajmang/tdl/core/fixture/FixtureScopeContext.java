package io.github.ajmang.tdl.core.fixture;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record FixtureScopeContext(
        String engineRunId,
        String testClassName,
        String testMethodName,
        String junitUniqueId,
        InjectionPoint injectionPoint,
        String injectionTarget,
        Integer parameterIndex,
        long threadId,
        Set<String> tags,
        Set<String> annotations,
        String packageName
) {

    public FixtureScopeContext {
        tags = tags == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        annotations = annotations == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(annotations));
    }


    public enum InjectionPoint {
        FIELD,
        PARAMETER
    }
}
