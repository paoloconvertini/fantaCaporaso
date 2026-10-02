package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mini_auction_session")
public class MiniAuctionSessionEntity extends PanacheEntity {
    @Version public long version;
    @Column(nullable = false, unique = true) public String code;
    @Column(nullable = false) public String label;
    @Column(nullable = false) public String sourceSessionCode;
    @Column(nullable = false) public LocalDate sourceDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Status status = Status.DRAFT;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime activatedAt;
    public LocalDateTime closedAt;
    public enum Status { DRAFT, ACTIVE, CLOSED, CANCELLED }
}
