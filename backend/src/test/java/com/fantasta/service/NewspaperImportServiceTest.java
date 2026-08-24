package com.fantasta.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewspaperImportServiceTest {
    private final NewspaperImportService service = new NewspaperImportService();

    @Test
    void recognizesAnUncalculatedFantaMasterExport() throws Exception {
        var preview = service.preview(new ByteArrayInputStream(workbook("-")));
        assertFalse(preview.calculated);
        assertEquals(2, preview.matches.size());
        assertEquals(4, preview.teamsFound);
        assertEquals("Atletico", preview.matches.get(0).home);
        assertEquals(2, preview.standings.size());
        assertEquals(1, preview.teamSheets.size());
        assertEquals(12, preview.teamSheets.get(0).players.size());
        assertTrue(preview.teamSheets.get(0).players.get(10).starter);
        assertFalse(preview.teamSheets.get(0).players.get(11).starter);
        assertTrue(preview.teamSheets.get(0).players.get(11).counted);
        assertTrue(preview.fantasfiga.isEmpty());
    }

    @Test
    void recognizesCalculatedResultsWithoutConvertingThem() throws Exception {
        var preview = service.preview(new ByteArrayInputStream(workbook("66,5 - 73")));
        assertTrue(preview.calculated);
        assertEquals("66,5 - 73", preview.matches.get(0).result);
        assertEquals(4, preview.fantasfiga.size());
        assertEquals("Havana", preview.fantasfiga.get(0).team);
        assertEquals(3, preview.fantasfiga.get(0).actualPoints);
    }

    private byte[] workbook(String result) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("Punti 0");
            var sheet = workbook.createSheet("FantaCAPORASO 2026-27");
            var header = sheet.createRow(20);
            header.createCell(0).setCellValue("Casa");
            header.createCell(1).setCellValue("Trasferta");
            header.createCell(2).setCellValue("Risultato");
            addMatch(sheet, 21, "Atletico", "Havana", result);
            addMatch(sheet, 22, "Young Boys", "Ruverpool", result);
            sheet.createRow(24).createCell(0).setCellValue("Classifica: FantaCAPORASO 2026-27 - Campionato");
            var first = sheet.createRow(25);
            first.createCell(0).setCellValue("1 Atletico");
            first.createCell(1).setCellValue("V 1 P 0 S 0 GF 2 GS 1");
            first.createCell(2).setCellValue("3 (73)");
            var second = sheet.createRow(26);
            second.createCell(0).setCellValue("2 Havana");
            second.createCell(1).setCellValue("V 0 P 0 S 1 GF 1 GS 2");
            second.createCell(2).setCellValue("0 (66,5)");
            var formation = sheet.createRow(30);
            formation.createCell(4).setCellValue("Formazione: Atletico");
            for (int index = 0; index < 12; index++) {
                var player = sheet.createRow(31 + index);
                player.createCell(4).setCellValue("(C) Giocatore " + (index + 1));
                player.createCell(5).setCellValue("6");
                player.createCell(6).setCellValue(index == 11 ? "10" : "6");
                player.createCell(7).setCellValue(index == 11 ? "Si" : index < 11 ? "Si" : "No");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void addMatch(org.apache.poi.ss.usermodel.Sheet sheet, int row, String home, String away, String result) {
        var match = sheet.createRow(row);
        match.createCell(0).setCellValue(home);
        match.createCell(1).setCellValue(away);
        match.createCell(2).setCellValue(result);
    }
}
