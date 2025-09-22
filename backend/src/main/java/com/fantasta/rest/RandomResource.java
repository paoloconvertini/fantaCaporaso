package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.service.*;
import com.fantasta.ws.RoundSocket;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.Map;

@Path("/api/random")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RandomResource {

    @Inject
    RandomSelectorService selector;

    @Inject
    DbService db;

    @Inject
    RoundSocket socket;

    /**
     * 🔹 Imposta il ruolo (solo admin)
     */
    @POST
    @Path("/set-role")
    @RolesAllowed("admin")
    public Response setRole(Map<String, String> body) {
        String roleStr = body != null ? body.get("role") : null;
        if (roleStr == null || roleStr.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Role mancante")).build();
        }
        Role role = Role.fromString(roleStr);
        selector.setRole(role);
        socket.broadcast("ROLE_CHANGED", Map.of("role", role.name()));
        return Response.noContent().build();
    }

    /**
     * 🔹 Stato corrente random (visibile a tutti)
     */
    @GET
    @Path("/state")
    @RolesAllowed({"admin", "user"})
    public Map<String, Object> state() {
        GiroEntity giro = db.ensureCurrentGiro();

        String mode = selector != null && selector.getMode() != null ? selector.getMode().name() : "OFF";
        String role = (selector != null && selector.getRole() != null) ? selector.getRole().name() : null;

        Map<String, Object> stats = giro != null ? db.remainingAndSkippedByRole(giro.id) : Map.of();

        return Map.ofEntries(
                Map.entry("mode", mode),
                Map.entry("role", role != null ? role : ""),
                Map.entry("giroId", giro != null ? giro.id : -1L),
                Map.entry("remaining", stats instanceof Map ? ((Map<?,?>) stats).get("remaining") : Map.of()),
                Map.entry("skipped", stats instanceof Map ? ((Map<?,?>) stats).get("skipped") : Map.of())
        );
    }

    /**
     * 🔹 Estrae il prossimo giocatore (solo admin)
     */
    @POST
    @Path("/next")
    @RolesAllowed("admin")
    public Response next() {
        var giro = db.ensureCurrentGiro();
        var m = selector.getMode();
        PlayerEntity p;
        if (m == RandomSelectorService.Mode.ALL) p = db.drawRandom(giro.id, null);
        else if (m == RandomSelectorService.Mode.ROLE) p = db.drawRandom(giro.id, selector.getRole());
        else return Response.status(400).entity(Map.of("error", "Random OFF")).build();

        if (p == null) return Response.status(204).build();

        return Response.ok(Map.of(
                "name", p.name,
                "team", p.team,
                "role", p.role == null ? null : p.role.name(),
                "value", p.valore == null ? 0 : p.valore
        )).build();
    }

    /**
     * 🔹 Salta un giocatore (solo admin)
     */
    @POST
    @Path("/skip")
    @RolesAllowed("admin")
    public Response skip(Map<String, String> body) {
        var giro = db.ensureCurrentGiro();
        String name = body != null ? body.get("name") : null;
        String team = body != null ? body.get("team") : null;
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name mancante")).build();

        var p = db.findByNameTeam(name, team);
        if (p != null) db.skip(giro.id, p);

        return Response.noContent().build();
    }

    /**
     * 🔹 Reset degli skip (solo admin)
     */
    @POST
    @Path("/reset-skip")
    @RolesAllowed("admin")
    public Response resetSkip() {
        db.resetGiro();
        socket.broadcast("ROUND_UPDATED", Map.of("reason", "reset_giro"));
        return Response.noContent().build();
    }

    /**
     * 🔹 Torna al pick precedente (solo admin)
     */
    @POST
    @Path("/prev")
    @RolesAllowed("admin")
    public Response prev(Map<String, String> body) {
        var giro = db.ensureCurrentGiro();

        String name = body != null ? body.get("name") : null;
        String team = body != null ? body.get("team") : null;

        PlayerEntity current = null;
        if (name != null && !name.isBlank()) {
            current = db.findByNameTeam(name, team);
        }

        PlayerEntity p = db.previousUnassignedPick(giro.id, current);
        if (p == null) return Response.status(204).build();

        return Response.ok(Map.of(
                "name", p.name,
                "team", p.team,
                "role", p.role == null ? null : p.role.name(),
                "value", p.valore == null ? 0 : p.valore
        )).build();
    }
}
