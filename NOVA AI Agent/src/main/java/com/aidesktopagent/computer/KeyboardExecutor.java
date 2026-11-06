package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.logging.LoggingService;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Map;

/**
 * Types text and presses keys via {@link java.awt.Robot}.
 * Uses clipboard paste for reliability with unicode text.
 */
public final class KeyboardExecutor implements ActionExecutor {

    private static final Map<String, Integer> SPECIAL_KEYS = Map.ofEntries(
            Map.entry("ENTER", KeyEvent.VK_ENTER),
            Map.entry("RETURN", KeyEvent.VK_ENTER),
            Map.entry("TAB", KeyEvent.VK_TAB),
            Map.entry("ESC", KeyEvent.VK_ESCAPE),
            Map.entry("ESCAPE", KeyEvent.VK_ESCAPE),
            Map.entry("BACKSPACE", KeyEvent.VK_BACK_SPACE),
            Map.entry("DELETE", KeyEvent.VK_DELETE),
            Map.entry("SPACE", KeyEvent.VK_SPACE),
            Map.entry("UP", KeyEvent.VK_UP),
            Map.entry("DOWN", KeyEvent.VK_DOWN),
            Map.entry("LEFT", KeyEvent.VK_LEFT),
            Map.entry("RIGHT", KeyEvent.VK_RIGHT),
            Map.entry("HOME", KeyEvent.VK_HOME),
            Map.entry("END", KeyEvent.VK_END),
            Map.entry("PAGE_UP", KeyEvent.VK_PAGE_UP),
            Map.entry("PAGE_DOWN", KeyEvent.VK_PAGE_DOWN),
            Map.entry("F1", KeyEvent.VK_F1),
            Map.entry("F2", KeyEvent.VK_F2),
            Map.entry("F3", KeyEvent.VK_F3),
            Map.entry("F4", KeyEvent.VK_F4),
            Map.entry("F5", KeyEvent.VK_F5),
            Map.entry("CONTROL", KeyEvent.VK_CONTROL),
            Map.entry("CTRL", KeyEvent.VK_CONTROL),
            Map.entry("ALT", KeyEvent.VK_ALT),
            Map.entry("SHIFT", KeyEvent.VK_SHIFT)
    );

    private final LoggingService log;

    public KeyboardExecutor() {
        this(LoggingService.forComponent("KeyboardExecutor"));
    }

    public KeyboardExecutor(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("KeyboardExecutor");
    }

    @Override
    public boolean supports(Action action) {
        ActionType t = action.getType();
        return t == ActionType.TYPE_TEXT || t == ActionType.PRESS_KEY;
    }

    @Override
    public ActionResult execute(Action action) {
        // Headless environments cannot use Robot
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return ActionResult.failure(action.getType(),
                    "Keyboard simulation is unavailable in headless environment");
        }
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(20);
            if (action.getType() == ActionType.TYPE_TEXT) {
                return typeText(robot, action.getTarget().orElse(""));
            }
            return pressKey(robot, action.getTarget().orElse(""));
        } catch (AWTException e) {
            return ActionResult.failure(action.getType(), "Robot unavailable: " + e.getMessage());
        } catch (Exception e) {
            return ActionResult.failure(action.getType(), e.getMessage());
        }
    }

    private ActionResult typeText(Robot robot, String text) {
        if (text.isEmpty()) {
            return ActionResult.failure(ActionType.TYPE_TEXT, "No text provided");
        }
        // Clipboard paste is more reliable than per-character key events for unicode
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, null);

            int pasteKey = isMac() ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
            robot.keyPress(pasteKey);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(pasteKey);

            log.info("Typed text (" + text.length() + " chars) via clipboard paste");
            return ActionResult.success(ActionType.TYPE_TEXT, "Typed text (" + text.length() + " characters)");
        } catch (Exception e) {
            return ActionResult.failure(ActionType.TYPE_TEXT, "Failed to type: " + e.getMessage());
        }
    }

    private ActionResult pressKey(Robot robot, String keyName) {
        String key = keyName.trim().toUpperCase(Locale.ROOT);
        Integer code = SPECIAL_KEYS.get(key);
        if (code == null && key.length() == 1) {
            code = (int) key.charAt(0);
        }
        if (code == null) {
            return ActionResult.failure(ActionType.PRESS_KEY, "Unknown key: " + keyName);
        }
        robot.keyPress(code);
        robot.keyRelease(code);
        log.info("Pressed key: " + key);
        return ActionResult.success(ActionType.PRESS_KEY, "Pressed " + key);
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac");
    }
}
