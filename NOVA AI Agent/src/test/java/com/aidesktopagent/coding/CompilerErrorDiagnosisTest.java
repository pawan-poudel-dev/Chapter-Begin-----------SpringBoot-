package com.aidesktopagent.coding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompilerErrorDiagnosisTest {

    @Test
    void parsesMavenStyle() {
        String out = "[ERROR] /tmp/App.java:[10,5] cannot find symbol\n  symbol: class Foo";
        var r = new CompilerErrorDiagnosis().analyze(out);
        assertFalse(r.diagnostics().isEmpty());
        assertTrue(r.toHuman().toLowerCase().contains("compiler")
                || r.toHuman().toLowerCase().contains("found"));
        assertTrue(r.suggestion().toLowerCase().contains("import")
                || r.suggestion().toLowerCase().contains("classpath")
                || r.suggestion().length() > 5);
    }
}
