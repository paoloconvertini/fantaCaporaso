package com.fantasta.rest;

import com.fantasta.dto.PlayerDto;
import com.fantasta.service.PlayerQueryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/players")
@Produces(MediaType.APPLICATION_JSON)
public class PlayerQueryResource {

    @Inject
    PlayerQueryService playerQueryService;

    @GET
    @Path("/free")
    @RolesAllowed({"admin", "user"})
    public List<PlayerDto> getFreePlayers(@QueryParam("role") String role) {
        return playerQueryService.getFreePlayers(role);
    }
}
