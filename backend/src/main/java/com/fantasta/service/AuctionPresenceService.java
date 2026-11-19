package com.fantasta.service;

import com.fantasta.model.*;
import com.fantasta.ws.ChatSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class AuctionPresenceService {
    @Inject ChatSocket chat;
    @Inject MiniAuctionService miniAuctions;
    @Inject RosterService rosters;
    @Inject DbService db;
    @Inject AuctionService auction;

    @Transactional
    public Presence status() {
        return status(MercatoConfigEntity.findAll().firstResult());
    }

    private Presence status(MercatoConfigEntity market) {
        if (market == null || !market.attiva || miniAuctions.active() != null ||
                (market.fineSessione != null && !market.fineSessione.isAfter(LocalDateTime.now()))) {
            return new Presence(false, List.of());
        }
        Set<Long> online = chat.onlineTeams();
        List<Team> teams = ParticipantEntity.<ParticipantEntity>list("order by name").stream()
                .filter(team -> rosters.reservedCreditsForCurrentOpenSlots(rosters.roleCounts(team.id)) > 0)
                .map(team -> new Team(team.id, team.name, online.contains(team.id)))
                .toList();
        return new Presence(Boolean.TRUE.equals(market.presenzeRichieste), teams);
    }

    @Transactional
    public PlayerEntity draw(Role role) {
        MercatoConfigEntity market = requireReady();
        PlayerEntity player = db.drawRandom(db.ensureCurrentGiro().id, role);
        if (market != null && player != null) market.presenzeRichieste = false;
        return player;
    }

    @Transactional
    public PlayerEntity select(Long playerId) {
        MercatoConfigEntity market = requireReady();
        PlayerEntity player = auction.selectCalledPlayer(playerId);
        if (market != null) market.presenzeRichieste = false;
        return player;
    }

    private MercatoConfigEntity requireReady() {
        MercatoConfigEntity market = MercatoConfigEntity.findAll().withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (market == null || !Boolean.TRUE.equals(market.presenzeRichieste)) return null;
        Presence presence = status(market);
        if (!presence.blocking()) return null;
        List<String> missing = presence.teams().stream().filter(team -> !team.online()).map(Team::name).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Squadre non collegate: " + String.join(", ", missing));
        }
        return market;
    }

    public record Team(Long id, String name, boolean online) {}
    public record Presence(boolean blocking, List<Team> teams) {}
}
