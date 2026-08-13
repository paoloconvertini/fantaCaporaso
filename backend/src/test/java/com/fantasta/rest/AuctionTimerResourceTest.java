package com.fantasta.rest;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import com.fantasta.service.AuctionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AuctionTimerResourceTest {

    @Inject
    AuctionService auctionService;

    @Test
    void countdownClosesRoundAndAssignsGoalkeeperPackage() throws Exception {
        auctionService.reset();
        Long participantId = QuarkusTransaction.requiringNew().call(() -> {
            ParticipantEntity participant = new ParticipantEntity();
            participant.name = "Squadra timer";
            participant.totalCredits = 500;
            participant.persist();

            goalkeeper("Portiere timer 1", 10);
            goalkeeper("Portiere timer 2", 1);
            goalkeeper("Portiere timer 3", 1);
            return participant.id;
        });

        try {
            String adminCookie = given()
                    .contentType(ContentType.JSON)
                    .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                    .when().post("/api/auth/login")
                    .then().statusCode(200)
                    .extract().cookie("FANTASTA_AUTH");

            given().cookie("FANTASTA_AUTH", adminCookie)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "player", "Portiere timer 2",
                            "playerTeam", "Team timer",
                            "playerRole", "PORTIERE",
                            "durationSeconds", 1,
                            "tieBreak", "NONE",
                            "value", 1
                    ))
                    .when().post("/api/start")
                    .then().statusCode(200);

            given().cookie("FANTASTA_AUTH", adminCookie)
                    .contentType(ContentType.JSON)
                    .body(Map.of("participantId", participantId, "amount", 15))
                    .when().post("/api/bids")
                    .then().statusCode(200);

            long timeout = System.currentTimeMillis() + 5_000L;
            boolean closed = false;
            while (System.currentTimeMillis() < timeout) {
                closed = given().cookie("FANTASTA_AUTH", adminCookie)
                        .when().get("/api/round")
                        .jsonPath().getBoolean("closed");
                if (closed) break;
                Thread.sleep(100L);
            }

            assertEquals(true, closed, "Il round non e' stato chiuso dal countdown");
            QuarkusTransaction.requiringNew().run(() -> {
                var rosters = RosterEntity.<RosterEntity>list("participant.id", participantId);
                assertEquals(3, rosters.size());
                assertEquals(3D, rosters.stream().mapToDouble(r -> r.amount).sum());
                RosterEntity starter = rosters.stream()
                        .filter(r -> r.player.name.equals("Portiere timer 1"))
                        .findFirst().orElse(null);
                assertNotNull(starter);
                assertEquals(1D, starter.amount);
            });
        } finally {
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                RosterEntity.delete("participant.id", participantId);
                PlayerEntity.delete("team", "Team timer");
                ParticipantEntity.deleteById(participantId);
            });
        }
    }

    private void goalkeeper(String name, double value) {
        PlayerEntity player = new PlayerEntity();
        player.name = name;
        player.team = "Team timer";
        player.role = Role.PORTIERE;
        player.valore = value;
        player.active = true;
        player.persist();
    }
}
