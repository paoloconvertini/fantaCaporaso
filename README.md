
# FantAsta – v10 (Random + Team da Excel B/C/D)

- Excel unico: **B=Ruolo**, **C=Nome**, **D=Squadra** (prima riga intestazione).
- Admin mostra **giocatore, squadra, ruolo**; random per **TUTTI** o **RUOLO**, **skip** e **reset giro**.
- RoundState espone playerTeam/playerRole per display.

## Avvio
1) Copia `players.xlsx` in `backend/src/main/resources/` (o usa `-Dplayers.file`).
2) Compila `config/application-dev.properties` con i valori locali.
3) Avvia Postgres e Keycloak:
```bash
cd backend
docker compose up -d
```
4) Avvia il backend:
```bash
cd backend
mvn quarkus:dev
```
5) Avvia il frontend:
```bash
cd frontend
npm run start
```

Frontend locale → `http://localhost:4200`
Frontend LAN → `http://<IP-LAN-DELLA-MACCHINA>:4200`

Il frontend usa lo stesso hostname del browser per raggiungere Keycloak sulla porta `8081`.

## Avvio prod-like locale

Questo avvio serve per provare il comportamento di produzione in locale, dietro reverse proxy, senza usare Angular dev server e senza esporre direttamente backend o Keycloak al browser.

Comando:
```bash
docker compose --env-file config/application-prod.local.env.example -f docker-compose.prod.yml up -d --build
```

URL dal Mac:
```bash
http://localhost:8088
```

URL da telefono sulla stessa rete:
```bash
http://<IP-LAN-DEL-MAC>:8088
```

In questa configurazione il browser conosce solo il reverse proxy sulla porta `8088`:
- frontend: `/`
- backend API: `/api/*`
- WebSocket: `/ws/*`
- Keycloak: `/auth/*`

Database usati in prod-like locale:
- database applicativo: container Docker `postgres`
- database Keycloak: container Docker `keycloak-db`
- volumi persistenti: `prod_pgdata` e `prod_kcdata`

Questa configurazione **non usa Neon**. Neon sara' usato solo se una configurazione di produzione reale imposta esplicitamente il database esterno nelle variabili d'ambiente.

Per fermare lo stack:
```bash
docker compose --env-file config/application-prod.local.env.example -f docker-compose.prod.yml down
```

Per cancellare anche i dati locali Docker, incluse aste, utenti importati e database Keycloak:
```bash
docker compose --env-file config/application-prod.local.env.example -f docker-compose.prod.yml down -v
```

## Avvio produzione reale cloud

La produzione reale usa uno stack separato, per non rompere sviluppo e prod-like locale:

- sviluppo: `backend/docker-compose.yml` + `mvn quarkus:dev` + `npm run start`
- prod-like locale: `docker-compose.prod.yml` + `config/application-prod.local.env.example`
- cloud reale: `docker-compose.cloud.yml` + un file env reale non committato

Il profilo cloud e' pensato per:
- frontend Angular servito da Nginx
- backend Quarkus in container
- Keycloak in container
- database Keycloak locale persistente su volume Docker
- database applicativo esterno Neon/Postgres
- HTTPS pubblico tramite Cloudflare Tunnel

Preparazione:
```bash
cp config/application-cloud.env.example config/application-cloud.env
```

Compilare `config/application-cloud.env` con valori reali:
- `PUBLIC_BASE_URL=https://...`
- `CLOUDFLARE_TUNNEL_TOKEN=...`
- credenziali Neon in `POSTGRES_*`
- `POSTGRES_JDBC_PARAMS=?sslmode=require`
- password Keycloak forti
- `KEYCLOAK_BACKEND_SECRET` uguale al secret del client `fantasta-backend`

Avvio cloud:
```bash
docker compose --env-file config/application-cloud.env -f docker-compose.cloud.yml up -d --build
```

Log:
```bash
docker compose --env-file config/application-cloud.env -f docker-compose.cloud.yml logs -f
```

Stop:
```bash
docker compose --env-file config/application-cloud.env -f docker-compose.cloud.yml down
```

In cloud il browser deve conoscere solo `PUBLIC_BASE_URL`. Non deve usare porte backend, porte Keycloak, IP pubblici o URL Neon.

Prima di aprire la produzione agli utenti, verificare in Keycloak il client frontend:
- Valid redirect URIs: `https://dominio/*`
- Web origins: `https://dominio`
- Post logout redirect URIs: `https://dominio/*`

Durante i test iniziali si puo' usare una configurazione piu' permissiva, ma va ristretta prima dell'asta reale.

### Cloudflare Tunnel

Prerequisiti:
- dominio `fantacaporaso.it` gestito su Cloudflare
- sottodominio desiderato: `asta.fantacaporaso.it`
- server/VPS con Docker e Docker Compose

Creazione tunnel:
1. Accedere a Cloudflare.
2. Aprire **Zero Trust**.
3. Andare in **Networks** → **Tunnels**.
4. Creare un nuovo tunnel.
5. Scegliere tipo **Cloudflared**.
6. Assegnare un nome, ad esempio `fantasta-prod`.
7. Copiare il token del tunnel.
8. Inserire il token in `config/application-cloud.env`:

```env
CLOUDFLARE_TUNNEL_TOKEN=...
```

Configurazione Public Hostname:
- Subdomain: `asta`
- Domain: `fantacaporaso.it`
- Type: `HTTP`
- URL: `http://reverse-proxy:80`

Avvio:
```bash
docker compose --env-file config/application-cloud.env -f docker-compose.cloud.yml up -d --build
```

Verifica:
```bash
docker compose --env-file config/application-cloud.env -f docker-compose.cloud.yml logs -f cloudflared
```

Poi aprire:
```text
https://asta.fantacaporaso.it
```

## Deploy cloud iniziale
- Eseguire una sola istanza backend: lo stato live dell'asta e le connessioni WebSocket sono locali alla JVM.
- Il reverse proxy deve supportare WebSocket su `/ws/*` con `Upgrade`/`Connection` header e timeout lunghi.
- Evitare autoscaling orizzontale finche' lo stato round e il broadcast WebSocket non saranno spostati su storage/pub-sub condiviso.

## Login
- Gli utenti con ruolo Keycloak `admin` entrano nella gestione asta.
- Gli altri utenti entrano nella pagina mobile per puntare.
- La pagina mobile risolve il partecipante corrente tramite `GET /api/participant/me`.
- Per associare un utente a un partecipante, esporre nel token Keycloak il claim `participant_id`.
- Se `participant_id` non è presente, il backend usa come fallback lo username Keycloak uguale al nome del partecipante.

## Tunnel (rapido)
- ngrok: `ngrok http 8080`
- Cloudflare: `cloudflared tunnel --url http://<HOST-BACKEND>:8080`
