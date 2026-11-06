package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.security.SafePathResolver;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * File and folder operations — <strong>workspace-bound only</strong>.
 * Absolute paths and escapes are rejected. Overwrites require explicit parameter.
 */
public final class FileExecutor implements ActionExecutor {

    private final LoggingService log;
    private final SafePathResolver paths;

    public FileExecutor(SafePathResolver paths) {
        this(LoggingService.forComponent("FileExecutor"), paths);
    }

    public FileExecutor(LoggingService log, SafePathResolver paths) {
        this.log = log != null ? log : LoggingService.forComponent("FileExecutor");
        if (paths == null) {
            throw new IllegalArgumentException("SafePathResolver is required");
        }
        this.paths = paths;
    }

    /** @deprecated Prefer constructor with SafePathResolver */
    @Deprecated
    public FileExecutor() {
        try {
            this.log = LoggingService.forComponent("FileExecutor");
            this.paths = new SafePathResolver(Path.of(".").toAbsolutePath().normalize());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean supports(Action action) {
        return switch (action.getType()) {
            case CREATE_FILE, CREATE_FOLDER, READ_FILE, MOVE_FILE, COPY_FILE,
                 DELETE_FILE, OPEN_FOLDER -> true;
            default -> false;
        };
    }

    @Override
    public ActionResult execute(Action action) {
        try {
            return switch (action.getType()) {
                case CREATE_FILE -> createFile(action);
                case CREATE_FOLDER -> createFolder(action);
                case READ_FILE -> readFile(action);
                case MOVE_FILE -> moveFile(action);
                case COPY_FILE -> copyFile(action);
                case DELETE_FILE -> deleteFile(action);
                case OPEN_FOLDER -> openFolder(action);
                default -> ActionResult.failure(action.getType(), "Unsupported by FileExecutor");
            };
        } catch (SecurityException e) {
            log.warn("Path rejected: " + e.getMessage());
            return ActionResult.failure(action.getType(), e.getMessage());
        } catch (Exception e) {
            log.failure("file-op", e);
            return ActionResult.failure(action.getType(), e.getMessage());
        }
    }

    private Path resolve(String raw) throws IOException {
        return paths.resolve(raw);
    }

    private boolean allowOverwrite(Action action) {
        return action.getParameter("overwrite")
                .map(v -> v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"))
                .orElse(false);
    }

    private ActionResult createFile(Action action) throws IOException {
        Path path = resolve(action.getTarget().orElseThrow());
        if (Files.exists(path)) {
            return ActionResult.failure(ActionType.CREATE_FILE, "File already exists: " + path);
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String content = action.getParameter("content").orElse("");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        log.info("Created file: " + display(path));
        return ActionResult.success(ActionType.CREATE_FILE, "File created: " + display(path), path.toString());
    }

    private ActionResult createFolder(Action action) throws IOException {
        Path path = resolve(action.getTarget().orElseThrow());
        Files.createDirectories(path);
        log.info("Created folder: " + display(path));
        return ActionResult.success(ActionType.CREATE_FOLDER, "Folder created: " + display(path), path.toString());
    }

    private ActionResult readFile(Action action) throws IOException {
        Path path = resolve(action.getTarget().orElseThrow());
        if (!Files.isRegularFile(path)) {
            return ActionResult.failure(ActionType.READ_FILE, "Not a file: " + display(path));
        }
        long size = Files.size(path);
        if (size > 1_000_000) {
            return ActionResult.failure(ActionType.READ_FILE, "File too large to read (>" + size + " bytes)");
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        log.info("Read file: " + display(path) + " (" + size + " bytes)");
        return ActionResult.success(ActionType.READ_FILE, "Read " + display(path), content);
    }

    private ActionResult moveFile(Action action) throws IOException {
        Path src = resolve(action.getTarget().orElseThrow());
        Path dest = resolve(action.getParameter("destination")
                .or(() -> action.getParameter("to"))
                .orElseThrow(() -> new IllegalArgumentException("Missing destination")));
        if (!Files.exists(src)) {
            return ActionResult.failure(ActionType.MOVE_FILE, "Source not found: " + display(src));
        }
        if (Files.exists(dest) && !allowOverwrite(action)) {
            return ActionResult.failure(ActionType.MOVE_FILE,
                    "Destination already exists; explicit overwrite approval required: " + display(dest));
        }
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(dest) && allowOverwrite(action)) {
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(src, dest);
        }
        log.info("Moved " + display(src) + " → " + display(dest));
        return ActionResult.success(ActionType.MOVE_FILE, "Moved to " + display(dest), dest.toString());
    }

    private ActionResult copyFile(Action action) throws IOException {
        Path src = resolve(action.getTarget().orElseThrow());
        Path dest = resolve(action.getParameter("destination")
                .or(() -> action.getParameter("to"))
                .orElseThrow(() -> new IllegalArgumentException("Missing destination")));
        if (!Files.exists(src)) {
            return ActionResult.failure(ActionType.COPY_FILE, "Source not found: " + display(src));
        }
        if (Files.exists(dest) && !allowOverwrite(action)) {
            return ActionResult.failure(ActionType.COPY_FILE,
                    "Destination already exists; explicit overwrite approval required: " + display(dest));
        }
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(dest) && allowOverwrite(action)) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(src, dest);
        }
        log.info("Copied " + display(src) + " → " + display(dest));
        return ActionResult.success(ActionType.COPY_FILE, "Copied to " + display(dest), dest.toString());
    }

    private ActionResult deleteFile(Action action) throws IOException {
        Path path = resolve(action.getTarget().orElseThrow());
        if (!Files.exists(path)) {
            return ActionResult.failure(ActionType.DELETE_FILE, "Path not found: " + display(path));
        }
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                if (stream.findAny().isPresent()) {
                    return ActionResult.failure(ActionType.DELETE_FILE,
                            "Directory not empty (refusing recursive delete): " + display(path));
                }
            }
            Files.delete(path);
        } else {
            Files.delete(path);
        }
        log.info("Deleted: " + display(path));
        return ActionResult.success(ActionType.DELETE_FILE, "Deleted: " + display(path), path.toString());
    }

    private ActionResult openFolder(Action action) throws IOException {
        Path path = resolve(action.getTarget().orElseThrow());
        if (!Files.isDirectory(path)) {
            return ActionResult.failure(ActionType.OPEN_FOLDER, "Not a directory: " + display(path));
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile());
            return ActionResult.success(ActionType.OPEN_FOLDER, "Opened folder: " + display(path));
        }
        return ActionResult.failure(ActionType.OPEN_FOLDER, "Desktop open not supported");
    }

    private String display(Path path) {
        try {
            return paths.getRoot().relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString();
        }
    }
}
