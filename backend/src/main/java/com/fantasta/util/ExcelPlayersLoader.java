package com.fantasta.util;

import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.service.DbService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@ApplicationScoped
@Startup
public class ExcelPlayersLoader {

    @Inject
    DbService dbService;

    @PostConstruct
    @Transactional
    public void init() {
        try {
            if (PlayerEntity.count() == 0) {
                int n = loadFromExcel(); // primo seed
                Log.infof("ExcelPlayersLoader: imported %d players from Excel (seed)", n);
            } else {
                var rep = dbService.syncPlayersFromExcel(); // sincronizzazione completa
                Log.infof("ExcelPlayersLoader: sync done %s", rep);
            }
        } catch (Exception e) {
            Log.error("ExcelPlayersLoader: failed at startup", e);
        }
    }

    /**
     * PRIMO IMPORT (seed): inserisce solo chi non esiste per *nome* (case-insensitive).
     * Per tutti i successivi allineamenti viene usata syncPlayersFromExcel() in DbService.
     */
    @Transactional
    public int loadFromExcel() throws Exception {
        String external = System.getProperty("players.file", System.getenv("PLAYERS_FILE"));
        InputStream is;
        if (external != null && !external.isBlank()) {
            Log.infof("Loading players from external file: %s", external);
            is = Files.newInputStream(Path.of(external));
        } else {
            Log.info("Loading players from classpath: players.xlsx");
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("players.xlsx");
        }
        if (is == null) throw new IllegalStateException("players.xlsx not found");

        int inserted = 0;
        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // header
                String roleStr = getCellStr(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                String name    = getCellStr(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                String team    = getCellStr(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                Double valore  = parseValore(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)); // col E

                if (name == null || name.isBlank()) continue;
                Role role = Role.fromString(roleStr);
                if (role == null) continue;

                String nameKey = norm(name);

                PlayerEntity existing = PlayerEntity.find("lower(name)=?1", nameKey).firstResult();
                if (existing == null) {
                    PlayerEntity p = new PlayerEntity();
                    p.name   = name.trim();
                    p.team   = team == null ? "" : team.trim();
                    p.role   = role;
                    p.valore = Objects.requireNonNullElse(valore, 0d);
                    p.assigned = false;
                    p.active = true;
                    p.deletedAt = null;
                    p.persist();
                    inserted++;
                }
            }
        }
        return inserted;
    }

    // ==== helper identici a DbService ====
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }

    private static String getCellStr(Cell c){
        if (c == null) return null;
        return switch (c.getCellType()){
            case STRING -> c.getStringCellValue();
            case NUMERIC -> String.valueOf((long)c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> {
                String v = c.toString();
                yield (v != null && !v.isBlank()) ? v : null;
            }
        };
    }

    private static Double parseValore(Cell c) {
        if (c == null) return null;
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> c.getNumericCellValue();
                case STRING -> {
                    String s = c.getStringCellValue();
                    if (s == null) yield null;
                    s = s.trim();
                    if (s.isEmpty()) yield null;
                    s = s.replace(',', '.');
                    yield Double.parseDouble(s);
                }
                case FORMULA -> {
                    try { yield c.getNumericCellValue(); }
                    catch (IllegalStateException ex) { yield null; }
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
