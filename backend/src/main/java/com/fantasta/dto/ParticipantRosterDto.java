package com.fantasta.dto;

import java.util.List;

public class ParticipantRosterDto {
    public Long participantId;
    public String participantName;
    public List<RosterDto> roster;

    public ParticipantRosterDto(Long participantId, String participantName, List<RosterDto> roster) {
        this.participantId = participantId;
        this.participantName = participantName;
        this.roster = roster;
    }
}
