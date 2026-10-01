package com.fantasta.rest;

import com.fantasta.dto.AuctionHistoryPageDto;
import com.fantasta.service.AuctionHistoryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/auction-history")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "user", "observer"})
public class AuctionHistoryResource {
    @Inject AuctionHistoryService service;
    @Inject SecurityIdentity identity;

    @GET
    public AuctionHistoryPageDto list(@QueryParam("q") String query,
                                      @QueryParam("role") String role,
                                      @QueryParam("sort") @DefaultValue("date") String sort,
                                      @QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("size") @DefaultValue("30") int size) {
        Long participantId = null;
        if (!identity.hasRole("admin")) {
            Object claim = identity.getAttribute("participant_id");
            participantId = claim == null ? -1L : Long.valueOf(claim.toString());
        }
        return service.list(query, role, sort, page, size, participantId);
    }
}
