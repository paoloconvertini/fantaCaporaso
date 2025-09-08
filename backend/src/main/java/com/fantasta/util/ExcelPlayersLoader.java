package com.fantasta.util;

import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
@Startup
public class ExcelPlayersLoader {

    @PostConstruct
    @Transactional
    public void init() {
        try {
            if (PlayerEntity.count() > 0) {
                Log.info("ExcelPlayersLoader: player table already populated, skipping");
                return;
            }
            int n = loadFromExcel();
            Log.infof("ExcelPlayersLoader: imported %d players from Excel", n);
        } catch (Exception e) {
            Log.error("ExcelPlayersLoader: failed at startup", e);
        }
    }

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

        int imported = 0;
        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // header
                String roleStr = get(row.getCell(1));
                String name    = get(row.getCell(2));
                String team    = get(row.getCell(3));
                Double valore    = row.getCell(4).getNumericCellValue();
                Role role = Role.fromString(roleStr);
                if (role == null || name == null || name.isBlank()) continue;

                boolean exists = PlayerEntity.find("lower(name)=?1 and lower(team)=?2",
                                name.trim().toLowerCase(), (team == null ? "" : team.trim()).toLowerCase())
                        .firstResult() != null;
                if (!exists) {
                    PlayerEntity p = new PlayerEntity();
                    p.name = name.trim();
                    p.team = team == null ? "" : team.trim();
                    p.role = role;
                    p.valore = valore;
                    p.persist();
                    imported++;
                }
            }
        }
        return imported;
    }

    private static String get(Cell c){
        if (c == null) return null;
        return switch (c.getCellType()){
            case STRING -> c.getStringCellValue();
            case NUMERIC -> String.valueOf((long)c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> c.toString();
        };
    }
}
