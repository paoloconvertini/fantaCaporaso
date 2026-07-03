package com.fantasta.service;

import com.fantasta.dto.MercatoConfigDto;
import com.fantasta.model.MercatoConfigEntity;
import com.fantasta.model.MercatoSvincolo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class MercatoService {

    public MercatoConfigDto getConfig() {
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        return cfg != null ? toDto(cfg) : null;
    }

    @Transactional
    public MercatoConfigDto updateConfig(MercatoConfigDto dto) {
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        if (cfg == null) {
            cfg = new MercatoConfigEntity();
        }
        cfg.attiva = dto.attiva;
        cfg.fineSessione = dto.fineSessione;
        cfg.maxPortieri = dto.maxPortieri;
        cfg.maxDifensori = dto.maxDifensori;
        cfg.maxCentrocampisti = dto.maxCentrocampisti;
        cfg.maxAttaccanti = dto.maxAttaccanti;

        cfg.persist();
        return toDto(cfg);
    }

    private MercatoConfigDto toDto(MercatoConfigEntity e) {
        MercatoConfigDto dto = new MercatoConfigDto();
        dto.attiva = e.attiva;
        dto.fineSessione = e.fineSessione;
        dto.maxPortieri = e.maxPortieri;
        dto.maxDifensori = e.maxDifensori;
        dto.maxCentrocampisti = e.maxCentrocampisti;
        dto.maxAttaccanti = e.maxAttaccanti;
        return dto;
    }

    public int getMaxByRole(String role) {
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        if (cfg == null) return 0;

        return switch (role.toUpperCase()) {
            case "PORTIERE" -> cfg.maxPortieri;
            case "DIFENSORE" -> cfg.maxDifensori;
            case "CENTROCAMPISTA" -> cfg.maxCentrocampisti;
            case "ATTACCANTE" -> cfg.maxAttaccanti;
            default -> 0;
        };
    }

    @Transactional
    public void resetSvincoli() {
        MercatoSvincolo.deleteAll();
    }


    public boolean isMercatoAttivo() {
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        if (cfg == null) return false;

        LocalDateTime now = LocalDateTime.now();

        return cfg.attiva && (cfg.fineSessione == null || now.isBefore(cfg.fineSessione));
    }

}
