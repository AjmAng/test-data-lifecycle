package io.github.ajmang.tdl.testngexamples.dir;

import io.github.ajmang.testng.fixture.FixtureListener;
import io.github.ajmang.tdl.core.fixture.Fixture;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.nio.file.Files;

@Listeners(FixtureListener.class)
public class FieldInjectionSharedTest {

    private static String firstId;

    @Fixture(provider = DirectoryResourceProvider.class)
    private DirectoryResource resource;

    @Test(priority = 1)
    public void first() {
        Assert.assertNotNull(resource);
        Assert.assertTrue(Files.exists(resource.markerFile()));
        firstId = resource.id();
    }

    @Test(priority = 2, dependsOnMethods = "first")
    public void secondShouldShareSameFixtureInClass() {
        Assert.assertNotNull(resource);
        Assert.assertEquals(resource.id(), firstId);
    }
}

