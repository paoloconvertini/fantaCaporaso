package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auction_history", uniqueConstraints = @UniqueConstraint(name = "uk_auction_history_round", columnNames = "round_id"),
        indexes = {
                @Index(name = "idx_auction_history_session", columnList = "session_code,closed_at"),
                @Index(name = "idx_auction_history_player", columnList = "player_name"),
                @Index(name = "idx_auction_history_value", columnList = "player_value")
        })
public class AuctionHistoryEntity extends PanacheEntity {
    @Column(name = "round_id", nullable = false, length = 64) public String roundId;
    @Column(name = "origin_round_id", length = 64) public String originRoundId;
    @Column(name = "session_code", nullable = false, length = 64) public String sessionCode;
    @Column(name = "player_id") public Long playerId;
    @Column(name = "player_name", nullable = false) public String playerName;
    @Column(name = "player_team") public String playerTeam;
    @Column(name = "player_role", length = 32) public String playerRole;
    @Column(name = "player_value") public Integer playerValue;
    @Column(name = "winner_participant_id") public Long winnerParticipantId;
    @Column(name = "winner_name", nullable = false) public String winnerName;
    @Column(name = "winning_amount", nullable = false) public Double winningAmount;
    @Column(name = "bidder_count", nullable = false) public Integer bidderCount;
    @Column(name = "closed_at", nullable = false) public LocalDateTime closedAt;
}
