package com.aidesktopagent.coding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight view of a Java project's build system and layout.
 */
public final class ProjectMetadata {

    public enum BuildSystem {
        MAVEN,
        GRADLE,
        UNKNOWN
    }

    private final Path root;
    private final String name;
    private final BuildSystem buildSystem;
    private final List<Path> sourceRoots;
    private final List<Path> testRoots;
    private final boolean hasPom;
    private final boolean hasGradle;

    public ProjectMetadata(Path root, String name, BuildSystem buildSystem,
                           List<Path> sourceRoots, List<Path> testRoots,
                           boolean hasPom, boolean hasGradle) {
        this.root = root;
        this.name = name;
        this.buildSystem = buildSystem;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.testRoots = List.copyOf(testRoots);
        this.hasPom = hasPom;
        this.hasGradle = hasGradle;
    }

    public static ProjectMetadata scan(ProjectWorkspace workspace) {
        Path root = workspace.getRoot();
        boolean pom = Files.isRegularFile(root.resolve("pom.xml"));
        boolean gradle = Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"));
        BuildSystem bs = pom ? BuildSystem.MAVEN
                : (gradle ? BuildSystem.GRADLE : BuildSystem.UNKNOWN);

        String name = root.getFileName() != null ? root.getFileName().toString() : root.toString();
        if (pom) {
            name = tryReadArtifactId(root.resolve("pom.xml")).orElse(name);
        }

        List<Path> sources = new ArrayList<>();
        List<Path> tests = new ArrayList<>();
        Path mainJava = root.resolve("src/main/java");
        Path testJava = root.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            sources.add(mainJava);
        }
        if (Files.isDirectory(testJava)) {
            tests.add(testJava);
        }
        // Multi-module heuristic: immediate child dirs with src/main/java
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(d -> Files.isDirectory(d.resolve("src/main/java")))
                    .forEach(d -> {
                        Path s = d.resolve("src/main/java");
                        if (!sources.contains(s)) {
                            sources.add(s);
                        }
                        Path t = d.resolve("src/test/java");
                        if (Files.isDirectory(t) && !tests.contains(t)) {
                            tests.add(t);
                        }
                    });
        } catch (Exception ignored) {
            // best-effort scan
        }

        return new ProjectMetadata(root, name, bs, sources, tests, pom, gradle);
    }

    private static Optional<String> tryReadArtifactId(Path pom) {
        try {
            String xml = Files.readString(pom);
            int start = xml.indexOf("<artifactId>");
            int end = xml.indexOf("</artifactId>");
            if (start >= 0 && end > start) {
                return Optional.of(xml.substring(start + 12, end).trim());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    public Path getRoot() {
        return root;
    }

    public String getName() {
        return name;
    }

    public BuildSystem getBuildSystem() {
        return buildSystem;
    }

    public List<Path> getSourceRoots() {
        return sourceRoots;
    }

    public List<Path> getTestRoots() {
        return testRoots;
    }

    public boolean hasPom() {
        return hasPom;
    }

    public boolean hasGradle() {
        return hasGradle;
    }

    public String summary() {
        return "Project '" + name + "' (" + buildSystem + ") at " + root
                + " — source roots: " + sourceRoots.size()
                + ", test roots: " + testRoots.size();
    }

    @Override
    public String toString() {
        return summary();
    }
}
