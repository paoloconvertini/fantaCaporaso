package com.fantasta.service;

import com.fantasta.dto.AuctionSessionCloseDto;
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
import java.time.LocalDateTime;
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
    MercatoService mercatoService;

    @Inject
    MarketRestrictionService marketRestrictionService;

    @Inject
    EntityManager entityManager;

    @Inject
    MiniAuctionService miniAuctions;

    @Transactional
    public synchronized MiniAuctionSessionEntity prepareMini(String label, String code, java.time.LocalDate date,
                                                             List<Long> rosterIds) {
        requireNoOpenRound();
        return miniAuctions.prepare(label, code, date, rosterIds);
    }

    @Transactional
    public synchronized MiniAuctionSessionEntity activateMini(Long id) {
        requireNoOpenRound();
        MiniAuctionSessionEntity session = miniAuctions.activate(id);
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "mini_releases"));
        return session;
    }

    @Transactional
    public synchronized void finishMini(Long id) {
        requireNoOpenRound();
        miniAuctions.finish(id);
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "mini_finished"));
    }

    @Inject RosterMovementService rosterMovements;

    @Transactional
    public synchronized void revertMovement(Long movementId) {
        try { requireNoOpenRound(); }
        catch (IllegalStateException e) { throw new jakarta.ws.rs.BadRequestException(e.getMessage()); }
        rosterMovements.revert(movementId);
        state = null; clearCurrentState();
        socket.broadcast("ROUND_UPDATED", Map.of("reason", "assignment_reverted"));
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "assignment_reverted"));
    }

    private void requireNoOpenRound() {
        RoundState current = get();
        if (current != null && !current.closed) throw new IllegalStateException("Concludi o annulla prima il round attivo");
    }

    @Transactional
    public synchronized PlayerEntity selectCalledPlayer(Long playerId) {
        RoundState current = get();
        if (current != null && !current.closed) {
            throw new IllegalStateException("Concludi il round attivo prima di cambiare chiamata");
        }
        return dbService.selectCallable(playerId);
    }

    @Transactional
    public synchronized RoundState get() {
        if (state == null) {
            state = loadCurrentState();
        }
        return state;
    }

    @Transactional
    public RoundDto toDto(RoundState round) {
        return RoundDto.toDto(round);
    }

    @Transactional
    public synchronized RoundState start(String player, String team, String role,
                                         Integer duration, String tieBreak, Integer value,
                                         Set<Long> allowedUsers) {
        if (mercatoService.isMercatoAttivo()) {
            mercatoService.requireUpdatedQuotes();
        }
        if (state == null) state = loadCurrentState();
        RoundState previous = state;
        RoundState s = new RoundState();
        s.roundId = UUID.randomUUID().toString();
        s.player = player;
        s.playerTeam = team;
        s.playerRole = role;
        s.value = value;
        PlayerEntity calledPlayer = dbService.findByNameTeam(player, team);
        s.purchaseSize = calledPlayer == null ? 1 : dbService.purchaseSize(calledPlayer);
        s.closed = false;
        s.minimumBid = minimumBidFor(allowedUsers, calledPlayer);
        s.durationSeconds = duration;
        s.endEpochMillis = (duration != null && duration > 0)
                ? (System.currentTimeMillis() + duration * 1000L)
                : null;
        s.tieBreak = (tieBreak == null || tieBreak.isBlank()) ? "NONE" : tieBreak;
        s.allowedUsers = (allowedUsers != null && !allowedUsers.isEmpty())
                ? new HashSet<>(allowedUsers)
                : null;
        s.tieUsers = null;
        s.auctionSessionCode = auctionSessionCode(previous);
        MiniAuctionSessionEntity mini = miniAuctions.active();
        if (mini != null) {
            if (previous != null && !previous.closed) throw new IllegalStateException("Round già attivo");
            if (!dbService.callable(calledPlayer)) throw new IllegalArgumentException("Svincolato non disponibile");
            Set<Long> eligible = miniAuctions.eligible(mini.id, calledPlayer.role);
            if (s.allowedUsers != null) eligible.retainAll(s.allowedUsers);
            if (eligible.isEmpty()) throw new IllegalArgumentException("Nessun partecipante con uno slot aperto di questo ruolo");
            s.miniSessionId = mini.id; s.auctionSessionCode = mini.code; s.allowedUsers = eligible;
            double miniBase = calledPlayer.role == Role.PORTIERE ? 3D : 1D;
            s.minimumBid = miniBase;
            if (previous != null && previous.tieUsers != null && !previous.tieUsers.isEmpty()
                    && Objects.equals(previous.miniSessionId, mini.id) && allowedUsers != null) {
                s.minimumBid = Math.max(miniBase, previous.bids.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D) + 1D);
            }
            if (previous != null && Objects.equals(previous.miniSessionId, mini.id)
                    && previous.tieUsers != null && !previous.tieUsers.isEmpty()) {
                if (!Objects.equals(previous.player, player) || !Objects.equals(previous.playerTeam, team))
                    throw new IllegalArgumentException("Completare o annullare prima lo spareggio della mini asta");
                s.miniBidSlots = new LinkedHashMap<>(previous.miniBidSlots);
            }
        }

        s.previousAssignment = lastAssignment(previous);
        s.lastAssignment = s.previousAssignment;
        boolean tieBreakRound = allowedUsers != null && !allowedUsers.isEmpty()
                && previous != null && previous.tieUsers != null && !previous.tieUsers.isEmpty();
        if (tieBreakRound) {
            s.competitiveOriginRoundId = previous.competitiveOriginRoundId != null
                    ? previous.competitiveOriginRoundId : previous.roundId;
            s.historyBids = new LinkedHashMap<>(previous.historyBids == null
                    ? Collections.emptyMap() : previous.historyBids);
        }

        this.state = s;
        persistCurrentState();
        return state;
    }


    @Transactional
    public synchronized RoundState bid(Long participantId, Double amount) {
        applyBid(participantId, amount, null);
        return state;
    }

    @Transactional
    public synchronized RoundDto bidDto(Long participantId, Double amount) {
        return bidDto(participantId, amount, null);
    }

    @Transactional
    public synchronized RoundDto bidDto(Long participantId, Double amount, Long miniSlotId) {
        applyBid(participantId, amount, miniSlotId);
        RoundDto dto = RoundDto.toDto(state);
        // Il monitor deve coprire anche l'UPDATE effettivo. Senza flush, il commit
        // avverrebbe dopo il rilascio del monitor e richieste HTTP concorrenti
        // potrebbero aggiornare la stessa riga di stato in parallelo.
        entityManager.flush();
        return dto;
    }

    @Transactional
    public synchronized RoundDto withdrawBidDto(Long participantId) {
        if (state == null || state.closed) {
            throw new IllegalStateException("Round non attivo");
        }
        if (participantId == null) {
            throw new IllegalArgumentException("Partecipante mancante");
        }

        ParticipantEntity participant = ParticipantEntity.findById(participantId);
        if (participant == null) {
            throw new IllegalArgumentException("Partecipante non trovato: " + participantId);
        }
        if (state.bids.remove(String.valueOf(participantId)) == null) {
            throw new IllegalArgumentException("Nessuna offerta da ritirare");
        }

        if (state.miniBidSlots != null) state.miniBidSlots.remove(String.valueOf(participantId));
        persistCurrentState();
        entityManager.flush();
        socket.broadcast("BID_WITHDRAWN", Map.of("user", participant.name));
        return RoundDto.toDto(state);
    }

    private void applyBid(Long participantId, Double amount, Long miniSlotId) {
        if (state == null || state.closed)
            throw new IllegalStateException("Round non attivo");

        if (participantId == null)
            throw new IllegalArgumentException("Partecipante mancante");

        ParticipantEntity p = ParticipantEntity.findById(participantId);
        if (p == null)
            throw new IllegalArgumentException("Partecipante non trovato: " + participantId);

        if (state.allowedUsers != null && !state.allowedUsers.isEmpty()) {
            if (!state.allowedUsers.contains(participantId)) {
                throw new IllegalArgumentException("Questo round è riservato ai partecipanti abilitati");
            }
        }
        Role role = Role.fromString(state.playerRole);
        if (role == null) throw new IllegalArgumentException("Ruolo non valido");
        PlayerEntity auctionPlayer = dbService.findByNameTeam(state.player, state.playerTeam);
        if (auctionPlayer == null) throw new IllegalArgumentException("Giocatore non trovato");
        if (state.miniSessionId != null) {
            Long lockedSlot = state.miniBidSlots.get(String.valueOf(participantId));
            if (miniSlotId == null) miniSlotId = lockedSlot;
            if (lockedSlot != null && !lockedSlot.equals(miniSlotId))
                throw new IllegalArgumentException("Ritira l'offerta prima di cambiare slot");
            miniAuctions.validateBid(state.miniSessionId, miniSlotId, participantId, auctionPlayer, amount);
            if (amount < state.minimumBid) throw new IllegalArgumentException("Offerta minima round: " + state.minimumBid);
            state.miniBidSlots.put(String.valueOf(participantId), miniSlotId);
        } else {
        int purchaseSize = dbService.purchaseSize(auctionPlayer);
        double minimumBid = minimumBidForParticipant(auctionPlayer, p,
                state.minimumBid != null ? state.minimumBid : 1D);
        if (amount < minimumBid)
            throw new IllegalArgumentException("Offerta minima " + formatAmount(minimumBid));

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

        }

        // ✅ aggiorna stato round
        state.bids.put(String.valueOf(p.id), amount);

        // Durante il round e' pubblico soltanto chi ha puntato, mai l'importo.
        socket.broadcast("BID_ADDED", Map.of("user", p.name));
        persistCurrentState();

    }

    private Double minimumBidFor(Set<Long> allowedUsers, PlayerEntity player) {
        double roleMinimum = baseMinimumBid(player);
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

    private double baseMinimumBid(PlayerEntity player) {
        if (player == null || player.role != Role.PORTIERE) return 1D;
        if (mercatoService.isMercatoAttivo()) {
            return dbService.goalkeeperPackage(player).stream()
                    .mapToDouble(goalkeeper -> goalkeeper.valore == null ? 0D : goalkeeper.valore)
                    .sum();
        }
        return 3D;
    }

    private double minimumBidForParticipant(PlayerEntity player, ParticipantEntity participant, double roundMinimum) {
        if (player.role == Role.PORTIERE) {
            List<PlayerEntity> goalkeepers = dbService.goalkeeperPackage(player);
            double base = Math.max(roundMinimum, baseMinimumBid(player));
            return marketRestrictionService.hasRestriction(goalkeepers, participant) ? base + 1D : base;
        }
        return Math.max(roundMinimum, marketRestrictionService.minimumBid(player, participant));
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

        Double max = state.bids.values().stream().mapToDouble(i -> i).max().orElse(0D);

        var top = state.bids.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), max))
                .toList();

        if (state.miniSessionId != null && top.size() == 1) {
            var winning = top.get(0);
            miniAuctions.validateBid(state.miniSessionId, state.miniBidSlots.get(winning.getKey()),
                    Long.valueOf(winning.getKey()), dbService.findByNameTeam(state.player, state.playerTeam), winning.getValue());
        }
        state.closed = true;
        state.tieUsers = null;
        if (state.historyBids == null) state.historyBids = new LinkedHashMap<>();
        if (state.competitiveOriginRoundId == null && state.bids != null && state.bids.size() >= 2) {
            state.competitiveOriginRoundId = state.roundId;
        }
        if (state.bids != null) state.historyBids.putAll(state.bids);

        if (top.isEmpty()) {
            state.winner = null;
        } else if (top.size() == 1) {
            var e = top.get(0);
            Long id = Long.valueOf(e.getKey());
            ParticipantEntity p = ParticipantEntity.findById(id);
            PlayerEntity player = dbService.findByNameTeam(state.player, state.playerTeam);
            double chargedAmount = e.getValue();
            if (state.bids.size() == 1 && player != null) {
                chargedAmount = state.miniSessionId == null
                        ? minimumBidForParticipant(player, p, baseMinimumBid(player))
                        : Math.max(state.minimumBid, miniAuctions.requireSlot(state.miniSessionId,
                                state.miniBidSlots.get(e.getKey()), id, player.role).minimumBid);
            }
            state.winner = new Winner(id, p != null ? p.name : ("??-" + id), chargedAmount);

            // 🔹 Salvataggio su DB
            if (p != null && player != null) {
                if (state.miniSessionId != null) miniAuctions.fill(state.miniSessionId,
                        state.miniBidSlots.get(e.getKey()), p.id, player, chargedAmount, state.roundId);
                else dbService.markAssigned(state.roundId, player, p.id, chargedAmount);
                MarketMovementEntity purchase = MarketMovementEntity.find(
                        "player = ?1 and type in ?2 and revertedAt is null order by createdAt desc, id desc",
                        player, List.of(MarketMovementEntity.Type.PURCHASE, MarketMovementEntity.Type.MINI_PURCHASE)).firstResult();
                if (purchase != null) for (MarketMovementEntity movement : MarketMovementEntity.<MarketMovementEntity>list("operationCode", purchase.operationCode)) movement.auctionRoundId = state.roundId;
                state.lastAssignment = assignmentSummary(player, p, chargedAmount);
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
    public synchronized void resetForSkip() {
        if (state == null) {
            state = loadCurrentState();
        }
        if (state != null && !state.closed && state.bids != null && !state.bids.isEmpty()) {
            throw new IllegalStateException("Skip non disponibile: sono presenti offerte");
        }
        state = null;
        clearCurrentState();
    }

    @Transactional
    public synchronized RoundState manualAssign(Long participantId, String playerName, String team, Double amount) {
        if (miniAuctions.active() != null) throw new IllegalStateException("Usare il round della mini asta; completare prima tutti gli slot");
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
        double minimumBid = minimumBidForParticipant(player, p, baseMinimumBid(player));
        if (amount == null || amount < minimumBid) {
            throw new IllegalArgumentException("Offerta minima " + formatAmount(minimumBid));
        }

        boolean recognizedTie = state != null
                && Objects.equals(state.player, playerName)
                && Objects.equals(state.playerTeam, team)
                && state.historyBids != null && state.historyBids.size() >= 2
                && state.competitiveOriginRoundId != null;

        // 🔹 Salvataggio su DB
        dbService.assignPurchasedPlayer(player, p.id, amount);

        // 🔹 Aggiorna RoundState
        if (state == null) {
            state = new RoundState();
            state.roundId = UUID.randomUUID().toString();
            state.auctionSessionCode = auctionSessionCode(null);
        }
        state.player = playerName;
        state.playerTeam = team;
        state.playerRole = player.role.name();
        state.value = player.valore == null ? null : (int) Math.round(player.valore);
        if (!recognizedTie) {
            state.competitiveOriginRoundId = null;
            state.historyBids = new LinkedHashMap<>();
        }
        state.winner = new Winner(p.id, p.name, amount);
        state.lastAssignment = assignmentSummary(player, p, amount);
        state.closed = true;
        state.tieUsers = null;
        state.allowedUsers = null;
        persistCurrentState();

        // 🔔 NOTIFICA SUMMARY
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "manual_assign"));
        return state;
    }

    private String auctionSessionCode(RoundState previous) {
        var market = mercatoService.getConfig();
        if (market != null && market.attiva && market.sessionCode != null) {
            return market.sessionCode;
        }
        return previous != null && previous.auctionSessionCode != null
                ? previous.auctionSessionCode : UUID.randomUUID().toString();
    }

    private AssignmentSummary assignmentSummary(PlayerEntity player, ParticipantEntity participant, double amount) {
        AssignmentSummary summary = new AssignmentSummary();
        summary.player = player.name;
        summary.playerTeam = player.team;
        summary.playerRole = player.role.name();
        summary.winner = participant.name;
        summary.amount = amount;
        return summary;
    }

    private AssignmentSummary lastAssignment(RoundState previous) {
        if (previous == null) return null;
        if (previous.lastAssignment != null) return previous.lastAssignment;
        if (previous.closed && previous.winner != null) {
            AssignmentSummary summary = new AssignmentSummary();
            summary.player = previous.player;
            summary.playerTeam = previous.playerTeam;
            summary.playerRole = previous.playerRole;
            summary.winner = previous.winner.user;
            summary.amount = previous.winner.amount;
            return summary;
        }
        return previous.previousAssignment;
    }

    /**
     * Assegna un giocatore fuori turno oppure corregge proprietario e prezzo di
     * un acquisto esistente. La spesa e il rimborso derivano sempre dalle righe rosa.
     */
    @Transactional
    public synchronized RoundState adminAssign(Long playerId, Long participantId, Double amount) {
        if (miniAuctions.active() != null) throw new IllegalStateException("Usare il round della mini asta; completare prima tutti gli slot");
        if (playerId == null || participantId == null || amount == null || amount <= 0) {
            throw new IllegalArgumentException("Giocatore, partecipante e importo sono obbligatori");
        }
        PlayerEntity player = PlayerEntity.findById(playerId);
        ParticipantEntity participant = ParticipantEntity.findById(participantId);
        if (player == null || !player.active) throw new IllegalArgumentException("Giocatore non trovato o non attivo");
        if (participant == null) throw new IllegalArgumentException("Partecipante non trovato");

        RosterEntity currentEntry = RosterEntity.find("player", player).firstResult();
        if (currentEntry == null) {
            validateNewAssignment(participant, player, amount);
            dbService.assignPurchasedPlayer(player, participant.id, amount);
        } else {
            correctAssignment(participant, player, currentEntry, amount);
        }

        if (state == null) {
            state = loadCurrentState();
        }
        if (state == null) {
            state = new RoundState();
            state.roundId = UUID.randomUUID().toString();
            state.auctionSessionCode = auctionSessionCode(null);
            state.closed = true;
        }
        state.lastAssignment = assignmentSummary(player, participant, amount);
        boolean currentRound = state != null && Objects.equals(state.player, player.name)
                && Objects.equals(state.playerTeam, player.team);
        if (currentRound) {
            state.winner = new Winner(participant.id, participant.name, amount);
            state.closed = true;
            state.tieUsers = null;
            state.allowedUsers = null;
            persistCurrentState();
            socket.broadcast("ROUND_CLOSED", RoundDto.toDto(state));
        }
        persistCurrentState();
        if (!currentRound) socket.broadcast("ROUND_UPDATED", Map.of("reason", "manual_assignment"));
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "admin_assignment_corrected"));
        return currentRound ? state : null;
    }

    private void validateNewAssignment(ParticipantEntity participant, PlayerEntity player, double amount) {
        int purchaseSize = dbService.purchaseSize(player);
        int current = participantService.roleCounts(participant.id).getOrDefault(player.role, 0);
        if (current + purchaseSize > rosterService.max(player.role)) {
            throw new IllegalArgumentException("Quota piena per ruolo " + player.role);
        }
        int remaining = participantService.remainingCreditsById(participant.id, participant.totalCredits);
        if (amount > remaining) throw new IllegalArgumentException("Importo supera il credito residuo");
        int maxBid = rosterService.maxBid(participant.id, participant.totalCredits, purchaseSize);
        if (amount > maxBid) {
            throw new IllegalArgumentException("Importo massimo " + maxBid + ": conserva almeno 1 credito per ogni posto libero");
        }
        double minimumBid = minimumBidForParticipant(player, participant, baseMinimumBid(player));
        if (amount < minimumBid) {
            throw new IllegalArgumentException("Offerta minima " + formatAmount(minimumBid));
        }
    }

    private void correctAssignment(ParticipantEntity participant, PlayerEntity player,
                                   RosterEntity currentEntry, double amount) {
        ParticipantEntity previousOwner = currentEntry.participant;
        List<RosterEntity> entries = player.role == Role.PORTIERE
                ? RosterEntity.list("participant = ?1 and player.role = ?2 and lower(player.team) = ?3",
                        previousOwner, Role.PORTIERE, player.team.toLowerCase(Locale.ROOT))
                : List.of(currentEntry);
        int purchaseSize = entries.size();
        double oldAmount = entries.stream().mapToDouble(row -> row.amount).sum();

        if (!Objects.equals(previousOwner.id, participant.id)) {
            int current = participantService.roleCounts(participant.id).getOrDefault(player.role, 0);
            if (current + purchaseSize > rosterService.max(player.role)) {
                throw new IllegalArgumentException("Quota piena per ruolo " + player.role);
            }
            int remaining = participantService.remainingCreditsById(participant.id, participant.totalCredits);
            if (amount > remaining) throw new IllegalArgumentException("Importo supera il credito residuo");
            int maxBid = rosterService.maxBid(participant.id, participant.totalCredits, purchaseSize);
            if (amount > maxBid) {
                throw new IllegalArgumentException("Importo massimo " + maxBid + ": conserva almeno 1 credito per ogni posto libero");
            }
        } else {
            int remaining = participantService.remainingCreditsById(participant.id, participant.totalCredits);
            int reserved = rosterService.reservedCreditsForCurrentOpenSlots(
                    participantService.roleCounts(participant.id));
            double maxCorrectedAmount = remaining + oldAmount - reserved;
            if (amount > maxCorrectedAmount) {
                throw new IllegalArgumentException("Importo massimo " + formatAmount(maxCorrectedAmount)
                        + ": conserva almeno 1 credito per ogni posto libero");
            }
        }
        if (player.role == Role.PORTIERE && amount < purchaseSize) {
            throw new IllegalArgumentException("Importo insufficiente per il pacchetto portieri");
        }

        if (!Objects.equals(previousOwner.id, participant.id)) {
            String operationCode = UUID.randomUUID().toString();
            for (RosterEntity entry : entries) {
                recordRosterTransfer(entry, previousOwner, participant, amount, operationCode);
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            RosterEntity entry = entries.get(i);
            entry.participant = participant;
            entry.amount = player.role == Role.PORTIERE
                    ? (i == 0 ? amount - (entries.size() - 1) : 1D)
                    : amount;
            entry.player.assigned = true;
        }
    }

    private void recordRosterTransfer(RosterEntity entry, ParticipantEntity source,
                                      ParticipantEntity destination, double resultingAmount,
                                      String operationCode) {
        MarketMovementEntity movement = new MarketMovementEntity();
        movement.participant = source;
        movement.destinationParticipant = destination;
        movement.player = entry.player;
        movement.type = MarketMovementEntity.Type.EXCHANGE;
        movement.currentValue = entry.player.valore == null ? 0D : entry.player.valore;
        movement.previousRosterAmount = entry.amount == null ? 0D : entry.amount;
        movement.refundedAmount = 0D;
        movement.playerNameSnapshot = entry.player.name;
        movement.playerTeamSnapshot = entry.player.team;
        movement.sourceParticipantSnapshot = source.name;
        movement.destinationParticipantSnapshot = destination.name;
        movement.operationCode = operationCode;
        movement.resultingRosterAmount = resultingAmount;
        movement.creditsPreserved = false;
        var market = mercatoService.getConfig();
        movement.sessionCode = market == null ? null : market.sessionCode;
        movement.persist();
    }

    @Transactional
    public synchronized AuctionSessionCloseDto closeAuction() {
        if (miniAuctions.active() != null) throw new IllegalStateException("Usare il round della mini asta; completare prima tutti gli slot");
        if (state == null) state = loadCurrentState();
        if (state != null && !state.closed) {
            throw new IllegalStateException("Concludi o annulla prima il round attivo");
        }

        if (state == null || state.auctionSessionCode == null) {
            AuctionHistorySessionEntity latest = AuctionHistorySessionEntity.find(
                    "order by closedAt desc").firstResult();
            if (latest != null) return closeDto(latest, true);
            throw new IllegalStateException("Nessuna sessione d'asta da concludere");
        }

        AuctionHistorySessionEntity existing = AuctionHistorySessionEntity.find(
                "sessionCode", state.auctionSessionCode).firstResult();
        if (existing != null) return closeDto(existing, true);

        AuctionHistorySessionEntity session = new AuctionHistorySessionEntity();
        session.sessionCode = state.auctionSessionCode;
        var market = mercatoService.getConfig();
        session.label = market != null && market.attiva
                ? "Mercato di riparazione " + market.numeroMercato
                : "Asta iniziale";
        session.closedAt = LocalDateTime.now();
        session.rosterSnapshotId = System.currentTimeMillis();
        session.publishStatus = "PENDING";
        session.publishAttempts = 0;
        session.persistAndFlush();

        List<RosterEntity> roster = RosterEntity.listAll();
        RosterService.createRoster(session.rosterSnapshotId, roster);

        // Pulisce giro e skip
        GiroEntity.deleteAll();
        SkipEntity.deleteAll();
        state = null;
        clearCurrentState();
        socket.broadcast("SUMMARY_UPDATED", Map.of("reason", "auction_closed", "sessionId", session.id));
        return closeDto(session, false);
    }

    private AuctionSessionCloseDto closeDto(AuctionHistorySessionEntity session, boolean alreadyClosed) {
        return new AuctionSessionCloseDto(session.id, session.sessionCode, session.label,
                session.publishStatus, alreadyClosed);
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
            var saved = objectMapper.readTree(entity.stateJson);
            if (saved instanceof com.fasterxml.jackson.databind.node.ObjectNode round) {
                // Campi salvati dai test della prenotazione, ora sospesa.
                // La compatibilità riguarda solo lo stato persistito, non i payload API.
                round.remove(List.of("reservationRequired", "phase", "reservedUsers", "biddingDurationSeconds"));
            }
            return objectMapper.treeToValue(saved, RoundState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile leggere lo stato round salvato", e);
        }
    }

    private void clearCurrentState() {
        AuctionRoundStateEntity.deleteById(CURRENT_ROUND_STATE_ID);
    }

}
