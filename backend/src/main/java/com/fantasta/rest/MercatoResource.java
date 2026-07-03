package com.fantasta.rest;

import com.fantasta.dto.MercatoConfigDto;
import com.fantasta.service.MercatoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("api/mercato")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MercatoResource {

    @Inject
    MercatoService service;

    @GET
    @Path("/config")
    @RolesAllowed({"user", "admin"})
    public Response getConfig() {
        return Response.ok(service.getConfig()).build();
    }

    @POST
    @Path("/config")
    @RolesAllowed("admin")
    public Response updateConfig(MercatoConfigDto dto) {
        return Response.ok(service.updateConfig(dto)).build();
    }
}

