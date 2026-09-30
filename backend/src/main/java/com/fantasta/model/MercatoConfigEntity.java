package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "mercato_config")
public class MercatoConfigEntity extends PanacheEntity {

    public boolean attiva;
    public LocalDateTime fineSessione;

    public int maxPortieri;
    public int maxDifensori;
    public int maxCentrocampisti;
    public int maxAttaccanti;

    /** Numero regolamentare del mercato di riparazione: 1, 2 o 3. */
    public int numeroMercato;

    /** Cambia a ogni nuova sessione e separa conteggi e movimenti. */
    public String sessionCode;

    /** Gli svincoli restano bloccati finché le quotazioni non sono aggiornate. */
    public boolean quotazioniAggiornate;
    public boolean partitiImportati;
    public LocalDateTime quotazioniAggiornateAt;
}
