package com.fantasta.util;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
public class AppSecurityConfigValidator {

    @ConfigProperty(name = "app.jwt.secret")
    String jwtSecret;

    @ConfigProperty(name = "app.jwt.expiration-seconds")
    long jwtExpirationSeconds;

    @PostConstruct
    void validate() {
        if (jwtExpirationSeconds <= 0 || jwtExpirationSeconds > 86_400) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS deve essere compreso tra 1 e 86400");
        }
        if (LaunchMode.current() == LaunchMode.NORMAL
                && (jwtSecret.length() < 32 || "dev-secret-change-me".equals(jwtSecret))) {
            throw new IllegalStateException("JWT_SECRET deve contenere almeno 32 caratteri in produzione");
        }
    }
}
