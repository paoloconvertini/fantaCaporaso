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

  it('keeps observers out of the tocco even when linked to a tied team', () => {
    const page = component(true);
    page.pid = 7;
    page.round = { tocco: { participants: [{ id: 7, confirmed: false }] } };
    expect(page.myToccoParticipant).toBeNull();
  });

  it('uses the authenticated team for tocco rather than the participant query parameter', () => {
    const page = component();
    page.pid = 9;
    page.round = { tocco: { participants: [{ id: 7, confirmed: true }, { id: 9, confirmed: false }] } };
    expect(page.myToccoParticipant.id).toBe(7);
    expect(page.myToccoParticipant.confirmed).toBeTrue();
  });

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
  it('uses the chosen mini slot budget and requires a role match', () => {
    const page = component();
    page.pid = 7;
    page.round = { miniSessionId: 1, playerRole: 'DIFENSORE', closed: false, allowedUsers: [7], minimumBid: 1 };
    page.participant = { maxBid: 119 };
    page.miniSlots = [
      { id: 1, role: 'DIFENSORE', filled: false, minimumBid: 8, maximumBid: 106 },
      { id: 2, role: 'ATTACCANTE', filled: false, minimumBid: 13, maximumBid: 111 }
    ];
    expect(page.isBidAllowed()).toBeFalse();
    page.selectedMiniSlotId = 1;
    expect(page.minimumBidForCurrentRound).toBe(8);
    expect(page.maxBidForCurrentRound).toBe(106);
    expect(page.isBidAllowed()).toBeTrue();
    page.selectedMiniSlotId = 2;
    expect(page.isBidAllowed()).toBeFalse();
  });

  it('uses the higher round minimum in a mini auction tie break', () => {
    const page = component();
    page.round = { miniSessionId: 1, playerRole: 'DIFENSORE', minimumBid: 11 };
    page.miniSlots = [{ id: 1, role: 'DIFENSORE', filled: false, minimumBid: 8, maximumBid: 50 }];
    page.selectedMiniSlotId = 1;
    expect(page.minimumBidForCurrentRound).toBe(11);
  });

});
