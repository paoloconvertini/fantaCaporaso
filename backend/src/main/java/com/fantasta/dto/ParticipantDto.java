package com.fantasta.dto;

import java.util.Map;
import com.fantasta.model.Role;

public class ParticipantDto {
    public Long id;
    public String name;
    public String username;
    public int totalCredits;
    public int spentCredits;
    public int remainingCredits;
    public int maxBid;

    // giocatori per ruolo
    public Map<Role, Integer> takenByRole;
}
