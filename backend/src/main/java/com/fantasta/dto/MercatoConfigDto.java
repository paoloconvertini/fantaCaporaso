package com.fantasta.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.fantasta.model.MercatoConfigEntity} entity
 */
public class MercatoConfigDto {
    public boolean attiva;
    public boolean prenotazioneAbilitata;
    public int durataPrenotazioneSecondi = 15;
    public LocalDateTime fineSessione;

    public int maxPortieri;
    public int maxDifensori;
    public int maxCentrocampisti;
    public int maxAttaccanti;
    public int numeroMercato;
    public String sessionCode;
    public boolean quotazioniAggiornate;
    public boolean partitiImportati;
    public LocalDateTime quotazioniAggiornateAt;
}
