package com.fantasta.model;



import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "roster_history")
public class RosterHistoryEntity extends PanacheEntity {

    @Column(nullable = false)
    public Long sessionId;

    @ManyToOne
    @JoinColumn(name = "participant_id", nullable = false)
    public ParticipantEntity participant;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    public PlayerEntity player;

    @Column(nullable = false)
    public Double amount;
}

