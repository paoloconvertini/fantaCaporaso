package com.fantasta.service;

import com.fantasta.dto.RosterMovementDto;
import com.fantasta.model.MarketMovementEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import com.fantasta.model.*;

import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;

@ApplicationScoped
public class RosterMovementService {
    public List<RosterMovementDto> list(String query, String type, Long participantId, boolean includeReverted) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return MarketMovementEntity.<MarketMovementEntity>list("order by createdAt desc, id desc").stream()
                .filter(row -> includeReverted || row.revertedAt == null)
                .filter(row -> normalized.isEmpty() || playerName(row).toLowerCase(Locale.ROOT).contains(normalized))
                .filter(row -> type == null || type.isBlank() || row.type.name().equalsIgnoreCase(type))
                .filter(row -> participantId == null
                        || row.participant.id.equals(participantId)
                        || row.destinationParticipant != null && row.destinationParticipant.id.equals(participantId))
                .map(this::toDto)
                .toList();
    }

    private RosterMovementDto toDto(MarketMovementEntity row) {
        RosterMovementDto dto = new RosterMovementDto();
        dto.id = row.id;
        dto.type = row.type.name();
        dto.playerName = playerName(row);
        dto.playerTeam = row.playerTeamSnapshot != null ? row.playerTeamSnapshot : row.player.team;
        dto.sourceParticipant = row.sourceParticipantSnapshot != null
                ? row.sourceParticipantSnapshot : row.participant.name;
        dto.destinationParticipant = row.destinationParticipantSnapshot != null
                ? row.destinationParticipantSnapshot
                : row.destinationParticipant == null ? null : row.destinationParticipant.name;
        dto.currentValue = row.currentValue;
        dto.previousRosterAmount = row.previousRosterAmount;
        dto.refundedAmount = row.refundedAmount == null ? 0D : row.refundedAmount;
        dto.sessionCode = row.sessionCode;
        dto.createdAt = row.createdAt;
        dto.revertedAt = row.revertedAt;
        dto.canRevert = row.revertedAt == null && !isMiniRelease(row) && !hasLaterMovement(row);
        return dto;
    }

    @Transactional
    public void revert(Long movementId) {
        MarketMovementEntity selected = MarketMovementEntity.findById(movementId);
        if (selected == null) throw new BadRequestException("Movimento non trovato");
        if (isMiniRelease(selected)) throw new BadRequestException("Le cessioni della mini asta sono definitive; non annullare movimenti singoli");
        if (!isPurchase(selected) && MiniAuctionSessionEntity.count("status", MiniAuctionSessionEntity.Status.ACTIVE) > 0)
            throw new BadRequestException("Concludere prima la mini asta");
        List<MarketMovementEntity> rows = selected.operationCode == null
                ? List.of(selected)
                : MarketMovementEntity.list("operationCode = ?1 order by id desc", selected.operationCode);
        if (rows.stream().anyMatch(row -> row.revertedAt != null)) {
            throw new BadRequestException("Movimento già annullato");
        }
        if (rows.stream().anyMatch(this::hasLaterMovement)) {
            throw new BadRequestException("Impossibile annullare: esistono movimenti successivi per uno dei calciatori");
        }

        for (MarketMovementEntity row : rows) validateRevert(row);
        for (MarketMovementEntity row : rows) applyRevert(row);
        LocalDateTime now = LocalDateTime.now();
        rows.forEach(row -> row.revertedAt = now);
    }

    private boolean isMiniRelease(MarketMovementEntity row) {
        return row.operationCode != null && row.operationCode.startsWith("MINI_RELEASE:");
    }

    private void validateRevert(MarketMovementEntity row) {
        RosterEntity current = RosterEntity.find("player", row.player).firstResult();
        if (isPurchase(row)) {
            RosterAcquisitionEntity acquisition = acquisition(row);
            if (current == null || !current.participant.id.equals(row.participant.id)
                    || acquisition == null || !current.id.equals(acquisition.rosterEntryId)
                    || !java.util.Objects.equals(current.amount, row.resultingRosterAmount))
                throw new BadRequestException(row.player.name + ": assegnazione modificata dopo l’acquisto");
        } else if (row.type == MarketMovementEntity.Type.EXCHANGE) {
            if (current == null || row.destinationParticipant == null
                    || !current.participant.id.equals(row.destinationParticipant.id)) {
                throw new BadRequestException(row.player.name + ": non appartiene più alla squadra di destinazione");
            }
        } else if (current != null) {
            throw new BadRequestException(row.player.name + ": è già assegnato a una rosa");
        }
    }

    private boolean isPurchase(MarketMovementEntity row) {
        return row.type == MarketMovementEntity.Type.PURCHASE || row.type == MarketMovementEntity.Type.MINI_PURCHASE;
    }

    private RosterAcquisitionEntity acquisition(MarketMovementEntity row) {
        return RosterAcquisitionEntity.find("player = ?1 and participant = ?2 and sessionCode = ?3 order by acquiredAt desc", row.player, row.participant, row.sessionCode).firstResult();
    }

    private void applyRevert(MarketMovementEntity row) {
        if (isPurchase(row)) {
            RosterAcquisitionEntity acquisition = acquisition(row);
            RosterEntity current = RosterEntity.findById(acquisition.rosterEntryId);
            current.delete(); row.player.assigned = false;
            if (acquisition.ownerHistoryCreated) PlayerOwnerHistoryEntity.delete(
                    "player = ?1 and participant = ?2", row.player, row.participant);
            if (row.auctionRoundId != null) {
                for (AuctionHistoryEntity history : AuctionHistoryEntity.<AuctionHistoryEntity>list("roundId", row.auctionRoundId)) {
                    AuctionHistoryBidEntity.delete("history", history); history.delete();
                }
            }
            acquisition.delete();
            if (row.type == MarketMovementEntity.Type.MINI_PURCHASE) {
                Long slotId = Long.valueOf(row.operationCode.split(":")[1]);
                MiniAuctionSlotEntity slot = MiniAuctionSlotEntity.findById(slotId);
                slot.filled = false; slot.acquiredPlayerId = null; slot.paidAmount = null; slot.filledAt = null;
                if (slot.session.status == MiniAuctionSessionEntity.Status.CLOSED) {
                    if (MiniAuctionSessionEntity.count("status in ?1", java.util.List.of(MiniAuctionSessionEntity.Status.ACTIVE, MiniAuctionSessionEntity.Status.DRAFT)) > 0)
                        throw new BadRequestException("Un’altra mini asta è già aperta");
                    slot.session.status = MiniAuctionSessionEntity.Status.ACTIVE; slot.session.closedAt = null;
                }
            }
            return;
        }
        if (row.type == MarketMovementEntity.Type.EXCHANGE) {
            RosterEntity current = RosterEntity.find("player", row.player).firstResult();
            if (Boolean.TRUE.equals(row.creditsPreserved)) {
                preserveResidualsDuringTransfer(current, row.participant, row.previousRosterAmount);
            } else {
                current.participant = row.participant;
                current.amount = row.previousRosterAmount;
            }
            return;
        }

        RosterEntity restored = new RosterEntity();
        restored.participant = row.participant;
        restored.player = row.player;
        restored.amount = row.previousRosterAmount;
        restored.persist();
        row.player.assigned = true;
        double refund = row.refundedAmount == null ? row.currentValue : row.refundedAmount;
        row.participant.totalCredits -= (int) Math.round(refund - row.previousRosterAmount);

        boolean counted = Boolean.TRUE.equals(row.countedRelease)
                || row.countedRelease == null && row.type == MarketMovementEntity.Type.RELEASE
                && row.player.role != Role.PORTIERE;
        if (counted) decrementRelease(row.participant, row.player.role, row.sessionCode);
        if (row.type == MarketMovementEntity.Type.RELEASE) {
            PlayerReacquisitionRestrictionEntity.delete(
                    "player = ?1 and sessionCode = ?2", row.player, row.sessionCode);
        }
    }

    private void preserveResidualsDuringTransfer(RosterEntity entry, ParticipantEntity target, double oldAmount) {
        ParticipantEntity current = entry.participant;
        int currentRemaining = remaining(current);
        int targetRemaining = remaining(target);
        entry.participant = target;
        entry.amount = oldAmount;
        current.totalCredits = currentRemaining + spent(current);
        target.totalCredits = targetRemaining + spent(target);
    }

    private int remaining(ParticipantEntity participant) {
        return (int) Math.round(participant.totalCredits - spent(participant));
    }

    private int spent(ParticipantEntity participant) {
        return (int) Math.round(RosterEntity.<RosterEntity>list("participant", participant).stream()
                .mapToDouble(row -> row.amount == null ? 0D : row.amount).sum());
    }

    private void decrementRelease(ParticipantEntity participant, Role role, String sessionCode) {
        MercatoSvincolo count = MercatoSvincolo.find(
                "participant = ?1 and role = ?2 and sessionCode = ?3", participant, role, sessionCode).firstResult();
        if (count == null) return;
        count.count = Math.max(0, count.count - 1);
    }

    private boolean hasLaterMovement(MarketMovementEntity row) {
        return MarketMovementEntity.count(
                "player = ?1 and revertedAt is null and createdAt > ?2", row.player, row.createdAt) > 0;
    }

    private String playerName(MarketMovementEntity row) {
        return row.playerNameSnapshot != null ? row.playerNameSnapshot : row.player.name;
    }
}
