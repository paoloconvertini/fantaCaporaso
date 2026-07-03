package com.fantasta.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void fromStringAcceptsFullNamesPluralAndShortCodes() {
        assertEquals(Role.PORTIERE, Role.fromString("portiere"));
        assertEquals(Role.PORTIERE, Role.fromString("portieri"));
        assertEquals(Role.PORTIERE, Role.fromString("P"));

        assertEquals(Role.DIFENSORE, Role.fromString("difensore"));
        assertEquals(Role.DIFENSORE, Role.fromString("difensori"));
        assertEquals(Role.DIFENSORE, Role.fromString("d"));

        assertEquals(Role.CENTROCAMPISTA, Role.fromString("centrocampista"));
        assertEquals(Role.CENTROCAMPISTA, Role.fromString("centrocampisti"));
        assertEquals(Role.CENTROCAMPISTA, Role.fromString("C"));

        assertEquals(Role.ATTACCANTE, Role.fromString("attaccante"));
        assertEquals(Role.ATTACCANTE, Role.fromString("attaccanti"));
        assertEquals(Role.ATTACCANTE, Role.fromString("a"));
    }

    @Test
    void fromStringTrimsInputAndRejectsUnknownValues() {
        assertEquals(Role.DIFENSORE, Role.fromString("  DIFENSORE  "));
        assertNull(Role.fromString(null));
        assertNull(Role.fromString(""));
        assertNull(Role.fromString("allenatore"));
    }
}
