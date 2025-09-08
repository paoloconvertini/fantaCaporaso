package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "player")
public class PlayerEntity extends PanacheEntity {
    @Column(nullable = false)
    public String name;
    public String team;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;
    public Double valore;

    @Column(nullable = false)
    public boolean assigned = false;  // nuovo campo
}
