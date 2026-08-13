package com.fantasta.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppJwtServiceTest {

    private AppJwtService service;

    @BeforeEach
    void setUp() {
        service = new AppJwtService();
        service.secret = "test-secret-at-least-32-characters-long";
        service.expirationSeconds = 3600;
    }

    @Test
    void createsAndVerifiesToken() {
        String token = service.createToken("paolo", "ADMIN", 7L);

        AppJwtService.Claims claims = service.verify(token);

        assertEquals("paolo", claims.username());
        assertEquals("admin", claims.role());
        assertEquals(7L, claims.participantId());
    }

    @Test
    void rejectsTamperedAndMalformedTokens() {
        String token = service.createToken("paolo", "admin", null);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThrows(IllegalArgumentException.class, () -> service.verify(tampered));
        assertThrows(IllegalArgumentException.class, () -> service.verify("not-a-jwt"));
    }

    @Test
    void rejectsExpiredToken() {
        service.expirationSeconds = -1;
        assertThrows(IllegalStateException.class, () -> service.createToken("paolo", "admin", null));
    }

    @Test
    void rejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> service.createToken("paolo", "owner", null));
    }
}
