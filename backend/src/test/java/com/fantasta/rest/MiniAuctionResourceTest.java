package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.service.AuctionService;
import com.fantasta.service.PasswordService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class MiniAuctionResourceTest {
    @Inject PasswordService passwords;
    @Inject AuctionService auction;

    private String login(String username, String password) {
        return given().contentType(ContentType.JSON).body(java.util.Map.of("username",username,"password",password))
                .post("/api/auth/login").then().statusCode(200).extract().cookie("FANTASTA_AUTH");
    }

    @Test
    void anonymousCannotReadOrPrepareMiniAuctions() {
        given().get("/api/mini-auctions/mine").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").post("/api/mini-auctions/prepare").then().statusCode(401);
    }

    @Test
    void adminMustProvideValidSourceAndConfirmPermanentReleases() {
        String cookie=login("test-admin","test-password-strong");
        given().cookie("FANTASTA_AUTH",cookie).contentType(ContentType.JSON).body("{}")
                .post("/api/mini-auctions/prepare").then().statusCode(400).body("error",containsString("Dati"));
        given().cookie("FANTASTA_AUTH",cookie).contentType(ContentType.JSON).body("{\"confirm\":false}")
                .post("/api/mini-auctions/0/activate").then().statusCode(400).body("error",containsString("Confermare"));
    }

    @Test
    void userCannotPrepareAndCannotReadAnotherTeamsSlotsByQueryParameter() {
        auction.reset();
        String username="mini-http-"+UUID.randomUUID();
        Long[] ids=QuarkusTransaction.requiringNew().call(()->{
            ParticipantEntity own=new ParticipantEntity();own.name=username;own.totalCredits=500;own.persist();
            ParticipantEntity other=new ParticipantEntity();other.name=username+"-other";other.totalCredits=500;other.persist();
            AppUserEntity u=new AppUserEntity();u.username=username;u.passwordHash=passwords.hash("mini-test-password");u.role="user";u.participant=own;u.persist();
            MiniAuctionSessionEntity s=new MiniAuctionSessionEntity();s.code=UUID.randomUUID().toString();s.label="HTTP Mini";s.sourceSessionCode="source";s.sourceDate=LocalDate.of(2026,10,1);s.status=MiniAuctionSessionEntity.Status.ACTIVE;s.persist();
            for(ParticipantEntity p:java.util.List.of(own,other)){
                MiniAuctionSlotEntity slot=new MiniAuctionSlotEntity();slot.session=s;slot.participant=p;slot.role=Role.DIFENSORE;
                slot.releasedNames=p==own?"Own released player":"Other private slot";slot.refund=7;slot.minimumBid=8;slot.purchaseSize=1;slot.persist();
            }
            return new Long[]{own.id,other.id,u.id,s.id};
        });
        try {
            String cookie=login(username,"mini-test-password");
            given().cookie("FANTASTA_AUTH",cookie).queryParam("participantId",ids[1])
                    .get("/api/mini-auctions/mine").then().statusCode(200).body("size()",equalTo(1))
                    .body("[0].releasedNames",equalTo("Own released player"));
            given().cookie("FANTASTA_AUTH",cookie).contentType(ContentType.JSON).body("{}")
                    .post("/api/mini-auctions/prepare").then().statusCode(403);
        } finally {
            auction.reset();
            QuarkusTransaction.requiringNew().run(()->{
                MiniAuctionSlotEntity.<MiniAuctionSlotEntity>list("session.id",ids[3]).forEach(s->s.delete());
                MiniAuctionSessionEntity.deleteById(ids[3]);AppUserEntity.deleteById(ids[2]);
                ParticipantEntity.deleteById(ids[0]);ParticipantEntity.deleteById(ids[1]);
            });
        }
    }
}
