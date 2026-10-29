package com.aidesktopagent.coding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Detects installed IDEs (IntelliJ IDEA, VS Code) via common install paths and PATH.
 */
public final class IdeDetector {

    public enum IdeKind {
        INTELLIJ,
        VSCODE,
        UNKNOWN
    }

    public record IdeInstallation(IdeKind kind, String displayName, Path executable) {
    }

    private final List<Path> extraPaths;

    public IdeDetector() {
        this(List.of());
    }

    public IdeDetector(List<Path> extraPaths) {
        this.extraPaths = List.copyOf(extraPaths);
    }

    public List<IdeInstallation> detect() {
        List<IdeInstallation> found = new ArrayList<>();
        for (Path p : candidateIntelliJ()) {
            if (Files.isExecutable(p) || Files.isRegularFile(p)) {
                found.add(new IdeInstallation(IdeKind.INTELLIJ, "IntelliJ IDEA", p));
                break;
            }
        }
        for (Path p : candidateVsCode()) {
            if (Files.isExecutable(p) || Files.isRegularFile(p)) {
                found.add(new IdeInstallation(IdeKind.VSCODE, "Visual Studio Code", p));
                break;
            }
        }
        which("idea").ifPresent(p -> {
            if (found.stream().noneMatch(i -> i.kind() == IdeKind.INTELLIJ)) {
                found.add(new IdeInstallation(IdeKind.INTELLIJ, "IntelliJ IDEA", p));
            }
        });
        which("code").ifPresent(p -> {
            if (found.stream().noneMatch(i -> i.kind() == IdeKind.VSCODE)) {
                found.add(new IdeInstallation(IdeKind.VSCODE, "Visual Studio Code", p));
            }
        });
        for (Path extra : extraPaths) {
            if (Files.isRegularFile(extra)) {
                String name = extra.getFileName().toString().toLowerCase(Locale.ROOT);
                IdeKind k = name.contains("code") ? IdeKind.VSCODE
                        : name.contains("idea") ? IdeKind.INTELLIJ : IdeKind.UNKNOWN;
                found.add(new IdeInstallation(k, extra.getFileName().toString(), extra));
            }
        }
        return found;
    }

    public Optional<IdeInstallation> find(IdeKind kind) {
        return detect().stream().filter(i -> i.kind() == kind).findFirst();
    }

    private static List<Path> candidateIntelliJ() {
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> list = new ArrayList<>();
        if (os.contains("mac")) {
            list.add(Path.of("/Applications/IntelliJ IDEA.app/Contents/MacOS/idea"));
            list.add(Path.of("/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea"));
        } else if (os.contains("win")) {
            list.add(Path.of("C:/Program Files/JetBrains/IntelliJ IDEA/bin/idea64.exe"));
        } else {
            list.add(Path.of(home, ".local/share/JetBrains/Toolbox/scripts/idea"));
            list.add(Path.of("/usr/bin/idea"));
            list.add(Path.of("/opt/intellij/bin/idea.sh"));
        }
        return list;
    }

    private static List<Path> candidateVsCode() {
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> list = new ArrayList<>();
        if (os.contains("mac")) {
            list.add(Path.of("/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"));
        } else if (os.contains("win")) {
            list.add(Path.of(home, "AppData/Local/Programs/Microsoft VS Code/bin/code.cmd"));
        } else {
            list.add(Path.of("/usr/bin/code"));
            list.add(Path.of("/usr/share/code/bin/code"));
            list.add(Path.of(home, ".vscode-server/bin/code"));
        }
        return list;
    }

    private static Optional<Path> which(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !out.isBlank()) {
                return Optional.of(Path.of(out));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}
