package com.fantasta.rest;

import com.fantasta.dto.PlayerImportResult;
import com.fantasta.service.DbService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;
import java.util.Map;

@Path("/api/admin/players")
@Produces(MediaType.APPLICATION_JSON)
public class PlayerAdminResource {

    @Inject
    DbService dbService;

    private static final String ADMIN_PIN = System.getenv().getOrDefault("ADMIN_PIN", "1234");

    /**
     * Upload Excel dal browser → aggiorna il catalogo giocatori
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadPlayers(@QueryParam("pin") String pin,
                                  @RestForm("file") InputStream file) {
        if (pin == null || !pin.equals(ADMIN_PIN)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("error", "PIN amministratore non valido"))
                    .build();
        }
        if (file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "File Excel mancante"))
                    .build();
        }

        try {
            PlayerImportResult result = dbService.syncPlayersFromExcel(file);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
