package com.fantasta.rest;

import com.fantasta.service.PlayerTargetService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.NoCache;
import java.util.List;

@Path("/api/targets")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("observer")
@NoCache
public class PlayerTargetResource {
    @Inject PlayerTargetService service;
    @GET public List<Long> list() { return service.list(); }
    @GET @Path("/analysis") public PlayerTargetService.Analysis analyze() { return service.analyze(); }
    @PUT @Path("/{playerId}") public void select(@PathParam("playerId") Long id) { service.set(id, true); }
    @DELETE @Path("/{playerId}") public void deselect(@PathParam("playerId") Long id) { service.set(id, false); }
}
