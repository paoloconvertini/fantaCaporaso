package com.fantasta.service;

import com.fantasta.dto.PlayerImportResult;
import com.fantasta.model.*;
import com.fantasta.model.Role;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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


    // ====== STATISTICHE ======

    /** Conta quanti giocatori sono disponibili (non assegnati e non skippati in questo giro). */
    private Long countAvail(Long giroId, Role role) {
        if (role == Role.PORTIERE) {
            return (long) availableGoalkeeperDoors(giroId).size();
        }
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
        if (role == Role.PORTIERE) {
            q = "select count(distinct s.player.team) from SkipEntity s " +
                    "where s.giro.id = ?1 and s.player.active = true and s.player.role = ?2";
        } else if (role != null) q += " and s.player.role = ?2";

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
        if (role == Role.PORTIERE) {
            List<List<PlayerEntity>> doors = new ArrayList<>(availableGoalkeeperDoors(giroId).values());
            if (doors.isEmpty()) return null;
            List<PlayerEntity> selectedDoor = doors.get(ThreadLocalRandom.current().nextInt(doors.size()));
            PlayerEntity picked = selectedDoor.get(ThreadLocalRandom.current().nextInt(selectedDoor.size()));
            GiroPickEntity gp = new GiroPickEntity();
            gp.giro = GiroEntity.findById(giroId);
            gp.player = picked;
            gp.persist();
            return picked;
        }
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
        List<PlayerEntity> players = p.role == Role.PORTIERE
                ? PlayerEntity.list("active = true and role = ?1 and lower(team) = ?2",
                    Role.PORTIERE, normalizeTeam(p.team))
                : List.of(p);
        for (PlayerEntity player : players) {
            if (SkipEntity.count("giro = ?1 and player = ?2", g, player) > 0) continue;
            var s = new SkipEntity();
            s.player = player;
            s.giro = g;
            s.persist();
        }
    }

    // ====== ASSEGNAZIONE ======

    /** Marca un giocatore come assegnato a un partecipante (crea la riga di roster). */
    @Transactional
    public void markAssigned(String roundId, PlayerEntity player, Long participantId, Double amount) {
        assignPurchasedPlayer(player, participantId, amount);
    }

    /**
     * Registra un acquisto. Per un portiere assegna l'intero pacchetto della porta:
     * titolare (quotazione più alta) + due riserve, quando disponibili.
     */
    @Transactional
    public List<RosterEntity> assignPurchasedPlayer(PlayerEntity player, Long participantId, Double amount) {
        ParticipantEntity participant = ParticipantEntity.findById(participantId);
        if (participant == null) {
            throw new IllegalArgumentException("Partecipante non trovato con id=" + participantId);
        }
        if (player == null || amount == null || amount <= 0) {
            throw new IllegalArgumentException("Giocatore o importo non valido");
        }
        if (RosterEntity.count("player", player) > 0) {
            throw new IllegalStateException("Giocatore già assegnato: " + player.name);
        }

        List<PlayerEntity> packagePlayers = player.role == Role.PORTIERE
                ? goalkeeperPackage(player)
                : List.of(player);
        if (player.role == Role.PORTIERE && amount < 3D) {
            throw new IllegalArgumentException("Offerta minima per la porta: 3 crediti");
        }
        if (amount < packagePlayers.size()) {
            throw new IllegalArgumentException("Importo insufficiente per il pacchetto portieri");
        }

        List<RosterEntity> created = new ArrayList<>();
        for (int i = 0; i < packagePlayers.size(); i++) {
            PlayerEntity packagePlayer = packagePlayers.get(i);
            if (RosterEntity.count("player", packagePlayer) > 0) {
                throw new IllegalStateException("Giocatore già assegnato: " + packagePlayer.name);
            }
            RosterEntity rosterEntry = new RosterEntity();
            rosterEntry.participant = participant;
            rosterEntry.player = packagePlayer;
            rosterEntry.amount = player.role == Role.PORTIERE
                    ? (i == 0 ? amount - (packagePlayers.size() - 1) : 1D)
                    : amount;
            rosterEntry.persist();
            packagePlayer.assigned = true;
            packagePlayer.persist();
            created.add(rosterEntry);
        }
        return created;
    }

    /** Restituisce il pacchetto nell'ordine titolare, riserve. */
    public List<PlayerEntity> goalkeeperPackage(PlayerEntity calledPlayer) {
        if (calledPlayer == null || calledPlayer.role != Role.PORTIERE) {
            throw new IllegalArgumentException("Portiere non valido");
        }
        String team = normalizeTeam(calledPlayer.team);
        long teamTotal = PlayerEntity.count("active = true and role = ?1 and lower(team) = ?2", Role.PORTIERE, team);
        long teamAssigned = RosterEntity.count("player.role = ?1 and lower(player.team) = ?2", Role.PORTIERE, team);
        boolean donorStillAuctionable = teamTotal == 4 && teamAssigned == 1;
        if (teamAssigned > 0 && !donorStillAuctionable) {
            throw new IllegalStateException("Porta già assegnata: " + calledPlayer.team);
        }

        List<PlayerEntity> available = PlayerEntity.<PlayerEntity>list(
                "active = true and role = ?1 and lower(team) = ?2 " +
                        "and id not in (select r.player.id from RosterEntity r)",
                Role.PORTIERE, team);
        available.sort(goalkeeperOrder());
        if (available.isEmpty()) {
            throw new IllegalStateException("Nessun portiere disponibile per " + calledPlayer.team);
        }

        List<PlayerEntity> selected = new ArrayList<>();
        if (available.size() <= 3) {
            selected.addAll(available);
        } else {
            selected.add(available.get(0));
            selected.add(available.get(1));
            List<PlayerEntity> lowest = lowestValued(available.subList(2, available.size()));
            selected.add(lowest.get(ThreadLocalRandom.current().nextInt(lowest.size())));
        }

        if (selected.size() == 2) {
            findSurplusGoalkeeper(team).ifPresent(selected::add);
        }
        return selected;
    }

    public int purchaseSize(PlayerEntity player) {
        return player != null && player.role == Role.PORTIERE ? goalkeeperPackage(player).size() : 1;
    }

    private Optional<PlayerEntity> findSurplusGoalkeeper(String targetTeam) {
        List<PlayerEntity> all = PlayerEntity.<PlayerEntity>list("active = true and role = ?1", Role.PORTIERE);
        Map<String, List<PlayerEntity>> byTeam = new HashMap<>();
        for (PlayerEntity goalkeeper : all) {
            byTeam.computeIfAbsent(normalizeTeam(goalkeeper.team), ignored -> new ArrayList<>()).add(goalkeeper);
        }

        List<PlayerEntity> candidates = new ArrayList<>();
        for (Map.Entry<String, List<PlayerEntity>> entry : byTeam.entrySet()) {
            if (entry.getKey().equals(targetTeam) || entry.getValue().size() != 4) continue;
            List<PlayerEntity> teamPlayers = entry.getValue();
            teamPlayers.sort(goalkeeperOrder());
            List<PlayerEntity> free = teamPlayers.stream()
                    .filter(p -> RosterEntity.count("player", p) == 0)
                    .toList();
            int assigned = teamPlayers.size() - free.size();
            if (assigned == 0) {
                candidates.addAll(lowestValued(teamPlayers.subList(2, teamPlayers.size())));
            } else if (assigned == 3 && free.size() == 1) {
                candidates.add(free.get(0));
            }
        }
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private List<PlayerEntity> lowestValued(List<PlayerEntity> players) {
        double minimum = players.stream().mapToDouble(this::goalkeeperValue).min().orElse(0D);
        return players.stream().filter(p -> Double.compare(goalkeeperValue(p), minimum) == 0).toList();
    }

    private Comparator<PlayerEntity> goalkeeperOrder() {
        return Comparator.comparingDouble(this::goalkeeperValue).reversed()
                .thenComparing(p -> p.name == null ? "" : p.name, String.CASE_INSENSITIVE_ORDER);
    }

    private double goalkeeperValue(PlayerEntity player) {
        return player.valore == null ? 0D : player.valore;
    }

    private String normalizeTeam(String team) {
        return team == null ? "" : team.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, List<PlayerEntity>> availableGoalkeeperDoors(Long giroId) {
        List<PlayerEntity> free = PlayerEntity.<PlayerEntity>list(
                "active = true and role = ?1 " +
                        "and id not in (select r.player.id from RosterEntity r) " +
                        "and id not in (select s.player.id from SkipEntity s where s.giro.id = ?2)",
                Role.PORTIERE, giroId);
        Map<String, List<PlayerEntity>> freeByTeam = free.stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> normalizeTeam(p.team)));
        Map<String, List<PlayerEntity>> result = new HashMap<>();
        for (Map.Entry<String, List<PlayerEntity>> entry : freeByTeam.entrySet()) {
            String team = entry.getKey();
            long total = PlayerEntity.count("active = true and role = ?1 and lower(team) = ?2", Role.PORTIERE, team);
            long assigned = RosterEntity.count("player.role = ?1 and lower(player.team) = ?2", Role.PORTIERE, team);
            if (assigned == 0 || (total == 4 && assigned == 1 && entry.getValue().size() == 3)) {
                result.put(team, entry.getValue());
            }
        }
        return result;
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

    // ====== IMPORT/UPDATE CATALOGO GIOCATORI ======

    @Transactional
    public PlayerImportResult syncPlayersFromExcel() throws Exception {
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

        return syncPlayersFromExcel(is);
    }

    @Transactional
    public PlayerImportResult syncPlayersFromExcel(InputStream is) throws Exception {
        Map<String, ExcelRow> excelByName = new HashMap<>();
        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Map<String, Integer> columns = findColumns(sheet);
            for (Row row : sheet) {
                String roleStr = getCellStr(row.getCell(columns.get("role"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                String name    = getCellStr(row.getCell(columns.get("name"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                String team    = getCellStr(row.getCell(columns.get("team"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                Double valore  = parseValore(row.getCell(columns.get("value"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));

                if (name == null || name.isBlank()) continue;
                Role role = Role.fromString(roleStr);
                if (role == null) continue;
                if (team == null || team.isBlank()) {
                    throw new IllegalArgumentException("Squadra mancante per " + name);
                }
                if (valore == null || valore < 0) {
                    throw new IllegalArgumentException("Quotazione non valida per " + name);
                }

                String key = norm(name);
                if (excelByName.containsKey(key)) {
                    throw new IllegalArgumentException("Calciatore duplicato: " + name);
                }
                excelByName.put(key, new ExcelRow(
                        name.trim(),
                        team == null ? "" : team.trim(),
                        role,
                        valore == null ? 0d : valore));
            }
        }

        if (excelByName.isEmpty()) {
            throw new IllegalArgumentException("Nessun calciatore valido trovato nel file Excel");
        }

        return applyExcelToDb(excelByName);
    }

    /** Valida il file FantaMaster senza modificare il database. */
    public PlayerImportResult previewPlayersFromExcel(InputStream is) throws Exception {
        Map<String, ExcelRow> rows = parseExcel(is);
        return new PlayerImportResult(rows.size(), rows.size(), 0, 0, 0, 0, true);
    }

    /** Sostituzione completa del catalogo per il cambio stagione. */
    @Transactional
    public PlayerImportResult replacePlayersFromExcel(InputStream is) throws Exception {
        Map<String, ExcelRow> rows = parseExcel(is);

        int unassigned = Math.toIntExact(RosterEntity.count());
        GiroPickEntity.deleteAll();
        SkipEntity.deleteAll();
        RosterEntity.deleteAll();
        RosterHistoryEntity.deleteAll();
        GiroEntity.deleteAll();
        AuctionRoundStateEntity.deleteAll();
        PlayerEntity.deleteAll();

        for (ExcelRow xr : rows.values()) {
            PlayerEntity p = new PlayerEntity();
            p.name = xr.name;
            p.team = xr.team;
            p.role = xr.role;
            p.valore = xr.valore;
            p.assigned = false;
            p.active = true;
            p.persist();
        }
        return new PlayerImportResult(rows.size(), rows.size(), 0, 0, 0, unassigned, false);
    }

    private Map<String, ExcelRow> parseExcel(InputStream is) throws Exception {
        Map<String, ExcelRow> rows = new LinkedHashMap<>();
        try (Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Map<String, Integer> columns = findColumns(sheet);
            for (Row row : sheet) {
                String name = getCellStr(row.getCell(columns.get("name"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                String team = getCellStr(row.getCell(columns.get("team"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                String roleText = getCellStr(row.getCell(columns.get("role"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                Role role = Role.fromString(roleText);
                if (role == null) continue;
                Double value = parseValore(row.getCell(columns.get("value"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome calciatore mancante alla riga " + (row.getRowNum() + 1));
                if (team == null || team.isBlank()) throw new IllegalArgumentException("Squadra mancante per " + name);
                if (value == null || value < 0) throw new IllegalArgumentException("Quotazione non valida per " + name);
                String key = norm(name);
                if (rows.containsKey(key)) throw new IllegalArgumentException("Calciatore duplicato: " + name);
                rows.put(key, new ExcelRow(name.trim(), team.trim(), role, value));
            }
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Nessun calciatore valido trovato nel file Excel");
        return rows;
    }

    private static Map<String, Integer> findColumns(Sheet sheet) {
        for (Row row : sheet) {
            Map<String, Integer> found = new HashMap<>();
            for (Cell cell : row) {
                String text = norm(getCellStr(cell));
                if (text.equals("nome")) found.put("name", cell.getColumnIndex());
                if (text.equals("squadra")) found.put("team", cell.getColumnIndex());
                if (text.equals("ruolo")) found.put("role", cell.getColumnIndex());
                if (text.equals("quotazione") || text.equals("valore")) found.put("value", cell.getColumnIndex());
            }
            if (found.keySet().containsAll(Set.of("name", "team", "role", "value"))) return found;
        }
        throw new IllegalArgumentException("Intestazioni richieste non trovate: Nome, Squadra, Ruolo, Quotazione");
    }

    private PlayerImportResult applyExcelToDb(Map<String, ExcelRow> excelByName) {
        int inserted = 0, updated = 0, reactivated = 0, softDeleted = 0, unassigned = 0;

        // Upsert
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
            }
        }

        // Soft delete
        List<PlayerEntity> stillActive = PlayerEntity.list("active = true");
        for (PlayerEntity p : stillActive) {
            if (!excelByName.containsKey(norm(p.name))) {
                p.active = false;
                p.deletedAt = java.time.Instant.now();

                List<RosterEntity> ros = RosterEntity.list("player", p);
                for (RosterEntity r : ros) {
                    if (r.participant != null && r.amount != null) {
                        LOG.infof("Unassign %s from %s (amount=%.0f) due to catalog removal",
                                p.name, r.participant.name, r.amount);
                    }
                    r.delete();
                    unassigned++;
                }
                p.assigned = false;
                p.persist();
                softDeleted++;
            }
        }

        return new PlayerImportResult(inserted, updated, reactivated, softDeleted, unassigned);
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

    private static class ExcelRow {
        final String name;
        final String team;
        final Role role;
        final Double valore;
        ExcelRow(String name, String team, Role role, Double valore) {
            this.name = name; this.team = team; this.role = role; this.valore = valore;
        }
    }

}
