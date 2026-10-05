package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.service.AuctionService;
import com.fantasta.service.PasswordService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ToccoResourceTest {
    @Inject AuctionService auction;
    @Inject PasswordService passwords;

    String login(String username) {
        return given().contentType(ContentType.JSON).body(Map.of("username", username, "password", "tocco-password"))
                .post("/api/auth/login").then().statusCode(200).extract().cookie("FANTASTA_AUTH");
    }

    @Test
    void httpToccoUsesAuthenticatedTeamHidesChoicesAndRecordsOriginalBids() {
        auction.reset();
        String prefix = "tocco-http-" + UUID.randomUUID();
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            ParticipantEntity a = new ParticipantEntity(); a.name=prefix+"-a"; a.totalCredits=500; a.persist();
            ParticipantEntity b = new ParticipantEntity(); b.name=prefix+"-b"; b.totalCredits=500; b.persist();
            for (String role : List.of("user-a", "user-b", "observer")) {
                AppUserEntity u = new AppUserEntity(); u.username=prefix+role; u.passwordHash=passwords.hash("tocco-password");
                u.role=role.equals("observer") ? "observer" : "user"; u.participant=role.equals("user-b") ? b : a; u.persist();
            }
            PlayerEntity player = new PlayerEntity(); player.name=prefix; player.team="Tocco HTTP"; player.role=Role.DIFENSORE; player.valore=5D; player.active=true; player.persist();
            return new Long[]{a.id,b.id,player.id};
        });
        String roundId = null;
        try {
            PlayerEntity player = QuarkusTransaction.requiringNew().call(() -> PlayerEntity.findById(ids[2]));
            RoundState round = auction.start(player.name, player.team, player.role.name(), null, "NONE", 5, null);
            roundId=round.roundId;
            auction.bid(ids[0],10D); auction.bid(ids[1],10D); auction.close();
            String admin=given().contentType(ContentType.JSON).body(Map.of("username","test-admin","password","test-password-strong"))
                    .post("/api/auth/login").then().statusCode(200).extract().cookie("FANTASTA_AUTH");
            String a=login(prefix+"user-a"), b=login(prefix+"user-b"), observer=login(prefix+"observer");
            Map<String,Object> start=Map.of("roundId",roundId,"order",List.of(ids[0],ids[1]),"firstParticipantId",ids[0]);
            given().contentType(ContentType.JSON).body(start).post("/api/tocco/start").then().statusCode(401);
            given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON).body(start).post("/api/tocco/start").then().statusCode(403);
            String attempt=given().cookie("FANTASTA_AUTH",admin).contentType(ContentType.JSON).body(start)
                    .post("/api/tocco/start").then().statusCode(200).extract().path("tocco.id");
            Map<String,Object> choice=new HashMap<>(Map.of("roundId",roundId,"toccoId",attempt,"number",1,"participantId",ids[1]));
            given().cookie("FANTASTA_AUTH",observer).contentType(ContentType.JSON).body(choice).post("/api/tocco/choose").then().statusCode(403);
            choice.put("number",1.5);
            given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON).body(choice).post("/api/tocco/choose").then().statusCode(400);
            choice.put("number",1);
            given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON).body(choice).post("/api/tocco/choose").then().statusCode(200)
                    .body("tocco.completed",equalTo(false)).body("tocco.participants[0].confirmed",equalTo(true))
                    .body("tocco.participants[1].confirmed",equalTo(false)).body("tocco.participants.number",everyItem(nullValue()));
            given().cookie("FANTASTA_AUTH",observer).get("/api/round").then().statusCode(200).body("tocco.participants.number",everyItem(nullValue()));
            given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON).body(choice).post("/api/tocco/choose").then().statusCode(409);
            given().cookie("FANTASTA_AUTH",b).contentType(ContentType.JSON).body(choice).post("/api/tocco/choose").then().statusCode(200)
                    .body("tocco.completed",equalTo(true)).body("tocco.sum",equalTo(2)).body("tocco.winnerParticipantId",equalTo(ids[1].intValue()))
                    .body("tocco.participants.number",equalTo(List.of(1,1)));
            Map<String,Object> assign=Map.of("roundId",roundId,"toccoId",attempt,"amount",12,"participantId",ids[0]);
            given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON).body(assign).post("/api/tocco/assign").then().statusCode(403);
            given().cookie("FANTASTA_AUTH",admin).contentType(ContentType.JSON).body(assign).post("/api/tocco/assign").then().statusCode(200)
                    .body("winner.participantId",equalTo(ids[1].intValue())).body("winner.amount",equalTo(12F));
            String recordedRound=roundId;
            QuarkusTransaction.requiringNew().run(() -> {
                AuctionHistoryEntity history=AuctionHistoryEntity.find("roundId",recordedRound).firstResult();
                assertNotNull(history); assertEquals(12D,history.winningAmount);
                assertEquals(2,AuctionHistoryBidEntity.count("history",history));
                assertTrue(AuctionHistoryBidEntity.<AuctionHistoryBidEntity>list("history",history).stream().allMatch(bid -> bid.amount.equals(10D)));
                MarketMovementEntity movement=MarketMovementEntity.find("player.id",ids[2]).firstResult();
                assertEquals(recordedRound,movement.auctionRoundId);
            });
        } finally {
            auction.reset();
            String recordedRound=roundId;
            QuarkusTransaction.requiringNew().run(() -> {
                if(recordedRound!=null) for(AuctionHistoryEntity h:AuctionHistoryEntity.<AuctionHistoryEntity>list("roundId",recordedRound)) { AuctionHistoryBidEntity.delete("history",h); h.delete(); }
                MarketMovementEntity.delete("player.id",ids[2]); RosterAcquisitionEntity.delete("player.id",ids[2]);
                RosterEntity.delete("player.id",ids[2]); PlayerOwnerHistoryEntity.delete("player.id",ids[2]);
                AppUserEntity.delete("username like ?1",prefix+"%"); PlayerEntity.deleteById(ids[2]);
                ParticipantEntity.deleteById(ids[0]); ParticipantEntity.deleteById(ids[1]);
            });
        }
    }
}
