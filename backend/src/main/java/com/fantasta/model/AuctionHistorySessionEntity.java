package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "auction_history_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_auction_history_session_code", columnNames = "session_code"),
        indexes = @Index(name = "idx_auction_history_session_closed", columnList = "closed_at"))
public class AuctionHistorySessionEntity extends PanacheEntity {
    @Column(name = "session_code", nullable = false, length = 64) public String sessionCode;
    @Column(name = "label", nullable = false, length = 120) public String label;
    @Column(name = "closed_at", nullable = false) public LocalDateTime closedAt;
    @Column(name = "roster_snapshot_id", nullable = false) public Long rosterSnapshotId;
    @Column(name = "publish_status", nullable = false, length = 24) public String publishStatus;
    @Column(name = "publish_attempts", nullable = false) public Integer publishAttempts;
    @Column(name = "published_at") public LocalDateTime publishedAt;
    @Column(name = "publish_error", length = 1000) public String publishError;
}
