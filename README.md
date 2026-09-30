# FantAsta

Gestore dell'asta Fantacalcio con backend Quarkus, frontend Angular, PostgreSQL e aggiornamenti live WebSocket.

L'autenticazione e' interna all'applicazione: utenti, ruoli e hash password sono salvati in PostgreSQL; il backend emette un JWT in cookie `HttpOnly`. Keycloak non e' piu' necessario.

## Quale configurazione IntelliJ devo usare?

Nel normale utilizzo servono soltanto questi quattro comandi:

| Obiettivo | Configurazione | Risultato |
| --- | --- | --- |
| Sviluppare o provare modifiche senza toccare i dati reali | `DEV - AVVIA` | Avvia database DEV separato, backend Quarkus e frontend Angular su `http://localhost:4200` |
| Usare l'applicazione reale nei giorni dell'asta | `PROD - ASTA` | Verifica o avvia PROD e apre l'interfaccia dell'asta in Google Chrome |
| Preparare e pubblicare la Gazzetta | `PROD - GAZZETTA` | Verifica o avvia PROD e apre la redazione Gazzetta in Google Chrome |
| Controllare se PROD e' gia' pronto | `PROD - STATO` | Verifica container, volume, database, pagina locale e link pubblico |
| Spegnere i servizi applicativi PROD | `PROD - FERMA` | Ferma applicazione e tunnel conservando PostgreSQL e il volume `backend_pgdata` |

Le configurazioni nella cartella IntelliJ `Componenti DEV` sono dettagli interni usati da
`DEV - AVVIA`: non vanno lanciate singolarmente nell'uso ordinario. La cartella
`Manutenzione DEV` contiene soltanto i controlli del database di sviluppo.

### Pubblicare la Gazzetta

Il flusso editoriale assistito produce ogni edizione in `gazzetta-editions/<stagione>/giornata-<numero>`.
Testi e copertina vengono preparati e verificati prima della pubblicazione; l'export FantaMaster
resta la fonte per risultati, fantapunteggi, classifica e formazioni.
Le immagini profilo autorizzate delle squadre sono conservate in `gazzetta-assets/profili/` e
possono essere riutilizzate per rendere copertine e rubriche riconoscibili alla lega.
Le edizioni mantengono una larghezza editoriale fissa anche sugli schermi piccoli: il telefono
mostra inizialmente l'intera pagina e il lettore ingrandisce articoli e colonne con lo zoom,
senza trasformare il giornale in una sequenza verticale di blocchi.

Per controllare un'edizione senza pubblicarla:

```bash
./scripts/preview-gazzetta.sh gazzetta-editions/2026-27/giornata-01
```

La pubblicazione non usa il database e richiede conferma esplicita:

```bash
./scripts/publish-gazzetta.sh gazzetta-editions/2026-27/giornata-01 --confirm
```

Lo script accetta soltanto cartelle sotto `gazzetta-editions/`, verifica il progetto Cloudflare
dedicato `fantacaporaso-gazzetta` e rifiuta credenziali o configurazioni incomplete. Dopo il deploy
verificare `https://gazzetta.fantacaporaso.it`.

Il precedente laboratorio nell'applicazione resta temporaneamente disponibile durante la
transizione, ma non è il flusso editoriale raccomandato:

1. eseguire `PROD - GAZZETTA`: il comando verifica lo stato e, se PROD e' gia' attivo, non riavvia né ricrea i container;
2. attendere l'apertura automatica di `http://localhost:8088/admin/gazzetta` in Google Chrome ed effettuare il login admin;
3. caricare l'export FantaMaster, generare l'anteprima e modificare testi e immagine;
4. premere `Pubblica` soltanto dopo il controllo finale;
5. verificare `https://gazzetta.fantacaporaso.it`.

Non avviare DEV per pubblicare: in DEV il pulsante Cloudflare e' intenzionalmente disabilitato.
Se PROD e' gia' attivo non occorre riavviarlo e non va eseguito alcun rebuild.

## Architettura operativa

Lo scenario previsto per l'asta usa un solo Mac:

```text
partecipanti -> Cloudflare Tunnel -> Nginx -> frontend
                                         -> backend -> PostgreSQL locale
```

Cloudflare Tunnel usa una connessione in uscita e non richiede IP pubblico, port forwarding o database remoto. Neon non fa parte del percorso operativo.
La configurazione di produzione autorizza l'origin stabile `https://asta.fantacaporaso.it`, oltre a localhost e agli indirizzi LAN privati sulla porta 8088.

## Requisiti

- Docker Desktop con Docker Compose
- Java 21 per lo sviluppo backend
- Maven 3.9+
- Node 20.19.5 e npm 10 per lo sviluppo frontend
- un tunnel Cloudflare configurato verso `http://reverse-proxy:80` per l'accesso pubblico

## Sviluppo

1. Preparare gli env locali:

```bash
cp config/application-dev.env.example config/application-dev.env
```

Compilare i secret e le credenziali. Lo sviluppo usa esclusivamente il database `fantasta_dev` sul volume `fantasta_dev_pgdata`; il database storico di produzione resta sul volume `backend_pgdata`. Il catalogo calciatori non viene più sincronizzato automaticamente all'avvio: l'import stagionale è un'operazione esplicita dell'admin.

2. Avviare PostgreSQL:

```bash
./scripts/start-dev.sh
```

3. Avviare il backend con Java 21:

```bash
cd backend
set -a
source ../config/application-dev.env
set +a
mvn quarkus:dev
```

4. Avviare il frontend:

```bash
cd frontend
npm install
npm run start
```

URL frontend: `http://localhost:4200`.

Da IntelliJ `DEV - AVVIA` avvia insieme database separato, Quarkus e Angular; le tre configurazioni restano disponibili anche singolarmente. `DEV - DATABASE - FERMA` arresta soltanto PostgreSQL dev e conserva i dati. Il clone iniziale dalla produzione si esegue una sola volta con `./scripts/clone-prod-to-dev.sh`; lo script rifiuta di sovrascrivere un database dev gia' inizializzato.

## Stack completo locale

Creare il file reale, ignorato da Git:

```bash
cp config/application-prod.local.env.example config/application-prod.local.env
```

Sostituire almeno `POSTGRES_PASSWORD`, `JWT_SECRET`, `BOOTSTRAP_ADMIN_USERNAME` e `BOOTSTRAP_ADMIN_PASSWORD`. `JWT_SECRET` deve avere almeno 32 caratteri.

Avvio:

```bash
docker compose --env-file config/application-prod.local.env -f docker-compose.prod.yml up -d --build
```

Verifica:

```bash
docker compose --env-file config/application-prod.local.env -f docker-compose.prod.yml ps
curl --fail http://localhost:8088/api/auth/me
```

La seconda verifica deve rispondere `401`: dimostra che proxy e backend sono raggiungibili e l'endpoint e' protetto.

Lo stack espone per default il reverse proxy su `127.0.0.1:8088` e PostgreSQL su `127.0.0.1:5433` per gli strumenti di sviluppo locali. Per una prova diretta dalla LAN impostare temporaneamente `PUBLIC_BIND_ADDRESS=0.0.0.0`; PostgreSQL resta comunque limitato al Mac.

## Accesso pubblico con Cloudflare

La pagina istituzionale statica si trova in `landing-page/` ed e' destinata a Cloudflare Pages sul dominio principale `fantacaporaso.it`. Non dipende dai container locali e mostra lo stato di mercato chiuso anche quando il Mac e' spento. Il sottodominio `asta.fantacaporaso.it` resta riservato all'applicazione d'asta pubblicata tramite Cloudflare Tunnel.

La conclusione dell'intera sessione avviene dal comando admin `Concludi sessione`, distinto dalla chiusura del singolo round. Il comando rifiuta round ancora attivi, salva una sola fotografia finale delle rose, pulisce giro/skip e genera in background l'archivio statico completo in `backend/target/auction-archive/storico/`. Le sessioni precedenti restano consultabili e la pagina consente la ricerca per calciatore.

La pubblicazione e' separata per ambiente: DEV deve mantenere `APP_ENVIRONMENT=dev` e `AUCTION_ARCHIVE_PUBLISH_ENABLED=false` e produce soltanto l'anteprima locale. Soltanto PROD puo' abilitare la consegna a Cloudflare, impostando entrambe le condizioni; il codice rifiuta qualsiasi pubblicazione proveniente da DEV. Un errore dell'archivio non annulla mai la conclusione della sessione e viene registrato sul relativo record per un successivo tentativo.

Le credenziali Pages si configurano esclusivamente con `bash scripts/configure-cloudflare-pages.sh`: il token viene richiesto con input nascosto e salvato nel file PROD `config/application-cloud.env`, escluso da Git. Il token deve avere soltanto il permesso account `Pages Write`; non riutilizzare mai il token del Tunnel. Il deploy statico usa Wrangler nel container PROD e viene eseguito soltanto dopo la conclusione della sessione.

### Avvio servizi PROD da IntelliJ

Nel selettore delle configurazioni Run sono disponibili:

- `PROD - ASTA`: controlla o avvia PROD, verifica realmente l'HTTPS, stampa il link da condividere e apre l'interfaccia dell'asta in Google Chrome;
- `PROD - GAZZETTA`: esegue gli stessi controlli senza creare uno stack separato e apre direttamente la redazione Gazzetta in Google Chrome;
- `PROD - STATO`: ristampa link, container e controlli di raggiungibilità;
- `PROD - FERMA`: arresta soltanto i servizi applicativi; PostgreSQL di produzione resta attivo sul volume persistente.

L'applicazione usa il Named Tunnel Cloudflare `fantacaporaso-asta` e l'indirizzo stabile `https://asta.fantacaporaso.it`. Il token del tunnel e' salvato soltanto in `config/application-cloud.env`, escluso da Git. Durante l'asta non riavviare Docker Desktop e non sospendere il Mac. `PROD - ASTA` e `PROD - GAZZETTA` possono essere eseguiti anche per controllo: se tutti i servizi sono gia' operativi non li riavviano e non li ricreano. Conservare anche il link LAN mostrato in console come alternativa per i dispositivi collegati alla stessa rete.

Il tunnel forza HTTP/2 su TCP per evitare le disconnessioni QUIC/UDP osservate sulla rete locale. Per l'avvio manuale usare `./scripts/start-auction.sh`; per un deploy usare `./scripts/deploy-auction.sh --rebuild`; per il controllo usare `./scripts/status-auction.sh`.

Il volume `backend_pgdata` e' dichiarato esterno: Compose lo utilizza ma non ne gestisce il ciclo di vita. Il deploy applicativo non include mai PostgreSQL e ricrea soltanto backend, frontend e reverse proxy con `--no-deps`. Prima di procedere verifica il volume, controlla che il database non sia vuoto, blocca l'operazione con mercato o round attivo e crea un dump validato in `backups/`. Lo script confronta inoltre i conteggi di partecipanti, calciatori e righe rosa prima e dopo il deploy. Non eliminare manualmente `backend_pgdata` e non avviare un secondo PostgreSQL sullo stesso volume.

Il browser deve conoscere soltanto l'URL HTTPS pubblico. Il backend non pubblica porte nello stack completo; PostgreSQL di produzione pubblica soltanto `127.0.0.1:5433`, non raggiungibile dalla LAN o da Internet. In IntelliJ la connessione `PRODUZIONE - NON MODIFICARE` usa la porta `5433`, mentre `SVILUPPO - fantasta_dev` usa la porta `5432`. Le due istanze possono restare attive contemporaneamente perché usano container, database, porte e volumi distinti.

## Backup e ripristino

Ogni modifica futura allo schema deve avere una migrazione SQL idempotente in `database/migrations/`. Dopo la validazione in DEV, la stessa migrazione viene applicata al database PROD soltanto dopo controllo di round e mercato, backup validato e rilevazione dei conteggi principali prima e dopo. Le migrazioni di schema non copiano dati tra ambienti e non devono ricreare database, tabelle esistenti o volumi.

Creare un backup prima delle prove finali e prima dell'asta:

```bash
./scripts/backup-db.sh
```

I dump sono salvati in `backups/`, esclusa da Git. Copiare il dump pre-asta anche su un disco o cartella esterna al repository.

Ripristino distruttivo:

```bash
./scripts/restore-db.sh --confirm backups/fantasta-YYYYMMDD-HHMMSS.dump
```

## Mercato di riparazione

Il mercato di riparazione si prepara dalla pagina admin `Mercato` e segue un ordine obbligatorio:

1. selezionare il 1°, 2° o 3° mercato e salvare la configurazione;
2. caricare e confermare il file `Giocatori Partiti`, verificando gli abbinamenti con le rose;
3. caricare il listino aggiornato dei restanti calciatori e controllare l'anteprima;
4. correggere eventuali quotazioni mancanti e confermare l'aggiornamento;
5. effettuare gli svincoli manualmente dalla pagina Rose oppure importare le rose post-scambi;
6. aprire l'asta dei calciatori disponibili.

L'import dei partiti legge il foglio `Giocatori Partiti`: partecipante in B, ruolo in C, nome in D (rimuovendo il prefisso `ZZZ - Partito -`), squadra in E, quotazione in F e stato `Partito` in G. Ignora il riepilogo laterale. Nomi duplicati, dati invalidi o abbinamenti non riconosciuti impediscono la conferma senza modifiche parziali. La conferma è unica per sessione, mantiene le rose e usa la quotazione F per il successivo rimborso.

L'import di mercato dei calciatori non cancella mai le rose: aggiorna quotazioni, squadra e ruolo dei restanti e inserisce i nuovi arrivati. Non riattiva né cambia la quotazione dei partiti confermati nella sessione; i calciatori assenti dal listino mantengono stato e valore. Quotazioni e svincoli richiedono prima la conferma dei partiti; gli svincoli richiedono anche la conferma delle quotazioni. L'import rose di mercato accetta soltanto cessioni e scambi tra proprietari esistenti; aggiunte, duplicati, fogli mancanti e pacchetti portieri spezzati bloccano l'operazione.

Il rimborso di una cessione e' sempre la quotazione corrente. Gli scambi non modificano i crediti residui. Quando un giocatore viene svincolato, tutti i suoi proprietari registrati possono riacquistarlo soltanto dalla quotazione di svincolo piu' un credito. I giocatori usciti dalla lista non consumano il limite di svincoli. Nel 1° e 3° mercato il pacchetto portieri si cede interamente e il nuovo pacchetto parte dalla somma delle tre quotazioni piu' alte; nel 2° mercato la porta non puo' essere cambiata.

L'import `Importa quotazioni FantaMaster` fuori dalla pagina Mercato resta riservato al cambio stagione ed e' distruttivo: non utilizzarlo per un mercato di riparazione.

Il ripristino ferma il backend, ricrea il database, importa il dump e riavvia il backend.

## Cambio stagione e import FantaMaster

Prima di cambiare stagione creare sempre un backup. Dalla pagina admin `Importa quotazioni FantaMaster` selezionare il file `.xlsx` con le colonne `Nome`, `Squadra`, `Ruolo` e `Quotazione`.

`Importa rose FantaMaster` legge tutti i fogli del file `rose_lega_*.xlsx`. Il nome squadra nella prima riga di ogni foglio identifica il partecipante: se manca viene creato con i crediti iniziali configurati, anche quando il foglio non contiene calciatori. L'anteprima non modifica il database; la sostituzione avviene soltanto dopo conferma. Gli account vengono poi associati manualmente dalla gestione utenti, dove la password proposta `fanta2026` e' definitiva e non richiede il cambio al primo accesso.

La gestione utenti mostra username, squadra associata e stato dell'account, oltre alle squadre ancora prive di accesso. Le pagine delle rose riportano lo username sotto il nome della squadra per rendere immediata la verifica delle associazioni.

Il pulsante `Esporta rose FantaMaster` nella dashboard produce un file `.xlsx` basato sul template ufficiale scaricato dalla lega. L'export conserva senza rinominarli nomi e ordine dei fogli, intestazioni, celle unite, stili e contenuti preesistenti; compila esclusivamente dalla terza riga le colonne `Nome`, `Squadra`, `Ruolo`, `Costo`, senza aggiungere footer, timestamp o collegamenti. Se le squadre configurate non corrispondono al template, l'export viene bloccato per evitare un file parziale non importabile.

Il primo passaggio esegue soltanto l'anteprima: valida intestazioni, campi, ruoli, quotazioni e nomi duplicati senza modificare PostgreSQL. La successiva conferma sostituisce completamente il catalogo e azzera rose, storico rose, estrazioni, skip e stato dell'asta. Se la validazione fallisce non viene cancellato nulla.

Il riavvio ordinario del backend non importa né cancella calciatori. Hibernate resta configurato con strategia `update`, che aggiorna lo schema senza ricreare il database; il volume PostgreSQL conserva i dati.

Anche il round corrente e la sua scadenza sono persistiti: dopo un riavvio il backend riprogramma il tempo residuo oppure chiude il round se la scadenza è già trascorsa. Per gli account partecipante, l'identità dell'offerente viene sempre ricavata dalla sessione autenticata e non dai dati inviati dal browser.

## Partecipanti e primo accesso

L'admin usa `Gestione utenti` (`/admin/users`) per collegare un account a una squadra esistente, creare contestualmente un nuovo partecipante indicando nome squadra e crediti iniziali, oppure creare un osservatore senza squadra. L'osservatore può seguire l'asta corrente e consultare svincolati, rose e riepiloghi, ma non può offrire, ritirare offerte o eseguire operazioni legate a una squadra.

La password consegnata dall'admin è temporanea e deve avere almeno 4 caratteri. Al primo login l'account riceve una sessione limitata e deve scegliere una password diversa prima di poter accedere ad asta, rose e calciatori. I crediti iniziali sono configurabili esclusivamente dall'admin.

## Flusso asta iniziale

La dashboard guida l'admin nella sequenza operativa: configurazione partecipanti, scelta del ruolo, estrazione, avvio delle offerte e chiusura. Il round termina alla scadenza del timer oppure quando l'admin usa la chiusura manuale; in entrambi i casi vince l'offerta più alta. In caso di parità il round successivo è riservato ai soli partecipanti a pari merito e parte da un credito oltre l'offerta precedente. L'assegnazione manuale rimane sempre disponibile.

Partecipanti e osservatori raggiungono sempre il round attivo dalla voce `Asta corrente` del menu. La pagina recupera lo stato persistito anche dopo una navigazione o una riconnessione WebSocket, senza richiedere un aggiornamento manuale. Durante il countdown tutti vedono i nomi di chi ha puntato, deduplicati, ma mai gli importi. Alla chiusura, tutti vedono contemporaneamente la graduatoria completa, il vincitore e l'importo. Un partecipante può ritirare la propria offerta in qualsiasi momento prima della chiusura, anche quando è la più alta; il ritiro viene notificato in tempo reale. Le offerte a zero restano non valide. Durante l'asta la rosa è consultabile, ma lo svincolo dei calciatori è riservato all'admin.

Durante un nuovo round admin e partecipanti vedono anche l'ultima assegnazione conclusa, conservata nello stato persistito del round. La pagina `Storico puntate` registra esclusivamente le assegnazioni con almeno due offerenti e mostra le offerte finali, la quotazione, il vincitore e il prezzo; assegnazioni dirette, correzioni, round senza offerte e round con un solo offerente sono esclusi. L'admin consulta l'intero storico, mentre ogni partecipante vede soltanto le aste in cui la propria squadra ha presentato un'offerta; un osservatore senza squadra non vede risultati. La restrizione viene applicata dal backend usando l'identità autenticata. Parità e spareggi riconoscibili restano collegati. La registrazione avviene dopo assegnazione e notifica, in una transazione separata e silenziosa: un suo eventuale fallimento non modifica il round e non produce messaggi nell'interfaccia.

Il riepilogo mostra sempre i conteggi P/D/C/A, i crediti residui e il massimo spendibile per un singolo calciatore. Il massimo conserva obbligatoriamente almeno 1 credito per ogni altro posto ancora libero. La porta viene acquistata come pacchetto: quando una squadra possiede almeno un portiere, eventuali record mancanti nel pacchetto non riducono il massimo spendibile.

La dashboard admin mostra, per il ruolo selezionato, sia le chiamate ancora disponibili sia i posti rosa complessivamente vuoti su tutte le squadre. Per i portieri i posti sono conteggiati singolarmente, anche se una porta può riempirne più di uno con una sola asta. Il comando di assegnazione manuale apre una ricerca per nome del calciatore o squadra ed e' indipendente dal turno corrente: un giocatore libero può essere assegnato direttamente indicando partecipante e prezzo. La lista propone solo le squadre con posti sufficienti per il ruolo e per l'eventuale pacchetto portieri. Se il giocatore e' già assegnato, la stessa finestra mostra proprietario e costo correnti e consente di correggerli; il proprietario corrente resta selezionabile anche a quota piena per correggere il solo prezzo, mentre il vecchio proprietario viene rimborsato automaticamente in caso di trasferimento. Quote ruolo, crediti e massimo spendibile vengono nuovamente validati al salvataggio.

Nelle viste delle rose i reparti restano separati nell'ordine P/D/C/A e i calciatori sono ordinati alfabeticamente all'interno di ogni reparto, sia su desktop sia su mobile. Nome e squadra reale sono sempre mostrati in quest'ordine; i calciatori non più presenti nel listone ufficiale restano nella rosa e sono evidenziati con il badge `Partito` fino allo svincolo dell'admin.

La lista degli svincolati può essere filtrata contemporaneamente per ruolo e per nome del calciatore, senza distinzione tra maiuscole e minuscole.

La pagina admin `Movimenti rose` conserva e mostra svincoli, uscite di giocatori partiti e trasferimenti tra fantasquadre. Gli acquisti effettuati durante i round d'asta, gli aggiornamenti di quotazione e le correzioni del solo prezzo non fanno parte di questo registro. L'admin può annullare l'ultima operazione compatibile: il revert ripristina rosa, prezzo, crediti, conteggio svincoli e restrizioni e opera sull'intero gruppo dell'importazione o pacchetto portieri. I movimenti annullati restano conservati per audit, sono nascosti per impostazione predefinita e diventano consultabili attivando `Mostra annullati`. Il backend blocca il revert se esistono movimenti successivi o se il calciatore è stato nuovamente assegnato.

L'admin può correggere la quotazione corrente direttamente dalle liste degli svincolati e delle rose. La modifica non cambia il costo pagato, i crediti o il proprietario, ma aggiorna valore rosa e rimborso di un successivo svincolo.

Dalla gestione di una rosa l'admin può eseguire uno scambio diretto con un'altra fantasquadra. Lo scambio deve contenere lo stesso numero di calciatori per parte, conserva costo storico e crediti residui, valida contemporaneamente i limiti di ruolo ed e' registrato come un'unica operazione annullabile. I portieri possono essere scambiati soltanto come pacchetto completo contro un altro pacchetto completo; anche lo svincolo di un solo portiere dissocia automaticamente l'intero pacchetto.

Durante un round la pagina mobile riporta gli stessi due valori in forma compatta e non interattiva: icona Material `casino` per le chiamate disponibili e `group_add` per i posti rosa vuoti.

Il calciatore battuto è mostrato su una singola riga mobile con ruolo, nome, squadra e quotazione FantaMaster (`paid`), distinta dall'importo offerto.

Se alla chiusura esiste un solo offerente, la puntata resta visibile come valore dichiarato ma il costo effettivamente addebitato è 1 credito per un calciatore normale e 3 crediti per la porta. Con almeno due offerenti il vincitore paga la propria offerta completa; parità e spareggio restano invariati.

Admin e partecipanti ricevono inoltre un breve messaggio esplicativo quando il costo minimo viene applicato d'ufficio all'unico offerente.

Ogni riga del riepilogo squadre apre la rosa selezionata. Tutti gli utenti autenticati possono consultare le rose, dove costo d'acquisto e quotazione corrente sono mostrati in colonne separate e il valore complessivo della squadra e' la somma delle quotazioni dei suoi giocatori; un'eventuale quotazione assente vale zero. L'intestazione di ogni squadra riporta crediti residui e valore complessivo della rosa. Le operazioni di svincolo restano disponibili esclusivamente all'admin e secondo lo stato del mercato. L'apertura e la chiusura del mercato sono manuali tramite il relativo interruttore nella pagina admin; non è prevista una scadenza automatica.

Un giocatore senza offerte può essere saltato anche durante il round: lo skip annulla automaticamente il round e il relativo timer prima di passare al giocatore successivo. Non appena è presente almeno un'offerta, il comando viene disabilitato e il backend rifiuta comunque lo skip. Quando il ruolo non ha più chiamate disponibili, `Ricomincia giro` rende nuovamente estraibili tutti i giocatori saltati e ancora liberi.

Per i portieri si acquista la porta della squadra, non il singolo nome estratto. Il pacchetto contiene normalmente tre portieri, ha base minima 3 e addebita 1 credito a ciascuna riserva; il resto dell'offerta viene attribuito al titolare con quotazione più alta. Se la squadra ha quattro portieri vengono scelti i primi due per valore e uno casuale tra quelli con valore minimo. Se ne ha soltanto due, il sistema aggiunge quando disponibile un portiere eccedente a valore minimo proveniente da una squadra con quattro.

## Test e build

Backend:

```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
```

Frontend:

```bash
cd frontend
npm test
npm run build:prod
```

Build completa Docker:

```bash
docker compose --env-file config/application-prod.local.env -f docker-compose.prod.yml build
```

## Checklist pre-asta

- Docker Desktop configurato per non sospendere il Mac.
- Mac collegato all'alimentazione e rete stabile; preferire Ethernet.
- Java 21 e Node 20 verificati.
- test backend e frontend verdi.
- tutte le immagini Docker costruite prima del giorno dell'asta.
- volume PostgreSQL e spazio disco verificati.
- backup recente creato e copiato fuori dal repository.
- login admin e login di almeno un partecipante verificati.
- ruoli admin/user e associazioni alle squadre verificati.
- caricamento giocatori e rose provato con i file definitivi.
- puntata, chiusura turno e aggiornamento WebSocket provati da almeno due dispositivi.
- tunnel verificato dalla rete cellulare, non soltanto dal Wi-Fi locale.
- token Cloudflare e password non presenti nei file versionati.
- autoscaling disabilitato: lo stato live e le connessioni WebSocket appartengono a una singola JVM.

## Arresto

Senza tunnel:

```bash
docker compose --env-file config/application-prod.local.env -f docker-compose.prod.yml down
```

Con tunnel:

```bash
docker compose --env-file config/application-prod.local.env -f docker-compose.prod.yml -f docker-compose.cloud.yml down
```

Non aggiungere `-v` durante l'uso ordinario: cancellerebbe il database locale.

## Debito tecnico dopo l'asta

Il frontend usa ancora Angular 14. `npm audit --omit=dev` segnala vulnerabilita' corrette soltanto passando a una versione Angular moderna, con cambiamenti incompatibili. Per ridurre il rischio immediato, l'app non usa HTML/SVG dinamico o bypass del sanitizer e Nginx applica una Content Security Policy restrittiva. Dopo l'asta va pianificato l'upgrade completo di Angular, Material e toolchain, senza usare `npm audit fix --force` alla cieca.

Il database esistente usa ancora l'aggiornamento schema Hibernate. Dopo l'asta va creata una baseline Flyway verificata e il profilo di produzione deve passare dalla modifica automatica dello schema alla sola validazione.

La migrazione `database/migrations/20260930_market_departures.sql` aggiunge lo stato di import dei partiti alla configurazione e la sessione di partenza ai calciatori. Applicarla secondo la procedura DEV/PROD documentata prima del deploy. Le sessioni esistenti richiedono la conferma del nuovo passaggio dei partiti. L’endpoint admin `POST /api/admin/players/market-departures` accetta multipart `file` e `confirm` (false per anteprima).

L'abbinamento dei proprietari accetta il nome completo del partecipante o il nome della squadra tra parentesi. Riconosce automaticamente le equivalenze confermate `Em Fallét` / `Em Fallet`, `Johnson Oil` / `johnsons oil` e `3/4 e 1 Gazzosa` / `34 e 1 Gazzosa`. Altre differenze restano segnalate per evitare abbinamenti a proprietari diversi.
