package com.fantasta.service;

import com.fantasta.dto.NewspaperMatchDto;
import com.fantasta.dto.NewspaperFantasfigaDto;
import com.fantasta.dto.NewspaperPlayerDto;
import com.fantasta.dto.NewspaperPreviewDto;
import com.fantasta.dto.NewspaperStandingDto;
import com.fantasta.dto.NewspaperTeamSheetDto;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class NewspaperImportService {
    private static final Pattern POSITION_AND_TEAM = Pattern.compile("^\\s*(\\d+)\\s+(.+?)\\s*$");
    private static final Pattern FORMATION = Pattern.compile("^Formazione:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER = Pattern.compile("^\\(([^)]+)\\)\\s*(.+)$");
    private final DataFormatter formatter = new DataFormatter(Locale.ITALY);

    public NewspaperPreviewDto preview(InputStream input) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet league = findLeagueSheet(workbook);
            if (league == null) {
                throw new IllegalArgumentException("Nel file non è stato trovato il foglio del campionato");
            }

            NewspaperPreviewDto preview = new NewspaperPreviewDto();
            preview.competition = league.getSheetName();
            int fixtureHeader = findRow(league, "Casa", "Trasferta", "Risultato");
            if (fixtureHeader < 0) {
                throw new IllegalArgumentException("Nel foglio del campionato non è stato trovato il calendario");
            }
            readMatches(league, fixtureHeader + 1, preview);
            readStandings(league, preview);
            readTeamSheets(workbook, preview);
            Set<String> teams = new LinkedHashSet<>();
            preview.matches.forEach(match -> {
                teams.add(match.home);
                teams.add(match.away);
            });
            preview.teamsFound = teams.size();
            preview.calculated = !preview.matches.isEmpty() && preview.matches.stream()
                    .allMatch(match -> isCalculated(match.result));
            if (preview.calculated) calculateFantasfiga(preview);
            preview.message = preview.calculated
                    ? "Giornata calcolata: risultati disponibili per l’anteprima editoriale."
                    : "Il file è valido, ma la giornata non è ancora stata calcolata da FantaMaster.";
            return preview;
        }
    }

    private void readTeamSheets(Workbook workbook, NewspaperPreviewDto preview) {
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    Matcher header = FORMATION.matcher(text(row, column));
                    if (!header.matches()) continue;
                    NewspaperTeamSheetDto teamSheet = new NewspaperTeamSheetDto(header.group(1).trim());
                    readPlayers(sheet, rowIndex + 1, column, teamSheet);
                    if (!teamSheet.players.isEmpty()) preview.teamSheets.add(teamSheet);
                }
            }
        }
    }

    private void readPlayers(Sheet sheet, int startRow, int column, NewspaperTeamSheetDto teamSheet) {
        for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String playerValue = text(row, column);
            if (FORMATION.matcher(playerValue).matches()) break;
            Matcher player = PLAYER.matcher(playerValue);
            if (!player.matches()) {
                if (!teamSheet.players.isEmpty()) break;
                continue;
            }
            int position = teamSheet.players.size();
            teamSheet.players.add(new NewspaperPlayerDto(player.group(2).trim(), player.group(1).trim(),
                    text(row, column + 1), text(row, column + 2), position < 11,
                    isCounted(text(row, column + 3))));
        }
    }

    private boolean isCounted(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ITALY);
        return !normalized.isBlank() && !"-".equals(normalized) && !"no".equals(normalized)
                && !"false".equals(normalized) && !"0".equals(normalized);
    }

    private void calculateFantasfiga(NewspaperPreviewDto preview) {
        List<TeamScore> scores = new ArrayList<>();
        for (NewspaperMatchDto match : preview.matches) {
            double[] points = scoreValues(match.result);
            if (points == null) return;
            int homeGoals = goals(points[0], points[1]);
            int awayGoals = goals(points[1], points[0]);
            scores.add(new TeamScore(match.home, points[0], resultPoints(homeGoals, awayGoals)));
            scores.add(new TeamScore(match.away, points[1], resultPoints(awayGoals, homeGoals)));
        }
        if (scores.size() < 2) return;
        for (TeamScore team : scores) {
            double hypothetical = 0;
            for (TeamScore opponent : scores) {
                if (opponent == team) continue;
                int teamGoals = goals(team.score, opponent.score);
                int opponentGoals = goals(opponent.score, team.score);
                hypothetical += resultPoints(teamGoals, opponentGoals);
            }
            double expected = hypothetical / (scores.size() - 1);
            double delta = team.actualPoints - expected;
            preview.fantasfiga.add(new NewspaperFantasfigaDto(team.team, format(team.score), team.actualPoints,
                    rounded(expected), rounded(delta), verdict(delta)));
        }
        preview.fantasfiga.sort(Comparator.comparingDouble((NewspaperFantasfigaDto row) -> row.delta).reversed());
    }

    private double[] scoreValues(String result) {
        String[] values = result.split("\\s*[-–]\\s*");
        if (values.length != 2) return null;
        try {
            return new double[] { Double.parseDouble(values[0].replace(',', '.')),
                    Double.parseDouble(values[1].replace(',', '.')) };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int goals(double score, double opponentScore) {
        int goals = score >= 66 ? (int) Math.floor((score - 66) / 6) + 1 : 0;
        if (score < 66 && opponentScore < 66 && score - opponentScore >= 6) return 1;
        return goals;
    }

    private double resultPoints(int goals, int opponentGoals) {
        return goals > opponentGoals ? 3 : goals == opponentGoals ? 1 : 0;
    }

    private double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String verdict(double delta) {
        if (delta >= 1) return "Fortuna sfacciata";
        if (delta >= .35) return "Calendario favorevole";
        if (delta <= -1) return "FantaSfiga totale";
        if (delta <= -.35) return "Calendario crudele";
        return "In linea con il campo";
    }

    private String format(double value) {
        return value == Math.rint(value) ? String.format(Locale.ITALY, "%.0f", value)
                : String.format(Locale.ITALY, "%.1f", value);
    }

    private record TeamScore(String team, double score, double actualPoints) { }

    private Sheet findLeagueSheet(Workbook workbook) {
        Sheet fallback = null;
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            if (findRow(sheet, "Casa", "Trasferta", "Risultato") >= 0) return sheet;
            if (fallback == null && contains(sheet, "Classifica:")) fallback = sheet;
        }
        return fallback;
    }

    private void readMatches(Sheet sheet, int start, NewspaperPreviewDto preview) {
        for (int rowIndex = start; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String home = text(row, 0);
            String away = text(row, 1);
            if (home.isBlank() || away.isBlank() || home.startsWith("Classifica:")) break;
            preview.matches.add(new NewspaperMatchDto(home, away, normalizedResult(text(row, 2))));
        }
    }

    private void readStandings(Sheet sheet, NewspaperPreviewDto preview) {
        int header = findContainingRow(sheet, "Classifica:");
        if (header < 0) return;
        for (int rowIndex = header + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String first = text(row, 0);
            Matcher matcher = POSITION_AND_TEAM.matcher(first);
            if (!matcher.matches()) {
                if (!preview.standings.isEmpty()) break;
                continue;
            }
            preview.standings.add(new NewspaperStandingDto(
                    Integer.parseInt(matcher.group(1)), matcher.group(2), text(row, 1), text(row, 2)));
        }
    }

    private int findRow(Sheet sheet, String... values) {
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            boolean matches = true;
            for (int cell = 0; cell < values.length; cell++) {
                if (!values[cell].equalsIgnoreCase(text(row, cell))) {
                    matches = false;
                    break;
                }
            }
            if (matches) return rowIndex;
        }
        return -1;
    }

    private boolean contains(Sheet sheet, String value) {
        return findContainingRow(sheet, value) >= 0;
    }

    private int findContainingRow(Sheet sheet, String value) {
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            if (text(sheet.getRow(rowIndex), 0).startsWith(value)) return rowIndex;
        }
        return -1;
    }

    private String text(Row row, int cell) {
        if (row == null || row.getCell(cell) == null) return "";
        return formatter.formatCellValue(row.getCell(cell)).trim();
    }

    private String normalizedResult(String result) {
        return result.isBlank() ? "-" : result;
    }

    private boolean isCalculated(String result) {
        return result != null && !result.isBlank() && !"-".equals(result);
    }
}
