package com.fantasta.service;

import com.fantasta.model.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuctionServiceTest {

    @Inject
    AuctionService auctionService;

    @Inject
    RosterService rosterService;

    @Test
    @TestTransaction
    void tieBreakIsRestrictedToTiedParticipantsAndStartsAboveThePreviousBid() {
        cleanAuctionData();
        ParticipantEntity first = participant("Prima", 500);
        ParticipantEntity second = participant("Seconda", 500);
        ParticipantEntity outsider = participant("Terza", 500);
        player("Difensore", "Roma", Role.DIFENSORE, 8);

        auctionService.start("Difensore", "Roma", "DIFENSORE", 30, "NONE", 8, null);
        auctionService.bid(first.id, 10D);
        auctionService.bid(second.id, 10D);
        RoundState tied = auctionService.close();

        assertNull(tied.winner);
        assertEquals(Set.of(first.id, second.id), Set.copyOf(tied.tieUsers));
        assertEquals(0, RosterEntity.count());

        RoundState tieBreak = auctionService.start(
                "Difensore", "Roma", "DIFENSORE", 30, "NONE", 8, Set.copyOf(tied.tieUsers));
        assertEquals(11D, tieBreak.minimumBid);
        assertThrows(IllegalArgumentException.class, () -> auctionService.bid(outsider.id, 11D));
        assertDoesNotThrow(() -> auctionService.bid(first.id, 11D));
    }

    @Test
    @TestTransaction
    void manualCloseAssignsTheHighestBid() {
        cleanAuctionData();
        ParticipantEntity first = participant("Prima", 500);
        ParticipantEntity second = participant("Seconda", 500);
        player("Attaccante", "Milan", Role.ATTACCANTE, 20);
        int openSlotsBefore = rosterService.openSlotsByRole().get("ATTACCANTE");

        auctionService.start("Attaccante", "Milan", "ATTACCANTE", 60, "NONE", 20, null);
        auctionService.bid(first.id, 12D);
        auctionService.bid(second.id, 15D);
        RoundState closed = auctionService.close();

        assertEquals(second.id, closed.winner.participantId);
        RosterEntity roster = RosterEntity.find("player.name", "Attaccante").firstResult();
        assertNotNull(roster);
        assertEquals(second.id, roster.participant.id);
        assertEquals(15D, roster.amount);
        assertEquals(openSlotsBefore - 1, rosterService.openSlotsByRole().get("ATTACCANTE"));
    }

    @Test
    @TestTransaction
    void goalkeeperRoundHasThreeCreditMinimum() {
        cleanAuctionData();
        goalkeeper("Milan 1", "Milan", 10);
        goalkeeper("Milan 2", "Milan", 1);
        goalkeeper("Milan 3", "Milan", 1);

        RoundState round = auctionService.start("Milan 2", "Milan", "PORTIERE", 30, "NONE", 1, null);

        assertEquals(3D, round.minimumBid);
    }

    @Test
    @TestTransaction
    void latestBidFromTheSameParticipantReplacesThePreviousOne() {
        cleanAuctionData();
        ParticipantEntity participant = participant("Ripensamento", 500);
        player("Centrocampista", "Roma", Role.CENTROCAMPISTA, 10);

        auctionService.start("Centrocampista", "Roma", "CENTROCAMPISTA", 30, "NONE", 10, null);
        auctionService.bid(participant.id, 20D);
        RoundState updated = auctionService.bid(participant.id, 12D);

        assertEquals(1, updated.bids.size());
        assertEquals(12D, updated.bids.get(String.valueOf(participant.id)));
        assertEquals(participant.id, auctionService.close().winner.participantId);
        RosterEntity roster = RosterEntity.find("player.name", "Centrocampista").firstResult();
        assertEquals(1D, roster.amount);
    }

    @Test
    @TestTransaction
    void singleBidderWinsARegularPlayerForOneCredit() {
        cleanAuctionData();
        ParticipantEntity participant = participant("Solo offerente", 500);
        player("Difensore singolo", "Roma", Role.DIFENSORE, 10);

        auctionService.start("Difensore singolo", "Roma", "DIFENSORE", 30, "NONE", 10, null);
        auctionService.bid(participant.id, 14D);
        RoundState closed = auctionService.close();

        assertEquals(1D, closed.winner.amount);
        assertEquals(14D, closed.bids.get(String.valueOf(participant.id)));
        RosterEntity roster = RosterEntity.find("player.name", "Difensore singolo").firstResult();
        assertEquals(1D, roster.amount);
    }

    @Test
    @TestTransaction
    void singleBidderWinsGoalkeeperPackageForThreeCredits() {
        cleanAuctionData();
        ParticipantEntity participant = participant("Solo porta", 500);
        goalkeeper("Portiere singolo 1", "Torino", 10);
        goalkeeper("Portiere singolo 2", "Torino", 1);
        goalkeeper("Portiere singolo 3", "Torino", 1);

        auctionService.start("Portiere singolo 1", "Torino", "PORTIERE", 30, "NONE", 10, null);
        auctionService.bid(participant.id, 14D);
        RoundState closed = auctionService.close();

        assertEquals(3D, closed.winner.amount);
        assertEquals(14D, closed.bids.get(String.valueOf(participant.id)));
        List<RosterEntity> roster = RosterEntity.list("participant", participant);
        assertEquals(3, roster.size());
        assertEquals(3D, roster.stream().mapToDouble(row -> row.amount).sum());
    }

    @Test
    @TestTransaction
    void bidMustReserveOneCreditForEveryOtherEmptyRosterSlot() {
        cleanAuctionData();
        ParticipantEntity participant = participant("Budget protetto", 500);
        player("Difensore budget", "Roma", Role.DIFENSORE, 10);

        auctionService.start("Difensore budget", "Roma", "DIFENSORE", 30, "NONE", 10, null);

        assertDoesNotThrow(() -> auctionService.bid(participant.id, 476D));
        assertThrows(IllegalArgumentException.class, () -> auctionService.bid(participant.id, 477D));
    }

    @Test
    @TestTransaction
    void goalkeeperPackageMaxBidAccountsForThreePurchasedPlayers() {
        cleanAuctionData();
        ParticipantEntity participant = participant("Porta budget", 500);
        goalkeeper("Portiere budget 1", "Napoli", 10);
        goalkeeper("Portiere budget 2", "Napoli", 1);
        goalkeeper("Portiere budget 3", "Napoli", 1);

        RoundState round = auctionService.start("Portiere budget 1", "Napoli", "PORTIERE", 30, "NONE", 10, null);

        assertEquals(3, round.purchaseSize);
        assertDoesNotThrow(() -> auctionService.bid(participant.id, 478D));
        assertThrows(IllegalArgumentException.class, () -> auctionService.bid(participant.id, 479D));
    }

    @Test
    void acceptsSixteenConcurrentBidsWithoutLosingParticipants() throws Exception {
        cleanAuctionData();
        List<Long> participantIds = QuarkusTransaction.requiringNew().call(() -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 1; i <= 16; i++) {
                ids.add(participant("Squadra concorrente " + i, 500).id);
            }
            player("Difensore concorrente", "Inter", Role.DIFENSORE, 10);
            return ids;
        });
        auctionService.start("Difensore concorrente", "Inter", "DIFENSORE", 30, "NONE", 10, null);

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < participantIds.size(); i++) {
                Long participantId = participantIds.get(i);
                double amount = i + 1D;
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    auctionService.bid(participantId, amount);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> request : requests) {
                request.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(16, auctionService.get().bids.size());
        assertEquals(16D, auctionService.get().bids.get(String.valueOf(participantIds.get(15))));
        auctionService.bid(participantIds.get(0), 21D);
        assertEquals(16, auctionService.get().bids.size());
        assertEquals(21D, auctionService.get().bids.get(String.valueOf(participantIds.get(0))));

        auctionService.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            PlayerEntity.delete("name", "Difensore concorrente");
            for (Long participantId : participantIds) {
                ParticipantEntity.deleteById(participantId);
            }
        });
    }

    @Test
    @TestTransaction
    void timerCloseOnlyClosesTheExpectedActiveRound() {
        cleanAuctionData();
        ParticipantEntity participant = participant("Prima", 500);
        player("Attaccante timer", "Milan", Role.ATTACCANTE, 15);
        RoundState round = auctionService.start("Attaccante timer", "Milan", "ATTACCANTE", 30, "NONE", 15, null);
        auctionService.bid(participant.id, 7D);

        assertNull(auctionService.closeIfActive("round-superato"));
        assertFalse(auctionService.get().closed);
        RoundState closed = auctionService.closeIfActive(round.roundId);

        assertNotNull(closed);
        assertTrue(closed.closed);
        assertEquals(participant.id, closed.winner.participantId);
    }

    private void cleanAuctionData() {
        auctionService.reset();
    }

    private ParticipantEntity participant(String name, int credits) {
        ParticipantEntity entity = new ParticipantEntity();
        entity.name = name;
        entity.totalCredits = credits;
        entity.persist();
        return entity;
    }

    private PlayerEntity goalkeeper(String name, String team, double value) {
        return player(name, team, Role.PORTIERE, value);
    }

    private PlayerEntity player(String name, String team, Role role, double value) {
        PlayerEntity entity = new PlayerEntity();
        entity.name = name;
        entity.team = team;
        entity.role = role;
        entity.valore = value;
        entity.active = true;
        entity.persist();
        return entity;
    }
}
