package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUserEntity extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false)
    public String passwordHash;

    @Column(nullable = false)
    public String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    public ParticipantEntity participant;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean mustChangePassword = false;
}
