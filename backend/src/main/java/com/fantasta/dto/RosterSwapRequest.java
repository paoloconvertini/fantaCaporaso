package com.fantasta.dto;

import java.util.List;

public class RosterSwapRequest {
    public Long sourceParticipantId;
    public Long destinationParticipantId;
    public List<Long> sourcePlayerIds;
    public List<Long> destinationPlayerIds;
}
