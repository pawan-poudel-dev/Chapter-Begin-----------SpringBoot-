package com.aidesktopagent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndpointValidatorTest {

    @Test
    void acceptsHttps() {
        assertNotNull(EndpointValidator.validateRemoteEndpoint("https://api.openai.com/v1"));
    }

    @Test
    void rejectsHttpRemote() {
        assertThrows(IllegalArgumentException.class,
                () -> EndpointValidator.validateRemoteEndpoint("http://evil.example/v1"));
    }

    @Test
    void allowsLocalHttp() {
        assertNotNull(EndpointValidator.validateRemoteEndpoint("http://localhost:8080/v1", true));
    }

    @Test
    void rejectsUserInfo() {
        assertThrows(IllegalArgumentException.class,
                () -> EndpointValidator.validateRemoteEndpoint("https://user:pass@api.openai.com/v1"));
    }
}
