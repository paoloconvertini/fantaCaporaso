package com.fantasta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceTest {

    @Test
    void rejectsAnonymousAccessToProtectedEndpoint() {
        given()
                .when().get("/api/auth/me")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"));
    }

    @Test
    void rejectsInvalidCredentialsWithoutSettingCookie() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"wrong\"}")
                .when().post("/api/auth/login")
                .then()
                .statusCode(401)
                .header("Set-Cookie", nullValue());
    }

    @Test
    void loginCookieAuthenticatesAndLogoutExpiresIt() {
        String cookie = given()
                .header("X-Forwarded-Proto", "https")
                .contentType(ContentType.JSON)
                .body("{\"username\":\"TEST-ADMIN\",\"password\":\"test-password-strong\"}")
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .header("Cache-Control", containsString("no-store"))
                .header("Set-Cookie", allOf(containsString("HttpOnly"), containsString("Secure"), containsString("SameSite=Lax")))
                .body("username", equalTo("test-admin"))
                .body("roles", hasItem("admin"))
                .extract().cookie("FANTASTA_AUTH");

        given()
                .cookie("FANTASTA_AUTH", cookie)
                .when().get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo("test-admin"));

        given()
                .header("X-Forwarded-Proto", "https")
                .cookie("FANTASTA_AUTH", cookie)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/auth/logout")
                .then()
                .statusCode(204)
                .header("Set-Cookie", allOf(containsString("Max-Age=0"), containsString("Secure")));
    }

    @Test
    void bearerTokenAlsoAuthenticates() {
        String token = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("FANTASTA_AUTH");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("roles", hasItem("admin"));
    }

    @Test
    void adminCanCreateUserWithSimplePasswordButRejectsLessThanFourCharacters() {
        String token = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("FANTASTA_AUTH");

        given()
                .cookie("FANTASTA_AUTH", token)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"too-weak-admin\",\"password\":\"abc\",\"role\":\"admin\"}")
                .when().post("/api/admin/users")
                .then().statusCode(400);

        given()
                .cookie("FANTASTA_AUTH", token)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"simple-admin\",\"password\":\"test\",\"role\":\"admin\"}")
                .when().post("/api/admin/users")
                .then().statusCode(201);
    }

    @Test
    void adminCanCreateParticipantWithPermanentSharedPassword() {
        String token = given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                .when().post("/api/auth/login").then().statusCode(200)
                .extract().cookie("FANTASTA_AUTH");

        given().cookie("FANTASTA_AUTH", token).contentType(ContentType.JSON)
                .body("{\"username\":\"permanent-player\",\"password\":\"fanta2026\",\"participantName\":\"Permanent Team\",\"totalCredits\":500,\"permanentPassword\":true}")
                .when().post("/api/admin/users").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("{\"username\":\"permanent-player\",\"password\":\"fanta2026\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("mustChangePassword", equalTo(false))
                .body("roles", hasItem("user"));

        given().cookie("FANTASTA_AUTH", token)
                .when().get("/api/participant/all")
                .then().statusCode(200)
                .body("find { it.name == 'Permanent Team' }.username", equalTo("permanent-player"));
    }

    @Test
    void participantMustChangeTemporaryPasswordBeforeUsingApplication() {
        String adminCookie = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200).extract().cookie("FANTASTA_AUTH");

        given().cookie("FANTASTA_AUTH", adminCookie).contentType(ContentType.JSON)
                .body("{\"username\":\"new-player\",\"password\":\"temp1234\",\"participantName\":\"Nuova Squadra\",\"totalCredits\":500}")
                .when().post("/api/admin/users").then().statusCode(201);

        String restrictedCookie = given().contentType(ContentType.JSON)
                .body("{\"username\":\"new-player\",\"password\":\"temp1234\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("mustChangePassword", equalTo(true))
                .body("roles", hasItem("password-change"))
                .extract().cookie("FANTASTA_AUTH");

        given().cookie("FANTASTA_AUTH", restrictedCookie)
                .when().get("/api/players/free")
                .then().statusCode(403);

        String normalCookie = given().cookie("FANTASTA_AUTH", restrictedCookie)
                .contentType(ContentType.JSON).body("{\"password\":\"personal123\"}")
                .when().post("/api/auth/password")
                .then().statusCode(200)
                .body("mustChangePassword", equalTo(false))
                .body("roles", hasItem("user"))
                .extract().cookie("FANTASTA_AUTH");

        given().cookie("FANTASTA_AUTH", normalCookie)
                .when().get("/api/players/free")
                .then().statusCode(200);

        given().cookie("FANTASTA_AUTH", normalCookie)
                .queryParam("participantId", 1)
                .contentType(ContentType.JSON)
                .body("{\"playerId\":1}")
                .when().post("/api/admin/rosters/svincola")
                .then().statusCode(403);
    }
}
