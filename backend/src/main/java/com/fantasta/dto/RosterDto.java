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


    public RosterDto(Long participantId, String participantName,
                     Long playerId, String playerName, String team,
                     String role, Double amount, Double valore) {
        this.participantId = participantId;
        this.participantName = participantName;
        this.playerId = playerId;
        this.playerName = playerName;
        this.team = team;
        this.role = role;
        this.amount = amount;
        this.valore = valore != null ? valore : 0D;
    }

    public RosterDto(Long participantId, String participantName,
                     Long playerId, String playerName, String team,
                     String role, Double amount, Double valore, Double residui) {
        this(participantId, participantName, playerId, playerName, team, role, amount, valore);
        this.residui = residui;
    }
}
