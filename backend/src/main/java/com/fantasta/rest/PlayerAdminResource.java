package com.fantasta.rest;

import com.fantasta.dto.PlayerImportResult;
import com.fantasta.dto.AdminPlayerDto;
import com.fantasta.dto.AdminEligibleParticipantDto;
import com.fantasta.dto.MarketPlayerImportResult;
import com.fantasta.dto.UpdatePlayerValueDto;
import com.fantasta.model.PlayerEntity;
import com.fantasta.service.DbService;
import com.fantasta.service.MarketDepartureImportService;
import com.fantasta.service.PlayerQueryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;
import java.util.List;

@Path("/api/admin/players")
@Produces(MediaType.APPLICATION_JSON)
public class PlayerAdminResource {

    @Inject
    DbService dbService;

    @Inject
    PlayerQueryService playerQueryService;

    @Inject
    MarketDepartureImportService departureImportService;

    @POST
    @Path("/market-departures")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("admin")
    public Response importDepartures(@RestForm("file") InputStream file,
                                     @RestForm("confirm") String confirm) {
        if (file == null) throw new BadRequestException("File Excel mancante");
        try {
            return Response.ok(departureImportService.importDepartures(file, Boolean.parseBoolean(confirm))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.serverError().entity(java.util.Map.of("error", "File dei partiti non valido")).build();
        }
    }

    @GET
    @Path("/search")
    @RolesAllowed("admin")
    public List<AdminPlayerDto> search(@QueryParam("q") String query) {
        return playerQueryService.searchAdminPlayers(query);
    }

    @GET
    @Path("/{playerId}/eligible-participants")
    @RolesAllowed("admin")
    public List<AdminEligibleParticipantDto> eligibleParticipants(@PathParam("playerId") Long playerId) {
        return playerQueryService.eligibleParticipants(playerId);
    }

    /**
     * Upload Excel dal browser → aggiorna il catalogo giocatori
     * Accesso riservato al ruolo "admin"
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @RolesAllowed("admin")
    public Response uploadPlayers(@RestForm("file") InputStream file,
                                  @RestForm("confirm") String confirm) {
        if (file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "File Excel mancante"))
                    .build();
        }

        try {
            boolean confirmed = Boolean.parseBoolean(confirm);
            PlayerImportResult result = confirmed
                    ? dbService.replacePlayersFromExcel(file)
                    : dbService.previewPlayersFromExcel(file);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/market-update")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @RolesAllowed("admin")
    public Response updateMarketPlayers(@RestForm("file") InputStream file,
                                        @RestForm("confirm") String confirm) {
        if (file == null) throw new BadRequestException("File Excel mancante");
        try {
            MarketPlayerImportResult result = Boolean.parseBoolean(confirm)
                    ? dbService.updateMarketPlayersFromExcel(file)
                    : dbService.previewMarketPlayersFromExcel(file);
            return Response.ok(result).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.serverError().entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{playerId}/value")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed("admin")
    public Response updatePlayerValue(@PathParam("playerId") Long playerId, UpdatePlayerValueDto dto) {
        if (dto == null || dto.value == null || dto.value < 0) {
            throw new BadRequestException("Quotazione non valida");
        }
        PlayerEntity player = PlayerEntity.findById(playerId);
        if (player == null) throw new NotFoundException("Calciatore non trovato");
        player.valore = dto.value;
        return Response.ok().build();
    }
}
