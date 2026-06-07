package io.github.ajmang.tdl.junit5examples.tags;

import io.github.ajmang.fixture.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.api.Fixture;
import io.github.ajmang.tdl.core.fixture.strategy.FixtureTags;
import io.github.ajmang.tdl.core.fixture.strategy.SharedByTagStrategy;
import io.github.ajmang.tdl.junit5examples.basic.DirectoryResource;
import io.github.ajmang.tdl.junit5examples.basic.DirectoryResourceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SharedByFrameworkTagStrategyTest {

    private static String integrationId;
    private static String billingId;
    private static String combinedId;

    @Fixture(provider = DirectoryResourceProvider.class, strategy = SharedByTagStrategy.class)
    private DirectoryResource resource;

    @Test
    @Order(1)
    @FixtureTags("integration")
    void firstIntegrationTestCreatesFixture() {
        Assertions.assertNotNull(resource);
        integrationId = resource.id();
    }

    @Test
    @Order(2)
    @FixtureTags("integration")
    void secondIntegrationTestReusesFixture() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(integrationId, resource.id());
    }

    @Test
    @Order(3)
    @FixtureTags("billing")
    void billingTagDoesNotReuseIntegrationFixture() {
        Assertions.assertNotNull(resource);
        billingId = resource.id();
        Assertions.assertNotEquals(integrationId, billingId);
    }

    @Test
    @Order(4)
    @FixtureTags("billing")
    void secondBillingTestReusesBillingFixture() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(billingId, resource.id());
    }

    @Test
    @Order(5)
    @FixtureTags({"integration", "billing"})
    void combinedTagsCreateDedicatedFixture() {
        Assertions.assertNotNull(resource);
        combinedId = resource.id();
        Assertions.assertNotEquals(integrationId, combinedId);
        Assertions.assertNotEquals(billingId, combinedId);
    }

    @Test
    @Order(6)
    @FixtureTags("integration")
    void integrationStillUsesSingleTagFixtureWhenAvailable() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(integrationId, resource.id());
    }

    @Test
    @Order(7)
    @FixtureTags("integration")
    void parameterInjectionStillIsolated(
            @Fixture(provider = DirectoryResourceProvider.class, strategy = SharedByTagStrategy.class)
            DirectoryResource parameterResource
    ) {
        Assertions.assertNotNull(parameterResource);
        Assertions.assertNotEquals(integrationId, parameterResource.id());
    }
}

