package com.fantasta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;

@QuarkusTest
class MercatoResourceTest {
    @jakarta.inject.Inject com.fantasta.security.AppJwtService jwt;

    @Test
    void observerCannotConfirmNoDepartures() {
        Long id = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            var user = new com.fantasta.model.AppUserEntity();
            user.username = "no-departures-observer";
            user.passwordHash = "unused";
            user.role = "observer";
            user.persist();
            return user.id;
        });
        try {
            given().cookie("FANTASTA_AUTH", jwt.createToken("no-departures-observer", "observer", null))
                    .contentType(ContentType.JSON).body("{\"sessionCode\":\"test\"}")
                    .post("/api/mercato/departures/none").then().statusCode(403);
        } finally {
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() ->
                    com.fantasta.model.AppUserEntity.deleteById(id));
        }
    }

    @Test
    void noDeparturesRequiresAuthenticationAndSessionCode() {
        given().contentType(ContentType.JSON).body("{}")
                .post("/api/mercato/departures/none").then().statusCode(401);
        String cookie = given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                .post("/api/auth/login").then().statusCode(200).extract().cookie("FANTASTA_AUTH");
        given().cookie("FANTASTA_AUTH", cookie).contentType(ContentType.JSON).body("{}")
                .post("/api/mercato/departures/none").then().statusCode(400);
    }
}
