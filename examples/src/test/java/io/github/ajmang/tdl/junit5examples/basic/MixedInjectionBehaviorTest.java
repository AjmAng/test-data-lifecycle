package io.github.ajmang.tdl.junit5examples.basic;


import io.github.ajmang.fixture.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureExtension.class)
class MixedInjectionBehaviorTest {

    @Fixture(provider = DirectoryResourceProvider.class)
    private DirectoryResource fieldResource;

    @Test
    void field_and_parameter_should_not_be_forced_same_instance(
            @Fixture(provider = DirectoryResourceProvider.class)
            DirectoryResource paramResource
    ) {
        Assertions.assertNotNull(fieldResource);
        Assertions.assertNotNull(paramResource);
        Assertions.assertNotEquals(fieldResource.id(), paramResource.id());
    }
}
