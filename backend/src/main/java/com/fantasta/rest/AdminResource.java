package com.fantasta.rest;

import com.fantasta.dto.CreateUserRequest;
import com.fantasta.model.RoundState;
import com.fantasta.dto.AdminAssignmentDto;
import com.fantasta.service.AuctionService;
import com.fantasta.service.AuctionHistoryService;
import com.fantasta.service.AppUserService;
import com.fantasta.util.ParticipantsLoader;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    ParticipantsLoader participantsLoader;

    @Inject
    AppUserService appUserService;

    @Inject
    AuctionService auctionService;

    @Inject
    AuctionHistoryService auctionHistoryService;

    /**
     * Inserisce i partecipanti iniziali dal classpath
     * Accesso riservato agli utenti con ruolo "admin"
     */
    @POST
    @Path("/seed-participants")
    @Transactional
    @RolesAllowed("admin")
    public Response seed() {
        try {
            int def = Integer.parseInt(System.getProperty("app.credits.total",
                    System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));
            int n = participantsLoader.loadFromClasspath(def);
            return Response.ok(java.util.Map.of("added", n)).build();
        } catch (Exception e) {
            Log.error("Failed to seed participants", e);
            return Response.status(500)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/users")
    @RolesAllowed("admin")
    public Response createUser(CreateUserRequest request) {
        appUserService.createUser(request);
        return Response.status(Response.Status.CREATED)
                .entity(java.util.Map.of("created", true))
                .build();
    }

    @GET
    @Path("/users")
    @RolesAllowed("admin")
    public Response users() {
        return Response.ok(appUserService.listUsers()).build();
    }
    @PUT
    @Path("/assignments/{playerId}")
    @RolesAllowed("admin")
    public Response adminAssign(@PathParam("playerId") Long playerId, AdminAssignmentDto dto) {
        if (dto == null) throw new BadRequestException("Dati mancanti");
        RoundState round;
        try {
            round = auctionService.adminAssign(playerId, dto.participantId, dto.amount);
        } catch (IllegalArgumentException error) {
            throw new BadRequestException(error.getMessage());
        } catch (IllegalStateException error) {
            throw new WebApplicationException(error.getMessage(), 409);
        }
        if (round != null) {
            try {
                auctionHistoryService.record(round);
            } catch (Exception error) {
                Log.warn("Assegnazione completata, salvataggio storico fallito", error);
            }
        }
        return Response.ok(java.util.Map.of("message", "Assegnazione aggiornata",
                "assignment", auctionService.get().lastAssignment)).build();
    }

    public static class ObserverAssociationRequest { public Long participantId; }

    @PUT
    @Path("/users/{username}/observer-team")
    @RolesAllowed("admin")
    public Response associateObserver(@PathParam("username") String username, ObserverAssociationRequest request) {
        if (request == null) throw new jakarta.ws.rs.BadRequestException("Dati mancanti");
        appUserService.associateObserver(username, request.participantId);
        return Response.noContent().build();
    }
}
