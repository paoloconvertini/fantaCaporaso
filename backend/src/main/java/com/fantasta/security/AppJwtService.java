package com.fantasta.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class AppJwtService {

    public static final String COOKIE_NAME = "FANTASTA_AUTH";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "user", "password-change");

    @ConfigProperty(name = "app.jwt.secret")
    String secret;

    @ConfigProperty(name = "app.jwt.expiration-seconds", defaultValue = "28800")
    long expirationSeconds;

    public String createToken(String username, String role, Long participantId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username obbligatorio");
        }
        if (role == null || !ALLOWED_ROLES.contains(role.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Ruolo non valido");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("Durata JWT non valida");
        }
        long now = Instant.now().getEpochSecond();
        long exp = now + expirationSeconds;
        String normalizedRole = role.toLowerCase(Locale.ROOT);

        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{"
                + json("sub", username) + ","
                + json("username", username) + ","
                + json("role", normalizedRole) + ","
                + "\"participant_id\":" + (participantId == null ? "null" : participantId) + ","
                + "\"iat\":" + now + ","
                + "\"exp\":" + exp
                + "}";

        String unsigned = b64(header.getBytes(StandardCharsets.UTF_8)) + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + sign(unsigned);
    }

    public Claims verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Token non valido");
            }

            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) {
                throw new IllegalArgumentException("Firma token non valida");
            }

            JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())) {
                throw new IllegalArgumentException("Header token non valido");
            }

            String username = requiredText(payload, "username");
            String role = requiredText(payload, "role").toLowerCase(Locale.ROOT);
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalArgumentException("Ruolo token non valido");
            }
            JsonNode participantNode = payload.get("participant_id");
            Long participantId = participantNode == null || participantNode.isNull() ? null : participantNode.longValue();
            long exp = payload.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Token scaduto");
            }
            return new Claims(username, role, participantId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token non valido", e);
        }
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return b64(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String json(String key, String value) {
        return "\"" + key + "\":\"" + escape(value) + "\"";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String requiredText(JsonNode payload, String name) {
        String value = payload.path(name).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Claim token mancante: " + name);
        }
        return value;
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) return false;
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    public record Claims(String username, String role, Long participantId) {}
}
