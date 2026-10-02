package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;

@Entity
@Table(name = "roster_acquisition")
public class RosterAcquisitionEntity extends PanacheEntity {
    @Column(nullable = false, unique = true) public Long rosterEntryId;
    @ManyToOne(optional = false) @OnDelete(action = OnDeleteAction.CASCADE) public PlayerEntity player;
    @ManyToOne(optional = false) @OnDelete(action = OnDeleteAction.CASCADE) public ParticipantEntity participant;
    @Column(nullable = false) public String sessionCode;
    @Column(nullable = false) public String purchaseGroupCode;
    @Column(nullable = false) public LocalDateTime acquiredAt = LocalDateTime.now();
    @Column(nullable = false) public double paidAmount;
    @Column(nullable = false) public boolean repairMarket;
    public boolean ownerHistoryCreated;
}
