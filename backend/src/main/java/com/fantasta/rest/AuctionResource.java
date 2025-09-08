package com.fantasta.rest;

import com.fantasta.dto.BidDto;
import com.fantasta.dto.ManualAssignDto;
import com.fantasta.dto.RoundDto;
import com.fantasta.model.RoundState;
import com.fantasta.service.AuctionService;
import com.fantasta.ws.RoundSocket;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.Objects;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AuctionResource {

    @Inject
    Vertx vertx;

    // stato per il timer
    private Long autoCloseTimerId = null;
    private volatile String scheduledRoundId = null;

    @Inject AuctionService service;
    @Inject RoundSocket socket;

    @GET
    @Path("/round")
    public RoundDto getRound() {                    // ⬅️ cambiato tipo
        return RoundDto.toDto(service.get());
    }
    @POST
    @Path("/start")
    public RoundDto startRound(@HeaderParam("X-ADMIN-PIN") String pin, RoundState payload) {
        RoundState s = service.start(
                payload.player,
                payload.playerTeam,
                payload.playerRole,
                payload.durationSeconds,
                payload.tieBreak,
                payload.value,
                payload.allowedUsers
        );
        socket.broadcast("ROUND_STARTED", s);

        // --- AUTO-CLOSE TIMER ---
        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }

        if (s.endEpochMillis != null) {
            scheduledRoundId = s.roundId;
            long delay = Math.max(0L, s.endEpochMillis - System.currentTimeMillis());

            autoCloseTimerId = vertx.setTimer(delay, id -> {
                // ✅ Verifica che il round non sia cambiato/già chiuso
                RoundState current = service.get();
                if (current == null || current.closed || !Objects.equals(current.roundId, scheduledRoundId)) {
                    autoCloseTimerId = null;
                    scheduledRoundId = null;
                    return;
                }

                // ✅ Esegui la chiusura su worker thread (DB-safe)
                vertx.executeBlocking(promise -> {
                    try {
                        RoundState closed = service.close();
                        socket.broadcast("ROUND_CLOSED", closed);
                        promise.complete();
                    } catch (Throwable t) {
                        promise.fail(t);
                    }
                });

                autoCloseTimerId = null;
                scheduledRoundId = null;
            });
        }
        return RoundDto.toDto(s);
    }
    @POST
    @Path("/bids")
    @Transactional
    public RoundDto bid(BidDto dto) {
        try {
            RoundState after = service.bid(dto.participantId, dto.amount);
            return RoundDto.toDto(after);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), 400);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), 409);
        }
    }


    @POST
    @Path("/round/close")
    public RoundDto closeRound() {
        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }
        scheduledRoundId = null;   // ⬅️ AGGIUNGI
        RoundState s = service.close();
        socket.broadcast("ROUND_CLOSED", RoundDto.toDto(s));
        return RoundDto.toDto(s);
    }

    @POST
    @Path("/round/reset")
    public void resetRound() {
        // ⬇️ AGGIUNGI
        if (autoCloseTimerId != null) {
            vertx.cancelTimer(autoCloseTimerId);
            autoCloseTimerId = null;
        }
        scheduledRoundId = null;   // ⬅️ AGGIUNGI

        service.reset();
        socket.broadcast("ROUND_RESET", null);
    }

    @POST
    @Path("/assign")
    @Transactional
    public RoundDto manualAssign(ManualAssignDto dto) {
        RoundState s = service.manualAssign(dto.participantId, dto.player, dto.team, dto.amount);
        RoundDto roundDto = RoundDto.toDto(s);
        socket.broadcast("ROUND_CLOSED", roundDto);
        return roundDto;
    }

    /**
     * 🔹 Chiude l’asta e consolida le rose in history
     */
    @POST
    @Path("/close")
    @Transactional
    public void closeAuction(@QueryParam("sessionId") Long sessionId) {
        if (sessionId == null) {
            throw new BadRequestException("SessionId mancante");
        }
        service.closeAuction(sessionId);
    }

    /**
     * 🔹 Svincola un calciatore da roster
     */
    @POST
    @Path("/release/{rosterId}")
    @Transactional
    public void release(@PathParam("rosterId") Long rosterId) {
        if (rosterId == null) {
            throw new BadRequestException("RosterId mancante");
        }
        service.releasePlayer(rosterId);
    }

    /**
     * 🔹 Nuova sessione caricando rose da file
     */
    @POST
    @Path("/new")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public void startNewAuction(@QueryParam("sessionId") Long sessionId,
                                @FormParam("file") FileUpload fileUpload) {
        if (sessionId == null) {
            throw new BadRequestException("SessionId mancante");
        }
        if (fileUpload == null) {
            throw new BadRequestException("File mancante");
        }

        File file = fileUpload.uploadedFile().toFile();
        service.startNewAuctionFromFile(file, sessionId);
    }
}
