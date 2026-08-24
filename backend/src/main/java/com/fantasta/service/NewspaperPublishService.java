package com.fantasta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasta.dto.NewspaperBriefDto;
import com.fantasta.dto.NewspaperEditionDto;
import com.fantasta.dto.NewspaperMatchDto;
import com.fantasta.dto.NewspaperPublishStatusDto;
import com.fantasta.dto.NewspaperStandingDto;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;

@ApplicationScoped
public class NewspaperPublishService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private final ObjectMapper mapper = new ObjectMapper();

    @ConfigProperty(name = "app.environment", defaultValue = "dev") String environment;
    @ConfigProperty(name = "app.newspaper.publish-enabled", defaultValue = "false") boolean publishEnabled;
    @ConfigProperty(name = "app.newspaper.cloudflare-account-id") Optional<String> accountId;
    @ConfigProperty(name = "app.newspaper.cloudflare-api-token") Optional<String> apiToken;
    @ConfigProperty(name = "app.newspaper.cloudflare-project") Optional<String> projectName;

    public NewspaperPublishStatusDto status() {
        if (!"prod".equalsIgnoreCase(environment)) {
            return new NewspaperPublishStatusDto(false, environment,
                    "Pubblicazione disabilitata: l’anteprima DEV resta soltanto locale.");
        }
        if (!publishEnabled) {
            return new NewspaperPublishStatusDto(false, environment,
                    "Pubblicazione Gazzetta non ancora attivata in PROD.");
        }
        if (!hasDedicatedConfiguration()) {
            return new NewspaperPublishStatusDto(false, environment,
                    "Configurazione del progetto Cloudflare Pages Gazzetta incompleta.");
        }
        return new NewspaperPublishStatusDto(true, environment, "Pubblicazione Gazzetta disponibile.");
    }

    public void publish(String editionJson, Path image, String contentType, long imageSize) throws Exception {
        NewspaperPublishStatusDto status = status();
        if (!status.enabled()) throw new IllegalStateException(status.message());
        validateImage(image, contentType, imageSize);
        NewspaperEditionDto edition = mapper.readValue(editionJson, NewspaperEditionDto.class);
        validateEdition(edition);

        Path directory = Files.createTempDirectory("fantacaporaso-gazzetta-");
        try {
            String extension = extension(contentType);
            String imageName = "copertina." + extension;
            Files.copy(image, directory.resolve(imageName), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(directory.resolve("index.html"), render(edition, imageName), StandardCharsets.UTF_8);
            deploy(directory);
        } finally {
            deleteRecursively(directory);
        }
    }

    private boolean hasDedicatedConfiguration() {
        String project = projectName.orElse("").trim();
        return accountId.filter(value -> !value.isBlank()).isPresent()
                && apiToken.filter(value -> !value.isBlank()).isPresent()
                && !project.isBlank()
                && !"fantacaporaso".equalsIgnoreCase(project);
    }

    private void validateImage(Path image, String contentType, long size) throws IOException {
        if (image == null || !Files.isRegularFile(image)) throw new IllegalArgumentException("Immagine di copertina mancante");
        if (size <= 0 || size > MAX_IMAGE_BYTES) throw new IllegalArgumentException("L’immagine deve essere inferiore a 5 MB");
        if (!("image/jpeg".equals(contentType) || "image/png".equals(contentType) || "image/webp".equals(contentType))) {
            throw new IllegalArgumentException("Formato immagine non supportato: usa JPG, PNG o WebP");
        }
    }

    private void validateEdition(NewspaperEditionDto edition) {
        required(edition.headline, "Titolo principale", 160);
        required(edition.standfirst, "Sottotitolo", 500);
        required(edition.leadTitle, "Titolo articolo", 160);
        required(edition.leadText, "Testo articolo", 1200);
        required(edition.fantasfigaTitle, "Titolo FantaSfiga", 160);
        required(edition.fantasfigaText, "Testo FantaSfiga", 1200);
        if (edition.matches == null || edition.matches.isEmpty()) throw new IllegalArgumentException("Risultati mancanti");
        if (edition.matches.size() > 20 || edition.standings.size() > 30 || edition.briefs.size() > 6) {
            throw new IllegalArgumentException("Edizione troppo estesa");
        }
    }

    private void required(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " mancante");
        if (value.length() > maxLength) throw new IllegalArgumentException(label + " troppo lungo");
    }

    private void deploy(Path directory) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("wrangler", "pages", "deploy", directory.toString(),
                "--project-name=" + projectName.orElseThrow(), "--branch=main");
        builder.environment().put("CLOUDFLARE_ACCOUNT_ID", accountId.orElseThrow());
        builder.environment().put("CLOUDFLARE_API_TOKEN", apiToken.orElseThrow());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Cloudflare Pages ha rifiutato la pubblicazione: " + abbreviate(output));
    }

    private String render(NewspaperEditionDto edition, String imageName) {
        String matches = edition.matches.stream().map(this::matchHtml).reduce("", String::concat);
        String standings = edition.standings.stream().limit(10).map(this::standingHtml).reduce("", String::concat);
        String briefs = edition.briefs.stream().map(this::briefHtml).reduce("", String::concat);
        return """
                <!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>La Gazzetta di FantaCaporaso</title><style>%s</style></head><body><main class="paper">
                <header><small>LA GAZZETTA DI</small><h1>FantaCaporaso</h1><b>%s</b></header>
                <section class="lead"><label>%s</label><h2>%s</h2><p>%s</p></section>
                <section class="fantasfiga"><label>FANTASFIGA</label><h3>%s</h3><p>%s</p></section><div class="columns">
                <section class="column"><img src="%s" alt="Copertina"><h3>%s</h3><p>%s</p></section>
                <section class="column"><h4>RISULTATI</h4>%s<h4>CLASSIFICA</h4>%s</section>
                <aside class="column"><h4>BREVI DAL CAMPO</h4>%s</aside></div>
                <footer>FANTACAPORASO · EDIZIONE UFFICIALE</footer></main></body></html>
                """.formatted(css(), escape(edition.matchday), escape(edition.kicker), escape(edition.headline),
                escape(edition.standfirst), escape(edition.fantasfigaTitle), escape(edition.fantasfigaText), imageName,
                escape(edition.leadTitle), escape(edition.leadText), matches, standings, briefs);
    }

    private String matchHtml(NewspaperMatchDto match) {
        return "<div class=\"result\"><span>" + escape(match.home) + "<br>" + escape(match.away)
                + "</span><span><small>" + escape(match.result) + "</small><br><b>" + escape(match.goals) + "</b></span></div>";
    }

    private String standingHtml(NewspaperStandingDto row) {
        return "<div class=\"standing\"><b>" + row.position + "</b><span>" + escape(row.team)
                + "</span><strong>" + escape(row.points) + "</strong></div>";
    }

    private String briefHtml(NewspaperBriefDto brief) {
        return "<h3>" + escape(brief.title) + "</h3><p>" + escape(brief.text) + "</p>";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String extension(String contentType) {
        return switch (contentType) { case "image/png" -> "png"; case "image/webp" -> "webp"; default -> "jpg"; };
    }

    private String abbreviate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private String css() {
        return "*{box-sizing:border-box}body{margin:0;padding:22px;background:#151b18;color:#211c17;font-family:Georgia,serif}.paper{max-width:1100px;margin:auto;padding:24px;background:#f1ead8}header{text-align:center;border-bottom:6px double #211c17}header small{font:700 12px Arial;letter-spacing:.25em}header h1{margin:0;color:#b32219;font:italic 900 clamp(46px,9vw,96px)/.9 Georgia}header b{font:700 11px Arial}.lead{text-align:center;padding:18px;border-bottom:4px double #211c17}.lead label{color:#b32219;font:800 12px Arial}.lead h2{margin:8px;font-size:clamp(36px,7vw,72px);line-height:.92}.lead p{font-weight:700}.fantasfiga{margin:16px 18px 0;padding:14px 18px;border:2px solid #b32219;background:#eadfc7}.fantasfiga label{color:#b32219;font:900 12px Arial;letter-spacing:.16em}.fantasfiga h3{margin:5px 0;font-size:25px}.fantasfiga p{margin:0;line-height:1.45}.columns{display:grid;grid-template-columns:1.4fr 1fr 1fr}.column{padding:18px}.column+.column{border-left:1px solid #897c66}.column img{width:100%;max-height:430px;object-fit:cover}.column h4{padding:7px;background:#211c17;color:#f1ead8;font:800 13px Arial}.column h3{border-top:3px solid #b32219;padding-top:7px}.column p{line-height:1.45;text-align:justify}.result{display:flex;justify-content:space-between;padding:8px 2px;border-bottom:1px solid #a99c85;font:700 12px Arial}.standing{display:grid;grid-template-columns:20px 1fr auto;gap:5px;padding:5px 2px;border-bottom:1px dotted #9d907a;font:700 11px Arial}footer{border-top:2px solid #211c17;padding:9px;text-align:center;font:700 10px Arial}@media(max-width:760px){body{padding:7px}.paper{padding:13px}.columns{grid-template-columns:1fr}.column+.column{border-left:0;border-top:1px solid #897c66}}";
    }
}
