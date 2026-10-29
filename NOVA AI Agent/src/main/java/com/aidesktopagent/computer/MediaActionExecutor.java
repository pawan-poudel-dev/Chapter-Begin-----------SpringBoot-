package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.music.MediaController;
import com.aidesktopagent.music.MediaStatus;

/**
 * Routes media-related actions to {@link MediaController}.
 */
public final class MediaActionExecutor implements ActionExecutor {

    private final MediaController media;
    private final LoggingService log;

    public MediaActionExecutor(MediaController media) {
        this(media, LoggingService.forComponent("MediaExecutor"));
    }

    public MediaActionExecutor(MediaController media, LoggingService log) {
        this.media = media;
        this.log = log != null ? log : LoggingService.forComponent("MediaExecutor");
    }

    @Override
    public boolean supports(Action action) {
        return action.getType().isMediaRelated();
    }

    @Override
    public ActionResult execute(Action action) {
        MediaStatus status = media.handle(action);
        log.info("Media result: " + status);

        return switch (status.getOutcome()) {
            case SUCCESS -> ActionResult.success(action.getType(), status.getMessage(),
                    status.getDetail().orElse(null));
            case UNKNOWN -> ActionResult.success(action.getType(), status.getMessage(),
                    status.getDetail().orElse("unverified"));
            case NEEDS_AUTH, NEEDS_USER_ACTION, NO_ACTIVE_DEVICE ->
                    // Not a hard failure — user can act; still surface as unsuccessful execution
                    ActionResult.failure(action.getType(), status.getMessage());
            case FAILURE -> ActionResult.failure(action.getType(), status.getMessage());
        };
    }
}
