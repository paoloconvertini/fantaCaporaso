'use strict';
(async () => {
  const host = document.getElementById('archive-content');
  const kind = document.body.dataset.archive;
  const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const number = n => typeof n === 'number' ? n.toLocaleString('it-IT') : n;
  const table = (caption, heads, rows) => `<div class="table-scroll" tabindex="0" role="region" aria-label="${esc(caption)}"><table><caption>${esc(caption)}</caption><thead><tr>${heads.map(h=>`<th scope="col">${esc(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(r=>`<tr>${r.map((c,i)=>i===0?`<th scope="row">${esc(number(c))}</th>`:`<td>${esc(number(c))}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
  const options = (values, chosen) => values.map(([value,label])=>`<option value="${esc(value)}"${String(value)===String(chosen)?' selected':''}>${esc(label)}</option>`).join('');
  try {
    const response = await fetch('/archivio/data.json');
    if (!response.ok) throw new Error('Dati non disponibili');
    const data = await response.json();
    const days = data.days.slice().sort((a,b)=>a.day-b.day);
    const teams = [...new Set(days.flatMap(d=>d.standings.map(r=>r[1])))].sort((a,b)=>a.localeCompare(b,'it'));
    let selected = days.at(-1).day, team = '';
    const dayFilters = () => `<div class="filters"><div><label for="day">Giornata</label><select id="day">${options(days.map(d=>[d.day,`${d.day}ª giornata`]),selected)}</select></div>${kind==='risultati'?teamFilter():''}</div>`;
    const teamFilter = () => `<div><label for="team">Squadra</label><select id="team">${options([['','Tutte le squadre'],...teams.map(t=>[t,t])],team)}</select></div>`;
    const source = title => `<p class="source">Fonte: ${esc(title)}</p>`;
    function render() {
      const d = days.find(d=>d.day===selected);
      if (kind==='classifiche') {
        host.innerHTML = dayFilters() + `<p>Stagione ${esc(data.season)} · ${selected}ª giornata · 16 squadre</p><p class="source">Ordine ufficiale del file. Fantapunti totali indica il valore cumulativo della colonna “M.Punti”.</p>` + table('Classifica generale',['Pos.','Squadra','Punti','Fantapunti totali','Vittorie','Pareggi','Sconfitte','Gol fatti','Gol subiti','Diff. reti','Giocate'],d.standings) + source(d.source) + `<h2>Andamento delle squadre</h2><p class="source">Punti di classifica cumulativi per giornata, secondo i file ufficiali.</p>` + table('Punti nel campionato',['Squadra',...days.map(x=>`${x.day}ª`)],teams.map(t=>[t,...days.map(x=>x.standings.find(r=>r[1]===t)[2])])) + `<details><summary>Anomalie nelle fonti</summary><ul>${data.issues.map(i=>`<li>${esc(i)}</li>`).join('')}</ul></details>`;
      }
      if (kind==='risultati') {
        const matches=d.matches.filter(m=>!team||m[0]===team||m[1]===team);
        host.innerHTML = dayFilters()+`<p>Stagione ${esc(data.season)} · ${selected}ª giornata</p>` + (d.matches.length?table('Incontri di campionato',['Casa','Ospite','Fantapunti casa','Fantapunti ospite','Gol casa','Gol ospite'],matches):`<p class="notice">Gli incontri di questo file riportano “38ª” nell’intestazione. La loro giornata deve essere confermata prima di assegnarli alla 1ª.</p>`) + source(d.source) + (d.unconfirmedMatches.length?`<details><summary>Incontri del file · giornata non confermata</summary>${table('Giornata da confermare',['Casa','Ospite','Fantapunti casa','Fantapunti ospite','Gol casa','Gol ospite'],d.unconfirmedMatches.filter(m=>!team||m[0]===team||m[1]===team))}</details>`:'') + `<p class="notice">I risultati provengono dalla sezione incontri. Nelle classifiche della 4ª, 5ª e 6ª giornata i gol di Johnson Oil presentano incongruenze: i valori delle fonti sono conservati.</p>`;
      }
      if (kind==='coppa-italia') {
        host.innerHTML=`<p>Stagione ${esc(data.season)} · Prima fase a gironi</p><p class="intro">Classifiche dopo la 1ª giornata di Coppa (6ª di campionato). La 2ª giornata, abbinata all’8ª di campionato, è ancora da giocare.</p><div class="filters">${teamFilter()}</div><h2>Classifiche dei gironi</h2>`+data.cupStandings.filter(g=>!team||g.standings.some(r=>r[1]===team)).map(g=>table(`Girone ${g.group}`,['Pos.','Squadra','Punti','Fantapunti totali','Vittorie','Pareggi','Sconfitte','Gol fatti','Gol subiti','Diff. reti','Giocate'],g.standings)).join('')+`<h2>Risultati e calendario</h2>`+[...new Set(data.cup.map(m=>m.day))].sort((a,b)=>a-b).map(day=>{const ms=data.cup.filter(m=>m.day===day&&(!team||m.home===team||m.away===team));return table(`${day}ª di Coppa · ${data.cup.find(m=>m.day===day).leagueDay}ª di campionato`,['Girone','Casa','Ospite','Risultato','Fantapunti casa','Fantapunti ospite'],ms.map(m=>[m.group,m.home,m.away,m.status==='Disputata'?`${m.homeGoals} – ${m.awayGoals}`:'Da giocare',m.homeMagic??'—',m.awayMagic??'—']));}).join('')+source('FantaCAPORASO 2026-2027 Coppa Italia Calendario - 1ª Fase.xlsx; Classifica 01ª e 02ª Giornata.xlsx');
      }
      if (kind==='statistiche') {
        const stats=data.statistics;
        host.innerHTML=`<p>Stagione ${esc(stats.season)} · Dati fino alla ${stats.day}ª giornata</p><p class="intro">I riepiloghi dei file Formazioni, squadra per squadra. Le colonne “giornata” indicano il turno più recente; i totali sono cumulativi.</p><div class="filters">${teamFilter()}</div><nav class="archive-nav" aria-label="Categorie statistiche">${stats.sections.map(s=>`<a href="#stat-${esc(s.key)}">${esc(s.title)}</a>`).join('')}</nav>`+stats.sections.map(s=>`<section class="archive-section" id="stat-${esc(s.key)}"><h2>${esc(s.title)}</h2><p class="source">${esc(s.description)}</p>${table(s.title,s.heads,s.rows.filter(r=>!team||r[0]===team))}</section>`).join('')+source(stats.source);
      }
      host.querySelector('#day')?.addEventListener('change',e=>{selected=Number(e.target.value);render();document.getElementById('day').focus();});
      host.querySelector('#team')?.addEventListener('change',e=>{team=e.target.value;render();document.getElementById('team').focus();});
    }
    render();
  } catch (error) { host.insertAdjacentHTML('afterbegin','<p class="notice" role="alert">Impossibile caricare i selettori. Sono disponibili qui sotto i dati dell’ultima giornata verificata.</p>'); }
})();
