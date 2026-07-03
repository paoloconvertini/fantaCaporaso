package com.fantasta.service;

import com.fantasta.model.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomSelectorServiceTest {

    @Test
    void constructorUsesRoleModeByDefault() {
        RandomSelectorService selector = new RandomSelectorService("unexpected");

        assertEquals(RandomSelectorService.Mode.ROLE, selector.getMode());
        assertNull(selector.getRole());
    }

    @Test
    void constructorAcceptsAllAndOffModes() {
        assertEquals(RandomSelectorService.Mode.ALL, new RandomSelectorService("ALL").getMode());
        assertEquals(RandomSelectorService.Mode.OFF, new RandomSelectorService("off").getMode());
    }

    @Test
    void setRoleEnablesRoleModeAndStoresRole() {
        RandomSelectorService selector = new RandomSelectorService("OFF");

        selector.setRole(Role.CENTROCAMPISTA);

        assertEquals(RandomSelectorService.Mode.ROLE, selector.getMode());
        assertEquals(Role.CENTROCAMPISTA, selector.getRole());
    }

    @Test
    void setAllAndSetOffClearRole() {
        RandomSelectorService selector = new RandomSelectorService("ROLE");
        selector.setRole(Role.ATTACCANTE);

        selector.setAll();
        assertEquals(RandomSelectorService.Mode.ALL, selector.getMode());
        assertNull(selector.getRole());

        selector.setRole(Role.PORTIERE);
        selector.setOff();
        assertEquals(RandomSelectorService.Mode.OFF, selector.getMode());
        assertNull(selector.getRole());
    }
}
