package com.fantasta.dto;

import com.fantasta.model.RoundState;
import com.fantasta.model.Winner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoundDto {
    public String roundId;
    public String player;
    public String playerTeam;
    public String playerRole;
    public boolean closed;
    public Map<String, Integer> bids; // già con nomi
    public Winner winner;             // già arricchito
    public Integer durationSeconds;
    public Long endEpochMillis;
    public List<String> tieUsers;     // 🔹 qui mettiamo i NOMI, non gli id

    public static RoundDto toDto(RoundState s) {
        if (s == null) return null;

        RoundDto dto = new RoundDto();
        dto.roundId = s.roundId;
        dto.player = s.player;
        dto.playerTeam = s.playerTeam;
        dto.playerRole = s.playerRole;
        return dto;
    }


}
