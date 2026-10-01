package com.fantasta.service;

import com.fantasta.model.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GoalkeeperRosterCorrectionTest {
    @Inject RosterService rosterService;
    @Inject ParticipantService participantService;

    @Test
    @TestTransaction
    void correctsReserveWithoutCreditsOrGoalkeeperReleaseEvenInSecondMarket() throws Exception {
        var setup = setup();
        var incoming = keeper("Riserva nuova uno", 9);
        setup.second.player.team = "Club dopo trasferimento";
        setup.second.player.active = false;
        int remaining = participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits);
        int total = setup.owner.totalCredits;
        long movementCount = MarketMovementEntity.count();
        byte[] file = workbook(Map.of(setup.second.player.id, incoming));
        var preview = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), false);
        assertTrue(preview.errors.isEmpty(), preview.errors.toString());
        assertEquals(1, preview.goalkeeperCorrections.size());
        assertTrue(preview.releases.isEmpty());
        assertTrue(preview.exchanges.isEmpty());
        assertEquals("Riserva vecchia uno", setup.second.player.name);
        assertFalse(incoming.assigned);

        var old = setup.second.player;
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertEquals(incoming.id, setup.second.player.id);
        assertEquals(1D, setup.second.amount);
        assertEquals(total, setup.owner.totalCredits);
        assertEquals(remaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
        assertEquals(0, MercatoSvincolo.getCount(setup.owner, Role.PORTIERE, "reserve-correction-test"));
        assertEquals(movementCount, MarketMovementEntity.count());
        assertFalse(old.assigned);
        assertTrue(incoming.assigned);
        assertEquals(3, RosterEntity.count("participant", setup.owner));

        var repeat = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(repeat.errors.isEmpty(), repeat.errors.toString());
        assertTrue(repeat.goalkeeperCorrections.isEmpty());
        assertEquals(remaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
    }

    @Test
    @TestTransaction
    void correctsBothReservesKeepingTheirHistoricalCosts() throws Exception {
        var setup = setup();
        var first = keeper("Nuova riserva doppia uno", 6);
        var second = keeper("Nuova riserva doppia due", 3);
        int remaining = participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits);
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(workbook(
                Map.of(setup.second.player.id, first, setup.third.player.id, second))), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertEquals(2, result.goalkeeperCorrections.size());
        assertEquals(1D, setup.second.amount);
        assertEquals(1D, setup.third.amount);
        assertEquals(remaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
        assertEquals("Titolare correzione", setup.first.player.name);
    }

    @Test
    @TestTransaction
    void combinesReserveCorrectionWithOrdinaryReleaseWithoutChangingItsRefund() throws Exception {
        var setup = setup();
        MercatoConfigEntity config = MercatoConfigEntity.findAll().firstResult();
        config.maxAttaccanti = 1;
        var released = keeper("Attaccante ceduto con correzione", 12);
        released.role = Role.ATTACCANTE;
        roster(setup.owner, released, 5);
        var incoming = keeper("Riserva nuova con cessione", 3);
        int remaining = participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits);
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(workbook(
                Map.of(setup.second.player.id, incoming), Set.of(released.id))), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertEquals(1, result.goalkeeperCorrections.size());
        assertEquals(1, result.releases.size());
        assertEquals(remaining + 12, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
        assertEquals(0, MercatoSvincolo.getCount(setup.owner, Role.PORTIERE, config.sessionCode));
        assertEquals(1, MercatoSvincolo.getCount(setup.owner, Role.ATTACCANTE, config.sessionCode));
        assertEquals(0, RosterEntity.count("player", released));
        assertEquals(incoming.id, setup.second.player.id);
        assertEquals(1D, setup.second.amount);
    }

    @Test
    @TestTransaction
    void correctsAnyPortiereOfSameClubButRejectsDifferentClub() throws Exception {
        var setup = setup();
        var incoming = keeper("Cambio titolare vietato", 2);
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(workbook(
                Map.of(setup.first.player.id, incoming))), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertEquals(incoming.id, setup.first.player.id);
        assertEquals(20D, setup.first.amount);
        assertTrue(incoming.assigned);

        incoming = keeper("Portiere club diverso", 2);
        incoming.team = "Altro club";
        result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(workbook(
                Map.of(setup.second.player.id, incoming))), true);
        assertFalse(result.errors.isEmpty());
        assertEquals("Riserva vecchia uno", setup.second.player.name);
    }

    @Test
    @TestTransaction
    void preservesPackageCostWhenReplacedPortieriHaveDifferentHistoricalCosts() throws Exception {
        var setup = setup();
        setup.third.amount = 2D;
        var incoming = keeper("Ambigua nuova uno", 2);
        var other = keeper("Ambigua nuova due", 1);
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(workbook(
                Map.of(setup.second.player.id, incoming, setup.third.player.id, other))), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertTrue(incoming.assigned);
        assertTrue(other.assigned);
        assertEquals(3D, setup.second.amount + setup.third.amount);
        assertEquals(0, MercatoSvincolo.getCount(setup.owner, Role.PORTIERE, "reserve-correction-test"));
    }

    @Test
    @TestTransaction
    void rejectsAReplacementAlreadyOwnedByAnotherParticipant() throws Exception {
        var setup = setup();
        var otherOwner = new ParticipantEntity();
        otherOwner.name = "Altro proprietario correzione";
        otherOwner.totalCredits = 500;
        otherOwner.persist();
        var incoming = keeper("Portiere già posseduto", 4);
        roster(otherOwner, incoming, 1);
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(workbook(
                Map.of(setup.second.player.id, incoming))), true);
        assertFalse(result.errors.isEmpty());
        assertEquals("Riserva vecchia uno", setup.second.player.name);
        assertEquals(otherOwner.id, ((RosterEntity) RosterEntity.find("player", incoming).firstResult()).participant.id);
    }

    @Test
    @TestTransaction
    void completesMissingGoalkeepersWithoutIncreasingCostOrChangingCredits() throws Exception {
        var setup = setup();
        setup.second.delete();
        setup.third.delete();
        setup.second.player.assigned = false;
        setup.third.player.assigned = false;
        var one = keeper("Portiere mancante uno", 3);
        var two = keeper("Portiere mancante due", 2);
        int remaining = participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits);
        byte[] file = workbook(Map.of(), Set.of(), Map.of(setup.owner.id, List.of(one, two)));
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertEquals(2, result.goalkeeperCorrections.size());
        assertEquals(3, RosterEntity.count("participant", setup.owner));
        assertEquals(20, participantService.spentCreditsById(setup.owner.id));
        assertEquals(remaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
        assertEquals(0, MercatoSvincolo.getCount(setup.owner, Role.PORTIERE, "reserve-correction-test"));
        var repeat = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(repeat.errors.isEmpty());
        assertTrue(repeat.goalkeeperCorrections.isEmpty());
    }

    @Test
    @TestTransaction
    void correctsCostOnePackageAndTransfersBorrowedGoalkeeperWithoutExchange() throws Exception {
        var setup = setup();
        setup.first.amount = 1D;
        setup.third.player.team = "Club donor";
        var otherOwner = new ParticipantEntity();
        otherOwner.name = "Squadra club donor";
        otherOwner.totalCredits = 500;
        otherOwner.persist();
        var first = keeper("Portiere donor principale", 20);
        var second = keeper("Portiere donor confermato", 2);
        var outgoing = keeper("Portiere donor sostituito", 1);
        first.team = second.team = outgoing.team = "Club donor";
        roster(otherOwner, first, 49);
        roster(otherOwner, second, 1);
        roster(otherOwner, outgoing, 1);
        var incoming = keeper("Nuovo portiere costo uno", 3);
        int originalRemaining = participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits);
        int otherRemaining = participantService.remainingCreditsById(otherOwner.id, otherOwner.totalCredits);
        var donor = setup.third.player;
        byte[] file = workbook(Map.of(donor.id, incoming, outgoing.id, donor));
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(result.errors.isEmpty(), result.errors.toString());
        assertEquals(2, result.goalkeeperCorrections.size());
        assertTrue(result.exchanges.isEmpty());
        assertTrue(result.releases.isEmpty());
        assertEquals(originalRemaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
        assertEquals(otherRemaining, participantService.remainingCreditsById(otherOwner.id, otherOwner.totalCredits));
        assertTrue(donor.assigned);
        assertFalse(outgoing.assigned);
        assertEquals(otherOwner.id, ((RosterEntity) RosterEntity.find("player", donor).firstResult()).participant.id);
    }

    @Test
    @TestTransaction
    void restoresMissingOutfieldPlayerWithHistoricalCostAndPreservesRemainingCredits() throws Exception {
        var setup = setup();
        var missing = keeper("Difensore mancante storico", 7);
        missing.role = Role.DIFENSORE;
        int remaining = participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits);
        byte[] file = workbook(Map.of(), Set.of(), Map.of(setup.owner.id, List.of(missing)));
        var preview = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), false);
        assertTrue(preview.errors.isEmpty());
        assertEquals(1, preview.rosterCorrections.size());
        assertFalse(missing.assigned);
        var result = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(result.errors.isEmpty());
        assertEquals(2D, ((RosterEntity) RosterEntity.find("player", missing).firstResult()).amount);
        assertEquals(remaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
        assertTrue(missing.assigned);
        assertEquals(0, MercatoSvincolo.getCount(setup.owner, Role.DIFENSORE, "reserve-correction-test"));
        var repeat = rosterService.reconcileMarketRosters(new ByteArrayInputStream(file), true);
        assertTrue(repeat.rosterCorrections.isEmpty());
        assertEquals(remaining, participantService.remainingCreditsById(setup.owner.id, setup.owner.totalCredits));
    }

    private Setup setup() {
        MercatoConfigEntity.deleteAll();
        var config = new MercatoConfigEntity();
        config.numeroMercato = 2;
        config.sessionCode = "reserve-correction-test";
        config.attiva = true;
        config.partitiImportati = true;
        config.quotazioniAggiornate = true;
        config.maxPortieri = 0;
        config.persist();
        var owner = new ParticipantEntity();
        owner.name = "Squadra correzione portieri";
        owner.totalCredits = 500;
        owner.persist();
        return new Setup(owner, roster(owner, keeper("Titolare correzione", 30), 20),
                roster(owner, keeper("Riserva vecchia uno", 4), 1),
                roster(owner, keeper("Riserva vecchia due", 2), 1));
    }

    private PlayerEntity keeper(String name, double value) {
        var player = new PlayerEntity();
        player.name = name;
        player.team = "Club correzione";
        player.role = Role.PORTIERE;
        player.valore = value;
        player.persist();
        return player;
    }

    private RosterEntity roster(ParticipantEntity owner, PlayerEntity player, double cost) {
        var entry = new RosterEntity();
        entry.participant = owner;
        entry.player = player;
        entry.amount = cost;
        entry.persist();
        player.assigned = true;
        return entry;
    }

    private byte[] workbook(Map<Long, PlayerEntity> replacements) throws Exception {
        return workbook(replacements, Set.of());
    }

    private byte[] workbook(Map<Long, PlayerEntity> replacements, Set<Long> omitted) throws Exception {
        return workbook(replacements, omitted, Map.of());
    }

    private byte[] workbook(Map<Long, PlayerEntity> replacements, Set<Long> omitted,
                            Map<Long, List<PlayerEntity>> additions) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            for (ParticipantEntity owner : ParticipantEntity.<ParticipantEntity>listAll()) {
                var sheet = workbook.createSheet("Squadra " + owner.id);
                sheet.createRow(0).createCell(0).setCellValue(owner.name);
                int index = 2;
                for (RosterEntity entry : RosterEntity.<RosterEntity>list("participant", owner)) {
                    if (omitted.contains(entry.player.id)) continue;
                    PlayerEntity player = replacements.getOrDefault(entry.player.id, entry.player);
                    var row = sheet.createRow(index++);
                    row.createCell(0).setCellValue(player.name);
                    row.createCell(1).setCellValue(player.team);
                    row.createCell(3).setCellValue(entry.amount == null ? 0D : entry.amount);
                }
                for (PlayerEntity player : additions.getOrDefault(owner.id, List.of())) {
                    var row = sheet.createRow(index++);
                    row.createCell(0).setCellValue(player.name);
                    row.createCell(1).setCellValue(player.team);
                    row.createCell(3).setCellValue(player.role == Role.DIFENSORE ? 2D : 1D);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private record Setup(ParticipantEntity owner, RosterEntity first, RosterEntity second, RosterEntity third) {}
}
