package com.fantasta.rest;

import com.fantasta.dto.RosterMovementDto;
import com.fantasta.service.RosterMovementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/admin/roster-movements")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class RosterMovementResource {
    @Inject RosterMovementService service;

    @GET
    public List<RosterMovementDto> list(@QueryParam("q") String query,
                                        @QueryParam("type") String type,
                                        @QueryParam("participantId") Long participantId,
                                        @QueryParam("includeReverted") @DefaultValue("false") boolean includeReverted) {
        return service.list(query, type, participantId, includeReverted);
    }

    @POST
    @Path("/{id}/revert")
    public void revert(@PathParam("id") Long id) {
        service.revert(id);
    }
}
