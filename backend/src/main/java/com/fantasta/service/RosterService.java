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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    @Transactional
    public RosterImportResult importFromExcel(InputStream in) {
        List<String> errors = new ArrayList<>();
        int inserted = 0;

        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Giocatori assegnati in questo import
            Set<Long> assignedNow = new HashSet<>();

            // Per tenere traccia ultimo participant
            String currentParticipant = null;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String participantName = row.getCell(0).getStringCellValue().trim();
                String playerName = row.getCell(1).getStringCellValue().trim();
                Double amount = row.getCell(2).getNumericCellValue();

                // 🔹 Participant
                ParticipantEntity participant = ParticipantEntity.find("name", participantName).firstResult();
                if (participant == null) {
                    errors.add("Participant non trovato: " + participantName);
                    continue;
                }

                // 🔹 Player (match diretto, nomi già allineati)
                PlayerEntity player = PlayerEntity.find("LOWER(name) = ?1", playerName.toLowerCase()).firstResult();
                if (player == null) {
                    errors.add("Giocatore non trovato: " + playerName);
                    continue;
                }

                // 🔹 Cambio participant → reset roster
                if (!participantName.equals(currentParticipant)) {
                    copyRosterToHistory(participant);
                    RosterEntity.delete("participant", participant);
                    currentParticipant = participantName;
                }

                // 🔹 Inserisci riga di roster
                RosterEntity roster = new RosterEntity();
                roster.participant = participant;
                roster.player = player;
                roster.amount = amount;
                roster.persist();

                // 🔹 Marca come assegnato
                player.assigned = true;
                player.persist();

                assignedNow.add(player.id);

                inserted++;
            }

            if (assignedNow.isEmpty()) {
                // Caso limite: nessun giocatore importato → svincola tutti
                PlayerEntity.update("assigned = false WHERE assigned = true");
            } else {
                // Svincola chi era assegnato ma non è più nell’import
                PlayerEntity.update("assigned = false WHERE assigned = true AND id NOT IN ?1", assignedNow);
            }



        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'import da Excel: " + e.getMessage(), e);
        }

        return new RosterImportResult(inserted, errors);
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
                    return new ParticipantRosterDto(
                            sample.participant.id,
                            sample.participant.name,
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
