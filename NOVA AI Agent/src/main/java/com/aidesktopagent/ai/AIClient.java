package com.aidesktopagent.ai;

/**
 * Client that turns a natural-language user command into structured JSON
 * describing an action plan. Implementations must never execute OS commands.
 */
public interface AIClient {

    /**
     * Ask the model to produce a JSON action plan for the given command.
     *
     * @param userCommand free-text command (from STT or typed input)
     * @return raw JSON string (object with an "actions" array)
     * @throws AIClientException on transport or model errors
     */
    String planActions(String userCommand) throws AIClientException;
}
