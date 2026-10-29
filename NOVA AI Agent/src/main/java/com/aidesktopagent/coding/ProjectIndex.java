package com.aidesktopagent.coding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lightweight project index (Day 21): packages, classes, tests within workspace only.
 */
public final class ProjectIndex {

    private static final Pattern PKG = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLS = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?(?:final\\s+)?(?:abstract\\s+)?"
                    + "(?:class|interface|enum|record)\\s+(\\w+)", Pattern.MULTILINE);

    public record TypeInfo(String packageName, String simpleName, String relativePath, boolean test) {
        public String fqn() {
            return packageName == null || packageName.isBlank() ? simpleName : packageName + "." + simpleName;
        }
    }

    private final ProjectWorkspace workspace;
    private final List<TypeInfo> types = new ArrayList<>();
    private final Map<String, List<TypeInfo>> byPackage = new LinkedHashMap<>();

    public ProjectIndex(ProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    public void rebuild() throws IOException {
        types.clear();
        byPackage.clear();
        try (Stream<Path> walk = Files.walk(workspace.getRoot())) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .filter(workspace::contains)
                    .forEach(this::indexFileQuietly);
        }
    }

    private void indexFileQuietly(Path file) {
        try {
            String rel = workspace.getRoot().relativize(file).toString().replace('\\', '/');
            if (rel.contains("/target/") || rel.contains("/.git/")) {
                return;
            }
            boolean test = rel.contains("/src/test/");
            String content = Files.readString(file);
            String pkg = "";
            Matcher pm = PKG.matcher(content);
            if (pm.find()) {
                pkg = pm.group(1);
            }
            Matcher cm = CLS.matcher(content);
            while (cm.find()) {
                TypeInfo t = new TypeInfo(pkg, cm.group(1), rel, test);
                types.add(t);
                byPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(t);
            }
        } catch (Exception ignored) {
        }
    }

    public List<TypeInfo> allTypes() {
        return List.copyOf(types);
    }

    public List<String> packages() {
        return List.copyOf(byPackage.keySet());
    }

    public Optional<TypeInfo> findBySimpleName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String n = name.toLowerCase(Locale.ROOT);
        return types.stream().filter(t -> t.simpleName().toLowerCase(Locale.ROOT).equals(n)).findFirst();
    }

    public String summary() {
        long tests = types.stream().filter(TypeInfo::test).count();
        return "Index: " + types.size() + " types in " + byPackage.size()
                + " packages (" + tests + " test types)";
    }
}
