package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "player_target", uniqueConstraints = @UniqueConstraint(name = "player_target_account_player_unique", columnNames = {"account_id", "player_id"}))
public class PlayerTargetEntity extends PanacheEntity {
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(optional = false) @JoinColumn(name = "account_id")
    public AppUserEntity account;
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(optional = false) @JoinColumn(name = "player_id")
    public PlayerEntity player;
}
