package io.github.ajmang.tdl.junit5examples.tags;

import io.github.ajmang.fixture.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.SharedByTagStrategy;
import io.github.ajmang.tdl.junit5examples.basic.DirectoryResource;
import io.github.ajmang.tdl.junit5examples.basic.DirectoryResourceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SharedByFrameworkTagStrategyTest {

    private static String integrationId;
    private static String billingId;

    @Fixture(provider = DirectoryResourceProvider.class, strategy = SharedByTagStrategy.class)
    private DirectoryResource resource;

    @Test
    @Order(1)
    @Tag("integration")
    void firstIntegrationTestCreatesFixture() {
        Assertions.assertNotNull(resource);
        integrationId = resource.id();
    }

    @Test
    @Order(2)
    @Tag("integration")
    void secondIntegrationTestReusesFixture() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(integrationId, resource.id());
    }

    @Test
    @Order(3)
    @Tag("billing")
    void billingTagDoesNotReuseIntegrationFixture() {
        Assertions.assertNotNull(resource);
        billingId = resource.id();
        Assertions.assertNotEquals(integrationId, billingId);
    }

    @Test
    @Order(4)
    @Tag("billing")
    void secondBillingTestReusesBillingFixture() {
        Assertions.assertNotNull(resource);
        Assertions.assertEquals(billingId, resource.id());
    }

    @Test
    @Order(5)
    @Tag("integration")
    void parameterInjectionStillIsolated(
            @Fixture(provider = DirectoryResourceProvider.class, strategy = SharedByTagStrategy.class)
            DirectoryResource parameterResource
    ) {
        Assertions.assertNotNull(parameterResource);
        Assertions.assertNotEquals(integrationId, parameterResource.id());
    }
}

