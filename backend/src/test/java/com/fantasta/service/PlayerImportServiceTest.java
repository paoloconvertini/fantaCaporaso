package com.fantasta.service;

import com.fantasta.dto.PlayerImportResult;
import com.fantasta.model.PlayerEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PlayerImportServiceTest {

    @Inject
    DbService dbService;

    @Test
    @TestTransaction
    void previewsAndReplacesFantaMasterLayout() throws Exception {
        byte[] excel = workbook(new String[][]{
                {"Quotazioni Svincolati", "", "", ""},
                {"Nome", "Squadra", "Ruolo", "Quotazione"},
                {"Svilar", "Roma", "P", "38"},
                {"Martinez L", "Inter", "A", "88"},
                {"Ultimo aggiornamento", "", "", ""}
        });

        long before = PlayerEntity.count();
        PlayerImportResult preview = dbService.previewPlayersFromExcel(new ByteArrayInputStream(excel));
        assertTrue(preview.preview);
        assertEquals(2, preview.total);
        assertEquals(before, PlayerEntity.count());

        PlayerImportResult imported = dbService.replacePlayersFromExcel(new ByteArrayInputStream(excel));
        assertFalse(imported.preview);
        assertEquals(2, imported.inserted);
        assertEquals(2, PlayerEntity.count());
        PlayerEntity martinez = PlayerEntity.find("name", "Martinez L").firstResult();
        assertEquals("Inter", martinez.team);
        assertEquals(88d, martinez.valore);
    }

    @Test
    void rejectsDuplicateNamesBeforeImport() throws Exception {
        byte[] excel = workbook(new String[][]{
                {"Nome", "Squadra", "Ruolo", "Quotazione"},
                {"Svilar", "Roma", "P", "38"},
                {" svilar ", "Roma", "P", "37"}
        });
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dbService.previewPlayersFromExcel(new ByteArrayInputStream(excel)));
        assertTrue(error.getMessage().contains("duplicato"));
    }

    private byte[] workbook(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Tutti");
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
