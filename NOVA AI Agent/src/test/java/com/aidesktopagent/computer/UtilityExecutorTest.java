package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.memory.NotesStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UtilityExecutorTest {

    @TempDir
    Path temp;

    @Test
    void calculateAndNotes() {
        NotesStore notes = new NotesStore(temp.resolve("notes.json"));
        UtilityExecutor u = new UtilityExecutor(notes, temp);
        var calc = u.execute(Action.builder(ActionType.CALCULATE).target("10+5*2").build());
        assertTrue(calc.isSuccess());
        assertTrue(calc.getMessage().contains("20"));

        var save = u.execute(Action.builder(ActionType.SAVE_NOTE)
                .target("shop")
                .parameter("body", "milk")
                .build());
        assertTrue(save.isSuccess());
        var list = u.execute(Action.builder(ActionType.LIST_NOTES).build());
        assertTrue(list.getMessage().contains("shop"));
    }

    @Test
    void timeAndHelp() {
        UtilityExecutor u = new UtilityExecutor(new NotesStore(temp.resolve("n.json")), temp);
        assertTrue(u.execute(Action.builder(ActionType.GET_TIME).build()).isSuccess());
        assertTrue(u.execute(Action.builder(ActionType.SHOW_HELP).build()).getMessage().contains("calculate"));
        assertTrue(u.execute(Action.builder(ActionType.SYSTEM_INFO).build()).getMessage().contains("Java"));
    }
}
