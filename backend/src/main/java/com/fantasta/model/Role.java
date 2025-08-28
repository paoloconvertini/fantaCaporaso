package com.fantasta.model;

public enum Role {
    PORTIERE, DIFENSORE, CENTROCAMPISTA, ATTACCANTE;

    public static Role fromString(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase();
        switch (t) {
            case "portiere":
            case "portieri":
                return PORTIERE;
            case "difensore":
            case "difensori":
                return DIFENSORE;
            case "centrocampista":
            case "centrocampisti":
                return CENTROCAMPISTA;
            case "attaccante":
            case "attaccanti":
                return ATTACCANTE;
            default:
                return null;
        }
    }
}
