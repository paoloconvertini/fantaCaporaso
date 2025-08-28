package com.fantasta.rest;

import com.fantasta.dto.ParticipantDto;
import com.fantasta.dto.ParticipantSummaryDto;
import com.fantasta.model.ParticipantEntity;
import com.fantasta.service.ParticipantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/participant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParticipantResource {

    @Inject
    ParticipantService participantService;

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
    @Path("/{id}")
    public ParticipantDto getById(@PathParam("id") Long id) {
        ParticipantEntity e = ParticipantEntity.findById(id);
        if (e == null) throw new NotFoundException("Partecipante non trovato");
        return toDto(e);
    }

    @GET
    @Path("/all")
    public List<ParticipantDto> all() {
        return ParticipantEntity.<ParticipantEntity>listAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/summary")
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
                .collect(java.util.stream.Collectors.toList());
    }

}
