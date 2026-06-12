package io.github.ajmang.fixture.tdl;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;

public record InjectionMetadata(
        FixtureScopeContext.InjectionPoint injectionPoint,
        String injectionTarget,
        Integer parameterIndex
) {}