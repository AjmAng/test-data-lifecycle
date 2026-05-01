package io.github.ajmang.tdl.junit5examples.basic;

import java.nio.file.Path;

public record DirectoryResource(String id, Path dir, Path markerFile) {
}