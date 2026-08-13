package com.fantasta.service;

import com.fantasta.model.GiroEntity;
import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import com.fantasta.model.SkipEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GoalkeeperPackageServiceTest {

    @Inject
    DbService dbService;

    @Test
    @TestTransaction
    void assignsThreeGoalkeepersAndSplitsTheWinningBid() {
        ParticipantEntity participant = participant("Paolo", 500);
        PlayerEntity starter = goalkeeper("Milan 1", "Milan", 11);
        goalkeeper("Milan 2", "Milan", 10);
        goalkeeper("Milan 3", "Milan", 1);

        List<RosterEntity> assigned = dbService.assignPurchasedPlayer(starter, participant.id, 15D);

        assertEquals(3, assigned.size());
        Map<String, Double> costs = assigned.stream()
                .collect(Collectors.toMap(row -> row.player.name, row -> row.amount));
        assertEquals(13D, costs.get("Milan 1"));
        assertEquals(1D, costs.get("Milan 2"));
        assertEquals(1D, costs.get("Milan 3"));
        assertEquals(15D, assigned.stream().mapToDouble(row -> row.amount).sum());
    }

    @Test
    @TestTransaction
    void choosesTopTwoAndOneRandomMinimumValueGoalkeeperWhenTeamHasFour() {
        ParticipantEntity participant = participant("Paolo", 500);
        PlayerEntity starter = goalkeeper("Inter 1", "Inter", 12);
        goalkeeper("Inter 2", "Inter", 9);
        goalkeeper("Inter 3", "Inter", 1);
        goalkeeper("Inter 4", "Inter", 1);

        List<RosterEntity> assigned = dbService.assignPurchasedPlayer(starter, participant.id, 20D);

        assertEquals(3, assigned.size());
        assertTrue(assigned.stream().anyMatch(row -> row.player.name.equals("Inter 1") && row.amount == 18D));
        assertTrue(assigned.stream().anyMatch(row -> row.player.name.equals("Inter 2") && row.amount == 1D));
        assertEquals(1, assigned.stream().filter(row -> row.player.valore == 1D).count());
    }

    @Test
    @TestTransaction
    void completesATwoGoalkeeperTeamWithSurplusFromAFourGoalkeeperTeam() {
        ParticipantEntity participant = participant("Paolo", 500);
        PlayerEntity target = goalkeeper("Como 1", "Como", 8);
        goalkeeper("Como 2", "Como", 1);
        PlayerEntity donorStarter = goalkeeper("Roma 1", "Roma", 10);
        goalkeeper("Roma 2", "Roma", 7);
        goalkeeper("Roma 3", "Roma", 1);
        goalkeeper("Roma 4", "Roma", 1);

        List<RosterEntity> targetPackage = dbService.assignPurchasedPlayer(target, participant.id, 9D);

        assertEquals(3, targetPackage.size());
        assertEquals(2, targetPackage.stream().filter(row -> row.player.team.equals("Como")).count());
        assertEquals(1, targetPackage.stream().filter(row -> row.player.team.equals("Roma") && row.player.valore == 1D).count());
        assertEquals(3, dbService.goalkeeperPackage(donorStarter).size());
    }

    @Test
    @TestTransaction
    void rejectsGoalkeeperPackageBelowThreeCredits() {
        ParticipantEntity participant = participant("Paolo", 500);
        PlayerEntity starter = goalkeeper("Napoli 1", "Napoli", 8);
        goalkeeper("Napoli 2", "Napoli", 1);
        goalkeeper("Napoli 3", "Napoli", 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dbService.assignPurchasedPlayer(starter, participant.id, 2D));

        assertTrue(error.getMessage().contains("3 crediti"));
        assertEquals(0, RosterEntity.count());
    }

    @Test
    @TestTransaction
    void skippingOneGoalkeeperSkipsTheWholeDoorForTheCurrentPass() {
        goalkeeper("Lazio 1", "Lazio", 9);
        PlayerEntity reserve = goalkeeper("Lazio 2", "Lazio", 1);
        goalkeeper("Lazio 3", "Lazio", 1);
        GiroEntity giro = dbService.ensureCurrentGiro();

        dbService.skip(giro.id, reserve);
        Map<String, Object> state = dbService.remainingAndSkippedByRole(giro.id);

        assertEquals(3, SkipEntity.count());
        assertEquals(1, ((Map<?, ?>) state.get("skipped")).get("PORTIERE"));
        assertEquals(0, ((Map<?, ?>) state.get("remaining")).get("PORTIERE"));
    }

    private ParticipantEntity participant(String name, int credits) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.name = name;
        participant.totalCredits = credits;
        participant.persist();
        return participant;
    }

    private PlayerEntity goalkeeper(String name, String team, double value) {
        PlayerEntity player = new PlayerEntity();
        player.name = name;
        player.team = team;
        player.role = Role.PORTIERE;
        player.valore = value;
        player.active = true;
        player.persist();
        return player;
    }
}
