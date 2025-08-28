package com.fantasta.dto;

import java.util.Map;
import com.fantasta.model.Role;

public class ParticipantDto {
    public Long id;
    public String name;
    public int totalCredits;
    public int spentCredits;
    public int remainingCredits;

    // giocatori per ruolo
    public Map<Role, Integer> takenByRole;
}
