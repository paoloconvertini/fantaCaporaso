package com.fantasta.service;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ParticipantService {

    /** Somma importi in Roster per quel partecipante. */
    public int spentCreditsById(Long participantId) {
        Double sum = (Double) Panache.getEntityManager()
                .createQuery("select coalesce(sum(r.amount),0) from RosterEntity r where r.participant.id = ?1")
                .setParameter(1, participantId)
                .getSingleResult();
        return sum.intValue();
    }

    /** Residuo = totale - speso (mai negativo). */
    public int remainingCreditsById(Long participantId, int totalCredits) {
        int spent = spentCreditsById(participantId);
        int rem = totalCredits - spent;
        return Math.max(0, rem);
    }

    /** Conteggio giocatori per ruolo dalla RosterEntity. */
    public Map<Role, Integer> roleCounts(Long participantId) {
        var list = Panache.getEntityManager()
                .createQuery("select r.player.role, count(r) from RosterEntity r where r.participant.id = ?1 group by r.player.role", Object[].class)
                .setParameter(1, participantId)
                .getResultList();

        Map<Role, Integer> map = new EnumMap<>(Role.class);
        for (Object[] row : list) {
            Role role = (Role) row[0];
            Number n = (Number) row[1];
            map.put(role, n == null ? 0 : n.intValue());
        }
        // assicurati che ci siano tutte le chiavi
        for (Role r : Role.values()) map.putIfAbsent(r, 0);
        return map;
    }

    /** Versione string-key per DTOs: PORTIERE/DIFENSORE/... */
    public Map<String, Integer> roleCountsAsString(Long participantId) {
        Map<Role, Integer> byRole = roleCounts(participantId);
        Map<String, Integer> out = new HashMap<>();
        byRole.forEach((k, v) -> out.put(k.name(), v));
        return out;
    }
}
