package com.fantasta.rest;

import com.fantasta.model.*;
import com.fantasta.service.AuctionService;
import com.fantasta.security.AppJwtService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuctionReservationResourceTest {
    @Inject AuctionService auction;
    @Inject AppJwtService jwt;

    @Test
    void timerMovesFromBookingsToOffersAndClosesAtMinimumWithIdentityAndObserverChecks() throws Exception {
        auction.reset();
        Long[] ids=QuarkusTransaction.requiringNew().call(()->{
            MercatoConfigEntity.deleteAll();
            MercatoConfigEntity c=new MercatoConfigEntity(); c.attiva=true; c.numeroMercato=1;
            c.sessionCode="http-reservation"; c.partitiImportati=true; c.quotazioniAggiornate=true;
            c.prenotazioneAbilitata=true; c.durataPrenotazioneSecondi=4; c.persist();
            PlayerEntity p=new PlayerEntity(); p.name="HTTP reservation defender"; p.team="HTTP reservation club";
            p.role=Role.DIFENSORE; p.valore=1D; p.active=true; p.persist();
            ParticipantEntity a=new ParticipantEntity(); a.name="HTTP reservation A"; a.totalCredits=100; a.persist();
            ParticipantEntity b=new ParticipantEntity(); b.name="HTTP reservation B"; b.totalCredits=100; b.persist();
            account("http-reservation-a","user",a); account("http-reservation-b","user",b);
            account("http-reservation-observer","observer",a);
            return new Long[]{a.id,b.id,p.id};
        });
        String admin=jwt.createToken("test-admin","admin",null);
        String a=jwt.createToken("http-reservation-a","user",ids[0]);
        String b=jwt.createToken("http-reservation-b","user",ids[1]);
        String observer=jwt.createToken("http-reservation-observer","observer",ids[0]);
        try {
            String roundId=given().cookie("FANTASTA_AUTH",admin).contentType(ContentType.JSON)
                    .body(Map.of("player","HTTP reservation defender","playerTeam","HTTP reservation club",
                            "playerRole","DIFENSORE","durationSeconds",2))
                    .post("/api/start").then().statusCode(200).extract().path("roundId");
            given().cookie("FANTASTA_AUTH",observer).contentType(ContentType.JSON)
                    .body(Map.of("roundId",roundId)).post("/api/round/reserve").then().statusCode(403);
            var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var first=executor.submit(()->given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON)
                        .body(Map.of("roundId",roundId,"participantId",ids[1])).post("/api/round/reserve").statusCode());
                var duplicate=executor.submit(()->given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON)
                        .body(Map.of("roundId",roundId)).post("/api/round/reserve").statusCode());
                assertEquals(200,first.get());assertEquals(200,duplicate.get());
                assertEquals(1,auction.get().bids.size());
            } finally {executor.shutdownNow();}
            assertTrue(auction.get().reservedUsers.contains(ids[0]));
            assertFalse(auction.get().reservedUsers.contains(ids[1]));
            given().cookie("FANTASTA_AUTH",a).contentType(ContentType.JSON).body(Map.of())
                    .post("/api/bids/withdraw").then().statusCode(409);
            long deadline=System.currentTimeMillis()+10000;
            while ("RESERVATION".equals(auction.get().phase) && System.currentTimeMillis()<deadline) Thread.sleep(50);
            assertEquals("OFFERS",auction.get().phase);
            assertFalse(auction.get().closed);
            given().cookie("FANTASTA_AUTH",b).contentType(ContentType.JSON)
                    .body(Map.of("amount",2)).post("/api/bids").then().statusCode(400);
            while(!auction.get().closed && System.currentTimeMillis()<deadline) Thread.sleep(50);
            assertTrue(auction.get().closed); assertNotNull(auction.get().winner);
            assertEquals(ids[0],auction.get().winner.participantId);
            assertEquals(1D,auction.get().winner.amount);
        } finally {
            given().cookie("FANTASTA_AUTH",admin).post("/api/round/reset");
            QuarkusTransaction.requiringNew().run(()->{
                RosterEntity.delete("player.id",ids[2]);
                AppUserEntity.delete("username like ?1","http-reservation-%");
                PlayerEntity.deleteById(ids[2]); ParticipantEntity.deleteById(ids[0]); ParticipantEntity.deleteById(ids[1]);
                MercatoConfigEntity.deleteAll();
            });
        }
    }

    private void account(String username,String role,ParticipantEntity participant) {
        AppUserEntity u=new AppUserEntity(); u.username=username; u.role=role;
        u.passwordHash="unused-test-hash";u.participant=participant;u.persist();
    }
}
