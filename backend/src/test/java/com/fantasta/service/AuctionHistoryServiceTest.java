package com.fantasta.service;

import com.fantasta.model.AuctionHistoryBidEntity;
import com.fantasta.model.AuctionHistoryEntity;
import com.fantasta.model.RoundState;
import com.fantasta.model.Winner;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AuctionHistoryServiceTest {
    @Inject AuctionHistoryService service;
    private String roundId;

    @AfterEach
    void cleanup() {
        if (roundId == null) return;
        QuarkusTransaction.requiringNew().run(() -> {
            AuctionHistoryEntity history = AuctionHistoryEntity.find("roundId", roundId).firstResult();
            if (history != null) {
                AuctionHistoryBidEntity.delete("history", history);
                history.delete();
            }
        });
    }

    @Test
    void storesOnlyCompetitiveRoundsAndIsIdempotent() {
        RoundState competitive = round(2);
        roundId = competitive.roundId;
        service.record(competitive);
        service.record(competitive);

        assertEquals(1L, QuarkusTransaction.requiringNew().call(
                () -> AuctionHistoryEntity.count("roundId", roundId)));
        assertEquals(2L, QuarkusTransaction.requiringNew().call(
                () -> AuctionHistoryBidEntity.count("history.roundId", roundId)));
        assertEquals(1, service.list("Test storico", null, "date", 0, 30, null).items().size());
        assertEquals(1, service.list(null, null, "date", 0, 30, 900001L).items().size());
        assertEquals(0, service.list(null, null, "date", 0, 30, 900099L).items().size());
        assertEquals(0, service.list(null, null, "date", 0, 30, -1L).items().size());
    }

    @Test
    void skipsRoundsWithOneBidder() {
        RoundState single = round(1);
        roundId = single.roundId;
        service.record(single);
        assertEquals(0L, QuarkusTransaction.requiringNew().call(
                () -> AuctionHistoryEntity.count("roundId", roundId)));
    }

    private RoundState round(int bidders) {
        RoundState round = new RoundState();
        round.roundId = UUID.randomUUID().toString();
        round.competitiveOriginRoundId = round.roundId;
        round.auctionSessionCode = UUID.randomUUID().toString();
        round.player = "Test storico " + round.roundId;
        round.playerTeam = "Inter";
        round.playerRole = "ATTACCANTE";
        round.value = 20;
        round.winner = new Winner(null, "Squadra Alfa", 15D);
        round.historyBids = new LinkedHashMap<>();
        round.historyBids.put("900001", 15D);
        if (bidders > 1) round.historyBids.put("900002", 12D);
        return round;
    }
}
