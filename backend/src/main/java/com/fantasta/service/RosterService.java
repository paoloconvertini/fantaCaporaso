package com.fantasta.service;

import com.fantasta.dto.RosterImportResult;
import com.fantasta.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RosterService {


    @Inject
    ParticipantService participantService;

    /** Massimali per ruolo, letti da properties/env (default: 3-8-8-6) */
    public int max(Role role) {
        String v;
        switch (role) {
            case PORTIERE ->
                    v = System.getProperty("app.roster.portieri",
                            System.getenv().getOrDefault("APP_ROSTER_PORTIERI", "3"));
            case DIFENSORE ->
                    v = System.getProperty("app.roster.difensori",
                            System.getenv().getOrDefault("APP_ROSTER_DIFENSORI", "8"));
            case CENTROCAMPISTA ->
                    v = System.getProperty("app.roster.centrocampisti",
                            System.getenv().getOrDefault("APP_ROSTER_CENTROCAMPISTI", "8"));
            default ->
                    v = System.getProperty("app.roster.attaccanti",
                            System.getenv().getOrDefault("APP_ROSTER_ATTACCANTI", "6"));
        }
        try { return Integer.parseInt(v); } catch (Exception e) { return 0; }
    }

    /** Ritorna i conteggi attuali per ruolo (deriva da RosterEntity) */
    public Map<Role, Integer> roleCounts(Long participantId) {
        return participantService.roleCounts(participantId);
    }

    @Transactional
    public RosterImportResult importFromExcel(InputStream in) {
        List<String> errors = new ArrayList<>();
        int inserted = 0;

        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);

            String currentParticipant = null;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String participantName = row.getCell(0).getStringCellValue().trim();
                String playerName = row.getCell(1).getStringCellValue().trim();
                Double amount = row.getCell(2).getNumericCellValue();

                ParticipantEntity participant = ParticipantEntity.find("name", participantName).firstResult();
                if (participant == null) {
                    errors.add("Participant non trovato: " + participantName);
                    continue;
                }

                String[] parts = playerName.split(" ");
                PlayerEntity player = null;
                if (parts.length >= 2) {
                    String lastName = parts[0].toLowerCase();
                    String firstInitial = parts[1].substring(0, 1).toLowerCase();

                    player = (PlayerEntity) PlayerEntity.find("LOWER(name) LIKE ?1", lastName + " %")
                            .stream()
                            .filter(p -> {
                                String[] dbParts = ((PlayerEntity)p).name.split(" ");
                                return dbParts.length >= 2
                                        && dbParts[0].equalsIgnoreCase(lastName)
                                        && dbParts[1].substring(0, 1).equalsIgnoreCase(firstInitial);
                            })
                            .findFirst()
                            .orElse(null);
                }
                if (player == null) {
                    errors.add("Giocatore non trovato: " + playerName);
                    continue;
                }

                if (!participantName.equals(currentParticipant)) {
                    copyRosterToHistory(participant);
                    RosterEntity.delete("participant", participant);
                    currentParticipant = participantName;
                }

                var roster = new RosterEntity();
                roster.participant = participant;
                roster.player = player;
                roster.amount = amount;
                roster.persist();

                inserted++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'import da Excel: " + e.getMessage(), e);
        }

        return new RosterImportResult(inserted, errors);
    }



    private void copyRosterToHistory(ParticipantEntity participant) {
        Long sessionId = System.currentTimeMillis(); // per ora timestamp, poi si può usare session ufficiale
        List<RosterEntity> currentRoster = RosterEntity.list("participant", participant);
        for (RosterEntity r : currentRoster) {
            RosterHistoryEntity h = new RosterHistoryEntity();
            h.sessionId = sessionId;
            h.participant = r.participant;
            h.player = r.player;
            h.amount = r.amount;
            h.persist();
        }
    }
}
