package com.fantasta.service;

import com.fantasta.model.*;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MiniAuctionService {
    @Inject ParticipantService participants;
    @Inject RosterService rosters;
    @Inject DbService db;

    public MiniAuctionSessionEntity active() {
        return MiniAuctionSessionEntity.find("status", MiniAuctionSessionEntity.Status.ACTIVE).firstResult();
    }

    public List<MiniAuctionSlotEntity> slots(Long sessionId) {
        return MiniAuctionSlotEntity.list("session.id = ?1 order by id", sessionId);
    }

    public boolean isEligibleAcquisition(RosterEntity roster, String sourceCode, LocalDate date) {
        if (roster == null || sourceCode == null || date == null) return false;
        return RosterAcquisitionEntity.count("rosterEntryId = ?1 and player = ?2 and participant = ?3 "
                        + "and sessionCode = ?4 and repairMarket = true and acquiredAt >= ?5 and acquiredAt < ?6",
                roster.id, roster.player, roster.participant, sourceCode, date.atStartOfDay(), date.plusDays(1).atStartOfDay()) > 0;
    }

    public List<RosterEntity> eligibleCandidates(String sourceCode, LocalDate date) {
        if (sourceCode == null || date == null) return List.of();
        List<RosterEntity> all = RosterEntity.list("order by participant.name, player.role, player.name");
        return all.stream().filter(r -> isEligibleAcquisition(r, sourceCode, date))
                .filter(r -> r.player.role != Role.PORTIERE || eligibleWholeDoor(r.participant, sourceCode, date)).toList();
    }

    private boolean eligibleWholeDoor(ParticipantEntity participant, String sourceCode, LocalDate date) {
        List<RosterEntity> door = RosterEntity.list("participant = ?1 and player.role = ?2", participant, Role.PORTIERE);
        return door.size() == 3 && door.stream().allMatch(r -> isEligibleAcquisition(r, sourceCode, date));
    }

    @Transactional
    public MiniAuctionSessionEntity prepare(String label, String sourceCode, LocalDate date,
                                           List<Long> rosterIds) {
        if (label == null || label.isBlank() || sourceCode == null || sourceCode.isBlank() || date == null
                || rosterIds == null || rosterIds.isEmpty()) throw new IllegalArgumentException("Dati della mini asta incompleti");
        if (active() != null || MiniAuctionSessionEntity.count("status", MiniAuctionSessionEntity.Status.DRAFT) > 0)
            throw new IllegalStateException("Esiste già una mini asta da completare o annullare");
        if (new HashSet<>(rosterIds).size() != rosterIds.size()) throw new IllegalArgumentException("Cessioni duplicate");
        MiniAuctionSessionEntity session = new MiniAuctionSessionEntity();
        session.code = UUID.randomUUID().toString(); session.label = label.trim();
        session.sourceSessionCode = sourceCode; session.sourceDate = date; session.persist();
        Set<Long> processed = new HashSet<>();
        for (Long id : rosterIds) {
            if (processed.contains(id)) continue;
            RosterEntity entry = RosterEntity.findById(id);
            if (entry == null) throw new IllegalArgumentException("Acquisto non più in rosa: " + id);
            List<RosterEntity> entries = entry.player.role == Role.PORTIERE
                    ? RosterEntity.list("participant = ?1 and player.role = ?2", entry.participant, Role.PORTIERE)
                    : List.of(entry);
            if (entry.player.role == Role.PORTIERE && entries.size() != 3)
                throw new IllegalArgumentException("La porta deve comprendere tre portieri");
            if (entries.stream().anyMatch(r -> !isEligibleAcquisition(r, sourceCode, date)))
                throw new IllegalArgumentException("Seleziona solo acquisti registrati nella sessione di riparazione scelta; per la porta devono esserlo tutti e tre");
            MiniAuctionSlotEntity slot = new MiniAuctionSlotEntity();
            slot.session = session; slot.participant = entry.participant; slot.role = entry.player.role;
            slot.purchaseSize = entries.size();
            slot.releasedNames = entries.stream().map(r -> r.player.name).sorted().collect(Collectors.joining(", "));
            slot.refund = entries.stream().mapToDouble(r -> r.amount).sum();
            if (!Double.isFinite(slot.refund) || slot.refund < entries.size() || slot.refund != Math.floor(slot.refund))
                throw new IllegalArgumentException("Costo di acquisto non valido");
            slot.minimumBid = Math.max(entries.size(), slot.refund + 1);
            for (RosterEntity r : entries) {
                slot.sourceRosterIds.add(r.id); slot.releasedPlayerIds.add(r.player.id); processed.add(r.id);
            }
            slot.persist();
        }
        return session;
    }

    @Transactional
    public MiniAuctionSessionEntity activate(Long sessionId) {
        MiniAuctionSessionEntity session = requireSession(sessionId);
        if (session.status != MiniAuctionSessionEntity.Status.DRAFT) throw new IllegalStateException("Mini asta non in preparazione");
        List<MiniAuctionSlotEntity> all = slots(sessionId);
        if (all.isEmpty()) throw new IllegalArgumentException("Nessuna cessione preparata");
        // Tutte le cessioni sono verificate prima di modificare le rose.
        for (MiniAuctionSlotEntity slot : all) {
            List<RosterEntity> entries = sourceEntries(slot);
            if (entries.stream().anyMatch(r -> !isEligibleAcquisition(r, session.sourceSessionCode, session.sourceDate)))
                throw new IllegalStateException("Provenienza degli acquisti modificata: ricreare la preparazione");
            if (entries.stream().mapToDouble(r -> r.amount).sum() != slot.refund)
                throw new IllegalStateException("Costo modificato: ricreare la preparazione");
            if (slot.role == Role.PORTIERE && RosterEntity.count("participant = ?1 and player.role = ?2", slot.participant, slot.role) != 3)
                throw new IllegalStateException("Porta modificata: ricreare la preparazione");
        }
        for (Long pid : all.stream().map(s -> s.participant.id).distinct().toList()) {
            ParticipantEntity p = ParticipantEntity.findById(pid);
            List<MiniAuctionSlotEntity> mine = all.stream().filter(s -> s.participant.id.equals(pid)).toList();
            double refund = mine.stream().mapToDouble(s -> s.refund).sum();
            double minimums = mine.stream().mapToDouble(s -> s.minimumBid).sum();
            int ordinary = rosters.reservedCreditsForCurrentOpenSlots(participants.roleCounts(pid));
            if (participants.remainingCreditsById(pid, p.totalCredits) + refund < minimums + ordinary)
                throw new IllegalArgumentException(p.name + ": crediti insufficienti per coprire tutti i minimi e i posti già liberi");
        }
        for (MiniAuctionSlotEntity slot : all) {
            for (RosterEntity r : sourceEntries(slot)) {
                MarketMovementEntity movement = new MarketMovementEntity();
                movement.participant = r.participant; movement.player = r.player;
                movement.type = MarketMovementEntity.Type.RELEASE;
                movement.currentValue = r.amount; movement.previousRosterAmount = r.amount; movement.refundedAmount = r.amount;
                movement.playerNameSnapshot = r.player.name; movement.playerTeamSnapshot = r.player.team;
                movement.sourceParticipantSnapshot = r.participant.name;
                movement.sessionCode = session.code; movement.operationCode = "MINI_RELEASE:" + slot.id;
                movement.countedRelease = false; movement.creditsPreserved = false; movement.persist();
                r.player.assigned = false; r.delete();
            }
        }
        session.status = MiniAuctionSessionEntity.Status.ACTIVE;
        session.activatedAt = LocalDateTime.now();
        Panache.getEntityManager().flush();
        return session;
    }

    private List<RosterEntity> sourceEntries(MiniAuctionSlotEntity slot) {
        List<RosterEntity> entries = new ArrayList<>();
        for (int i = 0; i < slot.sourceRosterIds.size(); i++) {
            RosterEntity r = RosterEntity.findById(slot.sourceRosterIds.get(i));
            if (r == null || !r.participant.id.equals(slot.participant.id) || r.player.role != slot.role
                    || !r.player.id.equals(slot.releasedPlayerIds.get(i)))
                throw new IllegalStateException("Rosa modificata: ricreare la preparazione delle cessioni");
            entries.add(r);
        }
        return entries;
    }

    public MiniAuctionSessionEntity requireSession(Long id) {
        MiniAuctionSessionEntity session = id == null ? null : MiniAuctionSessionEntity.findById(id);
        if (session == null) throw new IllegalArgumentException("Mini asta non trovata");
        return session;
    }

    public Set<Long> eligible(Long sessionId, Role role) {
        return slots(sessionId).stream().filter(s -> !s.filled && s.role == role)
                .map(s -> s.participant.id).collect(Collectors.toSet());
    }

    public MiniAuctionSlotEntity requireSlot(Long sessionId, Long slotId, Long pid, Role role) {
        MiniAuctionSlotEntity slot = slotId == null ? null : MiniAuctionSlotEntity.findById(slotId);
        if (slot == null || !slot.session.id.equals(sessionId) || slot.session.status != MiniAuctionSessionEntity.Status.ACTIVE
                || !slot.participant.id.equals(pid) || slot.role != role || slot.filled)
            throw new IllegalArgumentException("Seleziona uno slot aperto tuo dello stesso ruolo");
        return slot;
    }

    public double maxBid(MiniAuctionSlotEntity chosen) {
        List<MiniAuctionSlotEntity> mine = slots(chosen.session.id).stream()
                .filter(s -> s.participant.id.equals(chosen.participant.id) && !s.filled).toList();
        int miniPlaces = mine.stream().mapToInt(s -> s.purchaseSize).sum();
        int ordinary = Math.max(0, rosters.reservedCreditsForCurrentOpenSlots(participants.roleCounts(chosen.participant.id)) - miniPlaces);
        double otherMinimums = mine.stream().filter(s -> !s.id.equals(chosen.id)).mapToDouble(s -> s.minimumBid).sum();
        return Math.max(0, participants.remainingCreditsById(chosen.participant.id, chosen.participant.totalCredits) - otherMinimums - ordinary);
    }

    public MiniAuctionSlotEntity validateBid(Long sessionId, Long slotId, Long pid, PlayerEntity player, Double amount) {
        if (player == null || !db.callable(player)) throw new IllegalArgumentException("Svincolato non disponibile");
        MiniAuctionSlotEntity slot = requireSlot(sessionId, slotId, pid, player.role);
        if (db.purchaseSize(player) != slot.purchaseSize) throw new IllegalArgumentException("Pacchetto incompatibile con lo slot");
        if (amount == null || !Double.isFinite(amount) || amount != Math.floor(amount) || amount < slot.minimumBid)
            throw new IllegalArgumentException("Offerta minima per questo slot: " + slot.minimumBid);
        if (amount > maxBid(slot)) throw new IllegalArgumentException("Offerta massima: " + maxBid(slot) + "; conserva i minimi degli altri slot");
        if (participants.roleCounts(pid).getOrDefault(player.role, 0) + slot.purchaseSize > rosters.max(player.role))
            throw new IllegalArgumentException("Quota del ruolo piena");
        return slot;
    }

    @Transactional
    public void fill(Long sessionId, Long slotId, Long pid, PlayerEntity player, Double amount, String roundId) {
        MiniAuctionSlotEntity slot = validateBid(sessionId, slotId, pid, player, amount);
        List<RosterEntity> entries = db.assignPurchasedPlayer(player, pid, amount);
        slot.filled = true; slot.acquiredPlayerId = player.id; slot.paidAmount = amount; slot.filledAt = LocalDateTime.now();
        for (RosterEntity entry : entries) {
            MarketMovementEntity movement = new MarketMovementEntity();
            movement.participant = entry.participant; movement.player = entry.player;
            movement.type = MarketMovementEntity.Type.MINI_PURCHASE;
            movement.currentValue = entry.amount; movement.previousRosterAmount = 0;
            movement.resultingRosterAmount = entry.amount; movement.sessionCode = slot.session.code;
            movement.operationCode = "MINI_PURCHASE:" + slot.id + ":" + roundId;
            movement.playerNameSnapshot = entry.player.name; movement.playerTeamSnapshot = entry.player.team;
            movement.destinationParticipantSnapshot = entry.participant.name; movement.countedRelease = false;
            movement.persist();
        }
    }

    @Transactional
    public void finish(Long id) {
        MiniAuctionSessionEntity session = requireSession(id);
        if (session.status == MiniAuctionSessionEntity.Status.DRAFT) session.status = MiniAuctionSessionEntity.Status.CANCELLED;
        else if (session.status == MiniAuctionSessionEntity.Status.ACTIVE) {
            if (slots(id).stream().anyMatch(s -> !s.filled)) throw new IllegalStateException("Riempire tutti gli slot prima di concludere");
            session.status = MiniAuctionSessionEntity.Status.CLOSED;
        } else throw new IllegalStateException("Mini asta già conclusa");
        session.closedAt = LocalDateTime.now();
    }
}
