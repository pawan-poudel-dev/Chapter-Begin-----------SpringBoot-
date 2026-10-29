package com.aidesktopagent.ai;

import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.actions.ActionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaRulesTest {

    private RuleBasedAIClient client;
    private ActionParser parser;
    private ActionValidator validator;

    @BeforeEach
    void setUp() {
        client = new RuleBasedAIClient();
        parser = new ActionParser();
        validator = new ActionValidator();
    }

    private ActionType planType(String cmd) throws Exception {
        String json = client.planActions(cmd);
        ActionPlan plan = parser.parse(cmd, json);
        var vr = validator.validate(plan);
        assertTrue(vr.hasAcceptedActions(), "rejected: " + vr.getRejections());
        return vr.getPlan().getActions().get(0).getType();
    }

    @Test
    void openYoutube() throws Exception {
        assertEquals(ActionType.OPEN_WEBSITE, planType("Open YouTube"));
    }

    @Test
    void findJavaTutorial() throws Exception {
        assertEquals(ActionType.SEARCH_YOUTUBE, planType("Find a Java tutorial"));
    }

    @Test
    void playOnSpotify() throws Exception {
        assertEquals(ActionType.PLAY_SPOTIFY, planType("Play relaxing music on Spotify"));
    }

    @Test
    void pause() throws Exception {
        assertEquals(ActionType.PAUSE_MEDIA, planType("Pause"));
    }

    @Test
    void resume() throws Exception {
        assertEquals(ActionType.RESUME_MEDIA, planType("Resume"));
    }

    @Test
    void nextTrack() throws Exception {
        assertEquals(ActionType.NEXT_TRACK, planType("Next track"));
    }

    @Test
    void openChromeAndSearch() throws Exception {
        assertEquals(ActionType.SEARCH_WEB, planType("Open Chrome and search for Java documentation"));
    }

    @Test
    void putOnMusic() throws Exception {
        assertEquals(ActionType.PLAY_YOUTUBE, planType("Put on some relaxing music"));
    }
}
