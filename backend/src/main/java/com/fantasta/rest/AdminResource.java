package com.fantasta.rest;

import com.fantasta.util.ExcelPlayersLoader;
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
    ExcelPlayersLoader excel;

    @Inject
    ParticipantsLoader participantsLoader;

    /**
     * Ricarica i giocatori da Excel
     * Accesso riservato agli utenti con ruolo "admin"
     */
    @POST
    @Path("/reload-players")
    @Transactional
    @RolesAllowed("admin")
    public Response reload() {
        try {
            int n = excel.loadFromExcel();
            Log.infof("Reloaded %d players from Excel", n);
            return Response.ok(java.util.Map.of("imported", n)).build();
        } catch (Exception e) {
            Log.error("Failed to reload players", e);
            return Response.status(500)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build();
        }
    }

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
}
