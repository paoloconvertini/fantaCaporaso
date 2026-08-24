package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_owner_history", uniqueConstraints =
        @UniqueConstraint(columnNames = {"player_id", "participant_id"}))
public class PlayerOwnerHistoryEntity extends PanacheEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    public PlayerEntity player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    public ParticipantEntity participant;

    @Column(nullable = false)
    public LocalDateTime firstOwnedAt = LocalDateTime.now();

    public static void remember(PlayerEntity player, ParticipantEntity participant) {
        if (player == null || participant == null) return;
        if (count("player = ?1 and participant = ?2", player, participant) > 0) return;
        PlayerOwnerHistoryEntity history = new PlayerOwnerHistoryEntity();
        history.player = player;
        history.participant = participant;
        history.persist();
    }
}
