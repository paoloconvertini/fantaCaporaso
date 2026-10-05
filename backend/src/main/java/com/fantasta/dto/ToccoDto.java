package com.fantasta.dto;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.ToccoState;
import java.util.List;

public record ToccoDto(String id, List<Participant> participants, Long firstParticipantId,
                       boolean completed, Integer sum, Long winnerParticipantId, String winnerName) {
    public record Participant(Long id, String name, boolean confirmed, Integer number) {}

    public static ToccoDto from(ToccoState state) {
        if (state == null) return null;
        boolean completed = state.winnerParticipantId != null;
        List<Participant> participants = state.order.stream().map(id -> {
            ParticipantEntity owner = ParticipantEntity.findById(id);
            return new Participant(id, owner == null ? "??-" + id : owner.name,
                    state.choices.containsKey(id.toString()), completed ? state.choices.get(id.toString()) : null);
        }).toList();
        String winnerName = participants.stream().filter(p -> p.id().equals(state.winnerParticipantId))
                .map(Participant::name).findFirst().orElse(null);
        return new ToccoDto(state.id, participants, state.firstParticipantId, completed,
                completed ? state.sum : null, state.winnerParticipantId, winnerName);
    }
}
