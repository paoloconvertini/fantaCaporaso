package com.fantasta.service;

import com.fantasta.model.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RosterServiceTest {

    private final RosterService service = new RosterService();

    @Test
    void incompletePurchasedGoalkeeperPackageDoesNotReserveExtraCredits() {
        Map<Role, Integer> counts = completeRosterExceptAttackersAndGoalkeepers(1);

        assertEquals(3, service.reservedCreditsForBid(counts, 1));
    }

    @Test
    void missingGoalkeeperPackageStillReservesCredits() {
        Map<Role, Integer> counts = completeRosterExceptAttackersAndGoalkeepers(0);

        assertEquals(6, service.reservedCreditsForBid(counts, 1));
    }

    @Test
    void completeGoalkeeperPackageKeepsTheExistingCalculation() {
        Map<Role, Integer> counts = completeRosterExceptAttackersAndGoalkeepers(3);

        assertEquals(3, service.reservedCreditsForBid(counts, 1));
    }

    @Test
    void priceCorrectionReservesEveryCurrentlyOpenSlot() {
        Map<Role, Integer> counts = completeRosterExceptAttackersAndGoalkeepers(1);

        assertEquals(4, service.reservedCreditsForCurrentOpenSlots(counts));
    }

    private Map<Role, Integer> completeRosterExceptAttackersAndGoalkeepers(int goalkeepers) {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        counts.put(Role.PORTIERE, goalkeepers);
        counts.put(Role.DIFENSORE, 8);
        counts.put(Role.CENTROCAMPISTA, 8);
        counts.put(Role.ATTACCANTE, 2);
        return counts;
    }
}
