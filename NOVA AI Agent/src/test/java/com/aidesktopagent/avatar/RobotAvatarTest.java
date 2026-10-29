package com.aidesktopagent.avatar;

import com.aidesktopagent.agent.AgentState;
import com.aidesktopagent.agent.AgentStateManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RobotAvatarTest {

    @Test
    void tracksAgentState() {
        RobotAvatar avatar = new RobotAvatar();
        AgentStateManager sm = new AgentStateManager();
        avatar.bindToAgentState(sm);
        sm.transitionTo(AgentState.LISTENING);
        assertEquals(AvatarMood.LISTENING, avatar.getMood());
        sm.transitionTo(AgentState.TRANSCRIBING);
        sm.transitionTo(AgentState.THINKING);
        assertEquals(AvatarMood.THINKING, avatar.getMood());
        String frame = avatar.renderFrame();
        assertTrue(frame.contains("Thinking") || frame.contains("?") || frame.contains("THINK")
                || frame.toLowerCase().contains("violet") || frame.contains("・・"));
    }

    @Test
    void notifiesListeners() {
        RobotAvatar avatar = new RobotAvatar();
        AtomicReference<AvatarMood> seen = new AtomicReference<>();
        avatar.addListener(seen::set);
        avatar.setMood(AvatarMood.SUCCESS);
        assertEquals(AvatarMood.SUCCESS, seen.get());
    }

    @Test
    void colorfulFrames() {
        RobotAvatar avatar = new RobotAvatar(false, new AnsiPalette(true));
        for (AvatarMood m : AvatarMood.values()) {
            avatar.setMood(m);
            String f = avatar.renderFrame();
            assertFalse(f.isBlank());
            assertTrue(f.contains("Aether") || f.contains("╭") || f.contains("Ready")
                    || f.contains("Listening") || f.contains("Done") || f.contains("Oops")
                    || f.contains("Resting") || f.contains("Working") || f.contains("Thinking"));
        }
        assertTrue(avatar.renderBadge().contains("◈"));
        assertTrue(avatar.renderIntro().contains("AETHER"));
    }
}
