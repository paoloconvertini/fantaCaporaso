package com.fantasta.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AppIdentityProvider implements IdentityProvider<AppAuthRequest> {

    @Override
    public Class<AppAuthRequest> getRequestType() {
        return AppAuthRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(AppAuthRequest request, AuthenticationRequestContext context) {
        AppJwtService.Claims claims = request.claims;
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(claims.username()))
                .addRole(claims.role())
                .addAttribute("participant_id", claims.participantId());

        return Uni.createFrom().item(builder.build());
    }
}
