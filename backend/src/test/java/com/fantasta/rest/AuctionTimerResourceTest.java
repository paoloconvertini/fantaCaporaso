package com.fantasta.rest;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import com.fantasta.service.AuctionService;
import com.fantasta.security.AppJwtService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AuctionTimerResourceTest {

    @Inject
    AuctionService auctionService;

    @Inject
    AppJwtService jwtService;

    @Test
    void acceptsSixteenSimultaneousHttpBidsAndKeepsLatestBid() throws Exception {
        auctionService.reset();
        List<Long> ids = QuarkusTransaction.requiringNew().call(() -> {
            List<Long> participantIds = new ArrayList<>();
            for (int i = 1; i <= 16; i++) participantIds.add(participant("HTTP squadra " + i).id);
            PlayerEntity player = new PlayerEntity();
            player.name = "Difensore carico HTTP";
            player.team = "Team carico HTTP";
            player.role = Role.DIFENSORE;
            player.valore = 5D;
            player.active = true;
            player.persist();
            return participantIds;
        });

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            auctionService.start("Difensore carico HTTP", "Team carico HTTP", "DIFENSORE", 60, "NONE", 5, null);
            List<Future<Integer>> requests = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                Long participantId = ids.get(i);
                double amount = i + 1D;
                String cookie = jwtService.createToken("http-user-" + i, "user", participantId);
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return given().cookie("FANTASTA_AUTH", cookie)
                            .contentType(ContentType.JSON)
                            .body(Map.of("participantId", -1, "amount", amount))
                            .when().post("/api/bids").statusCode();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<Integer> request : requests) assertEquals(200, request.get());

            assertEquals(16, auctionService.get().bids.size());
            String firstCookie = jwtService.createToken("http-user-0", "user", ids.get(0));
            given().cookie("FANTASTA_AUTH", firstCookie).contentType(ContentType.JSON)
                    .body(Map.of("participantId", ids.get(15), "amount", 22))
                    .when().post("/api/bids").then().statusCode(200);
            assertEquals(16, auctionService.get().bids.size());
            assertEquals(22D, auctionService.get().bids.get(String.valueOf(ids.get(0))));
        } finally {
            executor.shutdownNow();
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                PlayerEntity.delete("team", "Team carico HTTP");
                for (Long id : ids) ParticipantEntity.deleteById(id);
            });
        }
    }

    @Test
    void participantIdentityFromCookieOverridesPayloadParticipantId() {
        auctionService.reset();
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            ParticipantEntity real = participant("Squadra autenticata");
            ParticipantEntity forged = participant("Squadra nel payload");
            PlayerEntity player = new PlayerEntity();
            player.name = "Difensore identita";
            player.team = "Team identita";
            player.role = Role.DIFENSORE;
            player.valore = 5D;
            player.active = true;
            player.persist();
            return new Long[]{real.id, forged.id};
        });

        try {
            auctionService.start("Difensore identita", "Team identita", "DIFENSORE", 30, "NONE", 5, null);
            String userCookie = jwtService.createToken("utente-identita", "user", ids[0]);

            given().cookie("FANTASTA_AUTH", userCookie)
                    .contentType(ContentType.JSON)
                    .body(Map.of("participantId", ids[1], "amount", 7))
                    .when().post("/api/bids")
                    .then().statusCode(200)
                    .body("bidders", org.hamcrest.Matchers.contains("Squadra autenticata"));
            assertEquals(7D, auctionService.get().bids.get(String.valueOf(ids[0])));
            assertEquals(null, auctionService.get().bids.get(String.valueOf(ids[1])));
        } finally {
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                PlayerEntity.delete("team", "Team identita");
                ParticipantEntity.deleteById(ids[0]);
                ParticipantEntity.deleteById(ids[1]);
            });
        }
    }

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

    private ParticipantEntity participant(String name) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.name = name;
        participant.totalCredits = 500;
        participant.persist();
        return participant;
    }
}
