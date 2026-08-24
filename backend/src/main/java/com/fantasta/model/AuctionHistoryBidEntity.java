package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "auction_history_bid", uniqueConstraints = @UniqueConstraint(
        name = "uk_auction_history_bid_participant", columnNames = {"history_id", "participant_key"}),
        indexes = @Index(name = "idx_auction_history_bid_history", columnList = "history_id"))
public class AuctionHistoryBidEntity extends PanacheEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    public AuctionHistoryEntity history;
    @Column(name = "participant_id") public Long participantId;
    @Column(name = "participant_key", nullable = false, length = 64) public String participantKey;
    @Column(name = "participant_name", nullable = false) public String participantName;
    @Column(name = "amount", nullable = false) public Double amount;
}
