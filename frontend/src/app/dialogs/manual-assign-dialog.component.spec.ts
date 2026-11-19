import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ManualAssignDialogComponent } from './manual-assign-dialog.component';

describe('ManualAssignDialogComponent', () => {
  it('opens empty, searches only the latest text and clears a short query', fakeAsync(() => {
    const searchPlayers = jasmine.createSpy('searchPlayers').and.returnValue(of([{ id: 10 }]));
    const component = new ManualAssignDialogComponent(
      { searchPlayers } as any,
      jasmine.createSpyObj('MatDialogRef', ['close'])
    );

    expect(component.query).toBe('');
    expect(searchPlayers).not.toHaveBeenCalled();
    component.search('Ro');
    tick(150);
    component.search('Roma');
    tick(299);
    expect(searchPlayers).not.toHaveBeenCalled();
    tick(1);
    expect(searchPlayers).toHaveBeenCalledOnceWith('Roma');
    expect(component.players).toEqual([{ id: 10 }]);

    component.search('R');
    tick(300);
    expect(searchPlayers).toHaveBeenCalledTimes(1);
    expect(component.players).toEqual([]);
    expect(component.searching).toBeFalse();
    component.ngOnDestroy();
  }));

  it('selects an assigned player and returns corrected owner and amount', fakeAsync(() => {
    const dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    const component = new ManualAssignDialogComponent(
      {
        searchPlayers: () => of([{
          id: 10,
          name: 'Giocatore',
          team: 'Roma',
          role: 'ATTACCANTE',
          assigned: true,
          ownerParticipantId: 1,
          ownerParticipantName: 'Vecchio proprietario',
          amount: 18
        }]),
        getEligibleParticipants: () => of([
          { id: 1, name: 'Vecchio proprietario' },
          { id: 2, name: 'Nuovo proprietario' }
        ])
      } as any,
      dialogRef
    );

    component.search('Giocatore');
    tick(300);
    component.selectPlayer(10);
    component.selectedParticipantId = 2;
    component.amount = 35;
    component.save();

    expect(dialogRef.close).toHaveBeenCalledWith({ playerId: 10, playerName: 'Giocatore', playerTeam: 'Roma', participantId: 2, amount: 35 });
  }));

  it('shows only participants returned as eligible for the selected player', fakeAsync(() => {
    const component = new ManualAssignDialogComponent(
      {
        searchPlayers: () => of([{
          id: 11, name: 'Punta', team: 'Roma', role: 'ATTACCANTE', assigned: false
        }]),
        getEligibleParticipants: () => of([{ id: 2, name: 'Posto libero' }])
      } as any,
      jasmine.createSpyObj('MatDialogRef', ['close'])
    );

    component.search('Punta');
    tick(300);
    component.selectPlayer(11);

    expect(component.participants).toEqual([{ id: 2, name: 'Posto libero' }]);
  }));
});
