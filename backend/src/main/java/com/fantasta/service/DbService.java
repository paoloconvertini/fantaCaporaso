package com.fantasta.service;

import com.fantasta.model.*;
import com.fantasta.model.Role;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jboss.logging.Logger;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class DbService {

    private static final Logger LOG = Logger.getLogger(DbService.class);

    /**
     * Sincronizza il catalogo giocatori con l’Excel:
     * - upsert per nome (case-insensitive)
     * - aggiorna team/ruolo/valore, riattiva se era soft-deleted
     * - soft delete dei nomi che non sono più nel file
     * - se soft-deleto un player presente in roster: rimuovo le righe di roster (rimborso automatico via "spent")
     */
    @Transactional
    public Map<String, Integer> syncPlayersFromExcel() throws Exception {
        // 1) Apri file
        String external = System.getProperty("players.file", System.getenv("PLAYERS_FILE"));
        InputStream is;
        if (external != null && !external.isBlank()) {
            LOG.infof("Sync players from external file: %s", external);
            is = Files.newInputStream(Path.of(external));
        } else {
            LOG.info("Sync players from classpath: players.xlsx");
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("players.xlsx");
        }
        if (is == null) throw new IllegalStateException("players.xlsx not found");

        // 2) Leggi Excel → mappa per nome (normalizzato)
        Map<String, ExcelRow> excelByName = new HashMap<>();
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

                String key = norm(name);
                excelByName.put(key, new ExcelRow(name.trim(), team == null ? "" : team.trim(), role, valore == null ? 0d : valore));
            }
        }

        int inserted = 0, updated = 0, reactivated = 0, softDeleted = 0, unassigned = 0;

        // 3) Upsert per ogni riga Excel
        for (ExcelRow xr : excelByName.values()) {
            PlayerEntity existing = PlayerEntity.find("lower(name)=?1", norm(xr.name)).firstResult();
            if (existing == null) {
                PlayerEntity p = new PlayerEntity();
                p.name = xr.name;
                p.team = xr.team;
                p.role = xr.role;
                p.valore = xr.valore;
                p.assigned = false;
                p.active = true;
                p.deletedAt = null;
                p.persist();
                inserted++;
            } else {
                boolean changed = false;
                if (!Objects.equals(existing.team, xr.team)) { existing.team = xr.team; changed = true; }
                if (existing.role != xr.role) { existing.role = xr.role; changed = true; }
                if (!Objects.equals(existing.valore, xr.valore)) { existing.valore = xr.valore; changed = true; }
                if (!existing.active) { existing.active = true; existing.deletedAt = null; reactivated++; }
                if (changed) updated++;
                // (entity già managed, flush a fine tx)
            }
        }

        // 4) Soft delete dei giocatori non più presenti nel file
        List<PlayerEntity> stillActive = PlayerEntity.list("active = true");
        for (PlayerEntity p : stillActive) {
            if (!excelByName.containsKey(norm(p.name))) {
                // soft delete
                p.active = false;
                p.deletedAt = java.time.Instant.now();

                // se in roster, rimuovi righe (rimborso automatico via "spent")
                List<RosterEntity> ros = RosterEntity.list("player", p);
                for (RosterEntity r : ros) {
                    // opzionale: logga l’operazione
                    if (r.participant != null && r.amount != null) {
                        LOG.infof("Unassign %s from %s (amount=%.0f) due to catalog removal",
                                p.name, r.participant.name, r.amount);
                    }
                    r.delete();           // spent scende → remaining sale
                    unassigned++;
                }
                // flag di comodo
                p.assigned = false;
                p.persist();

                softDeleted++;
            }
        }

        Map<String, Integer> out = Map.of(
                "inserted", inserted,
                "updated", updated,
                "reactivated", reactivated,
                "softDeleted", softDeleted,
                "unassigned", unassigned
        );
        LOG.infof("SyncReport %s", out);
        return out;
    }

    // === helper ===
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

    // piccolo DTO interno
    private static class ExcelRow {
        final String name;
        final String team;
        final Role role;
        final Double valore;
        ExcelRow(String name, String team, Role role, Double valore) {
            this.name = name; this.team = team; this.role = role; this.valore = valore;
        }
    }

    // ====== STATISTICHE ======

    /** Conta quanti giocatori sono disponibili (non assegnati e non skippati in questo giro). */
    private Long countAvail(Long giroId, Role role) {
        String q = "select count(p) from PlayerEntity p " +
                "where p.active = true " + // 👈
                "and p.id not in (select r.player.id from RosterEntity r) " +
                "and p.id not in (select s.player.id from SkipEntity s where s.giro.id = ?1)";
        var query = Panache.getEntityManager().createQuery(role != null ? q + " and p.role = ?2" : q);
        query.setParameter(1, giroId);
        if (role != null) query.setParameter(2, role);
        return (Long) query.getSingleResult();
    }


    /** Conta quanti sono stati skippati in questo giro */
    private Long countSkipped(Long giroId, Role role) {
        String q = "select count(s) from SkipEntity s " +
                "where s.giro.id = ?1 and s.player.active = true"; // 👈
        if (role != null) q += " and s.player.role = ?2";

        var query = Panache.getEntityManager().createQuery(q);
        query.setParameter(1, giroId);
        if (role != null) query.setParameter(2, role);
        return (Long) query.getSingleResult();
    }


    /** Ritorna mappa con disponibili e scartati divisi per ruolo */
    public Map<String, Object> remainingAndSkippedByRole(Long giroId) {
        Map<String, Object> result = new HashMap<>();
        result.put("remaining", Map.of(
                "PORTIERE", countAvail(giroId, Role.PORTIERE).intValue(),
                "DIFENSORE", countAvail(giroId, Role.DIFENSORE).intValue(),
                "CENTROCAMPISTA", countAvail(giroId, Role.CENTROCAMPISTA).intValue(),
                "ATTACCANTE", countAvail(giroId, Role.ATTACCANTE).intValue(),
                "TUTTI", countAvail(giroId, null).intValue()
        ));
        result.put("skipped", Map.of(
                "PORTIERE", countSkipped(giroId, Role.PORTIERE).intValue(),
                "DIFENSORE", countSkipped(giroId, Role.DIFENSORE).intValue(),
                "CENTROCAMPISTA", countSkipped(giroId, Role.CENTROCAMPISTA).intValue(),
                "ATTACCANTE", countSkipped(giroId, Role.ATTACCANTE).intValue(),
                "TUTTI", countSkipped(giroId, null).intValue()
        ));
        return result;
    }

    // ====== GIRO MANAGEMENT ======

    /** Ritorna il giro aperto o ne crea uno nuovo. */
    @Transactional
    public GiroEntity ensureCurrentGiro() {
        GiroEntity g = GiroEntity.find("endedAt is null").firstResult();
        if (g == null) {
            g = new GiroEntity();
            g.persist();
        }
        return g;
    }

    /**
     * Reset giro:
     *  - chiude il giro attuale,
     *  - NON riporta gli skip nel nuovo (così TUTTI i non assegnati tornano eleggibili),
     *  - crea un nuovo giro "pulito".
     */
    @Transactional
    public GiroEntity resetGiro() {
        GiroEntity g = GiroEntity.find("endedAt is null").firstResult();
        if (g != null) {
            g.endedAt = java.time.Instant.now();
            g.persist();

            // (opzionale) pulizia history del giro chiuso
            Panache.getEntityManager()
                    .createQuery("delete from GiroPickEntity gp where gp.giro = ?1")
                    .setParameter(1, g)
                    .executeUpdate();
        }

        GiroEntity nuovo = new GiroEntity();
        nuovo.persist();
        return nuovo;
    }

    // ====== RANDOM & HISTORY ======

    /**
     * Estrae un giocatore random filtrando già quelli assegnati o skippati nel giro corrente.
     * ⚠ Annotato @Transactional per poter registrare la pick in history.
     */
    @Transactional
    public PlayerEntity drawRandom(Long giroId, Role role) {
        String q = "select p from PlayerEntity p " +
                "where p.active = true " + // 👈
                "and p.id not in (select r.player.id from RosterEntity r) " +
                "and p.id not in (select s.player.id from SkipEntity s where s.giro.id = ?1)" +
                (role != null ? " and p.role = ?2" : "") +
                " order by function('random') ";

        var query = Panache.getEntityManager().createQuery(q, PlayerEntity.class)
                .setParameter(1, giroId);
        if (role != null) query.setParameter(2, role);
        query.setMaxResults(1);

        var res = query.getResultList();
        if (res.isEmpty()) return null;

        PlayerEntity picked = res.get(0);

        GiroPickEntity gp = new GiroPickEntity();
        gp.giro = GiroEntity.findById(giroId);
        gp.player = picked;
        gp.persist();

        return picked;
    }


    /** Ultimo giocatore pescato nel giro che NON è stato assegnato (per “indietro”). */
    public PlayerEntity lastUnassignedPick(Long giroId) {
        List<PlayerEntity> res = Panache.getEntityManager()
                .createQuery(
                        "select gp.player from GiroPickEntity gp " +
                                "where gp.giro.id = :gid " +
                                "and not exists (select 1 from RosterEntity r where r.player = gp.player) " +
                                "order by gp.createdAt desc",
                        PlayerEntity.class)
                .setParameter("gid", giroId)
                .setMaxResults(1)
                .getResultList();
        return res.isEmpty() ? null : res.get(0);
    }

    /** Marca un giocatore come skippato nel giro corrente. */
    @Transactional
    public void skip(Long giroId, PlayerEntity p) {
        GiroEntity g = GiroEntity.findById(giroId);
        if (g == null) return;
        var s = new SkipEntity();
        s.player = p;
        s.giro = g;
        s.persist();
    }

    // ====== ASSEGNAZIONE ======

    /** Marca un giocatore come assegnato a un partecipante (crea la riga di roster). */
    @Transactional
    public void markAssigned(String roundId, PlayerEntity player, Long participantId, Double amount) {
        ParticipantEntity participant = ParticipantEntity.findById(participantId);
        if (participant == null) {
            throw new IllegalArgumentException("Partecipante non trovato con id=" + participantId);
        }

        RosterEntity rosterEntry = new RosterEntity();
        rosterEntry.participant = participant;
        rosterEntry.player = player;
        rosterEntry.amount = amount;
        rosterEntry.persist();

        // Se vuoi, puoi anche aggiornare un flag sul player:
        // player.assigned = true;
        // player.persist();
    }

    // ====== LOOKUP ======

    public PlayerEntity findByNameTeam(String name, String team) {
        return PlayerEntity.find(
                "lower(name)=?1 and lower(team)=?2",
                name.toLowerCase(),
                (team == null ? "" : team).toLowerCase()
        ).firstResult();
    }

    /** Ultima pescata non assegnata, ESCLUDENDO il corrente (se passato). */
    public PlayerEntity previousUnassignedPick(Long giroId, PlayerEntity current) {
        var em = Panache.getEntityManager();
        String jpql =
                "select gp.player from GiroPickEntity gp " +
                        "where gp.giro.id = :gid " +
                        "and not exists (select 1 from RosterEntity r where r.player = gp.player) ";
        if (current != null) {
            jpql += "and gp.player <> :curr ";
        }
        jpql += "order by gp.createdAt desc";

        var q = em.createQuery(jpql, PlayerEntity.class)
                .setParameter("gid", giroId)
                .setMaxResults(1);
        if (current != null) q.setParameter("curr", current);

        var res = q.getResultList();
        return res.isEmpty() ? null : res.get(0);
    }

}
