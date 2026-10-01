package com.fantasta.service;

import com.fantasta.dto.MercatoConfigDto;
import com.fantasta.model.MercatoConfigEntity;
import com.fantasta.model.MercatoSvincolo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class MercatoService {

    public MercatoConfigDto getConfig() {
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        return cfg != null ? toDto(cfg) : null;
    }

    @Transactional
    public MercatoConfigDto updateConfig(MercatoConfigDto dto) {
        if (dto.numeroMercato < 1 || dto.numeroMercato > 3) {
            throw new IllegalArgumentException("Seleziona il mercato di riparazione 1, 2 o 3");
        }
        if (dto.durataPrenotazioneSecondi < 1) {
            throw new IllegalArgumentException("La durata della prenotazione deve essere almeno 1 secondo");
        }
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        if (cfg == null) {
            cfg = new MercatoConfigEntity();
        }
        boolean newSession = cfg.sessionCode == null || cfg.numeroMercato != dto.numeroMercato;
        cfg.attiva = dto.attiva;
        cfg.prenotazioneAbilitata = dto.prenotazioneAbilitata;
        cfg.durataPrenotazioneSecondi = dto.durataPrenotazioneSecondi;
        cfg.fineSessione = dto.fineSessione;
        cfg.numeroMercato = dto.numeroMercato;
        applyOfficialLimits(cfg);
        if (newSession) {
            cfg.sessionCode = UUID.randomUUID().toString();
            cfg.quotazioniAggiornate = false;
            cfg.partitiImportati = false;
            cfg.quotazioniAggiornateAt = null;
        }

        cfg.persist();
        return toDto(cfg);
    }

    private MercatoConfigDto toDto(MercatoConfigEntity e) {
        MercatoConfigDto dto = new MercatoConfigDto();
        dto.attiva = e.attiva;
        dto.prenotazioneAbilitata = e.prenotazioneAbilitata;
        dto.durataPrenotazioneSecondi = e.durataPrenotazioneSecondi;
        dto.fineSessione = e.fineSessione;
        dto.maxPortieri = e.maxPortieri;
        dto.maxDifensori = e.maxDifensori;
        dto.maxCentrocampisti = e.maxCentrocampisti;
        dto.maxAttaccanti = e.maxAttaccanti;
        dto.numeroMercato = e.numeroMercato;
        dto.sessionCode = e.sessionCode;
        dto.quotazioniAggiornate = e.quotazioniAggiornate;
        dto.partitiImportati = e.partitiImportati;
        dto.quotazioniAggiornateAt = e.quotazioniAggiornateAt;
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
        return cfg != null && cfg.attiva;
    }

    public MercatoConfigEntity requireConfiguredMarket() {
        MercatoConfigEntity cfg = MercatoConfigEntity.findAll().firstResult();
        if (cfg == null || cfg.numeroMercato < 1 || cfg.numeroMercato > 3 || cfg.sessionCode == null) {
            throw new IllegalStateException("Configura prima la sessione di mercato");
        }
        return cfg;
    }

    public void requireUpdatedQuotes() {
        MercatoConfigEntity cfg = requireImportedDepartures();
        if (!cfg.quotazioniAggiornate) {
            throw new IllegalStateException("Aggiorna e conferma prima le quotazioni della sessione");
        }
    }

    public MercatoConfigEntity requireImportedDepartures() {
        MercatoConfigEntity cfg = requireConfiguredMarket();
        if (!cfg.partitiImportati) {
            throw new IllegalStateException("Importa e conferma prima il file dei giocatori partiti");
        }
        return cfg;
    }

    @Transactional
    public void markQuotesUpdated() {
        MercatoConfigEntity cfg = requireImportedDepartures();
        cfg.quotazioniAggiornate = true;
        cfg.quotazioniAggiornateAt = LocalDateTime.now();
    }

    private void applyOfficialLimits(MercatoConfigEntity cfg) {
        if (cfg.numeroMercato == 2) {
            cfg.maxPortieri = 0;
            cfg.maxDifensori = 1;
            cfg.maxCentrocampisti = 1;
            cfg.maxAttaccanti = 1;
        } else {
            cfg.maxPortieri = 1; // un unico cambio dell'intero pacchetto
            cfg.maxDifensori = 2;
            cfg.maxCentrocampisti = 2;
            cfg.maxAttaccanti = 2;
        }
    }

}
