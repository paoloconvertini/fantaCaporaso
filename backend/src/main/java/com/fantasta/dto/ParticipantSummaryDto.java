package com.fantasta.dto;

import java.util.Map;

public class ParticipantSummaryDto {
    public Long id;
    public String name;
    public int totalCredits;
    public int spentCredits;
    public int remainingCredits;

    // conta quanti giocatori per ruolo (es. {PORTIERE=2, DIFENSORE=5})
    public Map<String, Integer> roleCounts;


}
