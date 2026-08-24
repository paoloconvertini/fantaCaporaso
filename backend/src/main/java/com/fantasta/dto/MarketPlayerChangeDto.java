package com.fantasta.dto;

import java.util.List;

public class MarketPlayerChangeDto {
    public Long playerId;
    public String name;
    public String oldTeam;
    public String newTeam;
    public String role;
    public double oldValue;
    public double newValue;
    public boolean assigned;
    public List<String> owners;

    public MarketPlayerChangeDto(Long playerId, String name, String oldTeam, String newTeam,
                                 String role, double oldValue, double newValue,
                                 boolean assigned, List<String> owners) {
        this.playerId = playerId;
        this.name = name;
        this.oldTeam = oldTeam;
        this.newTeam = newTeam;
        this.role = role;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.assigned = assigned;
        this.owners = owners;
    }
}
