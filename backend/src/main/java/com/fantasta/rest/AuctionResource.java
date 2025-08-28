package com.fantasta.rest;

import com.fantasta.dto.*;
import com.fantasta.model.*;
import com.fantasta.service.*;
import com.fantasta.ws.RoundSocket;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuctionResource {
    @Inject
    AuctionService service;
    @Inject
    RoundSocket socket;
    @Inject
    DbService db;
    @Inject
    ParticipantService participantService;

    @Inject
    RosterService roster;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> autoCloseTask;

    private void ensureAdmin(String pin) {
        String expected = System.getProperty("admin.pin", System.getenv().getOrDefault("ADMIN_PIN", "1234"));
        if (expected == null) expected = "1234";
        if (pin == null || !expected.equals(pin)) throw new WebApplicationException("PIN errato", 403);
    }

    private int defaultCredits() {
        try {
            return Integer.parseInt(System.getProperty("app.credits.total",
                    System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));
        } catch (Exception e) {
            return 500;
        }
    }

    // -------- GET round --------
    @GET
    @Path("/round")
    public RoundDto get() {
        RoundState s = service.get();
        if (s != null && !s.closed) {
            // oscuriamo le puntate durante round aperto
            s.bids = new LinkedHashMap<>(s.bids);
            s.bids.replaceAll((k, v) -> 0);
        }
        return toDto(s);
    }

    // -------- START --------
    @POST
    @Path("/start")
    public RoundDto start(@HeaderParam("X-ADMIN-PIN") String pin, StartRoundDto dto) {
        ensureAdmin(pin);

        final String team = (dto.team != null) ? dto.team : dto.playerTeam;
        final String role = (dto.role != null) ? dto.role : dto.playerRole;
        final Integer duration = (dto.duration != null) ? dto.duration : dto.durationSeconds;
        final String tie = (dto.tieBreak == null || dto.tieBreak.isBlank()) ? "NONE" : dto.tieBreak;

        RoundState s = service.start(dto.player, team, role, duration, tie);
        scheduleAutoClose(s);

        // broadcast evento
        socket.broadcast("ROUND_STARTED", toDto(s));
        return toDto(s);
    }

    // -------- CLOSE --------
    @POST
    @Path("/round/close")
    public RoundDto close(@HeaderParam("X-ADMIN-PIN") String pin) {
        ensureAdmin(pin);
        if (autoCloseTask != null && !autoCloseTask.isDone()) {
            autoCloseTask.cancel(false);
        }

        RoundState s = service.close();
        RoundDto dto = toDto(s);

        socket.broadcast("ROUND_CLOSED", dto);

        if (s != null && s.winner != null && s.player != null) {
            var p = db.findByNameTeam(s.player, s.playerTeam);
            if (p != null) {
                db.markAssigned(s.roundId, p, s.winner.participantId, s.winner.amount);
            }
        }

        return dto;
    }

    @POST
    @Path("/bids")
    public RoundDto bid(BidDto dto) {
        RoundState s = service.get();
        if (s == null || s.closed)
            throw new WebApplicationException("Round non attivo", 400);

        if (dto == null || dto.participantId == null)
            throw new WebApplicationException("Partecipante mancante", 400);

        if (dto.amount < 1)
            throw new WebApplicationException("Offerta minima 1", 400);

        ParticipantEntity p = ParticipantEntity.findById(dto.participantId);
        if (p == null)
            throw new WebApplicationException("Partecipante non trovato", 404);

        Role role = Role.fromString(s.playerRole);


// ✅ residuo calcolato dalla Roster (no campo duplicato)
        int residuo = participantService.remainingCreditsById(p.id, p.totalCredits);
        if (dto.amount > residuo)
            throw new WebApplicationException("Offerta supera il credito residuo", 400);

// ✅ conteggio per ruolo dalla Roster
        int current = participantService.roleCounts(p.id).getOrDefault(role, 0);
        int max = roster.max(role);
        if (current >= max)
            throw new WebApplicationException("Quota piena per ruolo " + role, 400);

        RoundState after = service.bid(p.id, dto.amount);

        socket.broadcast("BID_ADDED", java.util.Map.of("user", p.name));

        // 🔹 ritorna il DTO “pulito” (se già hai il tuo mapper toDto)
        return toDto(after);
    }


    // -------- RESET --------
    @POST
    @Path("/round/reset")
    public Response reset(@HeaderParam("X-ADMIN-PIN") String pin) {
        ensureAdmin(pin);
        if (autoCloseTask != null && !autoCloseTask.isDone()) autoCloseTask.cancel(false);
        service.reset();
        socket.broadcast("ROUND_RESET", null);
        return Response.noContent().build();
    }

    // -------- AUTO-CLOSE --------
    private void scheduleAutoClose(RoundState s) {
        if (s.endEpochMillis != null) {
            if (autoCloseTask != null && !autoCloseTask.isDone()) {
                autoCloseTask.cancel(false);
            }
            long delay = Math.max(0L, s.endEpochMillis - System.currentTimeMillis());
            autoCloseTask = exec.schedule(() -> {
                try {
                    RoundState closed = service.close();
                    RoundDto dto = toDto(closed);
                    socket.broadcast("ROUND_CLOSED", dto);

                    if (closed != null && closed.winner != null && closed.player != null) {
                        var p = db.findByNameTeam(closed.player, closed.playerTeam);
                        if (p != null) {
                            db.markAssigned(closed.roundId, p, closed.winner.participantId, closed.winner.amount);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    // -------- CONVERTER --------
    private RoundDto toDto(RoundState s) {
        if (s == null) return null;

        RoundDto dto = new RoundDto();
        dto.roundId = s.roundId;
        dto.player = s.player;
        dto.playerTeam = s.playerTeam;
        dto.playerRole = s.playerRole;
        dto.closed = s.closed;
        dto.durationSeconds = s.durationSeconds;
        dto.endEpochMillis = s.endEpochMillis;

        // bids: id → nome
        dto.bids = s.bids.entrySet().stream().collect(
                Collectors.toMap(
                        e -> {
                            try {
                                Long id = Long.valueOf(e.getKey());
                                ParticipantEntity p = ParticipantEntity.findById(id);
                                return p != null ? p.name : ("??-" + id);
                            } catch (Exception ex) {
                                return e.getKey();
                            }
                        },
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                )
        );

        dto.winner = s.winner;

        if (s.tieUsers != null) {
            dto.tieUsers = s.tieUsers.stream()
                    .map(id -> {
                        ParticipantEntity p = ParticipantEntity.findById(id);
                        return p != null ? p.name : ("??-" + id);
                    })
                    .toList();
        }

        return dto;
    }
}
