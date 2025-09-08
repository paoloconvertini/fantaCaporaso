package com.fantasta.dto;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.RoundState;
import com.fantasta.model.Winner;

import java.util.*;
import java.util.stream.Collectors;

public class RoundDto {
    public String roundId;
    public String player;
    public String playerTeam;
    public String playerRole;
    public boolean closed;
    public Map<String, Integer> bids;   // chiave = nome partecipante
    public Winner winner;
    public Integer durationSeconds;
    public Long endEpochMillis;
    public List<String> tieUsers;       // Nomi dei partecipanti in parità
    public List<Long> tieUserIds;
    public List<Long> allowedUsers;
    public Integer value;               // (opzionale) valore del calciatore

    public static RoundDto toDto(RoundState s) {
        if (s == null) return null;

        RoundDto dto = new RoundDto();
        dto.roundId = s.roundId;
        dto.player = s.player;
        dto.playerTeam = s.playerTeam;
        dto.playerRole = s.playerRole;
        dto.closed = s.closed;
        dto.durationSeconds = s.durationSeconds;
        dto.endEpochMillis = s.endEpochMillis;
        dto.winner = s.winner;
        dto.value = s.value;

        // bids: id -> nome
        if (s.bids != null) {
            Map<String, Integer> out = new LinkedHashMap<>();
            s.bids.forEach((idStr, amount) -> {
                try {
                    Long id = Long.valueOf(idStr);
                    ParticipantEntity p = ParticipantEntity.findById(id);
                    String name = (p != null ? p.name : ("??-" + id));
                    out.put(name, (int)Math.round(amount));
                } catch (NumberFormatException e) {
                    // fallback: usa la chiave così com'è
                    out.put(idStr, (int)Math.round(amount));
                }
            });
            dto.bids = out;
        } else {
            dto.bids = Collections.emptyMap();
        }

        // tie users: ids -> nomi (+ tieni anche gli ids)
        if (s.tieUsers != null && !s.tieUsers.isEmpty()) {
            dto.tieUserIds = new ArrayList<>(s.tieUsers);
            dto.tieUsers = s.tieUsers.stream().map(id -> {
                ParticipantEntity p = ParticipantEntity.findById(id);
                return p != null ? p.name : ("??-" + id);
            }).collect(Collectors.toList());
            dto.allowedUsers = (s.allowedUsers == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(s.allowedUsers);
        } else {
            dto.tieUserIds = Collections.emptyList();
            dto.tieUsers = Collections.emptyList();
        }

        return dto;
    }
}
