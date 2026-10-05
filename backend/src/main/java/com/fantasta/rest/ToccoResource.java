package com.fantasta.rest;

import com.fantasta.dto.RoundDto;
import com.fantasta.model.RoundState;
import com.fantasta.service.AuctionService;
import com.fantasta.service.AuctionHistoryService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Supplier;

@Path("/api/tocco")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ToccoResource {
    @Inject AuctionService service;
    @Inject AuctionHistoryService history;
    @Inject SecurityIdentity identity;
    public record Request(String roundId, String toccoId, List<Long> order, Long firstParticipantId,
                          Double number, Double amount) {}

    private RoundDto invoke(Request request, Supplier<RoundState> action) {
        if (request == null) throw new BadRequestException("Dati mancanti");
        try { return RoundDto.toDto(action.get()); }
        catch (IllegalArgumentException e) { throw new WebApplicationException(e.getMessage(), 400); }
        catch (IllegalStateException e) { throw new WebApplicationException(e.getMessage(), 409); }
    }

    @POST @Path("/start") @RolesAllowed("admin")
    public RoundDto start(Request request) {
        return invoke(request, () -> service.startTocco(request.roundId(), request.order(), request.firstParticipantId()));
    }

    @POST @Path("/choose") @RolesAllowed({"user", "admin"})
    public RoundDto choose(Request request) {
        Long participantId = identity.getAttribute("participant_id");
        return invoke(request, () -> {
            Double number = request.number();
            if (number == null || !Double.isFinite(number) || number < 1 || number > 5 || number != Math.floor(number))
                throw new IllegalArgumentException("Scegli un numero intero da 1 a 5");
            return service.chooseTocco(request.roundId(), request.toccoId(), participantId, number.intValue());
        });
    }

    @POST @Path("/cancel") @RolesAllowed("admin")
    public RoundDto cancel(Request request) {
        return invoke(request, () -> service.cancelTocco(request.roundId(), request.toccoId()));
    }

    @POST @Path("/assign") @RolesAllowed("admin")
    public RoundDto assign(Request request) {
        return invoke(request, () -> {
            RoundState assigned = service.assignTocco(request.roundId(), request.toccoId(), request.amount());
            history.record(assigned);
            return assigned;
        });
    }
}
