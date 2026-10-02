package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.service.*;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

@Path("/api/mini-auctions")
@Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class MiniAuctionResource {
    @Inject MiniAuctionService minis;
    @Inject AuctionService auction;
    @Inject SecurityIdentity identity;

    public record PrepareRequest(String label, String sourceSessionCode, LocalDate sourceDate,
                                 List<Long> rosterIds) {}
    public record Confirmation(boolean confirm) {}
    public record SlotView(Long id, Long participantId, String participant, String role, String releasedNames,
                           double refund, double minimumBid, int purchaseSize, boolean filled, Double paidAmount) {}
    public record SessionView(Long id, String label, String sourceSessionCode, LocalDate sourceDate,
                              String status, List<SlotView> slots) {}
    public record OwnSlot(Long id, String role, String releasedNames, double minimumBid, double maximumBid,
                          int purchaseSize, boolean filled, boolean bidSelected) {}
    public record SourceChoice(String sessionCode, LocalDate date, long purchases, String label) {}
    public record Candidate(Long id, Long participantId, String participant, String player, String team,
                            String role, double amount) {}

    private SessionView view(MiniAuctionSessionEntity session) {
        if (session == null) return null;
        return new SessionView(session.id, session.label, session.sourceSessionCode, session.sourceDate,
                session.status.name(), minis.slots(session.id).stream().map(s -> new SlotView(s.id, s.participant.id,
                        s.participant.name, s.role.name(), s.releasedNames, s.refund, s.minimumBid,
                        s.purchaseSize, s.filled, s.paidAmount)).toList());
    }

    @GET @Path("/current") @Transactional @RolesAllowed({"admin", "user", "observer"})
    public SessionView current() {
        return view(MiniAuctionSessionEntity.find("status in (?1, ?2) order by id desc",
                MiniAuctionSessionEntity.Status.DRAFT, MiniAuctionSessionEntity.Status.ACTIVE).firstResult());
    }

    @GET @Path("/sources") @Transactional @RolesAllowed("admin")
    public List<SourceChoice> sources() {
        Map<String, SourceChoice> choices = new LinkedHashMap<>();
        for (RosterAcquisitionEntity a : RosterAcquisitionEntity.<RosterAcquisitionEntity>list("repairMarket = true order by acquiredAt desc")) {
            LocalDate date = a.acquiredAt.toLocalDate();
            String key = a.sessionCode + ":" + date;
            SourceChoice previous = choices.get(key);
            choices.put(key, new SourceChoice(a.sessionCode, date, previous == null ? 1 : previous.purchases() + 1,
                    "Asta di riparazione del " + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }
        return new ArrayList<>(choices.values());
    }

    @GET @Path("/candidates") @Transactional @RolesAllowed("admin")
    public List<Candidate> candidates(@QueryParam("sourceSessionCode") String code, @QueryParam("sourceDate") String date) {
        LocalDate sourceDate;
        try { sourceDate = date == null ? null : LocalDate.parse(date); }
        catch (java.time.format.DateTimeParseException e) { throw new BadRequestException("Data non valida"); }
        return minis.eligibleCandidates(code, sourceDate).stream()
                .map(r -> new Candidate(r.id, r.participant.id, r.participant.name, r.player.name, r.player.team,
                        r.player.role.name(), r.amount)).toList();
    }

    @GET @Path("/mine") @Transactional @RolesAllowed({"admin", "user"})
    public List<OwnSlot> mine(@QueryParam("participantId") Long adminParticipant) {
        Long pid = identity.hasRole("admin") ? adminParticipant : identity.getAttribute("participant_id");
        MiniAuctionSessionEntity active = minis.active();
        if (pid == null || active == null) return List.of();
        RoundState round = auction.get();
        Long selectedSlot = round != null && active.id.equals(round.miniSessionId) && round.miniBidSlots != null
                ? round.miniBidSlots.get(String.valueOf(pid)) : null;
        return minis.slots(active.id).stream().filter(s -> s.participant.id.equals(pid))
                .map(s -> new OwnSlot(s.id, s.role.name(), s.releasedNames, s.minimumBid,
                        s.filled ? 0 : minis.maxBid(s), s.purchaseSize, s.filled, s.id.equals(selectedSlot))).toList();
    }

    @POST @Path("/prepare") @RolesAllowed("admin")
    public Response prepare(PrepareRequest request) {
        return checked(() -> {
            if (request == null) throw new IllegalArgumentException("Dati mancanti");
            return view(auction.prepareMini(request.label(), request.sourceSessionCode(), request.sourceDate(),
                    request.rosterIds()));
        });
    }

    @POST @Path("/{id}/activate") @RolesAllowed("admin")
    public Response activate(@PathParam("id") Long id, Confirmation confirmation) {
        return checked(() -> {
            if (confirmation == null || !confirmation.confirm()) throw new IllegalArgumentException("Confermare le cessioni definitive");
            return view(auction.activateMini(id));
        });
    }

    @POST @Path("/{id}/finish") @RolesAllowed("admin")
    public Response finish(@PathParam("id") Long id) {
        return checked(() -> { auction.finishMini(id); return Map.of("message", "Mini asta conclusa"); });
    }

    private Response checked(Supplier<Object> action) {
        try { return Response.ok(action.get()).build(); }
        catch (IllegalArgumentException e) { return Response.status(400).entity(Map.of("error", e.getMessage())).build(); }
        catch (IllegalStateException e) { return Response.status(409).entity(Map.of("error", e.getMessage())).build(); }
    }
}
