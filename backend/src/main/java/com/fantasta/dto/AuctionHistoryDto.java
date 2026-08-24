package com.fantasta.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionHistoryDto(Long id, String playerName, String playerTeam, String playerRole,
                                Integer playerValue, String winnerName, double winningAmount,
                                int bidderCount, LocalDateTime closedAt, String sessionCode,
                                List<AuctionHistoryBidDto> bids) {}
