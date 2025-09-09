package com.fantasta.service;

import com.fantasta.dto.RosterDto;
import com.fantasta.model.RosterEntity;
import com.fantasta.model.RosterHistoryEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RosterQueryService {

    public List<RosterDto> getRosters(Long participantId, Long sessionId) {
        if (sessionId != null) {
            // Query filtrata su RosterHistory
            if (participantId != null) {
                return RosterHistoryEntity.find(
                                "participant.id = ?1 and sessionId = ?2", participantId, sessionId
                        ).stream()
                        .map(r -> toDto((RosterHistoryEntity) r))
                        .collect(Collectors.toList());
            } else {
                return RosterHistoryEntity.find(
                                "sessionId = ?1", sessionId
                        ).stream()
                        .map(r -> toDto((RosterHistoryEntity) r))
                        .collect(Collectors.toList());
            }
        } else {
            // Query filtrata su Roster corrente
            if (participantId != null) {
                return RosterEntity.find(
                                "participant.id = ?1", participantId
                        ).stream()
                        .map(r -> toDto((RosterEntity) r))
                        .collect(Collectors.toList());
            } else {
                return RosterEntity.findAll().stream()
                        .map(r -> toDto((RosterEntity) r))
                        .collect(Collectors.toList());
            }
        }
    }

    private RosterDto toDto(RosterEntity r) {
        return new RosterDto(
                r.participant.id,
                r.participant.name,
                r.player.id,
                r.player.name,
                r.player.team,
                r.player.role.toString(),
                r.amount
        );
    }

    private RosterDto toDto(RosterHistoryEntity r) {
        return new RosterDto(
                r.participant.id,
                r.participant.name,
                r.player.id,
                r.player.name,
                r.player.team,
                r.player.role.toString(),
                r.amount
        );
    }
}
