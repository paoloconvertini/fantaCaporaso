package com.fantasta.rest;

import com.fantasta.model.ParticipantEntity;
import com.fantasta.model.GiroPickEntity;
import com.fantasta.model.MercatoConfigEntity;
import com.fantasta.model.AppUserEntity;
import com.fantasta.model.PlayerEntity;
import com.fantasta.model.Role;
import com.fantasta.model.RosterEntity;
import com.fantasta.service.AuctionService;
import com.fantasta.service.RandomSelectorService;
import com.fantasta.security.AppJwtService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

    @TestHTTPResource
    URI testUrl;

    @Inject RandomSelectorService selector;

    @Test
    void firstCallRequiresPresenceAndLaterCallsAllowDisconnections() throws Exception {
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            MercatoConfigEntity market = new MercatoConfigEntity();
            market.attiva = true;
            market.numeroMercato = 2;
            market.sessionCode = "presence-test";
            market.presenzeRichieste = true;
            market.persist();
            ParticipantEntity team = participant("Squadra presenza");
            PlayerEntity player = new PlayerEntity();
            player.name = "Calciatore presenza";
            player.team = "Club presenza";
            player.role = Role.DIFENSORE;
            player.persist();
            return new Long[]{market.id, team.id, player.id};
        });
        String admin = jwtService.createToken("test-admin", "admin", null);
        String user = participantToken("presence-user", ids[1]);
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocket chat = null;
        var oldMode = selector.getMode();
        Role oldRole = selector.getRole();
        try {
            selector.setRole(Role.DIFENSORE);
            given().cookie("FANTASTA_AUTH", admin).contentType(ContentType.JSON).body("{}").post("/api/random/next").then().statusCode(409)
                    .body("error", org.hamcrest.Matchers.containsString("Squadra presenza"));
            given().cookie("FANTASTA_AUTH", admin).contentType(ContentType.JSON)
                    .body(Map.of("playerId", ids[2])).post("/api/random/select").then().statusCode(409);
            assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> GiroPickEntity.count("player.id", ids[2])));
            chat = connectSocket("/ws/chat", user, received);
            assertNotNull(received.poll(5, TimeUnit.SECONDS));
            given().cookie("FANTASTA_AUTH", admin).contentType(ContentType.JSON)
                    .body(Map.of("playerId", ids[2])).post("/api/random/select").then().statusCode(200);
            chat.sendClose(WebSocket.NORMAL_CLOSURE, "").get(5, TimeUnit.SECONDS);
            given().cookie("FANTASTA_AUTH", admin).get("/api/random/presence").then().statusCode(200)
                    .body("blocking", org.hamcrest.Matchers.is(false));
            given().cookie("FANTASTA_AUTH", admin).contentType(ContentType.JSON).body("{}").post("/api/random/next").then().statusCode(200);
        } finally {
            if (chat != null) chat.abort();
            switch (oldMode) {
                case ALL -> selector.setAll();
                case OFF -> selector.setOff();
                case ROLE -> selector.setRole(oldRole);
            }
            QuarkusTransaction.requiringNew().run(() -> {
                GiroPickEntity.delete("player.id", ids[2]);
                PlayerEntity.deleteById(ids[2]);
                AppUserEntity.delete("participant.id", ids[1]);
                ParticipantEntity.deleteById(ids[1]);
                MercatoConfigEntity.deleteById(ids[0]);
            });
        }
    }

    private WebSocket connectSocket(String path, String token, LinkedBlockingQueue<String> messages) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "http://" + testUrl.getRawAuthority())
                .header("Cookie", "FANTASTA_AUTH=" + token)
                .buildAsync(URI.create("ws://" + testUrl.getRawAuthority() + path), new WebSocket.Listener() {
                    private final StringBuilder frame = new StringBuilder();

                    @Override
                    public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        frame.append(data);
                        if (last) {
                            messages.add(frame.toString());
                            frame.setLength(0);
                        }
                        socket.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
    }

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
        List<WebSocket> sockets = new ArrayList<>();
        try {
            auctionService.start("Difensore carico HTTP", "Team carico HTTP", "DIFENSORE", 60, "NONE", 5, null);
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) tokens.add(participantToken("http-user-" + i, ids.get(i)));
            LinkedBlockingQueue<String> roundEvents = new LinkedBlockingQueue<>();
            sockets.add(connectSocket("/ws/round", tokens.getFirst(), roundEvents));
            offerBatch(executor, tokens, 0, roundEvents);
            long baseline = offerBatch(executor, tokens, 1, roundEvents);
            List<WebSocket> chats = new ArrayList<>();
            List<LinkedBlockingQueue<String>> chatEvents = new ArrayList<>();
            for (String token : tokens) {
                LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
                WebSocket chat = connectSocket("/ws/chat", token, events);
                sockets.add(chat);
                chats.add(chat);
                chatEvents.add(events);
                assertNotNull(events.poll(5, TimeUnit.SECONDS));
            }
            Future<?> traffic = executor.submit(() -> chats.forEach(chat -> chat.sendText(":Messaggio di prova", true).join()));
            long withChat = offerBatch(executor, tokens, 2, roundEvents);
            traffic.get(5, TimeUnit.SECONDS);
            for (int i = 0; i < 16; i++) assertNotNull(chatEvents.getFirst().poll(5, TimeUnit.SECONDS));
            System.out.printf("16 offerte e notifiche round, senza chat: %d ms, con chat: %d ms%n", baseline, withChat);

            assertEquals(16, auctionService.get().bids.size());
            String firstCookie = participantToken("http-user-0", ids.get(0));
            given().cookie("FANTASTA_AUTH", firstCookie).contentType(ContentType.JSON)
                    .body(Map.of("participantId", ids.get(15), "amount", 22))
                    .when().post("/api/bids").then().statusCode(200);
            assertEquals(16, auctionService.get().bids.size());
            assertEquals(22D, auctionService.get().bids.get(String.valueOf(ids.get(0))));
        } finally {
            sockets.forEach(WebSocket::abort);
            executor.shutdownNow();
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                PlayerEntity.delete("team", "Team carico HTTP");
                for (Long id : ids) { AppUserEntity.delete("participant.id", id); ParticipantEntity.deleteById(id); }
            });
        }
    }

    private long offerBatch(ExecutorService executor, List<String> tokens, int increment,
                            LinkedBlockingQueue<String> roundEvents) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String cookie = tokens.get(i);
            double amount = i + 1D + increment;
            requests.add(executor.submit(() -> {
                start.await();
                return given().cookie("FANTASTA_AUTH", cookie).contentType(ContentType.JSON)
                        .body(Map.of("participantId", -1, "amount", amount))
                        .post("/api/bids").statusCode();
            }));
        }
        long begin = System.nanoTime();
        start.countDown();
        for (int i = 0; i < tokens.size(); i++) {
            String event = roundEvents.poll(5, TimeUnit.SECONDS);
            assertNotNull(event);
            org.junit.jupiter.api.Assertions.assertTrue(event.contains("BID_ADDED"));
        }
        for (Future<Integer> request : requests) assertEquals(200, request.get(5, TimeUnit.SECONDS));
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
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
            String userCookie = participantToken("utente-identita", ids[0]);

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
                AppUserEntity.delete("participant.id", ids[0]);
                ParticipantEntity.deleteById(ids[0]);
                ParticipantEntity.deleteById(ids[1]);
            });
        }
    }

    @Test
    void authenticatedParticipantCanWithdrawBidThroughHttp() {
        auctionService.reset();
        Long participantId = QuarkusTransaction.requiringNew().call(() -> {
            ParticipantEntity participant = participant("Squadra ritiro HTTP");
            PlayerEntity player = new PlayerEntity();
            player.name = "Difensore ritiro HTTP";
            player.team = "Team ritiro HTTP";
            player.role = Role.DIFENSORE;
            player.valore = 5D;
            player.active = true;
            player.persist();
            return participant.id;
        });

        try {
            auctionService.start("Difensore ritiro HTTP", "Team ritiro HTTP", "DIFENSORE", 30, "NONE", 5, null);
            String userCookie = participantToken("utente-ritiro", participantId);

            given().cookie("FANTASTA_AUTH", userCookie)
                    .contentType(ContentType.JSON)
                    .body(Map.of("participantId", participantId, "amount", 7))
                    .when().post("/api/bids")
                    .then().statusCode(200)
                    .body("bidders", org.hamcrest.Matchers.contains("Squadra ritiro HTTP"));

            given().cookie("FANTASTA_AUTH", userCookie)
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when().post("/api/bids/withdraw")
                    .then().statusCode(200)
                    .body("bidders", org.hamcrest.Matchers.empty());
            assertEquals(0, auctionService.get().bids.size());
        } finally {
            auctionService.reset();
            QuarkusTransaction.requiringNew().run(() -> {
                PlayerEntity.delete("team", "Team ritiro HTTP");
                AppUserEntity.delete("participant.id", participantId);
                ParticipantEntity.deleteById(participantId);
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
                AppUserEntity.delete("participant.id", participantId);
                ParticipantEntity.deleteById(participantId);
            });
        }
    }

    private String participantToken(String username, Long participantId) {
        QuarkusTransaction.requiringNew().run(() -> {
            if (AppUserEntity.count("username", username) == 0) {
                AppUserEntity account = new AppUserEntity(); account.username = username;
                account.role = "user"; account.passwordHash = "unused-test-hash";
                account.participant = ParticipantEntity.findById(participantId); account.persist();
            }
        });
        return jwtService.createToken(username, "user", participantId);
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
