package com.fantasta.service;

import com.fantasta.dto.MarketDepartureImportResult;
import com.fantasta.dto.MarketPlayerChangeDto;
import com.fantasta.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class MarketDepartureImportService {
    @Inject MercatoService mercatoService;

    @Transactional
    public MarketDepartureImportResult importDepartures(InputStream in, boolean confirm) throws Exception {
        MercatoConfigEntity config = mercatoService.requireConfiguredMarket();
        if (config.partitiImportati) {
            throw new IllegalStateException("Il file dei partiti è già stato confermato per questa sessione");
        }
        MarketDepartureImportResult result = new MarketDepartureImportResult(!confirm);
        Map<PlayerEntity, Double> matched = new LinkedHashMap<>();
        for (Departure row : parse(in)) {
            List<PlayerEntity> candidates = PlayerEntity.list("lower(name) = ?1", normalize(row.name()));
            if (candidates.size() != 1) {
                result.errors.add("Calciatore non riconosciuto o ambiguo: " + row.name());
                continue;
            }
            PlayerEntity player = candidates.get(0);
            if (player.role != row.role() || !normalize(player.team).equals(normalize(row.team()))) {
                result.errors.add("Ruolo o squadra non corrispondenti per " + row.name());
                continue;
            }
            List<RosterEntity> owners = RosterEntity.list("player", player);
            if (owners.size() != 1 || !matchesOwner(row.owner(), owners.get(0).participant.name)) {
                result.errors.add("Proprietario non corrispondente per " + row.name() + ": " + row.owner());
                continue;
            }
            matched.put(player, row.value());
            result.players.add(new MarketPlayerChangeDto(player.id, player.name, player.team, player.team,
                    player.role.name(), player.valore == null ? 0D : player.valore, row.value(), true,
                    owners.stream().map(owner -> owner.participant.name).toList()));
        }
        if (!confirm || !result.errors.isEmpty()) return result;
        matched.forEach((player, value) -> {
            player.valore = value;
            player.active = false;
            player.deletedAt = Instant.now();
            player.departureSessionCode = config.sessionCode;
        });
        config.partitiImportati = true;
        config.quotazioniAggiornate = false;
        config.quotazioniAggiornateAt = null;
        return result;
    }

    static List<Departure> parse(InputStream in) throws Exception {
        List<Departure> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet("Giocatori Partiti");
            if (sheet == null) throw new IllegalArgumentException("Foglio Giocatori Partiti mancante");
            DataFormatter formatter = new DataFormatter(Locale.ITALY);
            for (Row row : sheet) {
                String status = text(row, 6, formatter);
                String rawName = text(row, 3, formatter);
                Role role = Role.fromString(text(row, 2, formatter));
                if (status.isBlank() && rawName.isBlank() && role == null) continue;
                String name = rawName.replaceFirst("(?i)^ZZZ\\s*-\\s*Partito\\s*-\\s*", "").trim();
                String owner = text(row, 1, formatter);
                String team = text(row, 4, formatter);
                double value;
                Cell cell = row.getCell(5);
                try {
                    value = cell != null && cell.getCellType() == CellType.NUMERIC
                            ? cell.getNumericCellValue()
                            : Double.parseDouble(text(row, 5, formatter).replace(',', '.'));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("Quotazione non valida alla riga " + (row.getRowNum() + 1));
                }
                if (!status.equalsIgnoreCase("Partito") || role == null || name.isBlank()
                        || owner.isBlank() || team.isBlank() || !Double.isFinite(value) || value < 0) {
                    throw new IllegalArgumentException("Dati non validi alla riga " + (row.getRowNum() + 1));
                }
                if (!names.add(normalize(name))) throw new IllegalArgumentException("Calciatore duplicato: " + name);
                result.add(new Departure(owner, name, team, role, value));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Nessun giocatore partito trovato");
        return result;
    }

    private static String text(Row row, int column, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static boolean matchesOwner(String supplied, String actual) {
        if (ownerKey(supplied).equals(ownerKey(actual))) return true;
        int start = supplied.lastIndexOf(" - (");
        return start >= 0 && supplied.endsWith(")")
                && ownerKey(supplied.substring(start + 4, supplied.length() - 1)).equals(ownerKey(actual));
    }

    private static String ownerKey(String name) {
        return switch (normalize(name)) {
            case "em fallét", "em fallet" -> "em fallet";
            case "johnson oil", "johnsons oil" -> "johnsons oil";
            case "3/4 e 1 gazzosa", "34 e 1 gazzosa" -> "34 e 1 gazzosa";
            default -> normalize(name);
        };
    }

    record Departure(String owner, String name, String team, Role role, double value) {}
}
