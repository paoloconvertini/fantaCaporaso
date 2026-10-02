import { of } from 'rxjs';
import { ManualAssignDialogComponent } from './manual-assign-dialog.component';

describe('ManualAssignDialogComponent', () => {
  it('selects an assigned player and returns corrected owner and amount', () => {
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
      dialogRef,
      {}
    );

    component.search('Giocatore');
    component.selectPlayer(10);
    component.selectedParticipantId = 2;
    component.amount = 35;
    component.save();

    expect(dialogRef.close).toHaveBeenCalledWith({ playerId: 10, playerName: 'Giocatore', playerTeam: 'Roma', participantId: 2, amount: 35 });
  });

  it('shows only participants returned as eligible for the selected player', () => {
    const component = new ManualAssignDialogComponent(
      {
        searchPlayers: () => of([{
          id: 11, name: 'Punta', team: 'Roma', role: 'ATTACCANTE', assigned: false
        }]),
        getEligibleParticipants: () => of([{ id: 2, name: 'Posto libero' }])
      } as any,
      jasmine.createSpyObj('MatDialogRef', ['close']),
      {}
    );

    component.search('Punta');
    component.selectPlayer(11);

    expect(component.participants).toEqual([{ id: 2, name: 'Posto libero' }]);
  });
});
