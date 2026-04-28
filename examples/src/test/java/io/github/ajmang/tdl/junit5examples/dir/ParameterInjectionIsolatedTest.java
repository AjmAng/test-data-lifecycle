package io.github.ajmang.tdl.junit5examples.dir;


import io.github.ajmang.fixture.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureExtension.class)
class ParameterInjectionIsolatedTest {

    @Test
    void parameter_should_never_share(
            @Fixture(provider = DirectoryResourceProvider.class)
            DirectoryResource r1,
            @Fixture(provider = DirectoryResourceProvider.class)
            DirectoryResource r2
    ) {
        Assertions.assertNotEquals(r1.id(), r2.id());
    }
}
