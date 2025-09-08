package com.fantasta.service;

import com.fantasta.model.*;
import com.fantasta.ws.RoundSocket;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.xml.xpath.XPathEvaluationResult;
import java.io.File;
import java.util.*;

@ApplicationScoped
public class AuctionService {
    private RoundState state;
    private final Random rnd = new Random();

    @Inject
    ParticipantService participantService;

    @Inject
    RoundSocket socket;

    @Inject
    RosterService rosterService;

    @Inject
    DbService dbService;

    public synchronized RoundState get() {
        return state;
    }

    public synchronized RoundState start(String player, String team, String role,
                                         Integer duration, String tieBreak, Integer value) {
        RoundState s = new RoundState();
        s.roundId = UUID.randomUUID().toString();
        s.player = player;
        s.playerTeam = team;
        s.playerRole = role;
        s.value = value;
        s.closed = false;
        s.durationSeconds = duration;
        s.endEpochMillis = (duration != null && duration > 0)
                ? (System.currentTimeMillis() + duration * 1000L)
                : null;
        s.tieBreak = (tieBreak == null || tieBreak.isBlank()) ? "NONE" : tieBreak;
        s.allowedUsers = null;
        s.tieUsers = null;

        this.state = s;
        return state;
    }


    @Transactional
    public synchronized RoundState bid(Long participantId, Double amount) {
        if (state == null || state.closed)
            throw new IllegalStateException("Round non attivo");

        if (participantId == null)
            throw new IllegalArgumentException("Partecipante mancante");

        if (amount < 1)
            throw new IllegalArgumentException("Offerta minima 1");

        ParticipantEntity p = ParticipantEntity.findById(participantId);
        if (p == null)
            throw new IllegalArgumentException("Partecipante non trovato: " + participantId);

        Role role = Role.fromString(state.playerRole);

        // ✅ residuo calcolato da RosterService
        int residuo = participantService.remainingCreditsById(p.id, p.totalCredits);
        if (amount > residuo)
            throw new IllegalArgumentException("Offerta supera il credito residuo");

        // ✅ quota per ruolo
        int current = participantService.roleCounts(p.id).getOrDefault(role, 0);
        int max = rosterService.max(role);
        if (current >= max)
            throw new IllegalArgumentException("Quota piena per ruolo " + role);

        // ✅ aggiorna stato round
        state.bids.put(String.valueOf(p.id), amount);

        // broadcast (facciamo qui, così API rimane solo "bridge")
        Map<String, Object> payload = new HashMap<>();
        payload.put("user", p.name);
        payload.put("participantId", p.id);
        payload.put("amount", amount);

        socket.broadcast("BID_ADDED", payload);

        return state;
    }

    @Transactional
    public synchronized RoundState close() {
        if (state == null) throw new IllegalStateException("Nessun round attivo");
        if (state.closed) return state;

        state.closed = true;
        Double max = state.bids.values().stream().mapToDouble(i -> i).max().orElse(0D);

        var top = state.bids.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), max))
                .toList();

        state.tieUsers = null;

        if (top.isEmpty()) {
            state.winner = null;
        } else if (top.size() == 1) {
            var e = top.get(0);
            Long id = Long.valueOf(e.getKey());
            ParticipantEntity p = ParticipantEntity.findById(id);
            state.winner = new Winner(id, p != null ? p.name : ("??-" + id), e.getValue());

            // 🔹 Salvataggio su DB
            if (p != null) {
                PlayerEntity player = dbService.findByNameTeam(state.player, state.playerTeam);
                if (player != null) {
                    dbService.markAssigned(state.roundId, player, p.id, e.getValue());
                }
            }
        } else {
            // Parità: spareggio
            state.winner = null;
            state.tieUsers = top.stream()
                    .map(e -> Long.valueOf(e.getKey()))
                    .toList();
        }
        // 🧹 azzera i timer per evitare riavvii del countdown su round chiuso
        state.endEpochMillis = null;
        state.durationSeconds = null;
        Map<String, Object> payload = Map.of("reason", "round_closed", "roundId", state.roundId);
        socket.broadcast("SUMMARY_UPDATED", payload);
        return state;
    }

    public synchronized void reset() {
        state = null;
    }

    @Transactional
    public synchronized RoundState manualAssign(Long participantId, String playerName, String team, Double amount) {
        if (participantId == null || playerName == null) {
            throw new IllegalArgumentException("Dati mancanti per assegnazione manuale");
        }

        ParticipantEntity p = ParticipantEntity.findById(participantId);
        if (p == null) throw new IllegalArgumentException("Partecipante non trovato con id=" + participantId);

        PlayerEntity player = dbService.findByNameTeam(playerName, team);
        if (player == null) throw new IllegalArgumentException("Giocatore non trovato: " + playerName);

        // 🔹 Salvataggio su DB
        dbService.markAssigned(String.valueOf(System.currentTimeMillis()), player, p.id, amount);

        // 🔹 Aggiorna RoundState
        if (state == null) state = new RoundState();
        state.winner = new Winner(p.id, p.name, amount);
        state.closed = true;
        state.tieUsers = null;
        state.allowedUsers = null;

        // 🔔 NOTIFICA SUMMARY
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "manual_assign"));
        return state;
    }

    @Transactional
    public void closeAuction(Long sessionId) {
        // Copia lo stato corrente delle rose
        List<RosterEntity> roster = RosterEntity.listAll();
        for (RosterEntity r : roster) {
            RosterHistoryEntity h = new RosterHistoryEntity();
            h.sessionId = sessionId;
            h.participant = r.participant;
            h.player = r.player;
            h.amount = r.amount;
            h.persist();
        }

        // Pulisce giro e skip
        GiroEntity.deleteAll();
        SkipEntity.deleteAll();
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "auction_closed", "sessionId", sessionId));

    }

    /**
     * Svincola un singolo giocatore
     */
    @Transactional
    public void releasePlayer(Long rosterId) {
        RosterEntity r = RosterEntity.findById(rosterId);
        if (r == null) return;

        ParticipantEntity p = r.participant;
        PlayerEntity pl = r.player;

        // restituisce i crediti del valore del player
        p.totalCredits += pl.valore;
        p.persist();

        // libera il player
        pl.assigned = false;
        pl.persist();

        // elimina dalla rosa
        r.delete();
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "release"));

    }

    /**
     * Avvia una nuova sessione di mercato caricando rose da file
     */
    @Transactional
    public void startNewAuctionFromFile(File file, Long sessionId) {
        // 1. consolidiamo history
        closeAuction(sessionId);

        // 2. svuotiamo roster corrente
        RosterEntity.deleteAll();

        // 3. parser file (stub)
        Map<String, List<String>> newRosters = parseFile(file);

        // 4. reinseriamo nuove rose
        for (var entry : newRosters.entrySet()) {
            String participantName = entry.getKey();
            ParticipantEntity participant = ParticipantEntity.find("name", participantName).firstResult();
            if (participant == null) continue;

            for (String playerName : entry.getValue()) {
                PlayerEntity player = PlayerEntity.find("name", playerName).firstResult();
                if (player == null) continue;

                RosterEntity r = new RosterEntity();
                r.participant = participant;
                r.player = player;
                r.amount = player.valore;
                r.persist();

                player.assigned = true;
                player.persist();
            }
        }
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "new_session_from_file"));

    }

    /**
     * Parser del file Excel/CSV (da completare più avanti)
     */
    private Map<String, List<String>> parseFile(File file) {
        return new HashMap<>();
    }

}
