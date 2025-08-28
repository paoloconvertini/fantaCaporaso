package com.fantasta.service;

import com.fantasta.model.Role;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RandomSelectorService {
    public enum Mode {OFF, ALL, ROLE}

    ;
    private Mode mode = Mode.OFF;
    private Role role = null;

    public RandomSelectorService(
            @ConfigProperty(name = "app.random.mode", defaultValue = "ROLE") String modeConfig
    ) {
        switch (modeConfig.toUpperCase()) {
            case "ALL" -> this.mode = Mode.ALL;
            case "OFF" -> this.mode = Mode.OFF;
            default -> this.mode = Mode.ROLE;
        }
        this.role = null; // sarà impostato al primo round
    }

    public synchronized void setAll() {
        mode = Mode.ALL;
        role = null;
    }

    public void setRole(Role r) {
        this.mode = Mode.ROLE;
        this.role = r;
    }


    public synchronized void setOff() {
        mode = Mode.OFF;
        role = null;
    }

    public synchronized Mode getMode() {
        return mode;
    }

    public synchronized Role getRole() {
        return role;
    }
}
