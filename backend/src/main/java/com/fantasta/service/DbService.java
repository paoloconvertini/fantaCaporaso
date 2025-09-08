package com.fantasta.service;

import com.fantasta.model.*;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.*;

@ApplicationScoped
public class DbService {

    /**
     * Conta quanti giocatori sono disponibili (non assegnati e non skippati in questo giro).
     */
    private Long countAvail(Long giroId, Role role) {
        String q = "select count(p) from PlayerEntity p " +
                "where p.id not in (select r.player.id from RosterEntity r) " +
                "and p.id not in (select s.player.id from SkipEntity s where s.giro.id = ?1)";
        var query = Panache.getEntityManager().createQuery(role != null ? q + " and p.role = ?2" : q);
        query.setParameter(1, giroId);
        if (role != null) query.setParameter(2, role);
        return (Long) query.getSingleResult();
    }

    /**
     * Conta quanti sono stati skippati in questo giro
     */
    private Long countSkipped(Long giroId, Role role) {
        String q = "select count(s) from SkipEntity s where s.giro.id = ?1";
        if (role != null) q += " and s.player.role = ?2";

        var query = Panache.getEntityManager().createQuery(q);
        query.setParameter(1, giroId);
        if (role != null) query.setParameter(2, role);
        return (Long) query.getSingleResult();
    }

    /**
     * Ritorna mappa con disponibili e scartati divisi per ruolo
     */
    public Map<String, Object> remainingAndSkippedByRole(Long giroId) {
        Map<String, Object> result = new HashMap<>();
        result.put("remaining", Map.of(
                "PORTIERE", countAvail(giroId, Role.PORTIERE).intValue(),
                "DIFENSORE", countAvail(giroId, Role.DIFENSORE).intValue(),
                "CENTROCAMPISTA", countAvail(giroId, Role.CENTROCAMPISTA).intValue(),
                "ATTACCANTE", countAvail(giroId, Role.ATTACCANTE).intValue(),
                "TUTTI", countAvail(giroId, null).intValue()
        ));
        result.put("skipped", Map.of(
                "PORTIERE", countSkipped(giroId, Role.PORTIERE).intValue(),
                "DIFENSORE", countSkipped(giroId, Role.DIFENSORE).intValue(),
                "CENTROCAMPISTA", countSkipped(giroId, Role.CENTROCAMPISTA).intValue(),
                "ATTACCANTE", countSkipped(giroId, Role.ATTACCANTE).intValue(),
                "TUTTI", countSkipped(giroId, null).intValue()
        ));
        return result;
    }

    /**
     * Estrae un giocatore random filtrando già quelli assegnati o skippati
     */
    public PlayerEntity drawRandom(Long giroId, Role role) {
        String q = "select p from PlayerEntity p " +
                "where p.id not in (select r.player.id from RosterEntity r) " +
                "and p.id not in (select s.player.id from SkipEntity s where s.giro.id = ?1)" +
                (role != null ? " and p.role = ?2" : "") + " order by rand()";

        var query = Panache.getEntityManager().createQuery(q, PlayerEntity.class)
                .setParameter(1, giroId);
        if (role != null) query.setParameter(2, role);
        query.setMaxResults(1);

        var res = query.getResultList();
        return res.isEmpty() ? null : res.get(0);
    }

    /**
     * Marca un giocatore come assegnato a un partecipante
     */
    @Transactional
    public void markAssigned(String roundId, PlayerEntity player, Long participantId, Double amount) {
        ParticipantEntity participant = ParticipantEntity.findById(participantId);
        if (participant == null) {
            throw new IllegalArgumentException("Partecipante non trovato con id=" + participantId);
        }

        RosterEntity rosterEntry = new RosterEntity();
        rosterEntry.participant = participant;
        rosterEntry.player = player;
        rosterEntry.amount = amount;
        rosterEntry.persist();

    }

    /**
     * Giro management
     */
    @Transactional
    public GiroEntity ensureCurrentGiro() {
        GiroEntity g = GiroEntity.find("endedAt is null").firstResult();
        if (g == null) {
            g = new GiroEntity();
            g.persist();
        }
        return g;
    }

    /**
     * Reset: chiude il giro attuale e ne apre uno nuovo.
     * Nel nuovo giro, i giocatori rimasti = solo quelli scartati nel giro precedente.
     */
    @Transactional
    public GiroEntity resetGiro() {
        GiroEntity g = GiroEntity.find("endedAt is null").firstResult();
        if (g != null) {
            g.endedAt = java.time.Instant.now();
            g.persist();

            // Recupero scartati dal giro precedente
            List<SkipEntity> skipped = SkipEntity.find("giro", g).list();

            // Nuovo giro
            GiroEntity nuovo = new GiroEntity();
            nuovo.persist();

            // Riporta gli scartati nel nuovo giro
            for (SkipEntity s : skipped) {
                SkipEntity ns = new SkipEntity();
                ns.player = s.player;
                ns.giro = nuovo;
                ns.persist();
            }

            return nuovo;
        }

        // Se non c'era nessun giro attivo, ne creo uno vuoto
        GiroEntity nuovo = new GiroEntity();
        nuovo.persist();
        return nuovo;
    }


    @Transactional
    public void skip(Long giroId, PlayerEntity p) {
        GiroEntity g = GiroEntity.findById(giroId);
        if (g == null) return;
        var s = new SkipEntity();
        s.player = p;
        s.giro = g;
        s.persist();
    }

    public PlayerEntity findByNameTeam(String name, String team) {
        return PlayerEntity.find(
                "lower(name)=?1 and lower(team)=?2",
                name.toLowerCase(),
                (team == null ? "" : team).toLowerCase()
        ).firstResult();
    }
}

