package com.fantasta.dto;

public class RosterDto {
    public Long participantId;
    public String participantName;
    public Long playerId;
    public String playerName;
    public String team;
    public String role;
    public Double amount;
    public Double valore;
    public Double residui;
    public boolean active;


    public RosterDto(Long participantId, String participantName,
                     Long playerId, String playerName, String team,
                     String role, Double amount, Double valore, boolean active) {
        this.participantId = participantId;
        this.participantName = participantName;
        this.playerId = playerId;
        this.playerName = playerName;
        this.team = team;
        this.role = role;
        this.amount = amount;
        this.valore = valore != null ? valore : 0D;
        this.active = active;
    }

    public RosterDto(Long participantId, String participantName,
                     Long playerId, String playerName, String team,
                     String role, Double amount, Double valore, Double residui, boolean active) {
        this(participantId, participantName, playerId, playerName, team, role, amount, valore, active);
        this.residui = residui;
    }
}
