package com.fantasta.rest;

import com.fantasta.dto.ParticipantRosterDto;
import com.fantasta.dto.RosterDto;
import com.fantasta.dto.RosterImportResult;
import com.fantasta.dto.SvincoloRequest;
import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.RosterEntity;
import com.fantasta.service.RosterService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/admin/rosters")
@Produces(MediaType.APPLICATION_JSON)
public class RosterResource {

    @Inject
    RosterService rosterService;

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    /**
     * Upload Excel → importa i roster
     * Accesso consentito solo agli admin
     */
    @POST
    @Path("/upload")
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("admin")
    public Response uploadExcel(@RestForm("file") InputStream file) {
        try {
            if (file == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "File mancante"))
                        .build();
            }

            RosterImportResult result = rosterService.importFromExcel(file);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/svincola")
    @RolesAllowed({"user", "admin"})
    public Response svincola(@QueryParam("participantId") Long participantId, SvincoloRequest req) {
        rosterService.svincola(participantId, req);
        return Response.ok().build();
    }


    @GET
    public List<RosterDto> getAllRosters() {
        return rosterService.getAllRosters();
    }

    @GET
    @Path("/mine")
    public List<RosterDto> getMyRoster(@QueryParam("participantId") Long participantId) {
        if (identity.hasRole("admin") && participantId != null) {
            return rosterService.getRosterByParticipant(participantId);
        }

        ParticipantEntity participant = findCurrentParticipant();
        if (participant == null) {
            String username = identity.getPrincipal().getName();
            throw new NotFoundException("Partecipante non trovato per utente " + username);
        }
        return rosterService.getRosterByParticipant(participant.id);
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
    @Path("/grouped")
    public List<ParticipantRosterDto> getAllRostersGrouped() {
        return rosterService.getAllRostersGrouped();
    }


}
