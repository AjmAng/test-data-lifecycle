package io.github.ajmang.tdl.testngexamples.dir;

import io.github.ajmang.tdl.core.fixture.FixtureProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class DirectoryResourceProvider implements FixtureProvider<DirectoryResource> {

    @Override
    public DirectoryResource create() {
        try {
            String id = UUID.randomUUID().toString();
            Path dir = Files.createTempDirectory("tdl-testng-dir-" + id + "-");
            Path marker = dir.resolve("resource-" + id + ".txt");
            Files.writeString(marker, "id=" + id);
            return new DirectoryResource(id, dir, marker);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory resource", e);
        }
    }

    @Override
    public void destroy(DirectoryResource fixture) {
        try {
            if (Files.exists(fixture.markerFile())) {
                Files.delete(fixture.markerFile());
            }
            if (Files.exists(fixture.dir())) {
                Files.delete(fixture.dir());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to clean directory resource", e);
        }
    }
}

