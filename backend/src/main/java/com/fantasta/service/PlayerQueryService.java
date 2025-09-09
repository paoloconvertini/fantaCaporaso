package com.fantasta.service;

import com.fantasta.dto.PlayerDto;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlayerQueryService {

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
