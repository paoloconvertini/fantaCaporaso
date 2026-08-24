package com.fantasta.service;

import com.fantasta.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class MarketRestrictionService {

    @Transactional
    public void rememberCurrentOwner(PlayerEntity player, ParticipantEntity participant) {
        PlayerOwnerHistoryEntity.remember(player, participant);
    }

    @Transactional
    public void createRestrictionsAtRelease(PlayerEntity player, ParticipantEntity currentOwner,
                                            double releaseValue, String sessionCode) {
        PlayerOwnerHistoryEntity.remember(player, currentOwner);
        List<PlayerOwnerHistoryEntity> owners = PlayerOwnerHistoryEntity.list("player", player);
        for (PlayerOwnerHistoryEntity owner : owners) {
            PlayerReacquisitionRestrictionEntity restriction =
                    PlayerReacquisitionRestrictionEntity.find(
                            "player = ?1 and participant = ?2", player, owner.participant).firstResult();
            if (restriction == null) {
                restriction = new PlayerReacquisitionRestrictionEntity();
                restriction.player = player;
                restriction.participant = owner.participant;
            }
            restriction.releaseValue = releaseValue;
            restriction.minimumBid = releaseValue + 1D;
            restriction.sessionCode = sessionCode;
            restriction.updatedAt = LocalDateTime.now();
            restriction.persist();
        }
    }

    public double minimumBid(PlayerEntity player, ParticipantEntity participant) {
        PlayerReacquisitionRestrictionEntity restriction =
                PlayerReacquisitionRestrictionEntity.find(
                        "player = ?1 and participant = ?2", player, participant).firstResult();
        return restriction == null ? 1D : restriction.minimumBid;
    }

    public boolean hasRestriction(List<PlayerEntity> players, ParticipantEntity participant) {
        return players.stream().anyMatch(player ->
                PlayerReacquisitionRestrictionEntity.count(
                        "player = ?1 and participant = ?2", player, participant) > 0);
    }
}
