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
    @jakarta.inject.Inject com.fantasta.service.AppUserService users;

    @Override
    public Class<AppAuthRequest> getRequestType() {
        return AppAuthRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(AppAuthRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            var user = users.currentIdentity(request.claims.username());
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(user.username))
                    .addRoles(new java.util.HashSet<>(user.roles));
            if (user.participantId != null) builder.addAttribute("participant_id", user.participantId);
            return builder.build();
        });
    }
}
