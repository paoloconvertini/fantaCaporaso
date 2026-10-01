package com.fantasta.service;

import com.fantasta.model.*;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.util.*;

@ApplicationScoped
public class PlayerTargetService {
    @Inject SecurityIdentity identity;
    @Inject ParticipantService participants;
    @Inject RosterService rosters;
    @Inject DbService db;
    @Inject MercatoService market;
    @Inject MarketRestrictionService restrictions;

    private AppUserEntity account() {
        AppUserEntity account = AppUserEntity.find("username", identity.getPrincipal().getName()).firstResult();
        if (account == null || !account.enabled || !"observer".equals(account.role)) {
            throw new ForbiddenException("Funzione riservata agli osservatori");
        }
        if (account.participant == null) throw new ForbiddenException("Associa una squadra per usare gli obiettivi");
        return account;
    }

    @Transactional
    public List<Long> list() {
        Set<Long> ids = new HashSet<>();
        for (PlayerTargetEntity target : PlayerTargetEntity.<PlayerTargetEntity>list("account", account())) {
            ids.add(target.player.id);
            if (target.player.role == Role.PORTIERE) {
                PlayerEntity.<PlayerEntity>list("role = ?1 and lower(team) = ?2", Role.PORTIERE,
                        target.player.team.toLowerCase(Locale.ROOT)).forEach(player -> ids.add(player.id));
            }
        }
        return ids.stream().sorted().toList();
    }

    @Transactional
    public void set(Long playerId, boolean selected) {
        AppUserEntity account = account();
        PlayerEntity player = PlayerEntity.findById(playerId);
        if (player == null) throw new NotFoundException("Calciatore non trovato");
        List<PlayerTargetEntity> existing = player.role == Role.PORTIERE
                ? PlayerTargetEntity.list("account = ?1 and player.role = ?2 and lower(player.team) = ?3",
                        account, Role.PORTIERE, player.team.toLowerCase(Locale.ROOT))
                : PlayerTargetEntity.list("account = ?1 and player.id = ?2", account, playerId);
        if (!selected) { existing.forEach(PlayerTargetEntity::delete); return; }
        PlayerTargetEntity target = existing.isEmpty() ? null : existing.get(0);
        if (player == null || !player.active || RosterEntity.count("player", player) > 0) {
            throw new BadRequestException("Scegli un calciatore attivo e svincolato");
        }
        if (target == null) {
            target = new PlayerTargetEntity(); target.account = account; target.player = player; target.persist();
        }
    }

    public record Target(Long id, String name, String team, double value, double minimumBid, boolean affordable) {}
    public record Opponent(String name, int credits, int roleSlots, int totalSlots, int capacity) {}
    public record RoleAnalysis(String role, int roleSlots, int totalSlots, int maximumBid, int purchaseSize,
                               List<Target> targets, List<Opponent> opponents, int blockers, Target reachable) {}
    public record Analysis(String team, int credits, String generatedAt, List<RoleAnalysis> roles, List<Target> unavailable) {}

    @Transactional
    public Analysis analyze() {
        AppUserEntity account = account(); ParticipantEntity own = account.participant;
        Map<Role, Integer> counts = participants.roleCounts(own.id);
        int credits = participants.remainingCreditsById(own.id, own.totalCredits);
        int totalSlots = rosters.reservedCreditsForCurrentOpenSlots(counts);
        List<PlayerTargetEntity> targets = PlayerTargetEntity.list("account", account);
        List<ParticipantEntity> others = ParticipantEntity.listAll();
        Map<Long, Map<Role, Integer>> otherCounts = new HashMap<>();
        Map<Long, Integer> otherCredits = new HashMap<>();
        for (ParticipantEntity other : others) {
            if (other.id.equals(own.id)) continue;
            otherCounts.put(other.id, participants.roleCounts(other.id));
            otherCredits.put(other.id, participants.remainingCreditsById(other.id, other.totalCredits));
        }
        List<RoleAnalysis> analyses = new ArrayList<>(); List<Target> unavailable = new ArrayList<>();
        for (Role role : Role.values()) {
            List<Target> available = new ArrayList<>(); Set<String> doors = new HashSet<>();
            int size = role == Role.PORTIERE ? 3 : 1;
            int slots = Math.max(0, rosters.max(role) - counts.getOrDefault(role, 0));
            if (role == Role.PORTIERE && counts.getOrDefault(role, 0) > 0) slots = 0;
            int maximum = slots < size ? 0 : rosters.maxBid(own.id, own.totalCredits, size);
            for (PlayerTargetEntity marked : targets) {
                PlayerEntity player = marked.player;
                if (player.role != role) continue;
                if (!player.active || RosterEntity.count("player", player) > 0) {
                    unavailable.add(new Target(player.id, player.name, player.team, value(player), 0, false)); continue;
                }
                double value = value(player); double minimum = restrictions.minimumBid(player, own);
                String name = player.name;
                if (role == Role.PORTIERE) {
                    if (!doors.add(player.team.toLowerCase(Locale.ROOT))) continue;
                    List<PlayerEntity> door;
                    try { door = db.goalkeeperPackage(player); }
                    catch (IllegalStateException ex) { unavailable.add(new Target(player.id, name, player.team, value, 0, false)); continue; }
                    if (door.size() != 3) { unavailable.add(new Target(player.id, name, player.team, value, 0, false)); continue; }
                    value = door.stream().mapToDouble(this::value).sum(); name = "Porta " + player.team;
                    minimum = market.isMercatoAttivo() ? value : 3;
                    if (restrictions.hasRestriction(door, own)) minimum += 1;
                }
                available.add(new Target(player.id, name, player.team, value, minimum, maximum >= minimum));
            }
            available.sort(Comparator.comparingDouble(Target::value).reversed().thenComparing(Target::name).thenComparing(Target::id));
            List<Opponent> opponents = new ArrayList<>(); int blockers = 0;
            for (ParticipantEntity other : others) {
                if (other.id.equals(own.id)) continue;
                Map<Role, Integer> otherCount = otherCounts.get(other.id);
                int otherSlots = Math.max(0, rosters.max(role) - otherCount.getOrDefault(role, 0));
                if (role == Role.PORTIERE && otherCount.getOrDefault(role, 0) > 0) otherSlots = 0;
                int allSlots = rosters.reservedCreditsForCurrentOpenSlots(otherCount);
                int capacity = TargetCompetitionCalculator.capacity(otherCredits.get(other.id), otherSlots, allSlots, size, maximum);
                if (capacity > 0) {
                    blockers += capacity;
                    opponents.add(new Opponent(other.name, otherCredits.get(other.id), otherSlots, allSlots, capacity));
                }
            }
            opponents.sort(Comparator.comparingInt(Opponent::capacity).reversed().thenComparing(Opponent::name));
            Target reachable = blockers < available.size() && available.get(blockers).affordable() ? available.get(blockers) : null;
            analyses.add(new RoleAnalysis(role.name(), slots, totalSlots, maximum, size, available, opponents, blockers, reachable));
        }
        return new Analysis(own.name, credits, java.time.Instant.now().toString(), analyses, unavailable);
    }

    private double value(PlayerEntity player) { return player.valore == null ? 0 : player.valore; }
}
