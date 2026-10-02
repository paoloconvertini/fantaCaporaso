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

La pagina istituzionale statica si trova in `landing-page/` ed e' destinata a Cloudflare Pages sul dominio principale `fantacaporaso.it`. Non dipende dai container locali. Lo stato e il pulsante di accesso sono statici: vanno aggiornati e pubblicati quando si apre o si chiude il mercato; non seguono automaticamente la configurazione del database. Il sottodominio `asta.fantacaporaso.it` resta riservato all'applicazione d'asta pubblicata tramite Cloudflare Tunnel.

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

L'import di mercato dei calciatori non cancella mai le rose: aggiorna quotazioni, squadra e ruolo dei restanti e inserisce i nuovi arrivati. Non riattiva né cambia la quotazione dei partiti confermati nella sessione; i calciatori assenti dal listino mantengono stato e valore. Quotazioni e svincoli richiedono prima la conferma dei partiti; gli svincoli richiedono anche la conferma delle quotazioni. L'import rose di mercato accetta cessioni e scambi tra proprietari esistenti e le correzioni delle riserve portieri descritte sotto; altre aggiunte, duplicati, fogli mancanti e pacchetti portieri spezzati bloccano l'operazione.

L'anteprima rose distingue le correzioni dei pacchetti portieri dalle cessioni. Il controllo riguarda il club della porta esistente, senza distinguere titolare e riserve e senza dedurli dal costo storico. Il file può sostituire anche il portiere più costoso e correggere pacchetti con costi tutti uguali, purché i nuovi portieri appartengano al club della porta e il pacchetto finale contenga tre portieri. Il costo complessivo della porta, i crediti e il contatore del cambio porta restano invariati. Le righe mancanti di un pacchetto già pagato vengono completate con costo aggiuntivo zero. Un portiere erroneamente assegnato a un'altra rosa può essere riallineato se anche il pacchetto di origine viene corretto nello stesso import. Altrimenti l'operazione viene bloccata. La riserva uscente può già risultare inattiva o trasferita nel listino aggiornato.

Questo import riconcilia le rose già esistenti: una riga mancante nel database per un giocatore di movimento viene ripristinata con il costo storico in colonna D, se il calciatore è attivo, riconosciuto nel catalogo e la rosa finale rispetta il limite del ruolo. Il ripristino mantiene i crediti residui, adeguando il totale crediti alla spesa storica recuperata; non è un nuovo acquisto né uno svincolo. Le correzioni sono indicate nell'anteprima, aggiornano lo storico dei proprietari e sono registrate nei log backend. La rilettura dello stesso file non le applica nuovamente. Le cessioni effettive mantengono invece i rimborsi e i limiti previsti dalle regole del mercato.

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

## Osservatori e obiettivi personali

La configurazione CORS ammette anche `DELETE`, necessario per rimuovere un obiettivo dal browser; origini consentite e autorizzazioni degli endpoint restano quelle previste per ciascun ambiente.

Il ruolo `observer` può essere associato facoltativamente a una squadra già dotata del proprio account `user`. Dalla gestione utenti si crea l'osservatore scegliendo una squadra esistente oppure lasciandola vuota; la squadra di un osservatore esistente si modifica dalla lista account. Più osservatori possono seguire la stessa squadra, mantenendo liste private indipendenti. L'osservatore non può inviare/ritirare offerte né svincolare giocatori. I permessi e l'associazione sono riletti dal database a ogni richiesta autenticata, anche con cookie già emessi; l'interfaccia aggiorna l'identità al successivo accesso o ricaricamento.

Applicare `database/migrations/20261001_observer_targets.sql` prima del deploy secondo la procedura con backup DEV/PROD. La migrazione idempotente converte soltanto gli account `user` senza squadra in `observer` e aggiunge `player_target`; gli account partecipanti associati rimangono invariati. La cancellazione di un account o giocatore elimina i suoi tag tramite chiavi esterne con cascata.

Gli osservatori associati trovano i pulsanti “Aggiungi agli obiettivi” e “Rimuovi dagli obiettivi” negli svincolati desktop e mobile e la pagina `I miei obiettivi` (`/targets`), dove il pulsante “Rimuovi” elimina il tag dalla lista personale. Le API `/api/targets` (GET), `/api/targets/{playerId}` (PUT/DELETE) e `/api/targets/analysis` (GET) richiedono `observer` e una squadra associata, e usano sempre l'account autenticato per isolare i tag. I portieri sono raggruppati in un unico obiettivo per club, con costo/minimo riferito al pacchetto di tre portieri. I tag di giocatori acquistati o inattivi restano rimovibili e sono indicati come non disponibili.

L'analisi è distinta per ruolo e ordinata per quotazione decrescente (somma del pacchetto per i portieri). La massima offerta personale viene dal servizio dell'asta, riservando un credito per ogni posto da finanziare oltre l'acquisto corrente. Per ciascun avversario si calcola il massimo numero `k` di acquisti nel medesimo ruolo con costo unitario `massimaPersonale + 1`, entro i suoi posti del ruolo e conservando `max(0, postiTotali - k * dimensioneAcquisto)` crediti. Il primo obiettivo dello scenario è alla posizione `somma(k) + 1`, se presente e acquistabile con il minimo personale. I vincoli di riacquisto personali sono inclusi; quelli avversari sono ignorati per mantenere la stima pessimistica. Il risultato è una simulazione, non una garanzia su prezzi, parità o ordine delle chiamate. L'analisi si aggiorna con gli eventi dell'asta e ogni 15 secondi; non scrive movimenti, offerte o crediti.

## Checklist pre-asta

L'app mostra un avviso globale per l'asta del 1 ottobre 2026: il 30 settembre indica “domani”, il 1 ottobre “oggi” e dal 2 ottobre scompare. La data segue il fuso Europe/Rome e viene ricontrollata ogni minuto, anche nelle pagine già aperte. Il testo è definito in `frontend/src/app/app.component.ts`.

L’app d’asta non mostra più il banner temporaneo del 1 ottobre, per lasciare spazio alla schermata anche su mobile.

La pagina pubblica `landing-page/index.html` indica separatamente la prossima asta del 1 ottobre 2026. Questa data statica va aggiornata per le sessioni successive; pubblicare su Cloudflare Pages mantenendo lo storico pubblico già presente.

Il riepilogo pubblico pre-asta è in `landing-page/riepilogo/index.html`, raggiungibile dalla home a `/riepilogo/`. Contiene soltanto nomi delle squadre, crediti residui e posti liberi per ruolo, estratti da PROD mediante l'API autenticata `/api/participant/summary`. I posti sono calcolati sottraendo i conteggi in rosa dai massimali PROD (3/8/8/6). È una fotografia statica datata, senza credenziali o accesso pubblico alle API e senza aggiornamenti durante l'asta; per aggiornarla occorre rileggere PROD, rigenerare la pagina e pubblicare insieme agli asset e allo storico esistenti.

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

## Controllo settimanale dell’archivio Drive

`config/archive-job.json` identifica esclusivamente Classifiche, Coppa Italia e Statistiche dell’archivio FantaCaporaso 2026–2027. Mercato Ottobre è escluso. Il job usa Codex CLI autenticato con ChatGPT e il plugin Google Drive collegato: non richiede l’avvio di Docker o PROD. Richiede macOS, l’utente collegato, il Mac acceso e la connessione disponibile. Per le esecuzioni locali non serve tenere aperta questa chat; una connessione scaduta o revocata richiede un nuovo accesso.

Installare il LaunchAgent con `./scripts/install-archive-job.sh`. Il programma valida il fuso del Mac (`Europe/Rome`), installa `~/Library/LaunchAgents/it.fantacaporaso.archive-preview.plist` e pianifica ogni martedì alle 09:00. Non esegue il job all’installazione. macOS può recuperare una scadenza persa durante il sonno al risveglio; il Mac deve restare sveglio per garantire l’orario esatto. Le esecuzioni consumano l’utilizzo Codex dell’account autenticato.

Comandi operativi:

```bash
./scripts/run-archive-job.sh --check-only  # Accesso, inventario e differenze; niente contenuti o nuove pagine
./scripts/run-archive-job.sh               # Prepara l’anteprima, senza pubblicazione
./scripts/preview-archive-job.sh           # Ultima anteprima completa, http://127.0.0.1:8787
./scripts/preview-archive-job.sh --review  # Candidato con anomalie da controllare; Ctrl-C per fermare il server
launchctl print gui/$(id -u)/it.fantacaporaso.archive-preview
```

Il runner crea uno spazio di lavoro temporaneo isolato, copiando il sito e l’ultima anteprima completata. Il prompt già approvato è in `scripts/archive-job/prompt.md`: consente soltanto aggiornamenti dell’anteprima delle pagine `/classifiche/`, `/risultati/`, `/coppa-italia/`, `/statistiche/` e relativi dati/asset. Il ramo principale, le credenziali, i database, l’asta e il sito pubblicato non vengono modificati. La prima preparazione acquisisce l’intero inventario; le successive confrontano ID, nome, MIME e data di modifica, riutilizzando sorgenti invariati. Le immagini statistiche possono essere mostrate originali e ingrandibili, senza estrarre valori non verificabili. Nella Coppa non ancora iniziata gli 0–0 sono segnaposto “Da giocare”. Le tabelle finanziarie dei workbook non vengono pubblicate.

Stato, sorgenti, log e rapporti sono privati e ignorati da Git, in `.local/archive-job/`. `last-run.json` indica l’ultimo esito; `runs/<id>/workspace/report.md` contiene il rapporto; `latest-preview` punta all’ultima anteprima completa. Un controllo `--check-only` non aggiorna l’inventario di riferimento. Un errore, un inventario incompleto o un file rimosso non sostituisce l’anteprima valida né avanza la baseline: viene segnalato `needs_attention`. Una preparazione con anomalie può conservare un candidato consultabile con `--review`; `review-preview` resta separato dall’ultima anteprima completa e dalla baseline. Il blocco `flock` impedisce esecuzioni sovrapposte. Il tempo massimo è configurato nel JSON (predefinito 30 minuti). Se un riferimento di download temporaneo non è materializzabile ma Drive resta leggibile, il runner supporta una compatibilità inline esplicitamente limitata a 1 MiB per file: la risposta viene materializzata automaticamente tra i sorgenti privati; mai nelle pagine pubbliche. Il limite è `legacyInlineMaxBytes` nel JSON. Non sono previste notifiche email o pubblicazione automatica: il gestore consulta il rapporto e approva separatamente il rilascio.

Per sospendere il job: `launchctl bootout gui/$(id -u)/it.fantacaporaso.archive-preview`; per riattivarlo eseguire l’installer. Il programma di test `python3 scripts/archive-job/test_run.py` verifica differenze, isolamento degli output, mantenimento della baseline in caso di errore e blocco delle esecuzioni sovrapposte, senza chiamare Drive o Codex.

### Recupero storico pubblico del mercato del 1 ottobre 2026

I reset tra chiamate frammentavano `auctionSessionCode`. Le nuove chiamate e le assegnazioni manuali usano ora il codice del mercato attivo, che resta invariato attraverso reset e skip. Nessuna modifica ai contratti API o allo schema DB.

`scripts/history/prepare-public-history.py <cartella-output>` prepara una fotografia pubblica delle 63 aste competitive verificate del 1 ottobre 2026, interrogando PROD in sola lettura. La cartella deve già contenere una copia completa del sito pubblico (home, riepilogo, storico e asset). Il comando conserva le altre sessioni pubblicate e indica “Aggiornato il”, senza dichiarare concluso il mercato. Blocca il recupero se il numero di aste cambia: il filtro di data e il codice mercato sono specifici di questo recupero, non un raggruppamento automatico dei mercati futuri.

Pubblicare separatamente questa cartella sul progetto Cloudflare Pages `fantacaporaso`, ramo `main`. La pubblicazione statica non richiede il riavvio dell’app d’asta. Il normale archivio delle sessioni concluse resta gestito da “Concludi l’intera sessione”; la correzione backend richiede il consueto deploy con mercato e round inattivi.

### Correzione assegnazione manuale e svincolati — 2 ottobre 2026

`PUT /api/admin/assignments/{playerId}` è ora dichiarato in `AdminResource`, sotto `/api/admin`, evitando il 404 causato dalla selezione della risorsa REST più specifica. Mantiene payload, ruolo admin e verifiche di capienza/crediti; assegna o corregge il costo e salva lo storico competitivo quando risolve il round corrente.

L’import di mercato disattiva i vecchi svincolati assenti dal file confermato, lasciando intatte le rose. Conserva gli assenti con una cessione `RELEASE` valida della stessa sessione: il primo listone Drive precede le cessioni. I giocatori esplicitamente marcati partiti della sessione non vengono riattivati da successivi import. L’anteprima elenca i liberi da escludere prima della conferma.

Per il mercato di ottobre il riferimento definitivo è il PDF “03 Mercato di Ottobre (Listone Liberi)”, con 268 giocatori, non il primo Excel dei 200 liberi. Patric e Milik sono esclusi come partiti su indicazione del gestore. Il confronto completo è in `docs/reviews/2026-10-02-svincolati.md`; la migrazione puntuale `database/migrations/20261002_reconcile_market_free_players.sql` richiede backup e verifica della sessione prima dell’applicazione. Riconciliazione applicata anche a PROD il 2 ottobre 2026 dopo backup validato: 62 svincolati disattivati, Patric e Milik marcati partiti; rose, partecipanti e account verificati identici prima e dopo.

Lo stato round persistito in DEV può contenere quattro campi residui della prenotazione sospesa (`reservationRequired`, `phase`, `reservedUsers`, `biddingDurationSeconds`). Il caricamento scarta solo questi campi noti, mantenendo gli altri dati del round e la validazione degli ulteriori campi sconosciuti. Non abilita la prenotazione né cambia i payload HTTP. Il test HTTP dell’assegnazione manuale usa anche questo stato legacy, per verificare il caso reale presente in DEV.

Dopo un’assegnazione manuale riuscita del giocatore mostrato, la schermata admin estrae automaticamente il successivo anche se non era stato avviato un round. La risposta HTTP e la notifica WebSocket condividono il controllo del giocatore corrente e il blocco delle estrazioni concorrenti: non producono una doppia estrazione. Le correzioni fuori turno e gli errori di assegnazione mantengono la chiamata corrente.

Le assegnazioni manuali mostrano una conferma con giocatore, partecipante e costo. Il campo opzionale `lastAssignment` nello stato round e nel DTO conserva l’ultimo esito, sia manuale sia d’asta; il riepilogo admin resta visibile anche tra le chiamate e dopo il ricaricamento. `previousAssignment` resta disponibile per compatibilità. Un’assegnazione fuori turno aggiorna il riepilogo senza chiudere né cambiare le offerte dell’altro round attivo. Nessuna migrazione di schema necessaria: lo stato è JSON persistito.

### Ricerca della chiamata in asta

In amministrazione, **Cerca calciatore** cerca per nome e ruolo tra gli svincolati attivi chiamabili, inclusi quelli già saltati nel giro. **Chiama** registra la scelta nella cronologia del giro e aggiorna il ruolo; le offerte partono solo con **Avvia offerte**. La selezione è riservata agli amministratori e viene rifiutata durante un round aperto. I portieri seguono le verifiche dei pacchetti da tre e delle porte già assegnate. Non modifica rose, crediti o gli altri skip.

API amministrative: `GET /api/random/search?q=...&role=...` (massimo 50 risultati, almeno due caratteri) e `POST /api/random/select` con `{ "playerId": 123 }`. La selezione restituisce 400 per un calciatore non disponibile e 409 durante un round aperto.
