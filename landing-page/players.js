(function () {
  'use strict';
  const roles = { PORTIERE: 'Portiere', DIFENSORE: 'Difensore', CENTROCAMPISTA: 'Centrocampista', ATTACCANTE: 'Attaccante' };
  const roleOrder = Object.keys(roles);
  const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');

  function validateCatalog(data) {
    if (!data || !Array.isArray(data.players) || !data.players.length || !Number.isFinite(Date.parse(data.updatedAt))) throw new Error('Catalogo non valido');
    const seen = new Set();
    for (const player of data.players) {
      if (!player || !roles[player.role] || !['name', 'club'].every(key => typeof player[key] === 'string' && player[key].trim())
        || !(player.owner === null || (typeof player.owner === 'string' && player.owner.trim()))) throw new Error('Calciatore non valido');
      const key = JSON.stringify([player.name.toLowerCase(), player.club.toLowerCase(), player.role]);
      if (seen.has(key)) throw new Error('Calciatore duplicato');
      seen.add(key);
    }
    const owned = data.players.filter(player => player.owner !== null);
    if (owned.length !== data.rosteredCount || data.players.length - owned.length !== data.freeCount
      || new Set(owned.map(player => player.owner)).size !== data.teamCount) throw new Error('Conteggi non coerenti');
    return data;
  }

  function searchPlayers(players, query) {
    const text = normalize(query);
    if (text.length < 2) return [];
    return players.filter(player => normalize(player.name).includes(text)).sort((a, b) => {
      const exactA = normalize(a.name) === text;
      const exactB = normalize(b.name) === text;
      return Number(exactB) - Number(exactA) || a.name.localeCompare(b.name, 'it') || a.club.localeCompare(b.club, 'it');
    });
  }

  function freePlayers(players, query, role) {
    const text = normalize(query);
    return players.filter(player => player.owner === null && (!role || player.role === role) && normalize(player.name).includes(text))
      .sort((a, b) => roleOrder.indexOf(a.role) - roleOrder.indexOf(b.role) || a.name.localeCompare(b.name, 'it'));
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = { normalize, validateCatalog, searchPlayers, freePlayers };
  if (typeof document === 'undefined') return;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  async function initialize() {
    const form = document.querySelector('[data-player-search]');
    if (form) form.addEventListener('submit', event => event.preventDefault());
    let data;
    try {
      const response = await fetch('/players.json', { cache: 'no-cache' });
      if (!response.ok) throw new Error('Catalogo non raggiungibile');
      data = validateCatalog(await response.json());
    } catch (error) {
      for (const id of ['search-message', 'free-message']) {
        const message = document.getElementById(id);
        if (message) message.textContent = 'Non è stato possibile caricare i calciatori. Ricarica la pagina per riprovare.';
      }
      return;
    }
    const date = new Intl.DateTimeFormat('it-IT', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Rome' }).format(new Date(data.updatedAt));
    document.querySelectorAll('[data-snapshot-date]').forEach(node => { node.textContent = `Dati aggiornati al ${date} · Ora italiana`; });

    if (form) {
      const input = document.getElementById('player-query');
      const message = document.getElementById('search-message');
      const results = document.getElementById('player-results');
      function renderSearch() {
        results.replaceChildren();
        results.hidden = true;
        if (normalize(input.value).length < 2) {
          message.textContent = 'Scrivi almeno 2 lettere del nome del calciatore.';
          return;
        }
        const matches = searchPlayers(data.players, input.value);
        if (!matches.length) {
          message.textContent = 'Nessun calciatore trovato nelle rose o tra gli svincolati. Prova con un altro nome.';
          return;
        }
        message.textContent = matches.length > 10 ? `${matches.length} calciatori trovati. Mostriamo i primi 10: completa il nome per affinare la ricerca.`
          : `${matches.length} ${matches.length === 1 ? 'calciatore trovato' : 'calciatori trovati'}.`;
        const fragment = document.createDocumentFragment();
        for (const player of matches.slice(0, 10)) {
          const row = element('li', 'player-result');
          const identity = element('div', 'player-identity');
          identity.append(element('strong', '', player.name), element('span', '', `${roles[player.role]} · ${player.club}`));
          const owner = element('span', player.owner === null ? 'player-owner owner-free' : 'player-owner', player.owner === null ? 'Svincolato' : player.owner);
          row.append(identity, owner);
          fragment.append(row);
        }
        results.append(fragment);
        results.hidden = false;
      }
      input.addEventListener('input', renderSearch);
      form.addEventListener('submit', renderSearch);
      renderSearch();
    }

    const table = document.getElementById('free-player-table');
    if (table) {
      const input = document.getElementById('free-query');
      const select = document.getElementById('free-role');
      const message = document.getElementById('free-message');
      const body = table.querySelector('tbody');
      function renderFree() {
        const players = freePlayers(data.players, input.value, select.value);
        const fragment = document.createDocumentFragment();
        for (const player of players) {
          const row = element('tr');
          const name = element('th', '', player.name);
          name.scope = 'row';
          const role = element('td', 'role-cell');
          role.append(element('span', `role-badge role-${player.role.toLowerCase()}`, roles[player.role]));
          row.append(name, role, element('td', 'club-cell', player.club));
          fragment.append(row);
        }
        body.replaceChildren(fragment);
        table.hidden = players.length === 0;
        message.textContent = players.length === 0 ? 'Nessun svincolato corrisponde ai filtri scelti.'
          : `${players.length} ${players.length === 1 ? 'svincolato' : 'svincolati'}${players.length === data.freeCount ? ' nel listone.' : ` su ${data.freeCount} nel listone.`}`;
      }
      input.addEventListener('input', renderFree);
      select.addEventListener('change', renderFree);
      renderFree();
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
  else initialize();
})();
