package com.fantasta.api;

import com.fantasta.dto.RosterDto;
import com.fantasta.service.RosterQueryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/rosters")
@Produces(MediaType.APPLICATION_JSON)
public class RosterQueryResource {

    @Inject
    RosterQueryService rosterQueryService;

    @GET
    public List<RosterDto> getRosters(
            @QueryParam("participantId") Long participantId,
            @QueryParam("sessionId") Long sessionId
    ) {
        return rosterQueryService.getRosters(participantId, sessionId);
    }
}
