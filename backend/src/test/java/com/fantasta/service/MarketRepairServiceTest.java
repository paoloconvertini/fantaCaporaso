package com.fantasta.service;

import com.fantasta.dto.MarketPlayerImportResult;
import com.fantasta.dto.ReleaseResultDto;
import com.fantasta.dto.SvincoloRequest;
import com.fantasta.dto.RosterSwapRequest;
import com.fantasta.model.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MarketRepairServiceTest {
    @Inject RosterService rosterService;
    @Inject ParticipantService participantService;
    @Inject MarketRestrictionService restrictionService;
    @Inject DbService dbService;
    @Inject AuctionService auctionService;
    @Inject MercatoService mercatoService;
    @Inject PlayerQueryService playerQueryService;
    @Inject RosterMovementService rosterMovementService;
    @Inject MarketDepartureImportService departureImportService;

    @Test
    @TestTransaction
    void importsExplicitDeparturesBeforeQuotesAndPreservesRefund() throws Exception {
        configureMarket(1, false);
        MercatoConfigEntity config = MercatoConfigEntity.findAll().firstResult();
        config.partitiImportati = false;
        ParticipantEntity owner = participant("Squadra partiti test");
        PlayerEntity departed = player("Partito esplicito test", 5, true);
        roster(owner, departed, 3);
        byte[] file = departureWorkbook(owner.name, departed.name, "23");
        assertThrows(IllegalStateException.class,
                () -> dbService.previewMarketPlayersFromExcel(new ByteArrayInputStream(workbook())));
        var preview = departureImportService.importDepartures(new ByteArrayInputStream(file), false);
        assertTrue(preview.preview);
        assertTrue(preview.errors.isEmpty());
        assertTrue(departed.active);
        assertEquals(5D, departed.valore);
        assertFalse(config.partitiImportati);

        departureImportService.importDepartures(new ByteArrayInputStream(file), true);
        assertTrue(config.partitiImportati);
        assertFalse(config.quotazioniAggiornate);
        assertFalse(departed.active);
        assertEquals(23D, departed.valore);
        assertEquals(1, RosterEntity.count("player", departed));
        assertThrows(IllegalStateException.class,
                () -> departureImportService.importDepartures(new ByteArrayInputStream(file), true));

        try (XSSFWorkbook quotes = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = quotes.createSheet("Tutti");
            String[][] rows = {{"Nome", "Squadra", "Ruolo", "Quotazione"},
                    {departed.name, "Inter", "A", "99"}, {"Aggiornato mercato", "Inter", "A", "34"}};
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i);
                for (int c = 0; c < rows[i].length; c++) row.createCell(c).setCellValue(rows[i][c]);
            }
            quotes.write(out);
            dbService.updateMarketPlayersFromExcel(new ByteArrayInputStream(out.toByteArray()));
        }
        assertFalse(departed.active);
        assertEquals(23D, departed.valore);
        SvincoloRequest request = new SvincoloRequest();
        request.playerId = departed.id;
        var release = rosterService.svincola(owner.id, request);
        assertTrue(release.departed);
        assertEquals(23D, release.refundedCredits);
        assertEquals(0, MercatoSvincolo.getCount(owner, Role.ATTACCANTE, config.sessionCode));
    }

    @Test
    @TestTransaction
    void departureOwnerMismatchDoesNotApplyPartialChanges() throws Exception {
        configureMarket(1, false);
        MercatoConfigEntity config = MercatoConfigEntity.findAll().firstResult();
        config.partitiImportati = false;
        PlayerEntity player = player("Partito mismatch test", 9, true);
        roster(participant("Proprietario corretto test"), player, 4);
        var result = departureImportService.importDepartures(new ByteArrayInputStream(
                departureWorkbook("Proprietario errato", player.name, "20")), true);
        assertFalse(result.errors.isEmpty());
        assertTrue(player.active);
        assertEquals(9D, player.valore);
        assertFalse(config.partitiImportati);
    }

    private byte[] departureWorkbook(String owner, String name, String value) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Giocatori Partiti");
            sheet.createRow(1).createCell(1).setCellValue("Giocatori Partiti");
            var row = sheet.createRow(3);
            String[] values = {"", "Responsabile - (" + owner + ")", "A", "ZZZ - Partito - " + name, "Inter", value, "Partito"};
            for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
            row.createCell(9).setCellValue("Riepilogo laterale ignorato");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @TestTransaction
    void marketOpeningDependsOnlyOnManualFlag() {
        configureMarket(1, true);
        MercatoConfigEntity config = MercatoConfigEntity.findAll().firstResult();
        config.fineSessione = LocalDateTime.now().minusDays(1);

        assertTrue(mercatoService.isMercatoAttivo());

        config.attiva = false;
        assertFalse(mercatoService.isMercatoAttivo());
    }

    @Test
    @TestTransaction
    void freePlayerSearchCombinesNameAndRole() {
        player("Ricerca Mercato Alfa", 8, true);
        PlayerEntity defender = player("Ricerca Mercato Beta", 7, true);
        defender.role = Role.DIFENSORE;

        assertEquals(1, playerQueryService.getFreePlayers("ATTACCANTE", "alfa").stream()
                .filter(result -> result.name.equals("Ricerca Mercato Alfa"))
                .count());
        assertTrue(playerQueryService.getFreePlayers("DIFENSORE", "alfa").stream()
                .noneMatch(result -> result.name.equals("Ricerca Mercato Alfa")));
    }

    @Test
    @TestTransaction
    void releaseAddsExactlyCurrentValueAndRestrictsEveryFormerOwner() {
        configureMarket(1, true);
        ParticipantEntity current = participant("Mercato corrente");
        ParticipantEntity former = participant("Mercato precedente");
        PlayerEntity player = player("Martinez mercato", 34, true);
        roster(current, player, 11);
        PlayerOwnerHistoryEntity.remember(player, former);

        int before = participantService.remainingCreditsById(current.id, current.totalCredits);
        SvincoloRequest request = new SvincoloRequest();
        request.playerId = player.id;
        ReleaseResultDto result = rosterService.svincola(current.id, request);

        assertEquals(before + 34, result.remainingCredits);
        assertEquals(34D, result.refundedCredits);
        assertEquals(35D, restrictionService.minimumBid(player, current));
        assertEquals(35D, restrictionService.minimumBid(player, former));
        assertEquals(1, MercatoSvincolo.getCount(current, Role.ATTACCANTE, "test-market-1"));
        MarketMovementEntity movement = MarketMovementEntity.find("player", player).firstResult();
        assertEquals(MarketMovementEntity.Type.RELEASE, movement.type);
        assertEquals(current.name, movement.sourceParticipantSnapshot);
        assertEquals(34D, movement.refundedAmount);

        rosterMovementService.revert(movement.id);
        RosterEntity restored = RosterEntity.find("player", player).firstResult();
        assertEquals(current.id, restored.participant.id);
        assertEquals(11D, restored.amount);
        assertEquals(before, participantService.remainingCreditsById(current.id, current.totalCredits));
        assertEquals(0, MercatoSvincolo.getCount(current, Role.ATTACCANTE, "test-market-1"));
        assertEquals(1D, restrictionService.minimumBid(player, current));
        assertTrue(rosterMovementService.list(player.name, null, null, false).isEmpty());
        assertEquals(1, rosterMovementService.list(player.name, null, null, true).size());
        assertNotNull(rosterMovementService.list(player.name, null, null, true).get(0).revertedAt);
    }

    @Test
    @TestTransaction
    void manualTransferIsAuditedButNewPurchaseIsNot() {
        configureMarket(1, true);
        ParticipantEntity source = participant("Audit origine");
        ParticipantEntity destination = participant("Audit destinazione");
        PlayerEntity transferred = player("Audit trasferito", 12, true);
        roster(source, transferred, 6);

        auctionService.adminAssign(transferred.id, destination.id, 6D);

        MarketMovementEntity movement = MarketMovementEntity.find("player", transferred).firstResult();
        assertEquals(MarketMovementEntity.Type.EXCHANGE, movement.type);
        assertEquals(source.name, movement.sourceParticipantSnapshot);
        assertEquals(destination.name, movement.destinationParticipantSnapshot);

        rosterMovementService.revert(movement.id);
        RosterEntity restored = RosterEntity.find("player", transferred).firstResult();
        assertEquals(source.id, restored.participant.id);
        assertEquals(6D, restored.amount);

        PlayerEntity purchased = player("Audit acquisto", 5, true);
        auctionService.adminAssign(purchased.id, destination.id, 1D);
        assertEquals(0, MarketMovementEntity.count("player", purchased));
    }

    @Test
    @TestTransaction
    void departedPlayerDoesNotConsumeReleaseLimit() {
        configureMarket(2, true);
        ParticipantEntity participant = participant("Mercato partito");
        PlayerEntity player = player("Partito mercato", 7, false);
        roster(participant, player, 3);

        SvincoloRequest request = new SvincoloRequest();
        request.playerId = player.id;
        ReleaseResultDto result = rosterService.svincola(participant.id, request);

        assertTrue(result.departed);
        assertEquals(0, MercatoSvincolo.getCount(participant, Role.ATTACCANTE, "test-market-2"));
    }

    @Test
    @TestTransaction
    void marketImportUpdatesQuotesAndPreservesPlayersAbsentFromList() throws Exception {
        configureMarket(1, false);
        ParticipantEntity participant = participant("Mercato import");
        PlayerEntity updated = player("Aggiornato mercato", 10, true);
        roster(participant, updated, 4);
        PlayerEntity departed = player("Assente mercato", 6, true);
        roster(participant, departed, 2);

        byte[] excel = workbook();
        MarketPlayerImportResult preview = dbService.previewMarketPlayersFromExcel(new ByteArrayInputStream(excel));
        assertTrue(preview.preview);
        assertEquals(1, preview.departedInRosters.stream().filter(p -> p.name.equals(departed.name)).count());

        dbService.updateMarketPlayersFromExcel(new ByteArrayInputStream(excel));

        assertEquals(34D, updated.valore);
        assertTrue(departed.active);
        assertEquals(1, RosterEntity.count("player", departed));
        assertTrue(rosterService.getRosterByParticipant(participant.id).stream()
                .filter(row -> row.playerId.equals(departed.id))
                .findFirst()
                .orElseThrow()
                .active);
        assertTrue(((MercatoConfigEntity) MercatoConfigEntity.findAll().firstResult()).quotazioniAggiornate);
    }

    @Test
    @TestTransaction
    void releaseIsBlockedUntilQuotesAreConfirmed() {
        configureMarket(1, false);
        ParticipantEntity participant = participant("Mercato bloccato");
        PlayerEntity player = player("Bloccato mercato", 12, true);
        roster(participant, player, 5);
        SvincoloRequest request = new SvincoloRequest();
        request.playerId = player.id;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> rosterService.svincola(participant.id, request));
        assertTrue(error.getMessage().contains("quotazioni"));
        assertEquals(1, RosterEntity.count("player", player));
    }

    @Test
    @TestTransaction
    void repairGoalkeeperPackageStartsFromCurrentValueSum() {
        configureMarket(1, true);
        goalkeeper("Portiere mercato 1", 15);
        goalkeeper("Portiere mercato 2", 4);
        goalkeeper("Portiere mercato 3", 1);

        RoundState round = auctionService.start(
                "Portiere mercato 1", "Napoli mercato", "PORTIERE", 30, "NONE", 15, null);

        assertEquals(20D, round.minimumBid);
        auctionService.reset();
    }

    @Test
    @TestTransaction
    void adminSwapIsBalancedPreservesCreditsAndCanBeRevertedAsOneOperation() {
        ParticipantEntity source = participant("Scambio origine");
        ParticipantEntity destination = participant("Scambio destinazione");
        PlayerEntity sourcePlayer = player("Scambio uscita", 20, true);
        PlayerEntity destinationPlayer = player("Scambio ingresso", 8, true);
        roster(source, sourcePlayer, 17);
        roster(destination, destinationPlayer, 4);
        int sourceRemaining = participantService.remainingCreditsById(source.id, source.totalCredits);
        int destinationRemaining = participantService.remainingCreditsById(destination.id, destination.totalCredits);

        RosterSwapRequest request = swapRequest(source, destination,
                List.of(sourcePlayer.id), List.of(destinationPlayer.id));
        rosterService.swap(request);

        assertEquals(destination.id, ((RosterEntity) RosterEntity.find("player", sourcePlayer).firstResult()).participant.id);
        assertEquals(source.id, ((RosterEntity) RosterEntity.find("player", destinationPlayer).firstResult()).participant.id);
        assertEquals(sourceRemaining, participantService.remainingCreditsById(source.id, source.totalCredits));
        assertEquals(destinationRemaining, participantService.remainingCreditsById(destination.id, destination.totalCredits));
        List<MarketMovementEntity> movements = MarketMovementEntity.list("player in ?1", List.of(sourcePlayer, destinationPlayer));
        assertEquals(2, movements.size());
        assertEquals(movements.get(0).operationCode, movements.get(1).operationCode);

        rosterMovementService.revert(movements.get(0).id);
        assertEquals(source.id, ((RosterEntity) RosterEntity.find("player", sourcePlayer).firstResult()).participant.id);
        assertEquals(destination.id, ((RosterEntity) RosterEntity.find("player", destinationPlayer).firstResult()).participant.id);
        assertTrue(movements.stream().allMatch(row -> row.revertedAt != null));
    }

    @Test
    @TestTransaction
    void adminSwapRejectsDifferentPlayerCountsWithoutPartialChanges() {
        ParticipantEntity source = participant("Scambio sbilanciato origine");
        ParticipantEntity destination = participant("Scambio sbilanciato destinazione");
        PlayerEntity first = player("Scambio sbilanciato uno", 10, true);
        PlayerEntity second = player("Scambio sbilanciato due", 9, true);
        PlayerEntity incoming = player("Scambio sbilanciato ingresso", 8, true);
        roster(source, first, 5);
        roster(source, second, 4);
        roster(destination, incoming, 3);

        assertThrows(BadRequestException.class, () -> rosterService.swap(swapRequest(
                source, destination, List.of(first.id, second.id), List.of(incoming.id))));
    }

    @Test
    @TestTransaction
    void releasingOneGoalkeeperDetachesTheWholePackage() {
        configureMarket(1, true);
        ParticipantEntity participant = participant("Svincolo porta completo");
        PlayerEntity first = goalkeeper("Svincolo portiere 1", 12);
        PlayerEntity second = goalkeeper("Svincolo portiere 2", 4);
        PlayerEntity third = goalkeeper("Svincolo portiere 3", 1);
        roster(participant, first, 8);
        roster(participant, second, 1);
        roster(participant, third, 1);
        int before = participantService.remainingCreditsById(participant.id, participant.totalCredits);

        SvincoloRequest request = new SvincoloRequest();
        request.playerId = first.id;
        ReleaseResultDto result = rosterService.svincola(participant.id, request);

        assertTrue(result.goalkeeperPackage);
        assertEquals(17D, result.refundedCredits);
        assertEquals(before + 17, result.remainingCredits);
        assertEquals(0, RosterEntity.count("participant", participant));
        assertFalse(first.assigned);
        assertFalse(second.assigned);
        assertFalse(third.assigned);
    }

    @Test
    @TestTransaction
    void adminSwapRejectsIndividualGoalkeepers() {
        ParticipantEntity source = participant("Scambio porta origine");
        ParticipantEntity destination = participant("Scambio porta destinazione");
        PlayerEntity sourceFirst = goalkeeper("Scambio porta origine 1", 10);
        PlayerEntity sourceSecond = goalkeeper("Scambio porta origine 2", 3);
        PlayerEntity sourceThird = goalkeeper("Scambio porta origine 3", 1);
        PlayerEntity destinationFirst = goalkeeper("Scambio porta destinazione 1", 9);
        PlayerEntity destinationSecond = goalkeeper("Scambio porta destinazione 2", 2);
        PlayerEntity destinationThird = goalkeeper("Scambio porta destinazione 3", 1);
        roster(source, sourceFirst, 8);
        roster(source, sourceSecond, 1);
        roster(source, sourceThird, 1);
        roster(destination, destinationFirst, 7);
        roster(destination, destinationSecond, 1);
        roster(destination, destinationThird, 1);

        BadRequestException error = assertThrows(BadRequestException.class, () -> rosterService.swap(swapRequest(
                source, destination, List.of(sourceFirst.id), List.of(destinationFirst.id))));
        assertTrue(error.getMessage().contains("pacchetto completo"));
    }

    private RosterSwapRequest swapRequest(ParticipantEntity source, ParticipantEntity destination,
                                          List<Long> sourceIds, List<Long> destinationIds) {
        RosterSwapRequest request = new RosterSwapRequest();
        request.sourceParticipantId = source.id;
        request.destinationParticipantId = destination.id;
        request.sourcePlayerIds = sourceIds;
        request.destinationPlayerIds = destinationIds;
        return request;
    }

    private void configureMarket(int number, boolean quotesUpdated) {
        MercatoConfigEntity.deleteAll();
        MercatoConfigEntity config = new MercatoConfigEntity();
        config.numeroMercato = number;
        config.sessionCode = "test-market-" + number;
        config.attiva = true;
        config.fineSessione = LocalDateTime.now().plusDays(1);
        config.quotazioniAggiornate = quotesUpdated;
        config.partitiImportati = true;
        config.maxPortieri = number == 2 ? 0 : 1;
        config.maxDifensori = number == 2 ? 1 : 2;
        config.maxCentrocampisti = number == 2 ? 1 : 2;
        config.maxAttaccanti = number == 2 ? 1 : 2;
        config.persist();
    }

    private ParticipantEntity participant(String name) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.name = name;
        participant.totalCredits = 500;
        participant.persist();
        return participant;
    }

    private PlayerEntity player(String name, double value, boolean active) {
        PlayerEntity player = new PlayerEntity();
        player.name = name;
        player.team = "Inter";
        player.role = Role.ATTACCANTE;
        player.valore = value;
        player.active = active;
        player.persist();
        return player;
    }

    private PlayerEntity goalkeeper(String name, double value) {
        PlayerEntity player = new PlayerEntity();
        player.name = name;
        player.team = "Napoli mercato";
        player.role = Role.PORTIERE;
        player.valore = value;
        player.active = true;
        player.persist();
        return player;
    }

    private void roster(ParticipantEntity participant, PlayerEntity player, double amount) {
        RosterEntity roster = new RosterEntity();
        roster.participant = participant;
        roster.player = player;
        roster.amount = amount;
        roster.persist();
        player.assigned = true;
    }

    private byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Tutti");
            String[][] values = {
                    {"Nome", "Squadra", "Ruolo", "Quotazione"},
                    {"Aggiornato mercato", "Inter", "A", "34"},
                    {"Nuovo mercato", "Roma", "D", "5"}
            };
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                for (int column = 0; column < values[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(values[rowIndex][column]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
