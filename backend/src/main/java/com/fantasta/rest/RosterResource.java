package com.fantasta.rest;

import com.fantasta.dto.RosterImportResult;
import com.fantasta.service.RosterService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;
import java.util.Map;

@Path("/api/admin/rosters")
@Produces(MediaType.APPLICATION_JSON)
public class RosterResource {

    private static final String ADMIN_PIN = System.getenv().getOrDefault("ADMIN_PIN", "1234");

    @Inject
    RosterService rosterService;

    @POST
    @Path("/upload")
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadExcel(@QueryParam("pin") String pin, @RestForm("file") InputStream file) {
        try {
            if (pin == null || !pin.equals(ADMIN_PIN)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(java.util.Map.of("error", "PIN amministratore non valido"))
                        .build();
            }

            if (file == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "File mancante"))
                        .build();
            }

            RosterImportResult result = rosterService.importFromExcel(file);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
