package io.github.ajmang.tdl.junit5examples.biztag;

import io.github.ajmang.fixture.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.UseFixtureCollectors;
import io.github.ajmang.tdl.junit5examples.basic.DirectoryResource;
import io.github.ajmang.tdl.junit5examples.basic.DirectoryResourceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureExtension.class)
@UseFixtureCollectors(BizTagFixtureContextCollector.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BizTagHierarchyShareStrategyTest {

    private static String firstId;
    private static String secondId;

    @Fixture(provider = DirectoryResourceProvider.class, strategy = BizTagHierarchyShareStrategy.class)
    private DirectoryResource resource;

    @Test
    @Order(1)
    @BizTag("team/shared/orders")
    void creates_fixture_for_specific_tag_path() {
        Assertions.assertNotNull(resource);
        firstId = resource.id();
    }

    @Test
    @Order(2)
    @BizTag("team/shared")
    void broader_consumer_tag_reuses_more_specific_fixture() {
        Assertions.assertNotNull(resource);
        secondId = resource.id();
        Assertions.assertEquals(firstId, secondId);
    }

    @Test
    @Order(3)
    @BizTag("team/payments")
    void unrelated_tag_does_not_reuse_existing_fixture() {
        Assertions.assertNotNull(resource);
        Assertions.assertNotEquals(firstId, resource.id());
    }

    @Test
    @Order(4)
    @BizTag("team/shared")
    void parameter_injection_stays_isolated(
            @Fixture(provider = DirectoryResourceProvider.class, strategy = BizTagHierarchyShareStrategy.class)
            DirectoryResource parameterResource
    ) {
        Assertions.assertNotNull(parameterResource);
        Assertions.assertNotEquals(secondId, parameterResource.id());
    }
}


