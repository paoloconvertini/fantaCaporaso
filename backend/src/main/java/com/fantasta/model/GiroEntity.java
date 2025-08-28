package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.*;

@Entity
@Table(name = "giro")
public class GiroEntity extends PanacheEntity {
    public Instant startedAt = Instant.now();
    public Instant endedAt;
}
