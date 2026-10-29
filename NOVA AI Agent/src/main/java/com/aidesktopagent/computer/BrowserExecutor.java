package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.browser.BrowserController;
import com.aidesktopagent.browser.BrowserException;
import com.aidesktopagent.browser.DesktopBrowserController;
import com.aidesktopagent.logging.LoggingService;

/**
 * Browser navigation actions (open URL / web search).
 * YouTube/Spotify media actions are handled by {@link MediaActionExecutor}.
 */
public final class BrowserExecutor implements ActionExecutor {

    private final BrowserController browser;
    private final LoggingService log;

    public BrowserExecutor() {
        this(new DesktopBrowserController(), LoggingService.forComponent("BrowserExecutor"));
    }

    public BrowserExecutor(BrowserController browser, LoggingService log) {
        this.browser = browser != null ? browser : new DesktopBrowserController();
        this.log = log != null ? log : LoggingService.forComponent("BrowserExecutor");
    }

    @Override
    public boolean supports(Action action) {
        ActionType t = action.getType();
        return t == ActionType.OPEN_WEBSITE || t == ActionType.SEARCH_WEB;
    }

    @Override
    public ActionResult execute(Action action) {
        try {
            String msg = switch (action.getType()) {
                case OPEN_WEBSITE -> browser.openUrl(action.getTarget().orElse(""));
                case SEARCH_WEB -> browser.searchWeb(action.getTarget().orElse(""));
                default -> throw new IllegalStateException("Unsupported: " + action.getType());
            };
            log.info(msg);
            return ActionResult.success(action.getType(), msg);
        } catch (BrowserException e) {
            return ActionResult.failure(action.getType(), e.getMessage());
        } catch (Exception e) {
            return ActionResult.failure(action.getType(), "Failed to open browser: " + e.getMessage());
        }
    }
}
