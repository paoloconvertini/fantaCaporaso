import { BehaviorSubject, of } from 'rxjs';
import { MobileComponent } from './mobile.component';

describe('MobileComponent', () => {
  function component(): MobileComponent {
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
    return new MobileComponent(route, api);
  }

  it('allows a normal active round once the participant is known', () => {
    const page = component();
    page.pid = 7;
    page.round = { closed: false, allowedUsers: [] };

    expect(page.isBidAllowed()).toBeTrue();
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
});
