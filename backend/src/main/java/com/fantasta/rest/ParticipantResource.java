package com.fantasta.rest;

import com.fantasta.dto.ParticipantDto;
import com.fantasta.dto.ParticipantSummaryDto;
import com.fantasta.model.ParticipantEntity;
import com.fantasta.service.ParticipantService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.NoCache;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/participant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParticipantResource {

    @Inject
    ParticipantService participantService;

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    private ParticipantDto toDto(ParticipantEntity e) {
        ParticipantDto dto = new ParticipantDto();
        dto.id = e.id;
        dto.name = e.name;
        dto.totalCredits = e.totalCredits;
        dto.spentCredits = participantService.spentCreditsById(e.id);
        dto.remainingCredits = participantService.remainingCreditsById(e.id, e.totalCredits);
        return dto;
    }

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "user"})
    public ParticipantDto me() {
        ParticipantEntity e = findCurrentParticipant();
        if (e == null) {
            String username = identity.getPrincipal().getName();
            throw new NotFoundException("Partecipante non trovato per utente " + username);
        }
        return toDto(e);
    }

    private ParticipantEntity findCurrentParticipant() {
        Object participantIdClaim = jwt.getClaim("participant_id");
        if (participantIdClaim != null) {
            Long participantId = Long.valueOf(participantIdClaim.toString());
            return ParticipantEntity.findById(participantId);
        }

        String username = identity.getPrincipal().getName();
        return ParticipantEntity.find("name", username).firstResult();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "user"})
    public ParticipantDto getById(@PathParam("id") Long id) {
        ParticipantEntity e = ParticipantEntity.findById(id);
        if (e == null) throw new NotFoundException("Partecipante non trovato");
        return toDto(e);
    }

    @GET
    @Path("/all")
    @RolesAllowed({"admin", "user"})
    public List<ParticipantDto> all() {
        return ParticipantEntity.<ParticipantEntity>listAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @NoCache
    @GET
    @Path("/summary")
    @RolesAllowed({"admin", "user"})
    public List<ParticipantSummaryDto> summary() {
        return ParticipantEntity.<ParticipantEntity>listAll().stream()
                .map(e -> {
                    var dto = new ParticipantSummaryDto();
                    dto.id = e.id;
                    dto.name = e.name;
                    dto.totalCredits = e.totalCredits;

                    dto.spentCredits = participantService.spentCreditsById(e.id);
                    dto.remainingCredits = Math.max(0, dto.totalCredits - dto.spentCredits);

                    dto.roleCounts = participantService.roleCountsAsString(e.id);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
