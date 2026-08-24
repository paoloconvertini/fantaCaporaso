package com.fantasta.rest;

import com.fantasta.service.NewspaperImportService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.InputStream;
import java.util.Map;

@Path("/api/admin/newspaper")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class NewspaperResource {
    @Inject NewspaperImportService service;
    @Inject com.fantasta.service.NewspaperPublishService publishService;

    @GET
    @Path("/publish-status")
    public Response publishStatus() {
        return Response.ok(publishService.status()).build();
    }

    @POST
    @Path("/preview")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response preview(@RestForm("file") InputStream file) {
        if (file == null) throw new BadRequestException("File Excel mancante");
        try {
            return Response.ok(service.preview(file)).build();
        } catch (IllegalArgumentException error) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", error.getMessage())).build();
        } catch (Exception error) {
            return Response.serverError().entity(Map.of("error", "Impossibile leggere il file FantaMaster")).build();
        }
    }

    @POST
    @Path("/publish")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response publish(@RestForm("edition") String edition,
                            @RestForm("image") FileUpload image) {
        if (edition == null || edition.isBlank()) throw new BadRequestException("Edizione mancante");
        if (image == null) throw new BadRequestException("Immagine di copertina mancante");
        try {
            publishService.publish(edition, image.uploadedFile(), image.contentType(), image.size());
            return Response.ok(Map.of("published", true)).build();
        } catch (IllegalArgumentException | IllegalStateException error) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", error.getMessage())).build();
        } catch (Exception error) {
            return Response.serverError().entity(Map.of("error", "Pubblicazione Gazzetta non riuscita")).build();
        }
    }
}
