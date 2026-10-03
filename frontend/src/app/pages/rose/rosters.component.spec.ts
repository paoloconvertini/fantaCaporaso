import { RostersComponent } from './rosters.component';
import { throwError } from 'rxjs';

describe('RostersComponent', () => {
  it('shows the export validation message returned as a JSON blob', async () => {
    const message = 'Export FantaMaster bloccato: GenSim e 2 Monelli — Bleve; GenSim e 2 Monelli — Penev';
    let shown!: () => void;
    const notification = new Promise<void>(resolve => shown = resolve);
    const snackBar = { open: jasmine.createSpy('open').and.callFake(() => shown()) };
    const adminApi = {
      exportRostersExcel: () => throwError(() => ({
        error: new Blob([JSON.stringify({ code: 'BAD_REQUEST', message })], { type: 'application/json' })
      }))
    };
    const component = new RostersComponent({} as any, adminApi as any, {} as any, snackBar as any, {} as any);
    component.exportRosters();
    await notification;
    expect(snackBar.open).toHaveBeenCalledWith(message, 'Chiudi');
  });

  it('sorts players alphabetically using the API playerName field', () => {
    const component = new RostersComponent({} as any, {} as any, {} as any, {} as any, {} as any);
    const players = [
      { playerId: 1, playerName: 'Zortea', role: 'DIFENSORE', amount: 1, valore: 8, participantId: 10, participantName: 'Team' },
      { playerId: 2, playerName: 'Buongiorno', role: 'DIFENSORE', amount: 2, valore: 12, participantId: 10, participantName: 'Team' },
      { playerId: 3, playerName: 'gabbia', role: 'DIFENSORE', amount: 3, valore: 0, participantId: 10, participantName: 'Team' }
    ];

    (component as any).buildTable(players, [{ id: 10, name: 'Team', remainingCredits: 494 }]);

    expect(component.playersFor(10, 'DIFENSORE').map(player => player.playerName))
      .toEqual(['Buongiorno', 'gabbia', 'Zortea']);
    expect(component.totalSpent(10)).toBe(6);
    expect(component.totalMarketValue(10)).toBe(20);
    expect(component.participants[0].remainingCredits).toBe(494);
  });
});
