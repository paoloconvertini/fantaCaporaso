package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "participant")
public class ParticipantEntity extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public int totalCredits;

    @OneToMany(mappedBy = "participant")
    public List<RosterEntity> roster;


}
