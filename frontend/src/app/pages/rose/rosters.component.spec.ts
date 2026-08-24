import { RostersComponent } from './rosters.component';

describe('RostersComponent', () => {
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
