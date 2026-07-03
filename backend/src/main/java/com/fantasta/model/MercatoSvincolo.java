package com.fantasta.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mercato_svincolo")
public class MercatoSvincolo extends PanacheEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_id")
    public ParticipantEntity participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role; // stesso enum Role usato nei giocatori

    @Column(nullable = false)
    public int count = 0; // numero svincoli effettuati per questo ruolo

    @Column(nullable = false)
    public LocalDateTime lastUpdate = LocalDateTime.now();

    /** 🔹 Metodo utility per incrementare */
    public static void increment(ParticipantEntity participant, Role role) {
        MercatoSvincolo record = find("participant = ?1 and role = ?2", participant, role).firstResult();
        if (record == null) {
            record = new MercatoSvincolo();
            record.participant = participant;
            record.role = role;
            record.count = 1;
        } else {
            record.count++;
            record.lastUpdate = LocalDateTime.now();
        }
        record.persist();
    }

    /** 🔹 Restituisce il numero di svincoli già fatti */
    public static int getCount(ParticipantEntity participant, Role role) {
        MercatoSvincolo record = find("participant = ?1 and role = ?2", participant, role).firstResult();
        return record != null ? record.count : 0;
    }
}
