package com.fantasta.rest;

import com.fantasta.dto.BidDto;
import com.fantasta.dto.ManualAssignDto;
import com.fantasta.dto.RoundDto;
import com.fantasta.model.RoundState;
import com.fantasta.service.AuctionService;
import com.fantasta.service.DbService;
import com.fantasta.service.AuctionHistoryService;
import com.fantasta.service.AuctionArchiveService;
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
    private record ClosedRound(RoundState state, RoundDto dto) {}

    @Inject
    Vertx vertx;

    // stato per il timer
    private volatile Long autoCloseTimerId = null;
    private volatile String scheduledRoundId = null;

    @Inject
    AuctionService service;

    @Inject
    DbService dbService;

    @Inject
    RoundSocket socket;

    @Inject
    SecurityIdentity identity;

    @Inject
    AuctionHistoryService auctionHistoryService;

    @Inject
    AuctionArchiveService auctionArchiveService;

    @PostConstruct
    void recoverPersistedTimer() {
        vertx.executeBlocking(() -> service.get())
                .onSuccess(this::scheduleAutoClose)
                .onFailure(t -> LOG.error("Impossibile recuperare il timer del round persistito", t));
    }

    // --- USER/ADMIN ENDPOINTS ---

    @GET
    @Path("/round")
    @RolesAllowed({"admin", "user", "observer"})
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

    @POST
    @Path("/bids/withdraw")
    @Transactional
    @RolesAllowed("user")
    public RoundDto withdrawBid() {
        try {
            Long participantId = identity.getAttribute("participant_id");
            if (participantId == null) {
                throw new IllegalArgumentException("Utente non associato a una squadra");
            }
            return service.withdrawBidDto(participantId);
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
                vertx.<ClosedRound>executeBlocking(promise -> {
                    try {
                        RoundState closed = service.closeIfActive(expectedRoundId);
                        promise.complete(closed == null ? null : new ClosedRound(closed, service.toDto(closed)));
                    } catch (Throwable t) {
                        promise.fail(t);
                    }
                }, false).onComplete(result -> {
                    if (result.succeeded() && result.result() != null) {
                        socket.broadcast("ROUND_CLOSED", result.result().dto());
                        recordHistoryBestEffort(result.result().state());
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
        recordHistoryBestEffort(s);
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
    @Path("/round/skip")
    @RolesAllowed("admin")
    public Response skipRound(Map<String, String> body) {
        String name = body != null ? body.get("name") : null;
        String team = body != null ? body.get("team") : null;
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name mancante");
        }

        try {
            // Il controllo delle offerte e il reset dello stato sono sincronizzati
            // con la chiusura automatica dentro AuctionService.
            service.resetForSkip();
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), 409);
        }

        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }
        scheduledRoundId = null;

        var giro = dbService.ensureCurrentGiro();
        var player = dbService.findByNameTeam(name, team);
        if (player != null) {
            dbService.skip(giro.id, player);
        }
        socket.broadcast("ROUND_RESET", null);
        return Response.noContent().build();
    }

    @POST
    @Path("/assign")
    @RolesAllowed("admin")
    public RoundDto manualAssign(ManualAssignDto dto) {
        RoundState s = service.manualAssign(dto.participantId, dto.player, dto.team, dto.amount);
        RoundDto roundDto = RoundDto.toDto(s);
        socket.broadcast("ROUND_CLOSED", roundDto);
        recordHistoryBestEffort(s);
        return roundDto;
    }

    private void recordHistoryBestEffort(RoundState round) {
        vertx.executeBlocking(() -> {
            auctionHistoryService.record(round);
            return null;
        }, false).onFailure(error -> LOG.debugf(error,
                "Storico puntate non salvato per il round %s", round == null ? null : round.roundId));
    }

    @POST
    @Path("/admin/close-auction")
    @Transactional
    @RolesAllowed("admin")
    public Response closeAuction() {
        var result = service.closeAuction();
        if (!result.alreadyClosed()) {
            vertx.executeBlocking(() -> {
                auctionArchiveService.generateAndPublishBestEffort(result.id());
                return null;
            }, false).onFailure(error -> LOG.debugf(error,
                    "Archivio statico non generato per la sessione %s", result.sessionCode()));
        }
        return Response.ok(result).build();
    }

}
