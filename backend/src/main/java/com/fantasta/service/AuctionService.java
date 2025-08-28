package com.fantasta.service;

import com.fantasta.model.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.*;

@ApplicationScoped
public class AuctionService {
    private RoundState state = null;
    private final Random rnd = new SecureRandom();
    private String tieBreak = "NONE";

    public synchronized RoundState start(String player, String team, String role, Integer duration, String tieBreak) {
        state = new RoundState();
        state.roundId = java.util.UUID.randomUUID().toString();
        state.player = player;
        state.playerTeam = team;
        state.playerRole = role;
        state.closed = false;
        state.winner = null;
        this.tieBreak = (tieBreak == null || tieBreak.isBlank()) ? "NONE" : tieBreak;
        state.durationSeconds = duration;
        state.endEpochMillis = (duration != null && duration > 0) ? (System.currentTimeMillis() + duration * 1000L) : null;
        state.allowedUsers = null;
        state.tieUsers = null;
        return state;
    }

    public synchronized RoundState get() {
        return state;
    }

    public synchronized RoundState bid(Long id, int amount) {
        if (state == null || state.closed)
            throw new IllegalStateException("Round non attivo");
        if (id == null || amount <= 0)
            throw new IllegalArgumentException("Dati non validi");

        ParticipantEntity p = ParticipantEntity.findById(id);
        if (p == null) throw new IllegalArgumentException("Partecipante non trovato");

        // 🔹 uso il nome come chiave della mappa
        state.bids.put(p.name, amount);

        return state;
    }



    public synchronized RoundState close() {
        if (state == null) throw new IllegalStateException("Nessun round");
        if (state.closed) return state;
        state.closed = true;

        int max = state.bids.values().stream()
                .mapToInt(i -> i)
                .max()
                .orElse(0);

        // raccogli tutti quelli col massimo
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (var e : state.bids.entrySet()) {
            if (e.getValue() == max) top.add(e);
        }

        state.tieUsers = null;
        if (top.isEmpty()) {
            state.winner = null;
        } else if (top.size() == 1 || "FIRST".equalsIgnoreCase(tieBreak)) {
            var e = top.get(0);
            ParticipantEntity p = ParticipantEntity.find("lower(name)=?1", e.getKey().toLowerCase()).firstResult();
            state.winner = new Winner(p != null ? p.id : null, e.getKey(), e.getValue());
        } else if ("RANDOM".equalsIgnoreCase(tieBreak)) {
            var e = top.get(rnd.nextInt(top.size()));
            ParticipantEntity p = ParticipantEntity.find("lower(name)=?1", e.getKey().toLowerCase()).firstResult();
            state.winner = new Winner(p != null ? p.id : null, e.getKey(), e.getValue());
        } else {
            // qui: converti i nomi in id (se non trovato → null)
            state.winner = null;
            state.tieUsers = top.stream()
                    .map(e -> {
                        ParticipantEntity p = ParticipantEntity.find("lower(name)=?1", e.getKey().toLowerCase()).firstResult();
                        return p != null ? p.id : null;
                    })
                    .filter(Objects::nonNull) // scarta eventuali null
                    .toList();
        }

        return state;
    }

    public synchronized void reset() {
        state = null;
    }
}
