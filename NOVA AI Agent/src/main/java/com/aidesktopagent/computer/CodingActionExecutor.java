package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.coding.BuildRunner;
import com.aidesktopagent.coding.CodeChangeProposal;
import com.aidesktopagent.coding.CodingAssistant;
import com.aidesktopagent.coding.IdeDetector;
import com.aidesktopagent.coding.IdeLauncher;
import com.aidesktopagent.coding.SafeCodeEditor;
import com.aidesktopagent.coding.StackTraceAnalyzer;
import com.aidesktopagent.logging.LoggingService;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Coding / IDE / build actions (Days 6–9). */
public final class CodingActionExecutor implements ActionExecutor {

    private final CodingAssistant coding;
    private final SafeCodeEditor editor;
    private final IdeLauncher ideLauncher;
    private final BuildRunner buildRunner;
    private final Function<Action, Boolean> confirmation;
    private final LoggingService log;

    public CodingActionExecutor(CodingAssistant coding) {
        this(coding, null, null, null, a -> false, LoggingService.forComponent("CodingExecutor"));
    }

    public CodingActionExecutor(CodingAssistant coding,
                                SafeCodeEditor editor,
                                IdeLauncher ideLauncher,
                                BuildRunner buildRunner,
                                Function<Action, Boolean> confirmation,
                                LoggingService log) {
        this.coding = Objects.requireNonNull(coding);
        this.editor = editor;
        this.ideLauncher = ideLauncher;
        this.buildRunner = buildRunner;
        this.confirmation = confirmation != null ? confirmation : a -> false;
        this.log = log != null ? log : LoggingService.forComponent("CodingExecutor");
    }

    @Override
    public boolean supports(Action action) {
        return action.getType().isCodingRelated();
    }

    @Override
    public ActionResult execute(Action action) {
        try {
            return switch (action.getType()) {
                case ANALYZE_PROJECT -> ActionResult.success(ActionType.ANALYZE_PROJECT, coding.analyzeProject());
                case LIST_SOURCE_FILES -> {
                    List<String> files = coding.listSources(action.getTarget().orElse(""));
                    String msg = files.isEmpty() ? "No Java sources found."
                            : "Found " + files.size() + " source file(s):\n" + String.join("\n", files);
                    yield ActionResult.success(ActionType.LIST_SOURCE_FILES, msg);
                }
                case READ_SOURCE_FILE -> {
                    String path = action.getTarget().orElseThrow();
                    String content = coding.readSource(path);
                    String preview = content.length() > 2000 ? content.substring(0, 2000) + "\n…" : content;
                    yield ActionResult.success(ActionType.READ_SOURCE_FILE,
                            "Read " + path + " (" + content.length() + " chars)", preview);
                }
                case EXPLAIN_ERROR -> {
                    StackTraceAnalyzer.AnalysisResult r = coding.explainError(action.getTarget().orElseThrow());
                    yield ActionResult.success(ActionType.EXPLAIN_ERROR, r.toHumanReport());
                }
                case PROPOSE_CODE_CHANGE -> {
                    String path = action.getTarget().orElseThrow();
                    CodeChangeProposal p = coding.proposeSafeStub(path,
                            action.getParameter("reason").orElse("Suggested improvement"));
                    yield p.isValidated()
                            ? ActionResult.success(ActionType.PROPOSE_CODE_CHANGE, p.toPreview())
                            : ActionResult.failure(ActionType.PROPOSE_CODE_CHANGE, "Path not in workspace");
                }
                case APPLY_CODE_CHANGE -> applyChange(action);
                case OPEN_IN_IDE -> openIde(action);
                case RUN_BUILD -> runBuild(action);
                case GENERATE_PROJECT -> generateProject(action);
                default -> ActionResult.failure(action.getType(), "Unsupported coding action");
            };
        } catch (SecurityException e) {
            return ActionResult.failure(action.getType(), e.getMessage());
        } catch (Exception e) {
            log.failure("coding", e);
            return ActionResult.failure(action.getType(), e.getMessage());
        }
    }

    private ActionResult applyChange(Action action) throws Exception {
        if (editor == null) {
            return ActionResult.failure(ActionType.APPLY_CODE_CHANGE, "Code editor not configured");
        }
        boolean ok = Boolean.TRUE.equals(confirmation.apply(action));
        String path = action.getTarget().orElseThrow();
        String find = action.getParameter("find").orElse("");
        String replace = action.getParameter("replace").orElse("");
        if (!find.isEmpty()) {
            var result = editor.applyReplace(path, find, replace, ok);
            return result.success()
                    ? ActionResult.success(ActionType.APPLY_CODE_CHANGE, result.message())
                    : ActionResult.failure(ActionType.APPLY_CODE_CHANGE, result.message());
        }
        String content = action.getParameter("content").orElse(null);
        if (content == null) {
            return ActionResult.failure(ActionType.APPLY_CODE_CHANGE, "Missing find/replace or content");
        }
        var result = editor.applyFullContent(path, content, ok);
        return result.success()
                ? ActionResult.success(ActionType.APPLY_CODE_CHANGE, result.message())
                : ActionResult.failure(ActionType.APPLY_CODE_CHANGE, result.message());
    }

    private ActionResult openIde(Action action) {
        if (ideLauncher == null) {
            return ActionResult.failure(ActionType.OPEN_IN_IDE, "IDE launcher not configured");
        }
        Path path = action.getTarget().map(Path::of).orElse(coding.getWorkspace().getRoot());
        String ide = action.getParameter("ide").orElse("").toLowerCase();
        IdeDetector.IdeKind kind = ide.contains("code") || ide.contains("vscode")
                ? IdeDetector.IdeKind.VSCODE
                : (ide.contains("idea") || ide.contains("intellij") ? IdeDetector.IdeKind.INTELLIJ : null);
        var result = ideLauncher.openProject(kind, path);
        return result.success()
                ? ActionResult.success(ActionType.OPEN_IN_IDE, result.message())
                : ActionResult.failure(ActionType.OPEN_IN_IDE, result.message());
    }

    private ActionResult runBuild(Action action) {
        if (buildRunner == null) {
            return ActionResult.failure(ActionType.RUN_BUILD, "Build runner not configured");
        }
        var result = buildRunner.run(action.getTarget().orElse("test"));
        if (result.cancelled()) {
            return ActionResult.cancelled(ActionType.RUN_BUILD, result.message());
        }
        return result.success()
                ? ActionResult.success(ActionType.RUN_BUILD, result.message(), result.output())
                : ActionResult.failure(ActionType.RUN_BUILD, result.message());
    }

    private ActionResult generateProject(Action action) {
        try {
            String artifact = action.getTarget().orElse("demo-app");
            String pkg = action.getParameter("package").orElse("com.example.demo");
            Path parent = coding.getWorkspace().getRoot().getParent() != null
                    ? coding.getWorkspace().getRoot().getParent()
                    : coding.getWorkspace().getRoot();
            // Prefer writing under workspace itself in a generated/ folder for safety
            Path genRoot = coding.getWorkspace().getRoot().resolve("generated");
            java.nio.file.Files.createDirectories(genRoot);
            var gen = new com.aidesktopagent.coding.ProjectGenerator(genRoot);
            Path created = gen.generate(artifact, pkg);
            return ActionResult.success(ActionType.GENERATE_PROJECT,
                    "Generated project at " + created);
        } catch (Exception e) {
            return ActionResult.failure(ActionType.GENERATE_PROJECT, e.getMessage());
        }
    }
}
