package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "giro_pick")
public class GiroPickEntity extends PanacheEntity {

    @ManyToOne(optional = false)
    public GiroEntity giro;

    @ManyToOne(optional = false)
    public PlayerEntity player;

    public Instant createdAt = Instant.now();
}
