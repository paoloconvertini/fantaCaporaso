package com.fantasta.rest;

import com.fantasta.dto.RosterImportResult;
import com.fantasta.service.RosterService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;
import java.util.Map;

@Path("/api/rosters")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class RosterResource {

    @Inject
    RosterService rosterService;

    @POST
    @Path("/upload")
    @Transactional
    public Response uploadExcel(@RestForm("file") InputStream file) {
        try {
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
