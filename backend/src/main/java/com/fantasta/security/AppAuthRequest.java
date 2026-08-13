package com.fantasta.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public class AppAuthRequest extends BaseAuthenticationRequest {
    public final AppJwtService.Claims claims;

    public AppAuthRequest(AppJwtService.Claims claims) {
        this.claims = claims;
    }
}
