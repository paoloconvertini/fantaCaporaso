package com.fantasta.dto;

import java.time.LocalDateTime;

public class RosterMovementDto {
    public Long id;
    public String type;
    public String playerName;
    public String playerTeam;
    public String sourceParticipant;
    public String destinationParticipant;
    public double currentValue;
    public double previousRosterAmount;
    public double refundedAmount;
    public String sessionCode;
    public LocalDateTime createdAt;
    public LocalDateTime revertedAt;
    public boolean canRevert;
}
