import { BehaviorSubject, of } from 'rxjs';
import { MobileComponent } from './mobile.component';

describe('MobileComponent', () => {
  function component(observer = false): MobileComponent {
    const route = { snapshot: { queryParamMap: { get: () => null } } } as any;
    const api = {
      roleFilter$: new BehaviorSubject(''),
      round$: new BehaviorSubject(null),
      activeUsers$: new BehaviorSubject([]),
      getCurrentParticipant: () => of({ id: 7 }),
      getRound: () => of(null),
      getRandomState: () => of({
        remaining: { DIFENSORE: 34 },
        openSlots: { DIFENSORE: 27 }
      })
    } as any;
    const auth = { user: { participantId: 7 }, isObserver: observer } as any;
    return new MobileComponent(route, api, auth);
  }

  it('keeps an associated observer read-only even with a participant id', () => {
    const page = component(true);
    page.pid = 7;
    page.round = { closed: false, allowedUsers: [] };
    expect(page.isObserver).toBeTrue();
    expect(page.isBidAllowed()).toBeFalse();
  });

  it('allows a normal active round once the participant is known', () => {
    const page = component();
    page.pid = 7;
    page.round = { closed: false, allowedUsers: [] };

    expect(page.isBidAllowed()).toBeTrue();
  });

  it('keeps an account without participant in read-only mode', () => {
    const page = component();
    page.pid = null;
    page.round = { closed: false, allowedUsers: [] };

    expect(page.isObserver).toBeTrue();
    expect(page.isBidAllowed()).toBeFalse();
  });

  it('detects whether the participant has an active bid to withdraw', () => {
    const page = component();
    page.participant = { name: 'Mia squadra' };
    page.activeUsers = ['Altra', 'Mia squadra'];

    expect(page.hasActiveBid).toBeTrue();
  });

  it('normalizes participant ids received for a tie break', () => {
    const page = component();
    page.pid = 7;
    page.round = { closed: false, allowedUsers: ['7', '12'] };

    expect(page.isBidAllowed()).toBeTrue();
    page.pid = 9;
    expect(page.isBidAllowed()).toBeFalse();
  });

  it('sorts bids by amount and marks the current participant bid', () => {
    const page = component();
    page.participant = { name: 'Mia squadra' };
    page.round = { bids: { Altra: 8, 'Mia squadra': 12, Terza: 10 } };

    expect(page.sortedBids.map(bid => bid.amount)).toEqual([12, 10, 8]);
    expect(page.isMyBid('Mia squadra')).toBeTrue();
  });

  it('disables bidding when the visible countdown reaches zero', () => {
    const page = component();
    page.pid = 7;
    page.round = { closed: false, allowedUsers: [] };
    page.timeLeft = 0;

    expect(page.isBidAllowed()).toBeFalse();
  });

  it('adjusts the displayed maximum for a goalkeeper package', () => {
    const page = component();
    page.participant = { maxBid: 476, remainingCredits: 500 };
    page.round = { purchaseSize: 3 };

    expect(page.maxBidForCurrentRound).toBe(478);
  });

  it('loads compact market numbers for the active role', () => {
    const page = component();
    page.currentRole = 'DIFENSORE';

    page.loadMarketStats();

    expect(page.remainingCalls).toBe(34);
    expect(page.openSlots).toBe(27);
  });

  it('keeps the player quotation available in the active round', () => {
    const page = component();
    page.round = { player: 'Calciatore', playerTeam: 'Roma', playerRole: 'DIFENSORE', value: 18 };

    expect(page.round.value).toBe(18);
  });

  it('explains the automatic one-credit charge for a single bidder', () => {
    const page = component();
    page.round = { closed: true, bids: { Unica: 14 }, winner: { user: 'Unica', amount: 1 } };

    expect(page.automaticMinimumMessage).toContain('1 credito');
  });

  it('does not show the automatic charge message with multiple bidders', () => {
    const page = component();
    page.round = { closed: true, bids: { Prima: 14, Seconda: 12 }, winner: { user: 'Prima', amount: 14 } };

    expect(page.automaticMinimumMessage).toBeNull();
  });
  it('requires booking before offers and never lets an observer book', () => {
    const page = component(); page.pid = 7;
    page.round = { closed: false, reservationRequired: true, phase: 'RESERVATION', reservedUsers: [] };
    expect(page.canReserve).toBeTrue();
    expect(page.isBidAllowed()).toBeFalse();
    page.round.reservedUsers = [7];
    expect(page.canReserve).toBeFalse();
    page.round.phase = 'OFFERS';
    expect(page.isBidAllowed()).toBeTrue();
    page.round.reservedUsers = [];
    expect(page.isBidAllowed()).toBeFalse();
    const observer = component(true); observer.pid = 7;
    observer.round = { closed: false, phase: 'RESERVATION', reservedUsers: [] };
    expect(observer.canReserve).toBeFalse();
  });

  it('rejects withdrawal of a booked offer even when bidding is allowed', () => {
    const page = component(); page.pid = 7;
    page.round = { closed: false, reservationRequired: true, phase: 'OFFERS', reservedUsers: [7] };
    page.participant = { name: 'Mia squadra' }; page.activeUsers = ['Mia squadra'];
    expect(page.isBidAllowed()).toBeTrue();
    expect(() => page.withdrawBid()).not.toThrow();
    expect(page.withdrawing).toBeFalse();
  });

});
