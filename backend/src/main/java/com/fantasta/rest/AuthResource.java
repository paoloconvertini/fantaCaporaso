package com.fantasta.rest;

import com.fantasta.dto.AuthUserDto;
import com.fantasta.dto.LoginRequest;
import com.fantasta.dto.ChangePasswordRequest;
import com.fantasta.model.AppUserEntity;
import com.fantasta.model.ParticipantEntity;
import com.fantasta.security.AppJwtService;
import com.fantasta.service.AppUserService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.List;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AppUserService appUserService;

    @Inject
    AppJwtService jwtService;

    @Inject
    SecurityIdentity identity;

    @POST
    @Path("/login")
    public Response login(LoginRequest request, @Context HttpHeaders headers) {
        AppUserEntity user = appUserService.authenticate(request);
        String token = jwtService.createToken(
                user.username,
                user.mustChangePassword ? "password-change" : user.role,
                user.participant == null ? null : user.participant.id);

        NewCookie cookie = new NewCookie.Builder(AppJwtService.COOKIE_NAME)
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(isHttps(headers))
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge((int) jwtService.expirationSeconds())
                .build();

        return Response.ok(appUserService.toDto(user))
                .cacheControl(noStore())
                .cookie(cookie)
                .build();
    }

    @POST
    @Path("/password")
    @RolesAllowed("password-change")
    public Response changePassword(ChangePasswordRequest request, @Context HttpHeaders headers) {
        AppUserEntity user = appUserService.changePassword(
                identity.getPrincipal().getName(), request == null ? null : request.password);
        String token = jwtService.createToken(user.username, user.role,
                user.participant == null ? null : user.participant.id);
        return Response.ok(appUserService.toDto(user))
                .cacheControl(noStore())
                .cookie(authCookie(token, headers))
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context HttpHeaders headers) {
        NewCookie cookie = new NewCookie.Builder(AppJwtService.COOKIE_NAME)
                .value("")
                .path("/")
                .httpOnly(true)
                .secure(isHttps(headers))
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(0)
                .build();
        return Response.noContent().cacheControl(noStore()).cookie(cookie).build();
    }

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "user", "password-change"})
    public AuthUserDto me() {
        String username = identity.getPrincipal().getName();
        Long participantId = identity.getAttribute("participant_id");
        String participantName = null;
        if (participantId != null) {
            ParticipantEntity participant = ParticipantEntity.findById(participantId);
            participantName = participant == null ? null : participant.name;
        }
        boolean mustChange = identity.hasRole("password-change");
        return new AuthUserDto(username, List.copyOf(identity.getRoles()), participantId, participantName, mustChange);
    }

    private NewCookie authCookie(String token, HttpHeaders headers) {
        return new NewCookie.Builder(AppJwtService.COOKIE_NAME)
                .value(token).path("/").httpOnly(true).secure(isHttps(headers))
                .sameSite(NewCookie.SameSite.LAX).maxAge((int) jwtService.expirationSeconds()).build();
    }

    private boolean isHttps(HttpHeaders headers) {
        String proto = headers.getHeaderString("X-Forwarded-Proto");
        return proto != null && proto.split(",", 2)[0].trim().equalsIgnoreCase("https");
    }

    private CacheControl noStore() {
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoStore(true);
        cacheControl.setNoCache(true);
        return cacheControl;
    }
}
