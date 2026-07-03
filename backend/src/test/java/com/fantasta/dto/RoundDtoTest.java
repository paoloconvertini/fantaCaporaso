package com.fantasta.dto;

import com.fantasta.model.RoundState;
import com.fantasta.model.Winner;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoundDtoTest {

    @Test
    void toDtoReturnsNullForNullState() {
        assertNull(RoundDto.toDto(null));
    }

    @Test
    void toDtoCopiesRoundFieldsAndMinimumBid() {
        RoundState state = new RoundState();
        state.roundId = "round-1";
        state.player = "Player";
        state.playerTeam = "Team";
        state.playerRole = "DIFENSORE";
        state.value = 12;
        state.closed = true;
        state.minimumBid = 11D;
        state.durationSeconds = 30;
        state.endEpochMillis = 123L;
        state.winner = new Winner(2L, "Angelo", 11D);
        state.allowedUsers = Set.of(2L, 3L);
        state.bids = new LinkedHashMap<>();

        RoundDto dto = RoundDto.toDto(state);

        assertNotNull(dto);
        assertEquals("round-1", dto.roundId);
        assertEquals("Player", dto.player);
        assertEquals("Team", dto.playerTeam);
        assertEquals("DIFENSORE", dto.playerRole);
        assertEquals(12, dto.value);
        assertTrue(dto.closed);
        assertEquals(11D, dto.minimumBid);
        assertEquals(30, dto.durationSeconds);
        assertEquals(123L, dto.endEpochMillis);
        assertEquals("Angelo", dto.winner.user);
        assertTrue(dto.allowedUsers.containsAll(List.of(2L, 3L)));
    }

    @Test
    void toDtoUsesEmptyCollectionsWhenTieAndAllowedUsersAreMissing() {
        RoundState state = new RoundState();
        state.bids = new LinkedHashMap<>();

        RoundDto dto = RoundDto.toDto(state);

        assertNotNull(dto.bids);
        assertTrue(dto.bids.isEmpty());
        assertNotNull(dto.tieUsers);
        assertTrue(dto.tieUsers.isEmpty());
        assertNotNull(dto.tieUserIds);
        assertTrue(dto.tieUserIds.isEmpty());
        assertNotNull(dto.allowedUsers);
        assertTrue(dto.allowedUsers.isEmpty());
    }
}
