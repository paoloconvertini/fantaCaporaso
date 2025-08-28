package com.fantasta.model;

public class Winner {
    public Long participantId; // serve al DB
    public String user;        // nome leggibile
    public int amount;

    public Winner(Long participantId, String user, int amount) {
        this.participantId = participantId;
        this.user = user;
        this.amount = amount;
    }
}

