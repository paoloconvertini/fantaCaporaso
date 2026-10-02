package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.service.ParticipantService;
import com.fantasta.service.AuctionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AdminAssignmentResourceTest {
    @Inject ParticipantService participantService;
    @Inject AuctionService auctionService;

    @Test
    void adminHttpRouteAssignsAndCorrectsPriceWithLegacyReservationState() {
        auctionService.reset();
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            ParticipantEntity owner = new ParticipantEntity();
            owner.name = "HTTP assegnazione manuale";
            owner.totalCredits = 500;
            owner.persist();
            PlayerEntity player = new PlayerEntity();
            player.name = "HTTP giocatore manuale";
            player.team = "Roma";
            player.role = Role.DIFENSORE;
            player.valore = 5D;
            player.persist();
            AuctionRoundStateEntity saved = new AuctionRoundStateEntity();
            saved.id = "current";
            saved.stateJson = "{\"roundId\":\"legacy-reservation-test\",\"player\":\"Vecchio test prenotazione\","
                    + "\"playerTeam\":\"Napoli\",\"playerRole\":\"CENTROCAMPISTA\",\"closed\":true,"
                    + "\"reservationRequired\":true,\"phase\":\"OFFERS\",\"reservedUsers\":[],"
                    + "\"biddingDurationSeconds\":30,\"bids\":{},\"historyBids\":{}}";
            saved.persist();
            return new Long[]{owner.id, player.id};
        });
        try {
            String cookie = given().contentType(ContentType.JSON)
                    .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                    .post("/api/auth/login").then().statusCode(200)
                    .extract().cookie("FANTASTA_AUTH");
            for (int amount : new int[]{3, 5}) {
                given().cookie("FANTASTA_AUTH", cookie).contentType(ContentType.JSON)
                        .body("{\"participantId\":" + ids[0] + ",\"amount\":" + amount + "}")
                        .put("/api/admin/assignments/" + ids[1]).then().statusCode(200)
                        .body("assignment.player", equalTo("HTTP giocatore manuale"))
                        .body("assignment.winner", equalTo("HTTP assegnazione manuale"))
                        .body("assignment.amount", equalTo((float) amount));
                QuarkusTransaction.requiringNew().run(() -> {
                    RosterEntity row = RosterEntity.find("player.id", ids[1]).firstResult();
                    assertNotNull(row);
                    assertEquals(ids[0], row.participant.id);
                    assertEquals((double) amount, row.amount);
                    assertEquals(500 - amount, participantService.remainingCreditsById(ids[0], 500));
                    assertEquals(1L, RosterEntity.count("player.id", ids[1]));
                });
            }
        } finally {
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                RosterEntity.delete("player.id", ids[1]);
                PlayerOwnerHistoryEntity.delete("player.id", ids[1]);
                PlayerEntity.deleteById(ids[1]);
                ParticipantEntity.deleteById(ids[0]);
            });
        }
    }

    @Test
    void manualTieResolutionIsAlsoRecordedInHistory() {
        auctionService.reset();
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            ParticipantEntity first = new ParticipantEntity();
            first.name = "HTTP spareggio primo";
            first.totalCredits = 500;
            first.persist();
            ParticipantEntity second = new ParticipantEntity();
            second.name = "HTTP spareggio secondo";
            second.totalCredits = 500;
            second.persist();
            PlayerEntity player = new PlayerEntity();
            player.name = "HTTP spareggio manuale";
            player.team = "Roma";
            player.role = Role.DIFENSORE;
            player.valore = 5D;
            player.persist();
            return new Long[]{first.id, second.id, player.id};
        });
        String roundId = auctionService.start("HTTP spareggio manuale", "Roma", "DIFENSORE",
                null, "NONE", 5, null).roundId;
        try {
            auctionService.bid(ids[0], 2D);
            auctionService.bid(ids[1], 2D);
            auctionService.close();
            String cookie = given().contentType(ContentType.JSON)
                    .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                    .post("/api/auth/login").then().statusCode(200)
                    .extract().cookie("FANTASTA_AUTH");
            given().cookie("FANTASTA_AUTH", cookie).contentType(ContentType.JSON)
                    .body("{\"participantId\":" + ids[0] + ",\"amount\":3}")
                    .put("/api/admin/assignments/" + ids[2]).then().statusCode(200);
            QuarkusTransaction.requiringNew().run(() -> {
                AuctionHistoryEntity history = AuctionHistoryEntity.find("roundId", roundId).firstResult();
                assertNotNull(history);
                assertEquals(ids[0], history.winnerParticipantId);
                assertEquals(3D, history.winningAmount);
                assertEquals(2L, AuctionHistoryBidEntity.count("history", history));
            });
        } finally {
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                AuctionHistoryEntity history = AuctionHistoryEntity.find("roundId", roundId).firstResult();
                if (history != null) {
                    AuctionHistoryBidEntity.delete("history", history);
                    history.delete();
                }
                RosterEntity.delete("player.id", ids[2]);
                PlayerOwnerHistoryEntity.delete("player.id", ids[2]);
                PlayerEntity.deleteById(ids[2]);
                ParticipantEntity.deleteById(ids[0]);
                ParticipantEntity.deleteById(ids[1]);
            });
        }
    }

    @Test
    void invalidAssignmentReturnsValidationErrorInsteadOfServerError() {
        String cookie = given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-strong\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("FANTASTA_AUTH");
        given().cookie("FANTASTA_AUTH", cookie).contentType(ContentType.JSON)
                .body("{\"participantId\":0,\"amount\":1}")
                .put("/api/admin/assignments/0").then().statusCode(400);
    }

    @Test
    void assignmentRequiresAuthentication() {
        given().contentType(ContentType.JSON).body("{\"participantId\":0,\"amount\":1}")
                .put("/api/admin/assignments/0").then().statusCode(401);
    }
}
