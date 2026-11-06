package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.coding.ProjectWorkspace;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Read-only git status inside workspace (Day extra). */
public final class GitStatusExecutor implements ActionExecutor {

    private final ProjectWorkspace workspace;

    public GitStatusExecutor(ProjectWorkspace workspace) {
        this.workspace = Objects.requireNonNull(workspace);
    }

    @Override
    public boolean supports(Action action) {
        return action.getType() == ActionType.GIT_STATUS;
    }

    @Override
    public ActionResult execute(Action action) {
        Path root = workspace.getRoot();
        if (!Files.isDirectory(root.resolve(".git"))) {
            return ActionResult.failure(ActionType.GIT_STATUS,
                    "Not a git repository: " + root);
        }
        try {
            Process p = new ProcessBuilder("git", "status", "-sb")
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            p.getInputStream().transferTo(buf);
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return ActionResult.failure(ActionType.GIT_STATUS, "git status timed out");
            }
            String out = buf.toString(StandardCharsets.UTF_8).trim();
            if (p.exitValue() != 0) {
                return ActionResult.failure(ActionType.GIT_STATUS, out.isBlank() ? "git failed" : out);
            }
            return ActionResult.success(ActionType.GIT_STATUS,
                    out.isBlank() ? "Clean working tree" : out, out);
        } catch (Exception e) {
            return ActionResult.failure(ActionType.GIT_STATUS, e.getMessage());
        }
    }
}
