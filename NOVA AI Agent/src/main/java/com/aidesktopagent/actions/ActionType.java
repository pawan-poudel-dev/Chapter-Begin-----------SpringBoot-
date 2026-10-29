package com.aidesktopagent.actions;

/**
 * Supported action types the AI may propose.
 */
public enum ActionType {

    OPEN_APPLICATION,
    CLOSE_APPLICATION,

    OPEN_WEBSITE,
    SEARCH_WEB,
    OPEN_FOLDER,

    CREATE_FILE,
    CREATE_FOLDER,
    READ_FILE,
    MOVE_FILE,
    COPY_FILE,
    DELETE_FILE,
    SEARCH_FILES,
    SUMMARIZE_FOLDER,

    TYPE_TEXT,
    PRESS_KEY,

    SEARCH_YOUTUBE,
    OPEN_YOUTUBE_VIDEO,
    PLAY_YOUTUBE,

    OPEN_SPOTIFY,
    SEARCH_SPOTIFY,
    PLAY_SPOTIFY,

    PAUSE_MEDIA,
    RESUME_MEDIA,
    STOP_MEDIA,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    GET_MEDIA_STATUS,

    ANALYZE_PROJECT,
    LIST_SOURCE_FILES,
    READ_SOURCE_FILE,
    EXPLAIN_ERROR,
    PROPOSE_CODE_CHANGE,
    APPLY_CODE_CHANGE,
    OPEN_IN_IDE,
    RUN_BUILD,
    GENERATE_PROJECT,

    SET_PREFERENCE,
    GET_PREFERENCE,

    // Utility / productivity
    CLIPBOARD_READ,
    CLIPBOARD_WRITE,
    GET_TIME,
    SYSTEM_INFO,
    CALCULATE,
    SAVE_NOTE,
    LIST_NOTES,
    READ_NOTE,
    SET_TIMER,
    GIT_STATUS,
    SHOW_HELP,
    CHAT_REPLY,
    WRITE_SNIPPET,

    RUN_COMMAND;

    public boolean isDestructive() {
        return this == DELETE_FILE || this == RUN_COMMAND || this == APPLY_CODE_CHANGE;
    }

    public boolean isMediaRelated() {
        return this == SEARCH_YOUTUBE || this == OPEN_YOUTUBE_VIDEO || this == PLAY_YOUTUBE
                || this == OPEN_SPOTIFY || this == SEARCH_SPOTIFY || this == PLAY_SPOTIFY
                || this == PAUSE_MEDIA || this == RESUME_MEDIA || this == STOP_MEDIA
                || this == NEXT_TRACK || this == PREVIOUS_TRACK || this == GET_MEDIA_STATUS;
    }

    public boolean isCodingRelated() {
        return this == ANALYZE_PROJECT || this == LIST_SOURCE_FILES || this == READ_SOURCE_FILE
                || this == EXPLAIN_ERROR || this == PROPOSE_CODE_CHANGE || this == APPLY_CODE_CHANGE
                || this == OPEN_IN_IDE || this == RUN_BUILD || this == GENERATE_PROJECT
                || this == GIT_STATUS;
    }

    public boolean isUtilityRelated() {
        return this == CLIPBOARD_READ || this == CLIPBOARD_WRITE || this == GET_TIME
                || this == SYSTEM_INFO || this == CALCULATE || this == SAVE_NOTE
                || this == LIST_NOTES || this == READ_NOTE || this == SET_TIMER
                || this == SHOW_HELP || this == CHAT_REPLY || this == WRITE_SNIPPET;
    }

    public static java.util.Optional<ActionType> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(ActionType.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
