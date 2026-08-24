package com.fantasta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasta.model.AuctionHistoryBidEntity;
import com.fantasta.model.AuctionHistoryEntity;
import com.fantasta.model.AuctionHistorySessionEntity;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AuctionArchiveService {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @ConfigProperty(name = "app.environment", defaultValue = "dev") String environment;
    @ConfigProperty(name = "app.auction-archive.output-dir", defaultValue = "target/auction-archive") String outputDir;
    @ConfigProperty(name = "app.auction-archive.landing-dir", defaultValue = "../landing-page") String landingDir;
    @ConfigProperty(name = "app.auction-archive.publish-enabled", defaultValue = "false") boolean publishEnabled;
    @ConfigProperty(name = "app.auction-archive.cloudflare-account-id") Optional<String> accountId;
    @ConfigProperty(name = "app.auction-archive.cloudflare-api-token") Optional<String> apiToken;
    @ConfigProperty(name = "app.auction-archive.cloudflare-project", defaultValue = "fantacaporaso") String projectName;

    /**
     * Generates a complete archive from the current database. Publishing is deliberately refused
     * unless both the environment and the explicit switch identify production.
     */
    public void generateAndPublishBestEffort(Long sessionId) {
        try {
            Path archive = QuarkusTransaction.requiringNew().call(() -> {
                AuctionHistorySessionEntity session = AuctionHistorySessionEntity.findById(sessionId);
                return session == null ? null : generate();
            });
            if (archive == null) return;
            if (!canPublish()) {
                updateStatus(sessionId, "LOCAL_ONLY", null, false);
                return;
            }
            requireProdCredentials();
            publishWithWrangler(archive);
            updateStatus(sessionId, "PUBLISHED", null, true);
            Log.infof("Archivio storico PROD pubblicato su Cloudflare Pages per la sessione %s", sessionId);
        } catch (Exception error) {
            updateStatus(sessionId, "FAILED", abbreviate(error.getMessage()), true);
            Log.debugf(error, "Pubblicazione archivio storico fallita per la sessione %s", sessionId);
        }
    }

    boolean canPublish() {
        return publishEnabled && "prod".equalsIgnoreCase(environment);
    }

    private Path generate() throws IOException {
        Path root = Path.of(outputDir).toAbsolutePath().normalize();
        copyLandingPage(root);
        Path historyDir = root.resolve("storico");
        Files.createDirectories(historyDir);
        List<Map<String, Object>> sessions = AuctionHistorySessionEntity.<AuctionHistorySessionEntity>list(
                        "order by closedAt desc").stream().map(this::sessionData).toList();
        Files.writeString(historyDir.resolve("data.json"), mapper.writeValueAsString(Map.of("sessions", sessions)),
                StandardCharsets.UTF_8);
        Files.writeString(historyDir.resolve("index.html"), archiveHtml(), StandardCharsets.UTF_8);
        return root;
    }

    private void copyLandingPage(Path target) throws IOException {
        Path source = Path.of(landingDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalStateException("Cartella landing page non disponibile: " + source);
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void requireProdCredentials() {
        if (accountId.isEmpty() || accountId.get().isBlank()
                || apiToken.isEmpty() || apiToken.get().isBlank() || projectName.isBlank()) {
            throw new IllegalStateException("Credenziali Cloudflare Pages PROD incomplete");
        }
    }

    private void publishWithWrangler(Path archive) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("wrangler", "pages", "deploy",
                archive.toString(), "--project-name=" + projectName, "--branch=main");
        processBuilder.environment().put("CLOUDFLARE_ACCOUNT_ID", accountId.orElseThrow());
        processBuilder.environment().put("CLOUDFLARE_API_TOKEN", apiToken.orElseThrow());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Cloudflare Pages ha rifiutato il deploy: " + abbreviate(output));
        }
    }

    private void updateStatus(Long sessionId, String status, String error, boolean attempted) {
        QuarkusTransaction.requiringNew().run(() -> {
            AuctionHistorySessionEntity session = AuctionHistorySessionEntity.findById(sessionId);
            if (session == null) return;
            session.publishStatus = status;
            session.publishError = error;
            if (attempted) session.publishAttempts = session.publishAttempts + 1;
            session.publishedAt = "PUBLISHED".equals(status) ? java.time.LocalDateTime.now() : session.publishedAt;
        });
    }

    private Map<String, Object> sessionData(AuctionHistorySessionEntity session) {
        List<Map<String, Object>> rounds = AuctionHistoryEntity.<AuctionHistoryEntity>list(
                        "sessionCode = ?1 order by closedAt desc", session.sessionCode).stream()
                .map(this::historyData).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", session.sessionCode);
        result.put("label", session.label);
        result.put("closedAt", session.closedAt);
        result.put("rounds", rounds);
        return result;
    }

    private Map<String, Object> historyData(AuctionHistoryEntity history) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("player", history.playerName);
        result.put("team", history.playerTeam);
        result.put("role", history.playerRole);
        result.put("value", history.playerValue);
        result.put("winner", history.winnerName);
        result.put("price", history.winningAmount);
        result.put("bids", AuctionHistoryBidEntity.<AuctionHistoryBidEntity>list(
                        "history = ?1 order by amount desc", history).stream()
                .map(bid -> Map.of("participant", bid.participantName, "amount", bid.amount)).toList());
        return result;
    }

    private String archiveHtml() {
        return """
                <!doctype html><html lang=\"it\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                <title>Storico puntate | FantaCaporaso</title><style>
                :root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#07120f;color:#effaf5;font:16px system-ui,sans-serif}main{max-width:1100px;margin:auto;padding:32px 18px}a{color:#83e6b7}h1{margin-bottom:8px}.muted{color:#9eb8ad}input,select{background:#10231d;color:#fff;border:1px solid #315448;border-radius:10px;padding:12px;margin:12px 8px 22px 0}.session{margin:26px 0}.card{background:#10231d;border:1px solid #29483d;border-radius:12px;padding:14px;margin:10px 0}.bids{display:flex;gap:8px;flex-wrap:wrap}.bid{background:#18352b;border-radius:20px;padding:6px 10px}.hidden{display:none}</style></head>
                <body><main><a href=\"/\">← FantaCaporaso</a><h1>Storico delle puntate</h1><p class=\"muted\">Aste competitive concluse, divise per sessione.</p>
                <input id=\"q\" type=\"search\" placeholder=\"Cerca calciatore\" autocomplete=\"off\"><select id=\"session\"><option value=\"\">Tutti i mercati</option></select><div id=\"content\"></div></main>
                <script>const esc=s=>String(s??'').replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[c]));let data=[];const q=document.querySelector('#q'),sel=document.querySelector('#session'),out=document.querySelector('#content');
                function render(){const term=q.value.trim().toLowerCase(),code=sel.value;out.innerHTML=data.filter(s=>!code||s.code===code).map(s=>{const rows=s.rounds.filter(r=>!term||r.player.toLowerCase().includes(term));if(!rows.length)return'';return `<section class=\"session\"><h2>${esc(s.label)}</h2><p class=\"muted\">Conclusa il ${new Date(s.closedAt).toLocaleString('it-IT')}</p>${rows.map(r=>`<article class=\"card\"><strong>${esc(r.player)} · ${esc(r.team)}</strong><p>Assegnato a ${esc(r.winner)} per <b>${r.price}</b></p><div class=\"bids\">${r.bids.map(b=>`<span class=\"bid\">${esc(b.participant)}: ${b.amount}</span>`).join('')}</div></article>`).join('')}</section>`}).join('')||'<p>Nessun risultato.</p>'}
                fetch('data.json',{cache:'no-store'}).then(r=>r.json()).then(x=>{data=x.sessions||[];data.forEach(s=>sel.insertAdjacentHTML('beforeend',`<option value=\"${esc(s.code)}\">${esc(s.label)}</option>`));render()});q.addEventListener('input',render);sel.addEventListener('change',render);</script></body></html>
                """;
    }

    private String abbreviate(String value) {
        if (value == null) return "Errore non specificato";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
