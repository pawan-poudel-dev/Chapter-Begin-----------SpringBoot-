package com.aidesktopagent.voice;

import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.actions.ActionValidator;
import com.aidesktopagent.agent.Agent;
import com.aidesktopagent.agent.AgentState;
import com.aidesktopagent.agent.AgentStateManager;
import com.aidesktopagent.ai.AIClient;
import com.aidesktopagent.ai.AIClientException;
import com.aidesktopagent.ai.RuleBasedAIClient;
import com.aidesktopagent.computer.CompositeActionExecutor;
import com.aidesktopagent.computer.FileExecutor;
import com.aidesktopagent.security.SafePathResolver;
import com.aidesktopagent.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration: mock STT text → processor → agent → file executor.
 */
class VoicePipelineIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void fullPipelineCreateFolder() throws Exception {
        Agent agent = new Agent(
                new RuleBasedAIClient(),
                new ActionParser(),
                new ActionValidator(),
                new CompositeActionExecutor(List.of(new FileExecutor(null, new SafePathResolver(temp)))),
                a -> false,
                null
        );
        AgentStateManager sm = new AgentStateManager();
        VoiceCommandProcessorTest.RecordingTts tts = new VoiceCommandProcessorTest.RecordingTts();
        VoiceCommandProcessor proc = new VoiceCommandProcessor(agent, sm, tts, null, 100L);

        // Simulate STT output
        MockSpeechToText stt = new MockSpeechToText("Create a folder called Java Projects");
        String heard = stt.transcribe(new byte[]{0, 1}, null);
        List<ActionResult> results = proc.processTranscription(heard);

        assertTrue(results.stream().anyMatch(ActionResult::isSuccess));
        assertTrue(Files.isDirectory(temp.resolve("Java Projects")));
        assertEquals(AgentState.IDLE, sm.getState());
    }

    @Test
    void aiFailureSurfacesAsErrorState() throws Exception {
        AIClient failing = cmd -> {
            throw new AIClientException("network down");
        };
        Agent agent = new Agent(failing, new ActionParser(), new ActionValidator(),
                new CompositeActionExecutor(List.of()), a -> false, null);
        AgentStateManager sm = new AgentStateManager();
        VoiceCommandProcessorTest.RecordingTts tts = new VoiceCommandProcessorTest.RecordingTts();
        VoiceCommandProcessor proc = new VoiceCommandProcessor(agent, sm, tts, null, 100L);

        List<ActionResult> results = proc.processTranscription("Open Chrome");
        assertTrue(results.isEmpty());
        assertEquals(AgentState.IDLE, sm.getState()); // recovered to IDLE after ERROR
        assertFalse(tts.lines.isEmpty());
    }

    @Test
    void cancellationStopsProcessing() throws Exception {
        Agent agent = new Agent(
                new RuleBasedAIClient(),
                new ActionParser(),
                new ActionValidator(),
                new CompositeActionExecutor(List.of(new FileExecutor(null, new SafePathResolver(temp)))),
                a -> false,
                null
        );
        AgentStateManager sm = new AgentStateManager();
        VoiceCommandProcessorTest.RecordingTts tts = new VoiceCommandProcessorTest.RecordingTts();
        VoiceCommandProcessor proc = new VoiceCommandProcessor(agent, sm, tts, null, 100L);

        proc.requestCancel();
        assertTrue(sm.isStopped());
        // After stop, clear and ensure emergency phrase still works
        proc.clearCancel();
        assertEquals(AgentState.IDLE, sm.getState());
    }

    @Test
    void voiceManagerProcessTextAsVoice() throws Exception {
        AppConfig config = new AppConfig.Builder().voiceEnabled(true).build();
        Agent agent = new Agent(
                new RuleBasedAIClient(),
                new ActionParser(),
                new ActionValidator(),
                new CompositeActionExecutor(List.of(new FileExecutor(null, new SafePathResolver(temp)))),
                a -> false,
                null
        );
        SpeechToText stt = new MockSpeechToText("unused");
        TextToSpeech tts = new ConsoleTextToSpeech();
        try (VoiceManager vm = new VoiceManager(config, agent, stt, tts, a -> false)) {
            vm.processTextAsVoice("Create a folder called FromVoiceManager");
            assertTrue(Files.isDirectory(temp.resolve("FromVoiceManager")));
        }
    }
}
