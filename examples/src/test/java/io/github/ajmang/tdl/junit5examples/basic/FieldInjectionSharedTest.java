package io.github.ajmang.tdl.junit5examples.basic;

import io.github.ajmang.fixture.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FieldInjectionSharedTest {

    private static String firstId;

    @Fixture(provider = DirectoryResourceProvider.class)
    private DirectoryResource resource;

    @Test
    @Order(1)
    void first() {
        Assertions.assertNotNull(resource);
        firstId = resource.id();
    }

    @Test
    @Order(2)
    void second_should_share_same_fixture_in_class() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(firstId, resource.id());
    }
}