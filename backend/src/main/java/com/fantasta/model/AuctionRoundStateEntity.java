package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auction_round_state")
public class AuctionRoundStateEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Lob
    @Column(nullable = false)
    public String stateJson;

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();
}
