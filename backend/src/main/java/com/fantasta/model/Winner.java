package com.fantasta.model;

public class Winner {
    public Long participantId;   // id partecipante vincente
    public String user;          // nome partecipante
    public Double amount;           // offerta vincente

    public Winner() {}

    public Winner(Long participantId, String user, Double amount) {
        this.participantId = participantId;
        this.user = user;
        this.amount = amount;
    }
}
