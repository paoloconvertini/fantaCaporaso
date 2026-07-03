package com.fantasta.service;

import com.fantasta.dto.CreateKeycloakUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class KeycloakAdminService {

    @ConfigProperty(name = "app.keycloak.admin.url", defaultValue = "")
    String keycloakUrl;

    @ConfigProperty(name = "app.keycloak.admin.realm", defaultValue = "fantasta")
    String realm;

    @ConfigProperty(name = "app.keycloak.admin.user", defaultValue = "")
    String adminUser;

    @ConfigProperty(name = "app.keycloak.admin.password", defaultValue = "")
    String adminPassword;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    public void createUser(CreateKeycloakUserRequest request) {
        validate(request);

        try {
            String token = adminToken();
            createKeycloakUser(token, request);
            String userId = findUserId(token, request.username);
            assignRealmRole(token, userId, "user");
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException("Errore creazione utente Keycloak: " + e.getMessage(), e);
        }
    }

    private void validate(CreateKeycloakUserRequest request) {
        if (keycloakUrl.isBlank() || adminUser.isBlank() || adminPassword.isBlank()) {
            throw new BadRequestException("Configurazione Keycloak Admin mancante");
        }
        if (request == null) {
            throw new BadRequestException("Dati utente mancanti");
        }
        if (request.username == null || request.username.isBlank()) {
            throw new BadRequestException("Username obbligatorio");
        }
        if (request.password == null || request.password.isBlank()) {
            throw new BadRequestException("Password obbligatoria");
        }
        if (request.participantId == null) {
            throw new BadRequestException("Nome squadra obbligatorio");
        }
    }

    private String adminToken() throws IOException, InterruptedException {
        String form = form(Map.of(
                "grant_type", "password",
                "client_id", "admin-cli",
                "username", adminUser,
                "password", adminPassword
        ));

        HttpRequest request = HttpRequest.newBuilder(tokenUri())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "Login admin Keycloak fallito");

        Map<?, ?> body = objectMapper.readValue(response.body(), Map.class);
        Object token = body.get("access_token");
        if (token == null) {
            throw new InternalServerErrorException("Token admin Keycloak non ricevuto");
        }
        return token.toString();
    }

    private void createKeycloakUser(String token, CreateKeycloakUserRequest request) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "username", request.username.trim(),
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of("participant_id", List.of(String.valueOf(request.participantId))),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", request.password,
                        "temporary", false
                ))
        );

        HttpRequest httpRequest = HttpRequest.newBuilder(adminUri("/users"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 409) {
            throw new BadRequestException("Username gia' esistente");
        }
        ensureSuccess(response, "Creazione utente Keycloak fallita");
    }

    private String findUserId(String token, String username) throws IOException, InterruptedException {
        URI uri = adminUri("/users?username=" + encode(username.trim()) + "&exact=true");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "Ricerca utente Keycloak fallita");

        List<?> users = objectMapper.readValue(response.body(), List.class);
        if (users.isEmpty()) {
            throw new InternalServerErrorException("Utente creato ma non ritrovato in Keycloak");
        }

        Object id = ((Map<?, ?>) users.get(0)).get("id");
        if (id == null) {
            throw new InternalServerErrorException("Id utente Keycloak non ricevuto");
        }
        return id.toString();
    }

    private void assignRealmRole(String token, String userId, String roleName) throws IOException, InterruptedException {
        HttpRequest roleRequest = HttpRequest.newBuilder(adminUri("/roles/" + encode(roleName)))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> roleResponse = http.send(roleRequest, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(roleResponse, "Lettura ruolo Keycloak fallita");

        Map<?, ?> role = objectMapper.readValue(roleResponse.body(), Map.class);
        String body = objectMapper.writeValueAsString(List.of(role));

        HttpRequest assignRequest = HttpRequest.newBuilder(adminUri("/users/" + encode(userId) + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> assignResponse = http.send(assignRequest, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(assignResponse, "Assegnazione ruolo Keycloak fallita");
    }

    private void ensureSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new InternalServerErrorException(message + " (" + response.statusCode() + "): " + response.body());
    }

    private URI tokenUri() {
        return URI.create(trimSlash(keycloakUrl) + "/realms/master/protocol/openid-connect/token");
    }

    private URI adminUri(String path) {
        return URI.create(trimSlash(keycloakUrl) + "/admin/realms/" + encode(realm) + path);
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
