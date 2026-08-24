import { RosterSwapDialogComponent } from './roster-swap-dialog.component';
import { RosterDto } from '../models/roster.dto';

describe('RosterSwapDialogComponent', () => {
  it('apre nella squadra ricevente il reparto del giocatore selezionato in uscita', () => {
    const defender: RosterDto = {
      participantId: 1,
      participantName: 'Squadra A',
      playerId: 10,
      playerName: 'Difensore test',
      team: 'Inter',
      role: 'DIFENSORE',
      amount: 5,
      valore: 8,
      residui: 100,
      active: true
    };
    const component = new RosterSwapDialogComponent({
      sourceParticipantId: 1,
      sourceParticipantName: 'Squadra A',
      sourceRoster: [defender],
      participants: [{ id: 1, name: 'Squadra A' }, { id: 2, name: 'Squadra B' }],
      initialRole: 'PORTIERE'
    }, {} as any, {} as any, {} as any);

    component.toggle(defender, 'source');

    expect(component.destinationRole).toBe('DIFENSORE');
    expect(component.sourceSelected.has(defender.playerId)).toBeTrue();
  });
});
