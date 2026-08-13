package com.fantasta.rest;

import com.fantasta.dto.CreateUserRequest;
import com.fantasta.service.AppUserService;
import com.fantasta.util.ParticipantsLoader;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    ParticipantsLoader participantsLoader;

    @Inject
    AppUserService appUserService;

    /**
     * Inserisce i partecipanti iniziali dal classpath
     * Accesso riservato agli utenti con ruolo "admin"
     */
    @POST
    @Path("/seed-participants")
    @Transactional
    @RolesAllowed("admin")
    public Response seed() {
        try {
            int def = Integer.parseInt(System.getProperty("app.credits.total",
                    System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));
            int n = participantsLoader.loadFromClasspath(def);
            return Response.ok(java.util.Map.of("added", n)).build();
        } catch (Exception e) {
            Log.error("Failed to seed participants", e);
            return Response.status(500)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/users")
    @RolesAllowed("admin")
    public Response createUser(CreateUserRequest request) {
        appUserService.createUser(request);
        return Response.status(Response.Status.CREATED)
                .entity(java.util.Map.of("created", true))
                .build();
    }

    @GET
    @Path("/users")
    @RolesAllowed("admin")
    public Response users() {
        return Response.ok(appUserService.listUsers()).build();
    }
}
