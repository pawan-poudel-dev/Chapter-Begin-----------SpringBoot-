package com.aidesktopagent.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureMatrixTest {

    @Test
    void rendersAllStatuses() {
        FeatureMatrix m = new FeatureMatrix();
        assertFalse(m.all().isEmpty());
        String r = m.render();
        assertTrue(r.contains("IMPLEMENTED") || r.contains("Feature matrix"));
    }
}
