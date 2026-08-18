# FantAsta

Gestore dell'asta Fantacalcio con backend Quarkus, frontend Angular, PostgreSQL e aggiornamenti live WebSocket.

L'autenticazione e' interna all'applicazione: utenti, ruoli e hash password sono salvati in PostgreSQL; il backend emette un JWT in cookie `HttpOnly`. Keycloak non e' piu' necessario.

## Architettura operativa

Lo scenario previsto per l'asta usa un solo Mac:

```text
partecipanti -> Cloudflare Tunnel -> Nginx -> frontend
                                         -> backend -> PostgreSQL locale
```

Cloudflare Tunnel usa una connessione in uscita e non richiede IP pubblico, port forwarding o database remoto. Neon non fa parte del percorso operativo.
La configurazione di produzione autorizza gli origin generati da `trycloudflare.com`, oltre a localhost e agli indirizzi LAN privati sulla porta 8088.

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
cp config/application-dev.env.example backend/.env
```

Compilare i secret e le credenziali. Il catalogo calciatori non viene più sincronizzato automaticamente all'avvio: l'import stagionale è un'operazione esplicita dell'admin.

2. Avviare PostgreSQL:

```bash
docker compose -f backend/docker-compose.yml up -d postgres
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

### Avvio asta da IntelliJ

Nel selettore delle configurazioni Run sono disponibili:

- `ASTA - AVVIA`: ferma PostgreSQL di sviluppo, avvia rapidamente le immagini già verificate sul volume persistente `backend_pgdata`, crea un nuovo Quick Tunnel, ne verifica realmente l'HTTPS e stampa il link da condividere;
- `ASTA - STATO`: ristampa link, container e controlli di raggiungibilità;
- `ASTA - FERMA`: arresta lo stack senza `-v` e riavvia il solo PostgreSQL di sviluppo.

Il link `trycloudflare.com` rimane invariato finché il container `cloudflared` resta attivo. Ogni nuova esecuzione di `ASTA - AVVIA` ricrea deliberatamente il tunnel per non riutilizzare hostname scaduti; se Cloudflare restituisce un hostname non raggiungibile, lo script prova automaticamente fino a tre volte. Durante l'asta non riavviare Docker Desktop, non sospendere il Mac e non eseguire nuovamente `ASTA - AVVIA`. Conservare anche il link LAN mostrato in console come alternativa per i dispositivi collegati alla stessa rete.

Il flusso non usa `CLOUDFLARE_TUNNEL_TOKEN`: si tratta deliberatamente di un Quick Tunnel temporaneo per la singola sessione d'asta. Il tunnel forza HTTP/2 su TCP per evitare le disconnessioni QUIC/UDP osservate sulla rete locale. Per l'avvio manuale usare `./scripts/start-auction.sh`; usare `./scripts/start-auction.sh --rebuild` soltanto dopo modifiche al codice; per il controllo usare `./scripts/status-auction.sh`.

Il browser deve conoscere soltanto l'URL HTTPS pubblico. Il backend non pubblica porte nello stack completo; PostgreSQL pubblica soltanto `127.0.0.1:5433`, non raggiungibile dalla LAN o da Internet. La configurazione IntelliJ `fantasta@localhost` usa `jdbc:postgresql://localhost:5433/fantasta` e continua a funzionare dopo la ricreazione del container perché non dipende dal suo IP interno. Utente e password provengono da `backend/.env`; salvare la password nello storage sicuro di IntelliJ.

## Backup e ripristino

Creare un backup prima delle prove finali e prima dell'asta:

```bash
./scripts/backup-db.sh
```

I dump sono salvati in `backups/`, esclusa da Git. Copiare il dump pre-asta anche su un disco o cartella esterna al repository.

Ripristino distruttivo:

```bash
./scripts/restore-db.sh --confirm backups/fantasta-YYYYMMDD-HHMMSS.dump
```

Il ripristino ferma il backend, ricrea il database, importa il dump e riavvia il backend.

## Cambio stagione e import FantaMaster

Prima di cambiare stagione creare sempre un backup. Dalla pagina admin `Importa quotazioni FantaMaster` selezionare il file `.xlsx` con le colonne `Nome`, `Squadra`, `Ruolo` e `Quotazione`.

`Importa rose FantaMaster` legge tutti i fogli del file `rose_lega_*.xlsx`. Il nome squadra nella prima riga di ogni foglio identifica il partecipante: se manca viene creato con i crediti iniziali configurati, anche quando il foglio non contiene calciatori. L'anteprima non modifica il database; la sostituzione avviene soltanto dopo conferma. Gli account vengono poi associati manualmente dalla gestione utenti, dove la password proposta `fanta2026` e' definitiva e non richiede il cambio al primo accesso.

La gestione utenti mostra username, squadra associata e stato dell'account, oltre alle squadre ancora prive di accesso. Le pagine delle rose riportano lo username sotto il nome della squadra per rendere immediata la verifica delle associazioni.

Il pulsante `Esporta rose FantaMaster` nella dashboard produce un file `.xlsx` basato sul template ufficiale della lega 1590336. L'export conserva nomi e identificativi dei fogli, celle unite, stili, footer e collegamento FantaMaster, popolando le colonne `Nome`, `Squadra`, `Ruolo`, `Costo`. Se le squadre configurate non corrispondono al template, l'export viene bloccato per evitare un file parziale non importabile.

Il primo passaggio esegue soltanto l'anteprima: valida intestazioni, campi, ruoli, quotazioni e nomi duplicati senza modificare PostgreSQL. La successiva conferma sostituisce completamente il catalogo e azzera rose, storico rose, estrazioni, skip e stato dell'asta. Se la validazione fallisce non viene cancellato nulla.

Il riavvio ordinario del backend non importa né cancella calciatori. Hibernate resta configurato con strategia `update`, che aggiorna lo schema senza ricreare il database; il volume PostgreSQL conserva i dati.

Anche il round corrente e la sua scadenza sono persistiti: dopo un riavvio il backend riprogramma il tempo residuo oppure chiude il round se la scadenza è già trascorsa. Per gli account partecipante, l'identità dell'offerente viene sempre ricavata dalla sessione autenticata e non dai dati inviati dal browser.

## Partecipanti e primo accesso

L'admin usa `Gestione utenti` (`/admin/users`) per collegare un account a una squadra esistente, creare contestualmente un nuovo partecipante indicando nome squadra e crediti iniziali, oppure creare un osservatore senza squadra. L'osservatore può seguire l'asta corrente e consultare svincolati, rose e riepiloghi, ma non può offrire, ritirare offerte o eseguire operazioni legate a una squadra.

La password consegnata dall'admin è temporanea e deve avere almeno 4 caratteri. Al primo login l'account riceve una sessione limitata e deve scegliere una password diversa prima di poter accedere ad asta, rose e calciatori. I crediti iniziali sono configurabili esclusivamente dall'admin.

## Flusso asta iniziale

La dashboard guida l'admin nella sequenza operativa: configurazione partecipanti, scelta del ruolo, estrazione, avvio delle offerte e chiusura. Il round termina alla scadenza del timer oppure quando l'admin usa la chiusura manuale; in entrambi i casi vince l'offerta più alta. In caso di parità il round successivo è riservato ai soli partecipanti a pari merito e parte da un credito oltre l'offerta precedente. L'assegnazione manuale rimane sempre disponibile.

Partecipanti e osservatori raggiungono sempre il round attivo dalla voce `Asta corrente` del menu. La pagina recupera lo stato persistito anche dopo una navigazione o una riconnessione WebSocket, senza richiedere un aggiornamento manuale. Durante il countdown tutti vedono i nomi di chi ha puntato, deduplicati, ma mai gli importi. Alla chiusura, tutti vedono contemporaneamente la graduatoria completa, il vincitore e l'importo. Un partecipante può ritirare la propria offerta in qualsiasi momento prima della chiusura, anche quando è la più alta; il ritiro viene notificato in tempo reale. Le offerte a zero restano non valide. Durante l'asta la rosa è consultabile, ma lo svincolo dei calciatori è riservato all'admin.

Il riepilogo mostra sempre i conteggi P/D/C/A, i crediti residui e il massimo spendibile per un singolo calciatore. Il massimo conserva obbligatoriamente almeno 1 credito per ogni altro posto ancora libero. La porta viene acquistata come pacchetto: quando una squadra possiede almeno un portiere, eventuali record mancanti nel pacchetto non riducono il massimo spendibile.

La dashboard admin mostra, per il ruolo selezionato, sia le chiamate ancora disponibili sia i posti rosa complessivamente vuoti su tutte le squadre. Per i portieri i posti sono conteggiati singolarmente, anche se una porta può riempirne più di uno con una sola asta. Il comando di assegnazione manuale apre una ricerca per nome del calciatore o squadra ed e' indipendente dal turno corrente: un giocatore libero può essere assegnato direttamente indicando partecipante e prezzo. Se il giocatore e' già assegnato, la stessa finestra mostra proprietario e costo correnti e consente di correggerli; il vecchio proprietario viene rimborsato automaticamente e quote ruolo, crediti e massimo spendibile vengono nuovamente validati.

Nelle viste delle rose i reparti restano separati nell'ordine P/D/C/A e i calciatori sono ordinati alfabeticamente all'interno di ogni reparto, sia su desktop sia su mobile.

Durante un round la pagina mobile riporta gli stessi due valori in forma compatta e non interattiva: icona Material `casino` per le chiamate disponibili e `group_add` per i posti rosa vuoti.

Il calciatore battuto è mostrato su una singola riga mobile con ruolo, nome, squadra e quotazione FantaMaster (`paid`), distinta dall'importo offerto.

Se alla chiusura esiste un solo offerente, la puntata resta visibile come valore dichiarato ma il costo effettivamente addebitato è 1 credito per un calciatore normale e 3 crediti per la porta. Con almeno due offerenti il vincitore paga la propria offerta completa; parità e spareggio restano invariati.

Admin e partecipanti ricevono inoltre un breve messaggio esplicativo quando il costo minimo viene applicato d'ufficio all'unico offerente.

Ogni riga del riepilogo squadre apre la rosa selezionata. Tutti gli utenti autenticati possono consultare le rose; le operazioni di svincolo restano disponibili esclusivamente all'admin e secondo lo stato del mercato.

Un giocatore senza offerte può essere saltato. Quando il ruolo non ha più chiamate disponibili, `Ricomincia giro` rende nuovamente estraibili tutti i giocatori saltati e ancora liberi.

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
