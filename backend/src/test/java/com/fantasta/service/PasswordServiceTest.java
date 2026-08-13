package com.fantasta.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    @Test
    void hashesWithSaltAndVerifiesPassword() {
        String first = service.hash("correct horse battery staple");
        String second = service.hash("correct horse battery staple");

        assertNotEquals(first, second);
        assertTrue(service.verify("correct horse battery staple", first));
        assertFalse(service.verify("wrong password", first));
    }

    @Test
    void rejectsNullAndMalformedHashes() {
        assertFalse(service.verify(null, "value"));
        assertFalse(service.verify("password", null));
        assertFalse(service.verify("password", "pbkdf2$invalid$%%%$%%%"));
        assertFalse(service.verify("password", "unknown$1$c2FsdA==$aGFzaA=="));
    }
}
