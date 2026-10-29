package com.aidesktopagent.voice;

import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.actions.ActionValidator;
import com.aidesktopagent.agent.Agent;
import com.aidesktopagent.agent.AgentState;
import com.aidesktopagent.agent.AgentStateManager;
import com.aidesktopagent.ai.RuleBasedAIClient;
import com.aidesktopagent.computer.CompositeActionExecutor;
import com.aidesktopagent.computer.FileExecutor;
import com.aidesktopagent.security.SafePathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VoiceCommandProcessorTest {

    @TempDir
    Path temp;

    private AgentStateManager stateManager;
    private VoiceCommandProcessor processor;
    private RecordingTts tts;

    @BeforeEach
    void setUp() throws Exception {
        stateManager = new AgentStateManager();
        tts = new RecordingTts();
        Agent agent = new Agent(
                new RuleBasedAIClient(),
                new ActionParser(),
                new ActionValidator(),
                new CompositeActionExecutor(List.of(new FileExecutor(null, new SafePathResolver(temp)))),
                a -> false, // deny destructive by default
                null
        );
        processor = new VoiceCommandProcessor(agent, stateManager, tts, null, 2_500L);
    }

    @Test
    void emptyTranscriptionDoesNotExecute() throws Exception {
        List<ActionResult> results = processor.processTranscription("   ");
        assertTrue(results.isEmpty());
        assertTrue(tts.lines.stream().anyMatch(s -> s.toLowerCase().contains("didn't catch")
                || s.toLowerCase().contains("catch")));
        assertEquals(AgentState.IDLE, stateManager.getState());
    }

    @Test
    void createFolderViaVoiceText() throws Exception {
        List<ActionResult> results = processor.processTranscription(
                "Create a folder called Java Projects");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).isSuccess());
        assertEquals(ActionType.CREATE_FOLDER, results.get(0).getActionType());
        assertTrue(Files.isDirectory(temp.resolve("Java Projects")));
        assertEquals(AgentState.IDLE, stateManager.getState());
    }

    @Test
    void emergencyStop() throws Exception {
        processor.processTranscription("Stop agent");
        assertTrue(stateManager.isStopped());
    }

    @Test
    void duplicatePrevention() throws Exception {
        processor.processTranscription("Create a folder called DupTest");
        int spokenBefore = tts.lines.size();
        List<ActionResult> second = processor.processTranscription("Create a folder called DupTest");
        assertTrue(second.isEmpty());
        assertTrue(tts.lines.subList(spokenBefore, tts.lines.size()).stream()
                .anyMatch(s -> s.toLowerCase().contains("already")));
    }

    @Test
    void unclearDoesNotGrantDeletePermission() throws Exception {
        // Empty / unclear path already tested; destructive denied without YES
        List<ActionResult> results = processor.processTranscription("Delete the file secrets.txt");
        // May plan DELETE but confirmation returns false → cancelled or empty success
        boolean deleted = results.stream().anyMatch(r ->
                r.getActionType() == ActionType.DELETE_FILE && r.isSuccess());
        assertFalse(deleted);
    }

    @Test
    void isEmergencyStopHelper() throws Exception {
        assertTrue(VoiceCommandProcessor.isEmergencyStop("stop agent"));
        assertTrue(VoiceCommandProcessor.isEmergencyStop("Cancel"));
        assertFalse(VoiceCommandProcessor.isEmergencyStop("Open Chrome"));
    }

    /** Captures TTS lines instead of speaking. */
    static final class RecordingTts implements TextToSpeech {
        final java.util.List<String> lines = new java.util.ArrayList<>();

        @Override
        public void speak(String text) {
            lines.add(text);
        }

        @Override
        public String providerName() {
            return "recording";
        }
    }
}
