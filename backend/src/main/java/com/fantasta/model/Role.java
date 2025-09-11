package com.fantasta.model;

public enum Role {
    PORTIERE, DIFENSORE, CENTROCAMPISTA, ATTACCANTE;

    public static Role fromString(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase();
        switch (t) {
            case "portiere":
            case "portieri":
            case "p":
                return PORTIERE;
            case "difensore":
            case "difensori":
            case "d":
                return DIFENSORE;
            case "centrocampista":
            case "centrocampisti":
            case "c":
                return CENTROCAMPISTA;
            case "attaccante":
            case "attaccanti":
            case "a":
                return ATTACCANTE;
            default:
                return null;
        }
    }
}
