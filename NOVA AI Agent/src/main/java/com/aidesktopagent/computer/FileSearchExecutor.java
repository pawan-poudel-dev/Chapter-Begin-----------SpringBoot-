package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.files.FileSearchService;
import com.aidesktopagent.logging.LoggingService;

import java.util.Objects;

public final class FileSearchExecutor implements ActionExecutor {

    private final FileSearchService search;
    private final LoggingService log;

    public FileSearchExecutor(FileSearchService search) {
        this(search, LoggingService.forComponent("FileSearchExecutor"));
    }

    public FileSearchExecutor(FileSearchService search, LoggingService log) {
        this.search = Objects.requireNonNull(search);
        this.log = log != null ? log : LoggingService.forComponent("FileSearchExecutor");
    }

    @Override
    public boolean supports(Action action) {
        ActionType t = action.getType();
        return t == ActionType.SEARCH_FILES || t == ActionType.SUMMARIZE_FOLDER;
    }

    @Override
    public ActionResult execute(Action action) {
        try {
            if (action.getType() == ActionType.SUMMARIZE_FOLDER) {
                String msg = search.summarizeFolder(action.getTarget().orElse(""));
                return ActionResult.success(ActionType.SUMMARIZE_FOLDER, msg);
            }
            String name = action.getTarget().orElse("");
            String ext = action.getParameter("extension").orElse("");
            int limit = action.getParameter("limit").map(Integer::parseInt).orElse(50);
            var result = search.search(name, ext, limit);
            return ActionResult.success(ActionType.SEARCH_FILES, result.summary(),
                    String.join("\n", result.paths()));
        } catch (Exception e) {
            log.failure("file-search", e);
            return ActionResult.failure(action.getType(), e.getMessage());
        }
    }
}
