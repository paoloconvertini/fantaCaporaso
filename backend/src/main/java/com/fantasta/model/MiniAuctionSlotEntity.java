package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mini_auction_slot")
public class MiniAuctionSlotEntity extends PanacheEntity {
    @Version public long version;
    @ManyToOne(optional = false) public MiniAuctionSessionEntity session;
    @ManyToOne(optional = false) public ParticipantEntity participant;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Role role;
    @Column(nullable = false) public String releasedNames;
    public double refund;
    public double minimumBid;
    public int purchaseSize;
    @ElementCollection @CollectionTable(name = "mini_auction_slot_roster", joinColumns = @JoinColumn(name = "slot_id"))
    @OrderColumn(name = "position") @Column(name = "roster_id") public List<Long> sourceRosterIds = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "mini_auction_slot_player", joinColumns = @JoinColumn(name = "slot_id"))
    @OrderColumn(name = "position") @Column(name = "player_id") public List<Long> releasedPlayerIds = new ArrayList<>();
    public boolean filled;
    public Long acquiredPlayerId;
    public Double paidAmount;
    public LocalDateTime filledAt;
}
