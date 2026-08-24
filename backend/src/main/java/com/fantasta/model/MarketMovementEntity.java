package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_movement")
public class MarketMovementEntity extends PanacheEntity {
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public ParticipantEntity participant;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.SET_NULL)
    public ParticipantEntity destinationParticipant;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public PlayerEntity player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Type type;

    @Column(nullable = false)
    public double currentValue;

    @Column(nullable = false)
    public double previousRosterAmount;

    public Double refundedAmount;

    public String playerNameSnapshot;
    public String playerTeamSnapshot;
    public String sourceParticipantSnapshot;
    public String destinationParticipantSnapshot;

    public String sessionCode;

    public String operationCode;
    public Double resultingRosterAmount;
    public Boolean countedRelease;
    public Boolean creditsPreserved;
    public LocalDateTime revertedAt;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public enum Type { RELEASE, DEPARTED, EXCHANGE }
}
