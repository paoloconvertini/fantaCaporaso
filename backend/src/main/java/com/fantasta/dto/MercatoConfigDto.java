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
    public LocalDateTime fineSessione;

    public int maxPortieri;
    public int maxDifensori;
    public int maxCentrocampisti;
    public int maxAttaccanti;
}