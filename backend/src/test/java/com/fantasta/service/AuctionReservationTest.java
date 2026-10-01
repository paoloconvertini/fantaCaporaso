package com.fantasta.service;

import com.fantasta.model.*;
import com.fantasta.dto.RoundDto;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuctionReservationTest {
    @Inject AuctionService auction;

    @AfterEach void reset() { auction.reset(); }

    private Long participant(String name) {
        ParticipantEntity p = new ParticipantEntity();
        p.name = name; p.totalCredits = 100; p.persist();
        return p.id;
    }

    private void setup(boolean enabled) {
        auction.reset();
        MercatoConfigEntity.deleteAll();
        MercatoConfigEntity c = new MercatoConfigEntity();
        c.attiva = true; c.numeroMercato = 1; c.sessionCode = "reservation-test";
        c.quotazioniAggiornate = true; c.partitiImportati = true;
        c.prenotazioneAbilitata = enabled; c.durataPrenotazioneSecondi = 30; c.persist();
        player("Reservation defender", Role.DIFENSORE);
    }

    private void player(String name, Role role) {
        PlayerEntity p = new PlayerEntity(); p.name = name; p.team = "Reservation club";
        p.role = role; p.valore = 1D; p.active = true; p.persist();
    }

    private RoundState start() {
        return auction.start("Reservation defender", "Reservation club", "DIFENSORE", 60, "NONE", 1, null);
    }

    private void advance(RoundState round) {
        auction.advanceIfActive(round.roundId, round.phase, round.endEpochMillis);
    }

    @Test @TestTransaction
    void bookingCommitsMinimumAndCannotBeWithdrawnAndNonBookedCannotBid() {
        setup(true); Long a = participant("Reservation A"), b = participant("Reservation B");
        RoundState s = start();
        assertEquals("RESERVATION", s.phase);
        assertEquals(30, s.durationSeconds);
        assertThrows(IllegalStateException.class, () -> auction.bid(a, 5D));
        RoundDto booked = auction.reserveDto(a, s.roundId);
        assertEquals(1D, s.bids.get(a.toString()));
        assertTrue(booked.bids.isEmpty(), "Amounts stay private");
        assertEquals(1, auction.reserveDto(a, s.roundId).reservedUsers.size());
        assertThrows(IllegalStateException.class, () -> auction.withdrawBidDto(a));
        assertThrows(IllegalStateException.class, this::start);
        String oldPhase = s.phase; Long oldDeadline = s.endEpochMillis;
        advance(s);
        assertEquals("OFFERS", s.phase); assertEquals(60, s.durationSeconds);
        assertNull(auction.advanceIfActive(s.roundId, oldPhase, oldDeadline));
        assertThrows(IllegalArgumentException.class, () -> auction.bid(b, 2D));
        assertThrows(IllegalStateException.class, () -> auction.reserveDto(b, s.roundId));
        auction.bid(a, 5D);
        assertThrows(IllegalArgumentException.class, () -> auction.bid(a, 0D));
        assertThrows(IllegalArgumentException.class, () -> auction.bid(a, 101D));
        RoundState closed = auction.close();
        assertEquals(a, closed.winner.participantId);
        assertEquals(1D, closed.winner.amount);
    }

    @Test @TestTransaction
    void automaticOffersTieAndTieBreakDoesNotRepeatBooking() {
        setup(true); Long a = participant("Reservation tie A"), b = participant("Reservation tie B");
        RoundState s = start(); auction.reserveDto(a,s.roundId); auction.reserveDto(b,s.roundId);
        advance(s); auction.close();
        assertEquals(2,s.tieUsers.size()); assertNull(s.winner);
        RoundState tie = auction.start(s.player,s.playerTeam,s.playerRole,60,"NONE",1,Set.of(a,b));
        assertEquals("OFFERS",tie.phase); assertTrue(tie.reservationRequired);
        auction.bid(a,2D);
        assertThrows(IllegalStateException.class,()->auction.withdrawBidDto(a));
        assertEquals(a,auction.close().winner.participantId);
    }

    @Test @TestTransaction
    void goalkeepersBookAtExistingPackageMinimum() {
        setup(true); Long a=participant("Reservation keeper A");
        for(int i=0;i<3;i++) player("Reservation keeper "+i,Role.PORTIERE);
        RoundState s=auction.start("Reservation keeper 0","Reservation club","PORTIERE",60,"NONE",1,null);
        auction.reserveDto(a,s.roundId);
        assertEquals(3D,s.bids.get(a.toString()));
        advance(s); assertEquals(3D,auction.close().winner.amount);
    }

    @Test @TestTransaction
    void expiredBookingAndStaleRoundAreRejectedAndNoBookingsCloseUnassigned() {
        setup(true); Long a=participant("Reservation deadline A"); RoundState s=start();
        assertThrows(IllegalStateException.class,()->auction.reserveDto(a,"older-round"));
        s.endEpochMillis=System.currentTimeMillis()-1;
        assertThrows(IllegalStateException.class,()->auction.reserveDto(a,s.roundId));
        advance(s); assertTrue(s.closed); assertNull(s.winner);
    }

    @Test @TestTransaction
    void persistedBookingSurvivesReloadAndOfferDeadlineIsEnforced() throws Exception {
        setup(true); Long a=participant("Reservation recovery A"); RoundState s=start();
        auction.reserveDto(a,s.roundId);
        Field field=AuctionService.class.getDeclaredField("state"); field.setAccessible(true); field.set(io.quarkus.arc.ClientProxy.unwrap(auction),null);
        RoundState recovered=auction.get();
        assertEquals("RESERVATION",recovered.phase); assertTrue(recovered.reservedUsers.contains(a));
        assertEquals(s.endEpochMillis,recovered.endEpochMillis);
        advance(recovered); recovered.endEpochMillis=System.currentTimeMillis()-1;
        assertThrows(IllegalStateException.class,()->auction.bid(a,2D));
        assertEquals(a,auction.close().winner.participantId);
    }

    @Test @TestTransaction
    void disabledFeatureKeepsCurrentAuctionAndWithdrawFlow() {
        setup(false); Long a=participant("Reservation disabled A"); RoundState s=start();
        assertEquals("OFFERS",s.phase); assertFalse(s.reservationRequired);
        auction.bid(a,2D); auction.withdrawBidDto(a); assertTrue(s.bids.isEmpty());
    }
    @Test @TestTransaction
    void bookingChecksCreditReserveAndRoleSlotsBeforeCommitting() {
        setup(true); Long a=participant("Reservation invalid budget A"); RoundState s=start();
        ParticipantEntity participant=ParticipantEntity.findById(a);
        participant.totalCredits=1;
        assertThrows(IllegalArgumentException.class,()->auction.reserveDto(a,s.roundId));
        assertTrue(s.bids.isEmpty()); assertTrue(s.reservedUsers.isEmpty());
        participant.totalCredits=100;
        for(int i=0;i<8;i++) {
            player("Reservation occupied "+i,Role.DIFENSORE);
            PlayerEntity occupied=PlayerEntity.find("name","Reservation occupied "+i).firstResult();
            RosterEntity r=new RosterEntity();r.participant=participant;r.player=occupied;r.amount=1D;r.persist();
        }
        assertThrows(IllegalArgumentException.class,()->auction.reserveDto(a,s.roundId));
        assertTrue(s.bids.isEmpty()); assertTrue(s.reservedUsers.isEmpty());
    }

    @Test @TestTransaction
    void goalkeeperBookingPreservesMarketQuoteMinimumAboveThree() {
        setup(true);Long a=participant("Reservation keeper quote A");
        for(int i=0;i<3;i++) {
            player("Reservation priced keeper "+i,Role.PORTIERE);
            PlayerEntity p=PlayerEntity.find("name","Reservation priced keeper "+i).firstResult();p.valore=3D;
        }
        RoundState s=auction.start("Reservation priced keeper 0","Reservation club","PORTIERE",60,"NONE",3,null);
        auction.reserveDto(a,s.roundId);assertEquals(9D,s.bids.get(a.toString()));
    }

}
