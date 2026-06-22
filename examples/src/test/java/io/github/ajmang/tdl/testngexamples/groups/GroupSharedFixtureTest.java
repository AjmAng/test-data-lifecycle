package io.github.ajmang.tdl.testngexamples.groups;

import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import io.github.ajmang.testng.fixture.FixtureListener;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TestNG example showing field injection and fixture sharing within a test class.
 */
@Listeners(FixtureListener.class)
public class GroupSharedFixtureTest {

    private static String firstId;
    private static String secondId;

    @Fixture(provider = GroupResourceProvider.class)
    GroupResource resource;

    @Test(priority = 1, groups = "integration")
    public void first() {
        Assert.assertNotNull(resource);
        firstId = resource.id();
    }

    @Test(priority = 2, groups = "integration")
    public void second() {
        Assert.assertNotNull(resource);
        secondId = resource.id();
        Assert.assertEquals(secondId, firstId);
    }

    public record GroupResource(String id) {
    }

    public static class GroupResourceProvider implements FixtureProvider<GroupResource> {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        public GroupResource create() {
            return new GroupResource("group-res-" + counter.incrementAndGet());
        }

        @Override
        public void destroy(GroupResource instance) {
        }
    }
}
