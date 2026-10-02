package com.fantasta.service;

import com.fantasta.model.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CalledPlayerSelectionTest {
    @Inject DbService db;
    @Inject AuctionService auction;

    private PlayerEntity player(String name, Role role) {
        PlayerEntity p = new PlayerEntity();
        p.name = name;
        p.team = "Selection Test Club";
        p.role = role;
        p.active = true;
        p.valore = 5.0;
        p.persist();
        return p;
    }

    @Test @TestTransaction
    void skippedPlayerCanBeCalledWithoutAssigningOrStartingRound() {
        auction.reset();
        PlayerEntity p = player("Selection Test Skipped", Role.DIFENSORE);
        GiroEntity giro = db.ensureCurrentGiro();
        db.skip(giro.id, p);
        long rosters = RosterEntity.count();
        assertTrue(db.searchCallable("Selection Test Skipped", Role.DIFENSORE).contains(p));
        assertEquals(p.id, auction.selectCalledPlayer(p.id).id);
        assertEquals(rosters, RosterEntity.count());
        assertTrue(auction.get() == null || auction.get().closed);
        assertTrue(GiroPickEntity.count("player", p) > 0);
    }

    @Test @TestTransaction
    void activeRoundBlocksChangingTheCalledPlayer() {
        auction.reset();
        MercatoConfigEntity.deleteAll();
        PlayerEntity first = player("Selection Test First", Role.DIFENSORE);
        PlayerEntity second = player("Selection Test Second", Role.DIFENSORE);
        auction.start(first.name, first.team, first.role.name(), 30, "RANDOM", 5, null);
        assertThrows(IllegalStateException.class, () -> auction.selectCalledPlayer(second.id));
        assertEquals(first.name, auction.get().player);
        auction.reset();
    }

    @Test @TestTransaction
    void inactivePlayersAndIncompleteDoorsAreRejected() {
        PlayerEntity inactive = player("Selection Test Inactive", Role.ATTACCANTE);
        inactive.active = false;
        PlayerEntity keeper = player("Selection Test Keeper", Role.PORTIERE);
        assertFalse(db.callable(inactive));
        assertFalse(db.callable(keeper));
        assertTrue(db.searchCallable("Selection Test Inactive", null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> db.selectCallable(inactive.id));
        assertThrows(IllegalArgumentException.class, () -> db.selectCallable(keeper.id));
    }
}
