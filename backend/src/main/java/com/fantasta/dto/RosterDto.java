package com.fantasta.dto;

public class RosterDto {
    public Long participantId;
    public String participantName;
    public Long playerId;
    public String playerName;
    public String team;
    public String role;
    public Double amount;
    public Double residui;


    public RosterDto(Long participantId, String participantName,
                     Long playerId, String playerName, String team,
                     String role, Double amount) {
        this.participantId = participantId;
        this.participantName = participantName;
        this.playerId = playerId;
        this.playerName = playerName;
        this.team = team;
        this.role = role;
        this.amount = amount;
    }

    public RosterDto(Long participantId, String participantName,
                     Long playerId, String playerName, String team,
                     String role, Double amount, Double residui) {
        this(participantId, participantName, playerId, playerName, team, role, amount);
        this.residui = residui;
    }
}
