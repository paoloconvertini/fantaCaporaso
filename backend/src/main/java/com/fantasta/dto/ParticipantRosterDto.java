package com.fantasta.dto;

import java.util.List;

public class ParticipantRosterDto {
    public Long participantId;
    public String participantName;
    public String username;
    public List<RosterDto> roster;

    public ParticipantRosterDto(Long participantId, String participantName, String username, List<RosterDto> roster) {
        this.participantId = participantId;
        this.participantName = participantName;
        this.username = username;
        this.roster = roster;
    }
}
