package com.fantasta.rest;

import com.fantasta.dto.BidDto;
import com.fantasta.dto.ManualAssignDto;
import com.fantasta.dto.RoundDto;
import com.fantasta.model.RoundState;
import com.fantasta.service.AuctionService;
import com.fantasta.ws.RoundSocket;
import io.vertx.core.Vertx;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AuctionResource {
    private static final Logger LOG = Logger.getLogger(AuctionResource.class);

    @Inject
    Vertx vertx;

    // stato per il timer
    private volatile Long autoCloseTimerId = null;
    private volatile String scheduledRoundId = null;

    @Inject
    AuctionService service;

    @Inject
    RoundSocket socket;

    @Inject
    SecurityIdentity identity;

    @PostConstruct
    void recoverPersistedTimer() {
        vertx.executeBlocking(() -> service.get())
                .onSuccess(this::scheduleAutoClose)
                .onFailure(t -> LOG.error("Impossibile recuperare il timer del round persistito", t));
    }

    // --- USER/ADMIN ENDPOINTS ---

    @GET
    @Path("/round")
    @RolesAllowed({"admin", "user"})
    public RoundDto getRound() {
        return RoundDto.toDto(service.get());
    }

    @POST
    @Path("/bids")
    @Transactional
    @RolesAllowed({"admin", "user"})
    public RoundDto bid(BidDto dto) {
        try {
            if (dto == null) throw new IllegalArgumentException("Offerta mancante");
            Long participantId = identity.hasRole("admin")
                    ? dto.participantId
                    : identity.getAttribute("participant_id");
            if (!identity.hasRole("admin") && participantId == null) {
                throw new IllegalArgumentException("Utente non associato a una squadra");
            }
            return service.bidDto(participantId, dto.amount);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), 400);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), 409);
        }
    }

    // --- ADMIN ONLY ENDPOINTS ---

    @POST
    @Path("/start")
    @RolesAllowed("admin")
    public RoundDto startRound(RoundState payload) {
        RoundState s = service.start(
                payload.player,
                payload.playerTeam,
                payload.playerRole,
                payload.durationSeconds,
                payload.tieBreak,
                payload.value,
                payload.allowedUsers
        );
        socket.broadcast("ROUND_STARTED", RoundDto.toDto(s));

        scheduleAutoClose(s);
        return RoundDto.toDto(s);
    }

    private synchronized void scheduleAutoClose(RoundState s) {
        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }

        if (s != null && !s.closed && s.endEpochMillis != null) {
            scheduledRoundId = s.roundId;
            String expectedRoundId = s.roundId;
            long delay = Math.max(0L, s.endEpochMillis - System.currentTimeMillis());

            autoCloseTimerId = vertx.setTimer(delay, id -> {
                // Ogni accesso transazionale resta sul worker thread: il callback del
                // timer gira sul thread I/O e non puo' aprire direttamente una JTA.
                vertx.<RoundDto>executeBlocking(promise -> {
                    try {
                        promise.complete(service.closeIfActiveDto(expectedRoundId));
                    } catch (Throwable t) {
                        promise.fail(t);
                    }
                }, false).onComplete(result -> {
                    if (result.succeeded() && result.result() != null) {
                        socket.broadcast("ROUND_CLOSED", result.result());
                    } else if (result.failed()) {
                        LOG.errorf(result.cause(), "Chiusura automatica fallita per il round %s", expectedRoundId);
                    }
                    if (expectedRoundId.equals(scheduledRoundId)) {
                        autoCloseTimerId = null;
                        scheduledRoundId = null;
                    }
                });
            });
        }
    }

    @POST
    @Path("/round/close")
    @RolesAllowed("admin")
    public RoundDto closeRound() {
        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }
        scheduledRoundId = null;
        RoundState s = service.close();
        socket.broadcast("ROUND_CLOSED", RoundDto.toDto(s));
        return RoundDto.toDto(s);
    }

    @POST
    @Path("/round/reset")
    @RolesAllowed("admin")
    public void resetRound() {
        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }
        scheduledRoundId = null;

        service.reset();
        socket.broadcast("ROUND_RESET", null);
    }

    @POST
    @Path("/assign")
    @Transactional
    @RolesAllowed("admin")
    public RoundDto manualAssign(ManualAssignDto dto) {
        RoundState s = service.manualAssign(dto.participantId, dto.player, dto.team, dto.amount);
        RoundDto roundDto = RoundDto.toDto(s);
        socket.broadcast("ROUND_CLOSED", roundDto);
        return roundDto;
    }

    @POST
    @Path("/admin/close-auction")
    @Transactional
    @RolesAllowed("admin")
    public Response closeAuction(@QueryParam("sessionId") Long sessionId) {
        if (sessionId == null) {
            throw new BadRequestException("SessionId mancante");
        }
        service.closeAuction(sessionId);
        return Response.ok().entity(Map.of("message", "Asta chiusa con successo")).build();
    }

}
