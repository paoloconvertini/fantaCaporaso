import { of, Subject } from 'rxjs';
import { MiniAuctionComponent } from './mini-auction.component';

describe('MiniAuctionComponent', () => {
  it('selects the whole goalkeeper door', () => {
    const page = new MiniAuctionComponent({} as any, {} as any);
    page.candidates = [
      { id: 1, participantId: 7, role: 'PORTIERE' },
      { id: 2, participantId: 7, role: 'PORTIERE' },
      { id: 3, participantId: 7, role: 'PORTIERE' },
      { id: 4, participantId: 8, role: 'PORTIERE' }
    ];
    page.toggle(page.candidates[0], true);
    expect(Array.from(page.selected)).toEqual([1, 2, 3]);
    page.toggle(page.candidates[1], false);
    expect(page.selected.size).toBe(0);
  });

  it('does not apply releases when the admin cancels confirmation', () => {
    const api = jasmine.createSpyObj('MiniApi', ['activate']);
    const dialog = { open: () => ({ afterClosed: () => of(false) }) };
    const page = new MiniAuctionComponent(api, dialog as any);
    page.session = { id: 1 };
    page.activate();
    expect(api.activate).not.toHaveBeenCalled();
    expect(page.busy).toBeFalse();
  });
  it('shows players only after selecting their teams and keeps search inside those teams', () => {
    const page = new MiniAuctionComponent({} as any, {} as any);
    page.candidates = [
      { id: 1, participantId: 7, participant: 'Prima', player: 'Ricci', team: 'Milan' },
      { id: 2, participantId: 8, participant: 'Seconda', player: 'Ricci S', team: 'Torino' },
      { id: 3, participantId: 9, participant: 'Terza', player: 'Elmas', team: 'Napoli' }
    ];
    expect(page.filtered).toEqual([]);
    page.toggleParticipant(7, true);
    page.toggleParticipant(9, true);
    expect(page.filtered.map(r => r.id)).toEqual([1, 3]);
    expect(page.involvedParticipants.map(p => p.participantId)).toEqual([7, 9]);
    expect(page.playersFor(7).map(r => r.id)).toEqual([1]);
    page.query = 'Ricci';
    expect(page.filtered.map(r => r.id)).toEqual([1]);
  });

  it('removes all releases of a deselected team while preserving the others', () => {
    const page = new MiniAuctionComponent({} as any, {} as any);
    page.candidates = [
      { id: 1, participantId: 7 }, { id: 2, participantId: 7 }, { id: 3, participantId: 8 }
    ];
    page.involvedParticipantIds = new Set([7, 8]);
    page.selected = new Set([1, 2, 3]);
    page.toggleParticipant(7, false);
    expect(Array.from(page.selected)).toEqual([3]);
    page.toggleParticipant(7, true);
    expect(Array.from(page.selected)).toEqual([3]);
  });

  it('loads purchases for the chosen source and clears previous team and player selections', () => {
    const api = { candidates: jasmine.createSpy().and.returnValue(of([{ id: 4, participantId: 8 }])) };
    const page = new MiniAuctionComponent(api as any, {} as any);
    page.selected.add(1);
    page.involvedParticipantIds.add(7);
    page.chooseSource({ sessionCode: 'repair-session', date: '2026-10-01' });
    expect(api.candidates).toHaveBeenCalledWith('repair-session', '2026-10-01');
    expect(page.candidates).toEqual([{ id: 4, participantId: 8 }]);
    expect(page.selected.size).toBe(0);
    expect(page.involvedParticipantIds.size).toBe(0);
  });

  it('ignores a stale purchase list after choosing another auction', () => {
    const older = new Subject<any[]>(), latest = new Subject<any[]>();
    const api = { candidates: jasmine.createSpy().and.returnValues(older, latest) };
    const page = new MiniAuctionComponent(api as any, {} as any);
    page.chooseSource({ sessionCode: 'first', date: '2026-10-01' });
    page.chooseSource({ sessionCode: 'second', date: '2026-10-02' });
    older.next([{ id: 1 }]);
    expect(page.candidates).toEqual([]);
    latest.next([{ id: 2 }]);
    expect(page.candidates).toEqual([{ id: 2 }]);
  });

});
