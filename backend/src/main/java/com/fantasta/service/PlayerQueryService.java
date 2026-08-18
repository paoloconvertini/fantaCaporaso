package com.fantasta.service;

import com.fantasta.dto.AdminPlayerDto;
import com.fantasta.dto.AdminEligibleParticipantDto;
import com.fantasta.dto.PlayerDto;
import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlayerQueryService {

    @Inject
    DbService dbService;

    @Inject
    ParticipantService participantService;

    @Inject
    RosterService rosterService;

    public List<PlayerDto> getFreePlayers(String role) {
        if (role != null) {
            Role roleEnum;
            try {
                roleEnum = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Ruolo non valido: " + role);
            }
            return PlayerEntity.find(
                            "assigned = false and active = true and role = ?1 order by valore desc", roleEnum
                    ).stream()
                    .map(p -> toDto((PlayerEntity) p))
                    .collect(Collectors.toList());
        } else {
            return PlayerEntity.find(
                            "assigned = false and active = true order by valore desc"
                    ).stream()
                    .map(p -> toDto((PlayerEntity) p))
                    .collect(Collectors.toList());
        }
    }

    public List<AdminPlayerDto> searchAdminPlayers(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return List.of();
        }
        String pattern = "%" + normalized + "%";
        return PlayerEntity.<PlayerEntity>find(
                        "active = true and (lower(name) like ?1 or lower(team) like ?1) order by name", pattern)
                .page(0, 50)
                .list().stream()
                .map(this::toAdminDto)
                .toList();
    }

    public List<AdminEligibleParticipantDto> eligibleParticipants(Long playerId) {
        PlayerEntity player = PlayerEntity.findById(playerId);
        if (player == null || !player.active) {
            throw new NotFoundException("Giocatore non trovato o non attivo");
        }

        RosterEntity currentEntry = RosterEntity.find("player", player).firstResult();
        Long currentOwnerId = currentEntry == null ? null : currentEntry.participant.id;
        int purchaseSize = currentEntry == null
                ? dbService.purchaseSize(player)
                : assignedPurchaseSize(player, currentEntry.participant);

        return ParticipantEntity.<ParticipantEntity>list("order by name").stream()
                .filter(participant -> participant.id.equals(currentOwnerId)
                        || hasCapacity(participant, player.role, purchaseSize))
                .map(participant -> new AdminEligibleParticipantDto(participant.id, participant.name))
                .toList();
    }

    private int assignedPurchaseSize(PlayerEntity player, ParticipantEntity owner) {
        if (player.role != Role.PORTIERE) return 1;
        return Math.toIntExact(RosterEntity.count(
                "participant = ?1 and player.role = ?2 and lower(player.team) = ?3",
                owner, Role.PORTIERE, player.team.toLowerCase(Locale.ROOT)));
    }

    private boolean hasCapacity(ParticipantEntity participant, Role role, int purchaseSize) {
        int current = participantService.roleCounts(participant.id).getOrDefault(role, 0);
        return current + purchaseSize <= rosterService.max(role);
    }

    private AdminPlayerDto toAdminDto(PlayerEntity player) {
        AdminPlayerDto dto = new AdminPlayerDto();
        dto.id = player.id;
        dto.name = player.name;
        dto.team = player.team;
        dto.role = player.role.toString();
        dto.valore = player.valore;
        RosterEntity roster = RosterEntity.find("player", player).firstResult();
        dto.assigned = roster != null;
        if (roster != null) {
            dto.ownerParticipantId = roster.participant.id;
            dto.ownerParticipantName = roster.participant.name;
            dto.amount = player.role == Role.PORTIERE
                    ? RosterEntity.<RosterEntity>list(
                            "participant = ?1 and player.role = ?2 and lower(player.team) = ?3",
                            roster.participant, Role.PORTIERE, player.team.toLowerCase(Locale.ROOT))
                            .stream().mapToDouble(row -> row.amount).sum()
                    : roster.amount;
        }
        return dto;
    }

    private PlayerDto toDto(PlayerEntity p) {
        return new PlayerDto(
                p.id,
                p.name,
                p.team,
                p.role.toString(),
                p.valore
        );
    }
}
