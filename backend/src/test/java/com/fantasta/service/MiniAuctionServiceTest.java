package com.fantasta.service;
import com.fantasta.model.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MiniAuctionServiceTest {
 @Inject MiniAuctionService minis; @Inject AuctionService auction; @Inject RosterService rosters;
 @Inject ParticipantService participants; @Inject RosterMovementService movements;
 PlayerEntity player(Role role,String team) { PlayerEntity p=new PlayerEntity(); p.name="Mini "+UUID.randomUUID();p.team=team;p.role=role;p.valore=4D;p.active=true;p.persist();return p; }
 ParticipantEntity full(int residual) {
  ParticipantEntity p=new ParticipantEntity();p.name="Mini team "+UUID.randomUUID();p.totalCredits=500;p.persist();int spent=0;
  for(Role role:Role.values())for(int i=0;i<rosters.max(role);i++) {
   RosterEntity r=new RosterEntity();r.participant=p;r.player=player(role,p.name);r.player.assigned=true;
   r.amount=role==Role.DIFENSORE&&i==0?7D:role==Role.DIFENSORE&&i==1?12D:1D;r.persist();spent+=r.amount.intValue();
   if(role==Role.PORTIERE || role==Role.DIFENSORE && i<2) {
    RosterAcquisitionEntity a=new RosterAcquisitionEntity();a.rosterEntryId=r.id;a.player=r.player;a.participant=p;
    a.sessionCode="source-market";a.purchaseGroupCode="test-group:"+p.id+":"+role;
    a.acquiredAt=LocalDate.of(2026,10,1).atTime(20,0);a.paidAmount=r.amount;a.repairMarket=true;a.persist();
   }
  } p.totalCredits=spent+residual;return p;
 }
 RosterEntity defender(ParticipantEntity p,double price) {return RosterEntity.find("participant = ?1 and player.role = ?2 and amount = ?3",p,Role.DIFENSORE,price).firstResult();}
 MiniAuctionSessionEntity prepare(Long...ids){auction.reset();MercatoConfigEntity.deleteAll();return auction.prepareMini("Mini test","source-market",LocalDate.of(2026,10,1),List.of(ids));}
 MiniAuctionSlotEntity slot(MiniAuctionSessionEntity s,ParticipantEntity p,double refund){return minis.slots(s.id).stream().filter(x->x.participant.id.equals(p.id)&&x.refund==refund).findFirst().orElseThrow();}
 RoundState start(PlayerEntity t){return auction.start(t.name,t.team,t.role.name(),30,"NONE",4,null);}

 @Test @TestTransaction void releaseIsDefinitiveAndSingleBidChargesPersonalMinimum(){
  ParticipantEntity p=full(100);RosterEntity old=defender(p,7);Long oldPlayer=old.player.id;MiniAuctionSessionEntity s=prepare(old.id);
  assertEquals(25,RosterEntity.count("participant",p));auction.activateMini(s.id);MiniAuctionSlotEntity sl=slot(s,p,7);
  assertEquals(107,participants.remainingCreditsById(p.id,p.totalCredits));assertEquals(0,RosterEntity.count("player.id",oldPlayer));assertEquals(8,sl.minimumBid);
  PlayerEntity t=player(Role.DIFENSORE,"Free Club");assertEquals(s.id,start(t).miniSessionId);auction.bidDto(p.id,100D,sl.id);
  assertEquals(8,auction.close().winner.amount);assertTrue(sl.filled);assertEquals(99,participants.remainingCreditsById(p.id,p.totalCredits));assertEquals(25,RosterEntity.count("participant",p));
  auction.finishMini(s.id);assertEquals(MiniAuctionSessionEntity.Status.CLOSED,s.status);assertEquals(1,MarketMovementEntity.count("type",MarketMovementEntity.Type.MINI_PURCHASE));
 }
 @Test @TestTransaction void losingKeepsSlotAndClosureIsBlocked(){
  ParticipantEntity p=full(100),q=full(100);MiniAuctionSessionEntity s=prepare(defender(p,7).id,defender(q,12).id);auction.activateMini(s.id);
  PlayerEntity t=player(Role.DIFENSORE,"Free Club");start(t);auction.bidDto(p.id,15D,slot(s,p,7).id);auction.bidDto(q.id,18D,slot(s,q,12).id);auction.close();
  assertFalse(slot(s,p,7).filled);assertTrue(slot(s,q,12).filled);assertEquals(24,RosterEntity.count("participant",p));assertEquals(107,participants.remainingCreditsById(p.id,p.totalCredits));assertThrows(IllegalStateException.class,()->auction.finishMini(s.id));
 }
 @Test @TestTransaction void reservesOtherMinimumAndLocksSlotUntilWithdrawal(){
  ParticipantEntity p=full(100);MiniAuctionSessionEntity s=prepare(defender(p,7).id,defender(p,12).id);auction.activateMini(s.id);MiniAuctionSlotEntity a=slot(s,p,7),b=slot(s,p,12);
  assertEquals(106,minis.maxBid(a));PlayerEntity t=player(Role.DIFENSORE,"Free Club");start(t);
  assertThrows(IllegalArgumentException.class,()->auction.bidDto(p.id,107D,a.id));assertThrows(IllegalArgumentException.class,()->auction.bidDto(p.id,7D,a.id));auction.bidDto(p.id,8D,a.id);
  assertThrows(IllegalArgumentException.class,()->auction.bidDto(p.id,13D,b.id));auction.withdrawBidDto(p.id);auction.bidDto(p.id,13D,b.id);assertEquals(b.id,auction.get().miniBidSlots.get(String.valueOf(p.id)));
  auction.reset();assertFalse(a.filled);assertFalse(b.filled);assertEquals(23,RosterEntity.count("participant",p));
 }
 @Test @TestTransaction void zeroCreditsBlocksReleaseBeforeMutation(){
  ParticipantEntity p=full(0);MiniAuctionSessionEntity s=prepare(defender(p,7).id);assertThrows(IllegalArgumentException.class,()->auction.activateMini(s.id));
  assertEquals(25,RosterEntity.count("participant",p));assertEquals(0,participants.remainingCreditsById(p.id,p.totalCredits));assertEquals(MiniAuctionSessionEntity.Status.DRAFT,s.status);
 }
 @Test @TestTransaction void twoReleasesNeedTwoExtraCredits(){
  ParticipantEntity p=full(1);MiniAuctionSessionEntity s=prepare(defender(p,7).id,defender(p,12).id);assertThrows(IllegalArgumentException.class,()->auction.activateMini(s.id));assertEquals(25,RosterEntity.count("participant",p));
 }
 @Test @TestTransaction void wholeGoalkeeperPackageUsesTotalCostNotQuotation(){
  ParticipantEntity p=full(10);RosterEntity k=RosterEntity.find("participant = ?1 and player.role = ?2",p,Role.PORTIERE).firstResult();MiniAuctionSessionEntity s=prepare(k.id);auction.activateMini(s.id);MiniAuctionSlotEntity sl=slot(s,p,3);assertEquals(3,sl.purchaseSize);assertEquals(4,sl.minimumBid);
  PlayerEntity t=player(Role.PORTIERE,"New Door");player(Role.PORTIERE,"New Door");player(Role.PORTIERE,"New Door");
  MercatoConfigEntity c=new MercatoConfigEntity();c.attiva=true;c.numeroMercato=1;c.partitiImportati=true;c.quotazioniAggiornate=true;c.sessionCode="source-market";c.persist();
  start(t);assertEquals(3,auction.get().minimumBid);auction.bidDto(p.id,4D,sl.id);auction.close();assertEquals(3,RosterEntity.count("participant = ?1 and player.role = ?2",p,Role.PORTIERE));assertEquals(9,participants.remainingCreditsById(p.id,p.totalCredits));assertTrue(sl.filled);
 }
 @Test @TestTransaction void rejectsWrongOwnerRoleAndManualBypass(){
  ParticipantEntity p=full(10),q=full(10);MiniAuctionSessionEntity s=prepare(defender(p,7).id);auction.activateMini(s.id);PlayerEntity t=player(Role.DIFENSORE,"Free Club");start(t);
  assertThrows(IllegalArgumentException.class,()->auction.bidDto(q.id,8D,slot(s,p,7).id));assertThrows(IllegalArgumentException.class,()->minis.requireSlot(s.id,slot(s,p,7).id,p.id,Role.ATTACCANTE));assertThrows(IllegalStateException.class,()->auction.adminAssign(t.id,p.id,8D));
 }
 @Test @TestTransaction void tieRetainsSlotAndSession(){
  ParticipantEntity p=full(50),q=full(50);MiniAuctionSessionEntity s=prepare(defender(p,7).id,defender(q,7).id);auction.activateMini(s.id);PlayerEntity t=player(Role.DIFENSORE,"Free Club");start(t);
  auction.bidDto(p.id,10D,slot(s,p,7).id);auction.bidDto(q.id,10D,slot(s,q,7).id);RoundState tied=auction.close();assertEquals(2,tied.tieUsers.size());assertFalse(slot(s,p,7).filled);
  RoundState tie=auction.start(t.name,t.team,t.role.name(),30,"NONE",4,new HashSet<>(tied.tieUsers));assertEquals(s.id,tie.miniSessionId);assertEquals(11,tie.minimumBid);auction.bidDto(p.id,12D,slot(s,p,7).id);auction.close();assertTrue(slot(s,p,7).filled);assertFalse(slot(s,q,7).filled);
 }
 @Test @TestTransaction void toccoFillsOnlyWinningMiniSlotAndRevertRestoresIt(){
  auction.reset();
  ParticipantEntity p=full(50),q=full(50);MiniAuctionSessionEntity s=prepare(defender(p,7).id,defender(q,7).id);auction.activateMini(s.id);
  PlayerEntity t=player(Role.DIFENSORE,"Tocco Mini Club");RoundState r=start(t);
  auction.bidDto(p.id,10D,slot(s,p,7).id);auction.bidDto(q.id,10D,slot(s,q,7).id);auction.close();
  auction.startTocco(r.roundId,List.of(p.id,q.id),p.id);String attempt=r.tocco.id;
  auction.chooseTocco(r.roundId,attempt,p.id,1);auction.chooseTocco(r.roundId,attempt,q.id,1);
  assertThrows(IllegalArgumentException.class,()->auction.assignTocco(r.roundId,attempt,7D));
  auction.assignTocco(r.roundId,attempt,12D);
  assertFalse(slot(s,p,7).filled);assertTrue(slot(s,q,7).filled);assertEquals(12D,r.winner.amount);
  MarketMovementEntity m=MarketMovementEntity.find("player = ?1 and type = ?2",t,MarketMovementEntity.Type.MINI_PURCHASE).firstResult();
  assertEquals(r.roundId,m.auctionRoundId);
  movements.revert(m.id);
  assertFalse(slot(s,q,7).filled);assertEquals(0,RosterEntity.count("player",t));
 }
 @Test @TestTransaction void miniMovementsAreNotIndividuallyReversible(){
  ParticipantEntity p=full(10);MiniAuctionSessionEntity s=prepare(defender(p,7).id);auction.activateMini(s.id);MarketMovementEntity m=MarketMovementEntity.find("sessionCode",s.code).firstResult();
  assertFalse(movements.list(null,null,p.id,false).stream().filter(x->x.id.equals(m.id)).findFirst().orElseThrow().canRevert);assertThrows(jakarta.ws.rs.BadRequestException.class,()->movements.revert(m.id));
 }
 @Test @TestTransaction void rejectsOldRosterPlayersEvenIfTheAdminSubmitsTheirId(){
  ParticipantEntity p=full(100);
  RosterEntity old=RosterEntity.find("participant = ?1 and player.role = ?2",p,Role.ATTACCANTE).firstResult();
  assertThrows(IllegalArgumentException.class,()->prepare(old.id));
  assertEquals(25,RosterEntity.count("participant",p));
 }
 @Test @TestTransaction void automaticCandidatesExcludeOtherSourcesAndPartialGoalkeeperPackages(){
  ParticipantEntity p=full(100);
  assertEquals(5,minis.eligibleCandidates("source-market",LocalDate.of(2026,10,1)).size());
  assertTrue(minis.eligibleCandidates("different-market",LocalDate.of(2026,10,1)).isEmpty());
  assertTrue(minis.eligibleCandidates("source-market",LocalDate.of(2026,10,2)).isEmpty());
  RosterAcquisitionEntity keeper=RosterAcquisitionEntity.find("participant = ?1 and player.role = ?2",p,Role.PORTIERE).firstResult();
  keeper.delete();
  assertEquals(2,minis.eligibleCandidates("source-market",LocalDate.of(2026,10,1)).size());
 }
 @Test @TestTransaction void recordsManualAndSingleBidPurchasesAutomatically(){
  auction.reset(); MercatoConfigEntity.deleteAll();
  ParticipantEntity p=full(100);
  MercatoConfigEntity market=new MercatoConfigEntity();market.attiva=true;market.numeroMercato=1;
  market.partitiImportati=true;market.quotazioniAggiornate=true;market.sessionCode="recorded-market";market.persist();
  defender(p,7).delete();
  PlayerEntity manual=player(Role.DIFENSORE,"Manual Free Club");
  auction.adminAssign(manual.id,p.id,3D);
  RosterEntity manualRoster=RosterEntity.find("player",manual).firstResult();
  assertTrue(minis.isEligibleAcquisition(manualRoster,"recorded-market",LocalDate.now()));
  defender(p,12).delete();
  PlayerEntity single=player(Role.DIFENSORE,"Single Free Club");
  start(single);auction.bid(p.id,10D);auction.close();
  RosterEntity singleRoster=RosterEntity.find("player",single).firstResult();
  assertEquals(1D,singleRoster.amount);
  assertTrue(minis.isEligibleAcquisition(singleRoster,"recorded-market",LocalDate.now()));
  RosterAcquisitionEntity acquisition=RosterAcquisitionEntity.find("rosterEntryId",singleRoster.id).firstResult();
  assertEquals(1D,acquisition.paidAmount);assertTrue(acquisition.repairMarket);
 }

 @Test @TestTransaction void revertsMiniPurchaseReopeningSlotWithoutUndoingRelease(){
  ParticipantEntity p=full(100); MiniAuctionSessionEntity session=prepare(defender(p,7).id);
  auction.activateMini(session.id); MiniAuctionSlotEntity selected=slot(session,p,7);
  PlayerEntity target=player(Role.DIFENSORE,"Revert Club"); start(target); auction.bidDto(p.id,20D,selected.id);auction.close();
  MarketMovementEntity purchase=MarketMovementEntity.find("type",MarketMovementEntity.Type.MINI_PURCHASE).firstResult();
  auction.finishMini(session.id);auction.revertMovement(purchase.id);
  assertFalse(selected.filled);assertEquals(MiniAuctionSessionEntity.Status.ACTIVE,session.status);
  assertEquals(107,participants.remainingCreditsById(p.id,p.totalCredits));assertFalse(target.assigned);
  assertEquals(24,RosterEntity.count("participant",p));assertNotNull(purchase.revertedAt);
  assertNull(auction.get()); assertEquals(8,selected.minimumBid);
  assertThrows(jakarta.ws.rs.BadRequestException.class,()->auction.revertMovement(purchase.id));
  start(target);auction.bidDto(p.id,8D,selected.id);auction.close();assertTrue(selected.filled);
 }
 @Test @TestTransaction void revertsManualKeeperPackageAndRejectsChangedAcquisition(){
  auction.reset();MercatoConfigEntity.deleteAll(); ParticipantEntity p=full(100);
  RosterEntity.delete("participant = ?1 and player.role = ?2",p,Role.PORTIERE);
  int before=participants.remainingCreditsById(p.id,p.totalCredits);
  PlayerEntity target=player(Role.PORTIERE,"Revert Door");player(Role.PORTIERE,"Revert Door");player(Role.PORTIERE,"Revert Door");
  auction.adminAssign(target.id,p.id,9D);
  MarketMovementEntity purchase=MarketMovementEntity.find("type = ?1 and player = ?2",MarketMovementEntity.Type.PURCHASE,target).firstResult();
  auction.revertMovement(purchase.id);
  assertEquals(before,participants.remainingCreditsById(p.id,p.totalCredits));assertEquals(0,RosterEntity.count("participant = ?1 and player.role = ?2",p,Role.PORTIERE));
  assertEquals(3,MarketMovementEntity.count("operationCode = ?1 and revertedAt is not null",purchase.operationCode));
  defender(p,7).delete();PlayerEntity field=player(Role.DIFENSORE,"Changed Club");auction.adminAssign(field.id,p.id,5D);
  MarketMovementEntity changed=MarketMovementEntity.find("type = ?1 and player = ?2",MarketMovementEntity.Type.PURCHASE,field).firstResult();
  RosterEntity current=RosterEntity.find("player",field).firstResult();current.amount=6D;
  assertThrows(jakarta.ws.rs.BadRequestException.class,()->auction.revertMovement(changed.id));assertTrue(field.assigned);
 }

 @Test @TestTransaction void revertsCompetitiveAuctionAndHistoryThenAllowsNewRound(){
  auction.reset();MercatoConfigEntity.deleteAll();ParticipantEntity p=full(100),q=full(100);
  defender(p,7).delete();defender(q,7).delete();PlayerEntity target=player(Role.DIFENSORE,"Competitive Revert");
  int before=participants.remainingCreditsById(p.id,p.totalCredits);
  start(target);auction.bid(p.id,9D);auction.bid(q.id,8D);RoundState closed=auction.close();
  AuctionHistoryEntity recorded=new AuctionHistoryEntity();recorded.roundId=closed.roundId;recorded.sessionCode="test";
  recorded.playerId=target.id;recorded.playerName=target.name;recorded.winnerParticipantId=p.id;recorded.winnerName=p.name;
  recorded.winningAmount=9D;recorded.bidderCount=2;recorded.closedAt=java.time.LocalDateTime.now();recorded.persist();
  assertEquals(1,AuctionHistoryEntity.count("roundId",closed.roundId));
  MarketMovementEntity purchase=MarketMovementEntity.find("player = ?1 and type = ?2",target,MarketMovementEntity.Type.PURCHASE).firstResult();
  auction.revertMovement(purchase.id);
  assertEquals(before,participants.remainingCreditsById(p.id,p.totalCredits));assertEquals(0,AuctionHistoryEntity.count("roundId",closed.roundId));
  assertEquals(0,PlayerOwnerHistoryEntity.count("player = ?1 and participant = ?2",target,p));assertFalse(target.assigned);
  start(target);auction.bid(q.id,8D);auction.close();assertEquals(q.id,((RosterEntity)RosterEntity.find("player",target).firstResult()).participant.id);
 }
}
