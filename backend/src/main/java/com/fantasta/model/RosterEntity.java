package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "rosters")
public class RosterEntity extends PanacheEntity {

    @ManyToOne(optional = false)
    public ParticipantEntity participant;

    @ManyToOne(optional = false)
    public PlayerEntity player;

    @Column(nullable = false)
    public Double amount; // prezzo pagato
}
