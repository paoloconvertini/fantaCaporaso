import { of, Subject, throwError } from 'rxjs';
import { AdminComponent } from './admin.component';

describe('AdminComponent skip', () => {
  function createComponent() {
    const api = jasmine.createSpyObj('AdminApiService', [
      'randomSkip', 'randomNext', 'getRandomState'
    ]);
    api.randomSkip.and.returnValue(of(null));
    api.randomNext.and.returnValue(of(null));
    api.getRandomState.and.returnValue(of({ remaining: {}, skipped: {}, openSlots: {} }));
    const snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    const component = new AdminComponent(api, {} as any, snackBar);
    component.player = 'Giocatore';
    component.team = 'Roma';
    return { component, api };
  }

  it('disables skip while an active round has bidders', () => {
    const { component, api } = createComponent();
    component.round = { closed: false };
    component.activeUsers = ['Squadra'];

    component.skip();

    expect(component.skipDisabled).toBeTrue();
    expect(component.skipTooltip).toContain('sono presenti offerte');
    expect(api.randomSkip).not.toHaveBeenCalled();
  });

  it('uses the safe skip endpoint when the active round has no bids', () => {
    const { component, api } = createComponent();
    component.round = { closed: false };
    component.activeUsers = [];

    component.skip();

    expect(api.randomSkip).toHaveBeenCalledOnceWith('Giocatore', 'Roma');
    expect(component.round).toBeNull();
    expect(component.skipping).toBeFalse();
  });
});


describe('AdminComponent manual assignment navigation', () => {
  function setup(selectedName = 'Chiamato') {
    const result = { playerId: 10, playerName: selectedName, playerTeam: 'Roma', participantId: 2, amount: 3 };
    const api = jasmine.createSpyObj('AdminApiService', ['updateAssignment', 'getRound', 'getRandomState', 'randomNext']);
    api.updateAssignment.and.returnValue(of({}));
    api.getRound.and.returnValue(of(null));
    api.getRandomState.and.returnValue(of({ remaining: {}, skipped: {}, openSlots: {} }));
    api.randomNext.and.returnValue(of({ name: 'Successivo', team: 'Inter', role: 'DIFENSORE', value: 7 }));
    const dialog = { open: () => ({ afterClosed: () => of(result) }) };
    const snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    const component = new AdminComponent(api, dialog as any, snackBar);
    component.player = 'Chiamato';
    component.team = 'Roma';
    return { component, api, snackBar };
  }

  it('draws the next player after assigning the displayed player without a round', () => {
    const { component, api } = setup();
    component.openManualAssign();
    expect(api.updateAssignment).toHaveBeenCalledOnceWith(10, 2, 3);
    expect(api.randomNext).toHaveBeenCalledTimes(1);
    expect(component.player).toBe('Successivo');
    expect(component.loadingAssign).toBeFalse();
  });

  it('confirms a successful manual assignment with player, owner and amount', () => {
    const { component, api, snackBar } = setup();
    const assignment = { player: 'Chiamato', playerTeam: 'Roma', winner: 'Squadra vincitrice', amount: 3 };
    api.updateAssignment.and.returnValue(of({ assignment }));
    api.getRound.and.returnValue(of({ closed: true, bids: {}, lastAssignment: assignment }));
    component.openManualAssign();
    expect(snackBar.open).toHaveBeenCalledWith(
      'Chiamato assegnato a Squadra vincitrice per 3 crediti', 'Chiudi', { duration: 4000 });
    expect(component.latestAssignment).toEqual(assignment);
    expect(component.player).toBe('Successivo');
  });

  it('keeps the latest manual assignment visible after reloading the round', () => {
    const { component, api } = setup();
    const assignment = { player: 'Manuale', winner: 'Squadra', amount: 1 };
    api.getRound.and.returnValue(of({ closed: true, bids: {}, lastAssignment: assignment }));
    component.load();
    expect(component.latestAssignment).toEqual(assignment);
  });

  it('keeps the displayed player when correcting another player', () => {
    const { component, api } = setup('Altro giocatore');
    component.openManualAssign();
    expect(api.randomNext).not.toHaveBeenCalled();
    expect(component.player).toBe('Chiamato');
  });

  it('does not draw the next player when assignment fails', () => {
    const { component, api, snackBar } = setup();
    api.updateAssignment.and.returnValue(throwError(() => ({ error: { message: 'Credito insufficiente' } })));
    component.openManualAssign();
    expect(api.randomNext).not.toHaveBeenCalled();
    expect(component.player).toBe('Chiamato');
    expect(component.loadingAssign).toBeFalse();
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('draws only once when assignment response and live notification arrive together', () => {
    const { component, api } = setup();
    const next = new Subject<any>();
    api.randomNext.and.returnValue(next);
    component.openManualAssign();
    (component as any).advanceAssignedPlayer('Chiamato', 'Roma');
    expect(api.randomNext).toHaveBeenCalledTimes(1);
    next.next({ name: 'Successivo', team: 'Inter', role: 'DIFENSORE', value: 7 });
    (component as any).advanceAssignedPlayer('Chiamato', 'Roma');
    expect(api.randomNext).toHaveBeenCalledTimes(1);
    expect(component.player).toBe('Successivo');
  });
});
