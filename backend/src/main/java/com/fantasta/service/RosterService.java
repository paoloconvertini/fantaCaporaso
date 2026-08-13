package com.fantasta.service;

import com.fantasta.dto.ParticipantRosterDto;
import com.fantasta.dto.RosterDto;
import com.fantasta.dto.RosterImportResult;
import com.fantasta.dto.SvincoloRequest;
import com.fantasta.model.*;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class RosterService {

    @Inject
    SecurityIdentity identity;

    @Inject
    ParticipantService participantService;

    @Inject
    MercatoService mercatoService;

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

    /** Massimo spendibile conservando un credito per ogni posto che restera' vuoto. */
    public int maxBid(Long participantId, int totalCredits, int purchaseSize) {
        int remainingCredits = participantService.remainingCreditsById(participantId, totalCredits);
        Map<Role, Integer> counts = participantService.roleCounts(participantId);
        int remainingSlots = 0;
        for (Role role : Role.values()) {
            remainingSlots += Math.max(0, max(role) - counts.getOrDefault(role, 0));
        }
        int reservedCredits = Math.max(0, remainingSlots - Math.max(1, purchaseSize));
        return Math.max(0, remainingCredits - reservedCredits);
    }

    /** Posti rosa ancora vuoti, sommati su tutti i partecipanti e divisi per ruolo. */
    public Map<String, Integer> openSlotsByRole() {
        int participants = Math.toIntExact(ParticipantEntity.count());
        Map<String, Integer> result = new LinkedHashMap<>();
        int total = 0;
        for (Role role : Role.values()) {
            int assigned = Math.toIntExact(RosterEntity.count("player.role", role));
            int open = Math.max(0, participants * max(role) - assigned);
            result.put(role.name(), open);
            total += open;
        }
        result.put("TUTTI", total);
        return result;
    }

    @Transactional
    public RosterImportResult importFromExcel(InputStream in, boolean confirm) {
        List<String> errors = new ArrayList<>();
        int inserted = 0;
        int teamsCreated = 0;
        int teamsFound = 0;
        int defaultCredits = Integer.parseInt(System.getProperty("app.credits.total",
                System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));

        try (Workbook workbook = WorkbookFactory.create(in)) {
            Set<Long> assignedNow = new HashSet<>();
            Set<Long> resetParticipants = new HashSet<>();

            for (Sheet sheet : workbook) {
                String participantName = cellText(sheet.getRow(0), 0);
                if (participantName.isBlank()) {
                    errors.add("Foglio senza nome squadra: " + sheet.getSheetName());
                    continue;
                }
                teamsFound++;
                ParticipantEntity participant = ParticipantEntity.find("lower(name) = ?1", participantName.toLowerCase(Locale.ROOT)).firstResult();
                if (participant == null) {
                    teamsCreated++;
                    if (confirm) {
                        participant = new ParticipantEntity();
                        participant.name = participantName;
                        participant.totalCredits = defaultCredits;
                        participant.persist();
                    }
                }
                if (confirm && resetParticipants.add(participant.id)) {
                    copyRosterToHistory(participant);
                    RosterEntity.delete("participant", participant);
                }
                for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    String playerName = cellText(row, 0);
                    if (playerName.isBlank() || playerName.startsWith("Ultimo aggiornamento:") || playerName.equalsIgnoreCase("Scarica FantaMaster")) continue;
                    String team = cellText(row, 1);
                    double amount = numericCell(row, 3);
                    PlayerEntity player = PlayerEntity.find("lower(name) = ?1 and lower(team) = ?2",
                            playerName.toLowerCase(Locale.ROOT), team.toLowerCase(Locale.ROOT)).firstResult();
                    if (player == null) {
                        errors.add("Giocatore non trovato: " + playerName + " (" + team + ")");
                        continue;
                    }
                    inserted++;
                    if (!confirm) continue;
                    RosterEntity roster = new RosterEntity();
                    roster.participant = participant;
                    roster.player = player;
                    roster.amount = amount;
                    roster.persist();
                    player.assigned = true;
                    assignedNow.add(player.id);
                }
            }

            if (confirm && !assignedNow.isEmpty()) {
                PlayerEntity.update("assigned = false WHERE assigned = true AND id NOT IN ?1", assignedNow);
            } else if (confirm) {
                PlayerEntity.update("assigned = false WHERE assigned = true");
            }
        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'import da Excel: " + e.getMessage(), e);
        }
        return new RosterImportResult(inserted, errors, teamsFound, teamsCreated, !confirm);
    }

    @Transactional
    public byte[] exportFantaMaster() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<ParticipantEntity> participants = ParticipantEntity.list("order by name");
            for (ParticipantEntity participant : participants) {
                Sheet sheet = workbook.createSheet(safeSheetName(participant.name, workbook));
                Row title = sheet.createRow(0);
                title.createCell(0).setCellValue(participant.name);
                Row header = sheet.createRow(1);
                header.createCell(0).setCellValue("Nome");
                header.createCell(1).setCellValue("Squadra");
                header.createCell(2).setCellValue("Ruolo");
                header.createCell(3).setCellValue("Costo");
                List<RosterEntity> roster = RosterEntity.list("participant = ?1 order by player.role, player.name", participant);
                int rowIndex = 2;
                for (RosterEntity entry : roster) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(entry.player.name);
                    row.createCell(1).setCellValue(entry.player.team);
                    row.createCell(2).setCellValue(entry.player.role.name());
                    row.createCell(3).setCellValue(entry.amount == null ? 0 : entry.amount);
                }
                sheet.setColumnWidth(0, 28 * 256);
                sheet.setColumnWidth(1, 18 * 256);
                sheet.setColumnWidth(2, 18 * 256);
                sheet.setColumnWidth(3, 12 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile esportare le rose", e);
        }
    }

    private String safeSheetName(String name, Workbook workbook) {
        String base = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(name);
        base = base.substring(0, Math.min(31, base.length()));
        String candidate = base;
        int suffix = 2;
        while (workbook.getSheet(candidate) != null) {
            String end = " " + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 31 - end.length())) + end;
        }
        return candidate;
    }

    private String cellText(Row row, int index) {
        if (row == null || row.getCell(index) == null) return "";
        return new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(row.getCell(index)).trim();
    }

    private double numericCell(Row row, int index) {
        String text = cellText(row, index).replace(',', '.');
        try { return Double.parseDouble(text); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Costo non valido: " + text); }
    }



    private void copyRosterToHistory(ParticipantEntity participant) {
        long sessionId = System.currentTimeMillis(); // per ora timestamp, poi si può usare session ufficiale
        List<RosterEntity> currentRoster = RosterEntity.list("participant", participant);
        createRoster(sessionId, currentRoster);
    }

    static void createRoster(long sessionId, List<RosterEntity> currentRoster) {
        for (RosterEntity r : currentRoster) {
            RosterHistoryEntity h = new RosterHistoryEntity();
            h.sessionId = sessionId;
            h.participant = r.participant;
            h.player = r.player;
            h.amount = r.amount;
            h.persist();
        }
    }

    @Transactional
    public void svincola(Long participantId, SvincoloRequest req) {
        if (participantId == null || req == null || req.playerId == null) {
            throw new BadRequestException("Parametri mancanti per lo svincolo");
        }

        // 🔹 Mercato attivo?
        if (!mercatoService.isMercatoAttivo()) {
            throw new ForbiddenException("Mercato chiuso: svincolo non consentito");
        }

        RosterEntity roster = RosterEntity.find(
                "participant.id = ?1 and player.id = ?2",
                participantId,
                req.playerId
        ).firstResult();

        if (roster == null) {
            throw new NotFoundException("Giocatore non trovato nella rosa");
        }

        ParticipantEntity participant = roster.participant;
        Role role = roster.player.role;
        if (role == null) {
            throw new BadRequestException("Ruolo non valido per lo svincolo");
        }

        // 🔹 Verifica limite svincoli per ruolo
        int maxSvincoli = mercatoService.getMaxByRole(role.name());
        int fatti = MercatoSvincolo.getCount(participant, role);

        if (fatti >= maxSvincoli) {
            throw new ForbiddenException("Hai già raggiunto il limite massimo di svincoli per ruolo " + role.name());
        }

        // 🔹 Esegui svincolo
        double oldAmount = roster.amount != null ? roster.amount : 0;
        roster.delete();

        // 🔹 Registra svincolo
        MercatoSvincolo.increment(participant, role);

        // 🔹 Riaccredito crediti
        participant.totalCredits += (int) oldAmount;
        participant.persist();

        Log.infof(
                "Svincolato %s (%s) da %s: +%.1f crediti (svincoli %d/%d)",
                roster.player.name, role, participant.name, oldAmount, fatti + 1, maxSvincoli
        );
    }

    @Transactional
    public List<RosterDto> getRosters() {
        boolean isAdmin = identity.hasRole("admin");
        List<RosterEntity> entities;

        if (isAdmin) {
            entities = RosterEntity.listAll();
        } else {
            String username = identity.getPrincipal().getName();
            ParticipantEntity p = ParticipantEntity.find("name", username).firstResult();
            if (p == null) {
                throw new IllegalStateException("Partecipante non trovato per utente: " + username);
            }
            entities = RosterEntity.find("participant", p).list();
        }

        return entities.stream()
                .map(r -> new RosterDto(
                        r.participant.id,
                        r.participant.name,
                        r.player.id,
                        r.player.name,
                        r.player.team,
                        r.player.role.name(),
                        r.amount
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<RosterDto> getRosterByParticipant(Long participantId) {
        List<RosterEntity> rosterEntities = RosterEntity.find("participant.id", participantId).list();
        return toRosterDtoListWithResidui(rosterEntities);
    }

    @Transactional
    public List<ParticipantRosterDto> getAllRostersGrouped() {
        List<RosterEntity> entities = RosterEntity.listAll();

        Map<Long, List<RosterEntity>> grouped = entities.stream()
                .collect(Collectors.groupingBy(r -> r.participant.id));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<RosterEntity> rosterEntities = entry.getValue();
                    List<RosterDto> rosterDtos = toRosterDtoListWithResidui(rosterEntities);
                    RosterEntity sample = rosterEntities.get(0);
                    AppUserEntity account = AppUserEntity.find("participant", sample.participant).firstResult();
                    return new ParticipantRosterDto(
                            sample.participant.id,
                            sample.participant.name,
                            account == null ? null : account.username,
                            rosterDtos
                    );
                })
                .toList();
    }

    @Transactional
    public List<RosterDto> getAllRosters() {
        return RosterEntity.findAll().stream()
                .map(r -> toDto((RosterEntity) r))
                .collect(Collectors.toList());
    }

    private RosterDto toDto(RosterEntity r) {
        return new RosterDto(
                r.participant.id,
                r.participant.name,
                r.player.id,
                r.player.name,
                r.player.team,
                r.player.role.toString(),
                r.amount
        );
    }

    private List<RosterDto> toRosterDtoListWithResidui(List<RosterEntity> rosterEntities) {
        if (rosterEntities == null || rosterEntities.isEmpty()) {
            return List.of();
        }

        ParticipantEntity participant = rosterEntities.get(0).participant;
        double totaleDisponibile = participant != null ? participant.totalCredits : 500;
        double spesi = rosterEntities.stream()
                .mapToDouble(r -> r.amount != null ? r.amount : 0)
                .sum();
        double residui = totaleDisponibile - spesi;

        return rosterEntities.stream()
                .map(r -> new RosterDto(
                        r.participant.id,
                        r.participant.name,
                        r.player.id,
                        r.player.name,
                        r.player.team,
                        r.player.role != null ? r.player.role.name() : null,
                        r.amount,
                        residui // 👈 calcolato
                ))
                .collect(Collectors.toList());
    }



}
