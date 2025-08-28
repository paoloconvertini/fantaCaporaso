package com.fantasta.rest;

import com.fantasta.util.ExcelPlayersLoader;
import com.fantasta.util.ParticipantsLoader;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject ExcelPlayersLoader excel;
    @Inject
    ParticipantsLoader participantsLoader;


    private void ensureAdmin(String pin){
        String expected = System.getProperty("admin.pin", System.getenv().getOrDefault("ADMIN_PIN", "1234"));
        if (expected == null) expected = "1234";
        if (pin == null || !expected.equals(pin)){
            throw new WebApplicationException("PIN errato", 403);
        }
    }

    @POST @Path("/reload-players")
    @Transactional
    public Response reload(@HeaderParam("X-ADMIN-PIN") String pin){
        ensureAdmin(pin);
        try {
            int n = excel.loadFromExcel();
            Log.infof("Reloaded %d players from Excel", n);
            return Response.ok(java.util.Map.of("imported", n)).build();
        } catch (Exception e) {
            Log.error("Failed to reload players", e);
            return Response.status(500).entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }

    @POST @Path("/seed-participants")
    @Transactional
    public jakarta.ws.rs.core.Response seed(@HeaderParam("X-ADMIN-PIN") String pin) {
        ensureAdmin(pin);
        try {
            int def = Integer.parseInt(System.getProperty("app.credits.total",
                    System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));
            int n = participantsLoader.loadFromClasspath(def);
            return jakarta.ws.rs.core.Response.ok(java.util.Map.of("added", n)).build();
        } catch (Exception e) {
            return jakarta.ws.rs.core.Response.status(500)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }
}

