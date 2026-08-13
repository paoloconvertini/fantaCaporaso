package com.fantasta.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class AppAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    AppJwtService jwtService;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String token = tokenFromCookie(context);
        if (token == null) {
            token = tokenFromAuthorization(context);
        }
        if (token == null || token.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        try {
            return identityProviderManager.authenticate(new AppAuthRequest(jwtService.verify(token)));
        } catch (RuntimeException e) {
            return Uni.createFrom().nullItem();
        }
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Set<Class<? extends io.quarkus.security.identity.request.AuthenticationRequest>> getCredentialTypes() {
        return Set.of(AppAuthRequest.class);
    }

    private String tokenFromCookie(RoutingContext context) {
        Cookie cookie = context.request().getCookie(AppJwtService.COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }

    private String tokenFromAuthorization(RoutingContext context) {
        String value = context.request().getHeader("Authorization");
        if (value == null || !value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return null;
        }
        return value.substring("Bearer ".length()).trim();
    }
}
