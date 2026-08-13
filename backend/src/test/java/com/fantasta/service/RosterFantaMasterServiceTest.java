package com.fantasta.service;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RosterFantaMasterServiceTest {

    @Inject
    RosterService service;

    @Test
    @TestTransaction
    void emptySheetsCreateMissingParticipantsOnlyAfterConfirmation() throws Exception {
        byte[] file = workbook("Squadra nuova", null, null, null, 0);

        var preview = service.importFromExcel(new ByteArrayInputStream(file), false);
        assertTrue(preview.preview);
        assertEquals(1, preview.teamsFound);
        assertEquals(1, preview.teamsCreated);
        assertEquals(0, ParticipantEntity.count("name", "Squadra nuova"));

        var imported = service.importFromExcel(new ByteArrayInputStream(file), true);
        assertFalse(imported.preview);
        assertEquals(1, imported.teamsCreated);
        ParticipantEntity participant = ParticipantEntity.find("name", "Squadra nuova").firstResult();
        assertNotNull(participant);
        assertEquals(500, participant.totalCredits);
    }

    @Test
    @TestTransaction
    void exportsAndReimportsTheFantaMasterRosterFormat() throws Exception {
        ParticipantEntity participant = new ParticipantEntity();
        participant.name = "Squadra round trip";
        participant.totalCredits = 500;
        participant.persist();
        PlayerEntity player = new PlayerEntity();
        player.name = "Giocatore round trip";
        player.team = "Roma";
        player.role = Role.DIFENSORE;
        player.valore = 12D;
        player.active = true;
        player.assigned = true;
        player.persist();
        RosterEntity roster = new RosterEntity();
        roster.participant = participant;
        roster.player = player;
        roster.amount = 17D;
        roster.persist();

        byte[] exported = service.exportFantaMaster();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheet("Squadra round trip");
            assertNotNull(sheet);
            assertEquals("Nome", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Giocatore round trip", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(17D, sheet.getRow(2).getCell(3).getNumericCellValue());
        }

        RosterEntity.delete("participant", participant);
        player.assigned = false;
        var imported = service.importFromExcel(new ByteArrayInputStream(exported), true);
        assertTrue(imported.inserted >= 1);
        RosterEntity restored = RosterEntity.find("participant = ?1 and player = ?2", participant, player).firstResult();
        assertNotNull(restored);
        assertEquals(17D, restored.amount);
    }

    private byte[] workbook(String participant, String player, String team, String role, double cost) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("team-id");
            sheet.createRow(0).createCell(0).setCellValue(participant);
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("Nome");
            header.createCell(1).setCellValue("Squadra");
            header.createCell(2).setCellValue("Ruolo");
            header.createCell(3).setCellValue("Costo");
            if (player != null) {
                var row = sheet.createRow(2);
                row.createCell(0).setCellValue(player);
                row.createCell(1).setCellValue(team);
                row.createCell(2).setCellValue(role);
                row.createCell(3).setCellValue(cost);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
