package com.fantasta.service;

import com.fantasta.dto.*;
import com.fantasta.model.*;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class AuctionHistoryService {
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(RoundState round) {
        if (round == null || round.winner == null || round.roundId == null
                || round.historyBids == null || round.historyBids.size() < 2
                || round.competitiveOriginRoundId == null
                || AuctionHistoryEntity.count("roundId", round.roundId) > 0) return;

        AuctionHistoryEntity history = new AuctionHistoryEntity();
        history.roundId = round.roundId;
        history.originRoundId = round.competitiveOriginRoundId;
        history.sessionCode = round.auctionSessionCode == null ? "legacy" : round.auctionSessionCode;
        PlayerEntity player = findPlayer(round.player, round.playerTeam);
        history.playerId = player == null ? null : player.id;
        history.playerName = round.player;
        history.playerTeam = round.playerTeam;
        history.playerRole = round.playerRole;
        history.playerValue = round.value;
        history.winnerParticipantId = round.winner.participantId;
        history.winnerName = round.winner.user;
        history.winningAmount = round.winner.amount;
        history.bidderCount = round.historyBids.size();
        history.closedAt = LocalDateTime.now();
        history.persist();

        round.historyBids.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> persistBid(history, entry));
    }

    public AuctionHistoryPageDto list(String query, String role, String sortBy, int page, int size,
                                      Long participantId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        StringBuilder where = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (!normalized.isEmpty()) {
            where.append(" and (lower(playerName) like :query or lower(winnerName) like :query)");
            params.put("query", "%" + normalized + "%");
        }
        if (role != null && !role.isBlank()) {
            where.append(" and playerRole = :role");
            params.put("role", role);
        }
        if (participantId != null) {
            where.append(" and id in (select bid.history.id from AuctionHistoryBidEntity bid where bid.participantId = :participantId)");
            params.put("participantId", participantId);
        }
        Sort sort = switch (sortBy == null ? "" : sortBy) {
            case "value" -> Sort.descending("playerValue").and("closedAt", Sort.Direction.Descending);
            case "price" -> Sort.descending("winningAmount").and("closedAt", Sort.Direction.Descending);
            case "bidders" -> Sort.descending("bidderCount").and("closedAt", Sort.Direction.Descending);
            default -> Sort.descending("closedAt");
        };
        PanacheQuery<AuctionHistoryEntity> result = AuctionHistoryEntity.find(where.toString(), sort, params);
        long total = result.count();
        List<AuctionHistoryDto> items = result.page(Page.of(safePage, safeSize)).list().stream()
                .map(this::toDto).toList();
        return new AuctionHistoryPageDto(items, total, safePage, safeSize);
    }

    private AuctionHistoryDto toDto(AuctionHistoryEntity history) {
        List<AuctionHistoryBidDto> bids = AuctionHistoryBidEntity.<AuctionHistoryBidEntity>list(
                        "history = ?1 order by amount desc", history).stream()
                .map(bid -> new AuctionHistoryBidDto(bid.participantName, bid.amount)).toList();
        return new AuctionHistoryDto(history.id, history.playerName, history.playerTeam, history.playerRole,
                history.playerValue, history.winnerName, history.winningAmount, history.bidderCount,
                history.closedAt, history.sessionCode, bids);
    }

    private void persistBid(AuctionHistoryEntity history, Map.Entry<String, Double> entry) {
        AuctionHistoryBidEntity bid = new AuctionHistoryBidEntity();
        bid.history = history;
        bid.participantKey = entry.getKey();
        try {
            bid.participantId = Long.valueOf(entry.getKey());
            ParticipantEntity participant = ParticipantEntity.findById(bid.participantId);
            bid.participantName = participant == null ? "??-" + entry.getKey() : participant.name;
        } catch (NumberFormatException exception) {
            bid.participantName = entry.getKey();
        }
        bid.amount = entry.getValue();
        bid.persist();
    }

    private PlayerEntity findPlayer(String name, String team) {
        if (name == null) return null;
        return PlayerEntity.find("name = ?1 and team = ?2", name, team).firstResult();
    }
}
