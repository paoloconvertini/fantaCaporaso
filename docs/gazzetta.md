# Gazzetta di FantaCaporaso

La prima fase introduce un laboratorio admin in sola anteprima per gli export delle giornate FantaMaster.

## Confini operativi

- L’upload `POST /api/admin/newspaper/preview` richiede il ruolo `admin`.
- L’import legge risultati, classifica e blocchi `Formazione:` dell’export FantaMaster. Nei blocchi formazione
  i primi undici calciatori sono considerati titolari; i successivi sono la panchina e la colonna `Calcolato`
  indica se il loro contributo è entrato nel conteggio.
- La sezione **FantaSfiga** confronta i punti realmente ottenuti con la media dei punti che la squadra avrebbe
  raccolto affrontando tutte le altre nella stessa giornata. Usa le stesse soglie gol applicate nell’anteprima.
- Minuti dei gol e cronaca reale della Serie A sono arricchimenti editoriali verificati e modificabili: il backend
  non effettua scraping né dipende da servizi esterni durante importazione o pubblicazione.
- Il file viene analizzato in memoria e non modifica il database.
- Una giornata con risultati mancanti (`-`) viene riconosciuta come non calcolata.
- I valori importati non vengono convertiti o reinterpretati: il risultato ufficiale resta quello esportato da FantaMaster.
- La demo del frontend contiene dati inventati ed è marcata come non pubblicabile.
- `gazzetta-page/` è la radice iniziale del progetto Cloudflare Pages separato `fantacaporaso-gazzetta`.
  Non deve essere inserita nel deploy di `landing-page/` né nel progetto usato dallo storico puntate.
- La landing page principale collega l’archivio pubblico su `https://gazzetta.fantacaporaso.it/`.

## Anteprima e pubblicazione

- L’anteprima viene generata e modificata interamente nella pagina admin.
- Immagini ammesse: JPG, PNG o WebP, massimo 5 MB.
- `POST /api/admin/newspaper/publish` è sempre disabilitato fuori da PROD.
- Anche in PROD richiede `NEWSPAPER_PUBLISH_ENABLED=true` e `CLOUDFLARE_GAZZETTA_PROJECT`.
- Il servizio rifiuta esplicitamente il progetto `fantacaporaso`, riservato al sito principale.
- La pubblicazione crea una cartella temporanea e non legge o modifica dati di asta, mercato o rose.
- Non esistono scheduler o pubblicazioni automatiche: durante aste e mercati il flusso Gazzetta resta inattivo.

Il comando `scripts/configure-gazzetta-pages.sh` abilita il progetto dedicato
`fantacaporaso-gazzetta` nel file PROD locale già protetto da Git. Lo script non modifica
le credenziali e si interrompe se quelle Pages esistenti non sono presenti.

## Sviluppi sospesi

Persistenza delle edizioni, generazione dei trafiletti dai dati reali e pubblicazione saranno definiti dopo aver verificato un export FantaMaster di una giornata calcolata.
