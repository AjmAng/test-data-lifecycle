package io.github.ajmang.tdl.core.fixture;

import java.util.Collections;
import java.util.Set;

public record FixtureScopeContext(
        String engineRunId,
        String testClassName,
        String testMethodName,
        String junitUniqueId,
        InjectionPoint injectionPoint,
        String injectionTarget,
        Integer parameterIndex,
        long threadId
) {


    public enum InjectionPoint {
        FIELD,
        PARAMETER
    }
}
