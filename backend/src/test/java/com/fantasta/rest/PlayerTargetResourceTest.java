package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.dto.CreateUserRequest;
import com.fantasta.service.AppUserService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PlayerTargetResourceTest {
    @Inject AppUserService users;
    Long teamId, playerId;
    @BeforeEach void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            ParticipantEntity team = new ParticipantEntity(); team.name = "Target test team"; team.totalCredits = 100; team.persist(); teamId = team.id;
            for (String name : new String[]{"target-player", "target-observer-one", "target-observer-two", "target-observer-unbound"}) {
                CreateUserRequest request = new CreateUserRequest(); request.username = name; request.password = "test-target-password";
                request.role = name.equals("target-player") ? "user" : "observer"; request.permanentPassword = true;
                request.participantId = name.endsWith("unbound") ? null : teamId;
                users.createUser(request);
            }
            PlayerEntity player = new PlayerEntity(); player.name = "Target test defender"; player.team = "Target club";
            player.role = Role.DIFENSORE; player.active = true; player.assigned = false; player.valore = 20D; player.persist(); playerId = player.id;
        });
    }
    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            PlayerTargetEntity.delete("player.id", playerId);
            RosterEntity.delete("player.id", playerId);
            AppUserEntity.delete("username like ?1", "target-%");
            PlayerEntity.deleteById(playerId); ParticipantEntity.deleteById(teamId);
        });
    }
    String login(String username) {
        return given().contentType(ContentType.JSON).body(Map.of("username", username, "password", "test-target-password"))
                .post("/api/auth/login").then().statusCode(200).extract().cookie("FANTASTA_AUTH");
    }
    @Test void observerSharesTeamWithoutBiddingAndTargetsStayPrivate() {
        String one = login("target-observer-one"), two = login("target-observer-two");
        given().cookie("FANTASTA_AUTH", one).get("/api/auth/me").then().statusCode(200).body("roles", hasItem("observer")).body("participantId", equalTo(teamId.intValue()));
        given().cookie("FANTASTA_AUTH", one).contentType(ContentType.JSON).body(Map.of("participantId", teamId, "amount", 1)).post("/api/bids").then().statusCode(403);
        given().cookie("FANTASTA_AUTH", one).contentType(ContentType.JSON).body("{}").post("/api/bids/withdraw").then().statusCode(403);
        given().cookie("FANTASTA_AUTH", one).get("/api/participant/summary").then().statusCode(200);
        given().cookie("FANTASTA_AUTH", one).put("/api/targets/" + playerId).then().statusCode(204);
        given().cookie("FANTASTA_AUTH", one).put("/api/targets/" + playerId).then().statusCode(204);
        given().cookie("FANTASTA_AUTH", one).get("/api/targets").then().statusCode(200).body("size()", equalTo(1));
        given().cookie("FANTASTA_AUTH", two).get("/api/targets").then().statusCode(200).body("size()", equalTo(0));
        given().cookie("FANTASTA_AUTH", one).get("/api/targets/analysis").then().statusCode(200).body("team", equalTo("Target test team"));
        given().cookie("FANTASTA_AUTH", login("target-player")).get("/api/targets").then().statusCode(403);
        given().cookie("FANTASTA_AUTH", login("target-observer-unbound")).get("/api/targets").then().statusCode(403);
        given().cookie("FANTASTA_AUTH", one).header("Origin", "http://localhost:4200")
                .delete("/api/targets/" + playerId).then().statusCode(204);
        given().cookie("FANTASTA_AUTH", one).get("/api/targets").then().statusCode(200).body("size()", equalTo(0));
    }
    @Test void usesRoleSlotsAndUpdatesAfterAnAcquisition() {
        String one = login("target-observer-one");
        given().cookie("FANTASTA_AUTH", one).put("/api/targets/" + playerId).then().statusCode(204);
        given().cookie("FANTASTA_AUTH", one).get("/api/targets/analysis").then().statusCode(200)
                .body("roles.find { it.role == 'DIFENSORE' }.roleSlots", equalTo(8))
                .body("roles.find { it.role == 'DIFENSORE' }.totalSlots", equalTo(25))
                .body("roles.find { it.role == 'DIFENSORE' }.maximumBid", equalTo(76))
                .body("roles.find { it.role == 'DIFENSORE' }.targets.size()", equalTo(1))
                .body("roles.find { it.role == 'ATTACCANTE' }.targets.size()", equalTo(0));
        QuarkusTransaction.requiringNew().run(() -> {
            RosterEntity entry = new RosterEntity(); entry.player = PlayerEntity.findById(playerId);
            entry.participant = ParticipantEntity.findById(teamId); entry.amount = 2D; entry.persist();
        });
        given().cookie("FANTASTA_AUTH", one).get("/api/targets/analysis").then().statusCode(200)
                .body("credits", equalTo(98))
                .body("roles.find { it.role == 'DIFENSORE' }.roleSlots", equalTo(7))
                .body("roles.find { it.role == 'DIFENSORE' }.maximumBid", equalTo(75))
                .body("roles.find { it.role == 'DIFENSORE' }.targets.size()", equalTo(0))
                .body("unavailable.size()", equalTo(1));
    }

    @Test void rejectsUnavailableTargetsAndAdminCanAssociateExistingObserver() {
        QuarkusTransaction.requiringNew().run(() -> { PlayerEntity p = PlayerEntity.findById(playerId); p.active = false; });
        String one = login("target-observer-one");
        given().cookie("FANTASTA_AUTH", one).put("/api/targets/" + playerId).then().statusCode(400);
        String unbound = login("target-observer-unbound");
        String admin = given().contentType(ContentType.JSON).body(Map.of("username", "test-admin", "password", "test-password-strong"))
                .post("/api/auth/login").then().statusCode(200).extract().cookie("FANTASTA_AUTH");
        given().cookie("FANTASTA_AUTH", admin).contentType(ContentType.JSON).body(Map.of("participantId", teamId))
                .put("/api/admin/users/target-observer-unbound/observer-team").then().statusCode(204);
        given().cookie("FANTASTA_AUTH", unbound).get("/api/auth/me").then().statusCode(200).body("participantId", equalTo(teamId.intValue()));
        given().cookie("FANTASTA_AUTH", unbound).get("/api/targets").then().statusCode(200);
        given().cookie("FANTASTA_AUTH", unbound).contentType(ContentType.JSON).body("{}").post("/api/bids/withdraw").then().statusCode(403);
    }
}
