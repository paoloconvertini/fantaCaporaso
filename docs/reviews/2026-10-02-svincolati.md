# Verifica svincolati — mercato ottobre 2026

Confronto in sola lettura di PROD con il PDF locale “03 Mercato di Ottobre (Listone Liberi)”, aggiornato dopo le cessioni. Contiene 268 giocatori: tutti i 200 del primo Excel Drive più 68 cessioni annotate nel PDF. Il confronto usa i nomi completi, normalizzando il trattino tipografico.

62 giocatori liberi e attivi non sono ammessi: assenti dal listone finale oppure esplicitamente indicati come partiti dall’utente (Patric e Milik). Nessuna rosa, offerta o credito PROD è stato modificato da questa verifica. Patric e Milik sono nel PDF ma esclusi per istruzione dell’utente; il precedente movimento RELEASE di Patric resta un fatto storico.

## Giocatori da escludere

| Giocatore | Squadra | Motivo |
|---|---|---|
| Albarracin | Cagliari | Assente dal listone finale |
| Asllani | Inter | Assente dal listone finale |
| Audero | Como | Assente dal listone finale |
| Barcella | Frosinone | Assente dal listone finale |
| Berenbruch | Inter | Assente dal listone finale |
| Bohinen | Venezia | Assente dal listone finale |
| Brorsson | Monza | Assente dal listone finale |
| Cajuste | Napoli | Assente dal listone finale |
| Camara A | Udinese | Assente dal listone finale |
| Casas | Venezia | Assente dal listone finale |
| Cichero | Frosinone | Assente dal listone finale |
| Colley M | Frosinone | Assente dal listone finale |
| Comi | Atalanta | Assente dal listone finale |
| Corrado | Frosinone | Assente dal listone finale |
| De Marzi | Roma | Assente dal listone finale |
| Di Gregorio | Juventus | Assente dal listone finale |
| Farji | Venezia | Assente dal listone finale |
| Galazzi | Monza | Assente dal listone finale |
| Gelli J | Frosinone | Assente dal listone finale |
| Gigot | Lazio | Assente dal listone finale |
| Grabara | Juventus | Assente dal listone finale |
| Grosso F | Frosinone | Assente dal listone finale |
| Iannoni | Sassuolo | Assente dal listone finale |
| Junior Ligue | Venezia | Assente dal listone finale |
| Kamate | Inter | Assente dal listone finale |
| Karlsson | Bologna | Assente dal listone finale |
| Kospo | Fiorentina | Assente dal listone finale |
| Kouassi | Lecce | Assente dal listone finale |
| Kouda | Parma | Assente dal listone finale |
| Koutsoupias | Frosinone | Assente dal listone finale |
| Kumer Celik | Genoa | Assente dal listone finale |
| Lella | Venezia | Assente dal listone finale |
| Lindstrom | Napoli | Assente dal listone finale |
| Loubao | Monza | Assente dal listone finale |
| Lysionok | Genoa | Assente dal listone finale |
| Mannini | Roma | Assente dal listone finale |
| Martin | Genoa | Assente dal listone finale |
| Mazzitelli | Como | Assente dal listone finale |
| Milik | Juventus | Partito: istruzione utente |
| Mlacic | Udinese | Assente dal listone finale |
| Moro L | Sassuolo | Assente dal listone finale |
| Nuamah | Sassuolo | Assente dal listone finale |
| Obaretin | Napoli | Assente dal listone finale |
| Odogu | Milan | Assente dal listone finale |
| Okoro | Venezia | Assente dal listone finale |
| Oyono J | Frosinone | Assente dal listone finale |
| Paleari | Torino | Assente dal listone finale |
| Patric | Lazio | Partito: istruzione utente |
| Perin | Juventus | Assente dal listone finale |
| Piana | Udinese | Assente dal listone finale |
| Pittarella | Milan | Assente dal listone finale |
| Pizzignacco | Monza | Assente dal listone finale |
| Pompei | Atalanta | Assente dal listone finale |
| Pozzi A | Venezia | Assente dal listone finale |
| Prati | Cagliari | Assente dal listone finale |
| Renzetti D | Lazio | Assente dal listone finale |
| Rossi F | Atalanta | Assente dal listone finale |
| Rui Modesto | Udinese | Assente dal listone finale |
| Siviero | Torino | Assente dal listone finale |
| Suzuki | Parma | Assente dal listone finale |
| Topalovic | Inter | Assente dal listone finale |
| Zeroli | Milan | Assente dal listone finale |

## Limiti

L’elenco riguarda gli svincolati, non i giocatori già in rosa. Una porta già assegnata resta esclusa dalla chiamata secondo le regole esistenti. La correzione generica dell’import conserva i liberi assenti con una cessione RELEASE valida della sessione: il primo Excel precede infatti le cessioni. Il PDF finale è il riferimento per la riconciliazione puntuale del mercato corrente.

## Esito in DEV

Applicata la riconciliazione dopo backup validato: 61 svincolati disattivati, Patric e Milik marcati partiti nella sessione corrente. Rose e record partecipanti (quindi crediti) confrontati integralmente prima e dopo: identici. La differenza con i 62 candidati PROD dipende dallo stato diverso della copia DEV; Corrado, Grabara non era tra i liberi disattivati in DEV.

La migrazione `database/migrations/20261002_reconcile_market_free_players.sql` contiene la correzione puntuale per questo mercato. È verificata in DEV e applicata a PROD il 2 ottobre 2026 dopo backup validato. Blocca una sessione diversa e Patric/Milik eventualmente acquisiti da una rosa.

## Esito in PROD

Applicata la migrazione il 2 ottobre 2026: 62 svincolati disattivati e Patric/Milik marcati partiti. Backup: `backups/fantasta-before-deploy-20261002-124404.dump`. Confronto integrale tramite hash ordinati prima e dopo di `rosters`, `participant` e `app_user`: identici (400 righe rosa, 16 partecipanti). Nessuna password DEV trasferita in PROD.
