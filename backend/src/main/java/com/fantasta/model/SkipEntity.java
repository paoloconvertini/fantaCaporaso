package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.*;

@Entity
@Table(name = "skip_entry")
public class SkipEntity extends PanacheEntity {
    @ManyToOne(optional = false)
    public PlayerEntity player;
    @ManyToOne(optional = false)
    public GiroEntity giro;
    public Instant createdAt = Instant.now();
}
