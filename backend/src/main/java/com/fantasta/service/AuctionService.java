package com.fantasta.service;

import com.fantasta.dto.RoundDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasta.model.*;
import com.fantasta.ws.RoundSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class AuctionService {
    private static final String CURRENT_ROUND_STATE_ID = "current";

    private RoundState state;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    ParticipantService participantService;

    @Inject
    RoundSocket socket;

    @Inject
    RosterService rosterService;

    @Inject
    DbService dbService;

    @Inject
    EntityManager entityManager;

    @Transactional
    public synchronized RoundState get() {
        if (state == null) {
            state = loadCurrentState();
        }
        return state;
    }

    @Transactional
    public synchronized RoundState start(String player, String team, String role,
                                         Integer duration, String tieBreak, Integer value,
                                         Set<Long> allowedUsers) {
        RoundState s = new RoundState();
        s.roundId = UUID.randomUUID().toString();
        s.player = player;
        s.playerTeam = team;
        s.playerRole = role;
        s.value = value;
        PlayerEntity calledPlayer = dbService.findByNameTeam(player, team);
        s.purchaseSize = calledPlayer == null ? 1 : dbService.purchaseSize(calledPlayer);
        s.closed = false;
        s.minimumBid = minimumBidFor(allowedUsers, role);
        s.durationSeconds = duration;
        s.endEpochMillis = (duration != null && duration > 0)
                ? (System.currentTimeMillis() + duration * 1000L)
                : null;
        s.tieBreak = (tieBreak == null || tieBreak.isBlank()) ? "NONE" : tieBreak;
        s.allowedUsers = (allowedUsers != null && !allowedUsers.isEmpty())
                ? new HashSet<>(allowedUsers)
                : null;
        s.tieUsers = null;

        this.state = s;
        persistCurrentState();
        return state;
    }


    @Transactional
    public synchronized RoundState bid(Long participantId, Double amount) {
        applyBid(participantId, amount);
        return state;
    }

    @Transactional
    public synchronized RoundDto bidDto(Long participantId, Double amount) {
        applyBid(participantId, amount);
        RoundDto dto = RoundDto.toDto(state);
        // Il monitor deve coprire anche l'UPDATE effettivo. Senza flush, il commit
        // avverrebbe dopo il rilascio del monitor e richieste HTTP concorrenti
        // potrebbero aggiornare la stessa riga di stato in parallelo.
        entityManager.flush();
        return dto;
    }

    private void applyBid(Long participantId, Double amount) {
        if (state == null || state.closed)
            throw new IllegalStateException("Round non attivo");

        if (participantId == null)
            throw new IllegalArgumentException("Partecipante mancante");

        double minimumBid = state.minimumBid != null ? state.minimumBid : 1D;
        if (amount < minimumBid)
            throw new IllegalArgumentException("Offerta minima " + formatAmount(minimumBid));

        ParticipantEntity p = ParticipantEntity.findById(participantId);
        if (p == null)
            throw new IllegalArgumentException("Partecipante non trovato: " + participantId);

        if (state.allowedUsers != null && !state.allowedUsers.isEmpty()) {
            if (!state.allowedUsers.contains(participantId)) {
                throw new IllegalArgumentException("Spareggio in corso: solo i partecipanti in parità possono offrire");
            }
        }
        Role role = Role.fromString(state.playerRole);
        if (role == null) throw new IllegalArgumentException("Ruolo non valido");
        PlayerEntity auctionPlayer = dbService.findByNameTeam(state.player, state.playerTeam);
        if (auctionPlayer == null) throw new IllegalArgumentException("Giocatore non trovato");
        int purchaseSize = dbService.purchaseSize(auctionPlayer);

        // ✅ residuo calcolato da RosterService
        int residuo = participantService.remainingCreditsById(p.id, p.totalCredits);
        if (amount > residuo)
            throw new IllegalArgumentException("Offerta supera il credito residuo");
        int maxBid = rosterService.maxBid(p.id, p.totalCredits, purchaseSize);
        if (amount > maxBid)
            throw new IllegalArgumentException("Offerta massima " + maxBid + ": conserva almeno 1 credito per ogni posto libero");

        // ✅ quota per ruolo
        int current = participantService.roleCounts(p.id).getOrDefault(role, 0);
        int max = rosterService.max(role);
        if (current + purchaseSize > max)
            throw new IllegalArgumentException("Quota piena per ruolo " + role);

        // ✅ aggiorna stato round
        state.bids.put(String.valueOf(p.id), amount);

        // Durante il round e' pubblico soltanto chi ha puntato, mai l'importo.
        socket.broadcast("BID_ADDED", Map.of("user", p.name));
        persistCurrentState();

    }

    private Double minimumBidFor(Set<Long> allowedUsers, String role) {
        double roleMinimum = Role.fromString(role) == Role.PORTIERE ? 3D : 1D;
        if (state == null || allowedUsers == null || allowedUsers.isEmpty() || state.bids == null || state.bids.isEmpty()) {
            return roleMinimum;
        }

        double maxAllowedBid = state.bids.entrySet().stream()
                .filter(e -> allowedUsers.contains(Long.valueOf(e.getKey())))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0D);

        return Math.max(roleMinimum, maxAllowedBid + 1D);
    }

    private String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((int) amount);
        }
        return String.valueOf(amount);
    }

    @Transactional
    public synchronized RoundState close() {
        return closeCurrentRound();
    }

    /**
     * Chiude il round solo se e' ancora quello per cui era stato programmato il timer.
     * Il controllo e la chiusura condividono lo stesso monitor delle offerte, quindi
     * un'offerta viene interamente accettata prima della chiusura oppure rifiutata
     * perche' il round risulta gia' chiuso.
     */
    @Transactional
    public synchronized RoundState closeIfActive(String expectedRoundId) {
        if (state == null) {
            state = loadCurrentState();
        }
        if (state == null || state.closed || !Objects.equals(state.roundId, expectedRoundId)) {
            return null;
        }
        return closeCurrentRound();
    }

    @Transactional
    public synchronized RoundDto closeIfActiveDto(String expectedRoundId) {
        if (state == null) {
            state = loadCurrentState();
        }
        if (state == null || state.closed || !Objects.equals(state.roundId, expectedRoundId)) {
            return null;
        }
        return RoundDto.toDto(closeCurrentRound());
    }

    private RoundState closeCurrentRound() {
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
            PlayerEntity player = dbService.findByNameTeam(state.player, state.playerTeam);
            double chargedAmount = e.getValue();
            if (state.bids.size() == 1 && player != null) {
                chargedAmount = player.role == Role.PORTIERE
                        ? Math.max(3D, dbService.purchaseSize(player))
                        : 1D;
            }
            state.winner = new Winner(id, p != null ? p.name : ("??-" + id), chargedAmount);

            // 🔹 Salvataggio su DB
            if (p != null && player != null) {
                dbService.markAssigned(state.roundId, player, p.id, chargedAmount);
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
        persistCurrentState();
        return state;
    }

    @Transactional
    public synchronized void reset() {
        state = null;
        clearCurrentState();
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

        int purchaseSize = dbService.purchaseSize(player);
        int current = participantService.roleCounts(p.id).getOrDefault(player.role, 0);
        if (current + purchaseSize > rosterService.max(player.role)) {
            throw new IllegalArgumentException("Quota piena per ruolo " + player.role);
        }
        int remaining = participantService.remainingCreditsById(p.id, p.totalCredits);
        if (amount == null || amount > remaining) {
            throw new IllegalArgumentException("Importo supera il credito residuo");
        }
        int maxBid = rosterService.maxBid(p.id, p.totalCredits, purchaseSize);
        if (amount > maxBid) {
            throw new IllegalArgumentException("Importo massimo " + maxBid + ": conserva almeno 1 credito per ogni posto libero");
        }
        if (player.role == Role.PORTIERE && amount < 3D) {
            throw new IllegalArgumentException("Offerta minima per la porta: 3 crediti");
        }

        // 🔹 Salvataggio su DB
        dbService.assignPurchasedPlayer(player, p.id, amount);

        // 🔹 Aggiorna RoundState
        if (state == null) state = new RoundState();
        state.winner = new Winner(p.id, p.name, amount);
        state.closed = true;
        state.tieUsers = null;
        state.allowedUsers = null;
        persistCurrentState();

        // 🔔 NOTIFICA SUMMARY
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "manual_assign"));
        return state;
    }

    @Transactional
    public void closeAuction(Long sessionId) {
        // Copia lo stato corrente delle rose
        List<RosterEntity> roster = RosterEntity.listAll();
        RosterService.createRoster(sessionId, roster);

        // Pulisce giro e skip
        GiroEntity.deleteAll();
        SkipEntity.deleteAll();
        state = null;
        clearCurrentState();
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "auction_closed", "sessionId", sessionId));

    }

    private void persistCurrentState() {
        if (state == null) {
            clearCurrentState();
            return;
        }

        try {
            AuctionRoundStateEntity entity = AuctionRoundStateEntity.findById(CURRENT_ROUND_STATE_ID);
            if (entity == null) {
                entity = new AuctionRoundStateEntity();
                entity.id = CURRENT_ROUND_STATE_ID;
            }
            entity.stateJson = objectMapper.writeValueAsString(state);
            entity.updatedAt = Instant.now();
            entity.persist();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile salvare lo stato round", e);
        }
    }

    private RoundState loadCurrentState() {
        AuctionRoundStateEntity entity = AuctionRoundStateEntity.findById(CURRENT_ROUND_STATE_ID);
        if (entity == null || entity.stateJson == null || entity.stateJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(entity.stateJson, RoundState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile leggere lo stato round salvato", e);
        }
    }

    private void clearCurrentState() {
        AuctionRoundStateEntity.deleteById(CURRENT_ROUND_STATE_ID);
    }

}
