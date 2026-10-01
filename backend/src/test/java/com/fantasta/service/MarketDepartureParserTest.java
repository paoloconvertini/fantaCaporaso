package com.fantasta.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class MarketDepartureParserTest {
    @Test
    void matchesConfirmedTeamAliasesWithoutMatchingDifferentOwners() {
        assertTrue(MarketDepartureImportService.matchesOwner(
                "Christian S. & Antonio C. - (Em Fallét)", "Em Fallet"));
        assertTrue(MarketDepartureImportService.matchesOwner(
                "Luigi Spirito - (Johnson Oil)", "johnsons oil"));
        assertTrue(MarketDepartureImportService.matchesOwner(
                "Pasquale Sozio - (3/4 e 1 Gazzosa)", "34 e 1 Gazzosa"));
        assertTrue(MarketDepartureImportService.matchesOwner("Em Fallet", "Em Fallét"));
        assertFalse(MarketDepartureImportService.matchesOwner("Johnson Oil", "Johnson FC"));
        assertFalse(MarketDepartureImportService.matchesOwner("Em Fallét", "34 e 1 Gazzosa"));
    }

    @Test
    void rejectsDuplicateNamesAfterPrefixRemoval() throws Exception {
        byte[] file = file("10", true);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> MarketDepartureImportService.parse(new ByteArrayInputStream(file)))
                .getMessage().contains("duplicato"));
    }

    @Test
    void rejectsMissingNegativeAndNonFiniteQuotes() throws Exception {
        for (String value : new String[]{"", "-1", "NaN", "Infinity", "errato"}) {
            byte[] file = file(value, false);
            assertThrows(IllegalArgumentException.class,
                    () -> MarketDepartureImportService.parse(new ByteArrayInputStream(file)));
        }
    }

    private byte[] file(String value, boolean duplicate) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Giocatori Partiti");
            for (int i = 0; i < (duplicate ? 2 : 1); i++) {
                var row = sheet.createRow(i + 3);
                String[] cells = {"", "Proprietario", "A", i == 0 ? "ZZZ - Partito - Nome" : " nome ", "Inter", value, "Partito"};
                for (int c = 0; c < cells.length; c++) row.createCell(c).setCellValue(cells[c]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
