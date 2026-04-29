package io.github.ajmang.tdl.testngexamples.dir;

import java.nio.file.Path;

public record DirectoryResource(String id, Path dir, Path markerFile) {
}

