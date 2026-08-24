package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_reacquisition_restriction", uniqueConstraints =
        @UniqueConstraint(columnNames = {"player_id", "participant_id"}))
public class PlayerReacquisitionRestrictionEntity extends PanacheEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    public PlayerEntity player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    public ParticipantEntity participant;

    @Column(nullable = false)
    public double minimumBid;

    @Column(nullable = false)
    public double releaseValue;

    public String sessionCode;
    public LocalDateTime updatedAt = LocalDateTime.now();
}
