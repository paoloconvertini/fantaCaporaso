const { test } = require('node:test');
const assert = require('node:assert/strict');
const { searchPlayers, freePlayers, validateCatalog } = require('../../landing-page/players.js');
const players = [
  { name: 'Folorunsho', club: 'Monza', role: 'CENTROCAMPISTA', owner: 'VIKING 84' },
  { name: 'Koné I.', club: 'Sassuolo', role: 'CENTROCAMPISTA', owner: null },
  { name: 'Ricci', club: 'Milan', role: 'CENTROCAMPISTA', owner: 'Kecavoli' },
  { name: 'Ricci L.', club: 'Roma', role: 'DIFENSORE', owner: null }
];
test('search finds the real roster owner and excludes that player from free results', () => {
  assert.equal(searchPlayers(players, 'folorunsho')[0].owner, 'VIKING 84');
  assert.deepEqual(freePlayers(players, 'folorunsho', ''), []);
});
test('search handles accents, punctuation, casing and exact-name priority', () => {
  assert.equal(searchPlayers(players, ' KONE i ')[0].name, 'Koné I.');
  assert.deepEqual(searchPlayers(players, 'RICCI').map(p => p.name), ['Ricci', 'Ricci L.']);
});
test('short or missing search does not mislabel absent players as free', () => {
  assert.deepEqual(searchPlayers(players, ''), []);
  assert.deepEqual(searchPlayers(players, 'r'), []);
  assert.deepEqual(searchPlayers(players, 'not-in-the-list'), []);
});
test('role and text filters apply together to unowned players only', () => {
  assert.deepEqual(freePlayers(players, '', 'DIFENSORE').map(p => p.name), ['Ricci L.']);
  assert.deepEqual(freePlayers(players, 'ricci', 'CENTROCAMPISTA'), []);
  assert.equal(freePlayers(players, '', '').length, 2);
});
test('catalog is rejected when ownership counts, roles or identities are invalid', () => {
  const data = { players, updatedAt: '2026-10-05T12:00:00+02:00', rosteredCount: 2, freeCount: 2, teamCount: 2 };
  assert.equal(validateCatalog(data), data);
  assert.throws(() => validateCatalog({ ...data, freeCount: 3 }));
  assert.throws(() => validateCatalog({ ...data, players: [...players, players[0]] }));
  assert.throws(() => validateCatalog({ ...data, players: [{ ...players[0], role: 'INVALID' }] }));
});
