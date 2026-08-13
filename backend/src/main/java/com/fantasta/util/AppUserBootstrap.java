package com.fantasta.util;

import com.fantasta.dto.CreateUserRequest;
import com.fantasta.model.AppUserEntity;
import com.fantasta.service.AppUserService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
public class AppUserBootstrap {

    @ConfigProperty(name = "app.bootstrap.admin.username", defaultValue = "")
    String adminUsername;

    @ConfigProperty(name = "app.bootstrap.admin.password", defaultValue = "")
    String adminPassword;

    @Inject
    AppUserService appUserService;

    @PostConstruct
    public void init() {
        if (AppUserEntity.count() > 0 || adminUsername.isBlank() || adminPassword.isBlank()) {
            return;
        }

        CreateUserRequest request = new CreateUserRequest();
        request.username = adminUsername;
        request.password = adminPassword;
        request.role = "admin";
        request.participantId = null;
        appUserService.createUser(request);
        Log.infof("Bootstrap admin user created: %s", adminUsername);
    }
}
